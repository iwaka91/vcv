package id.tg.vcv.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.tg.vcv.MainActivity
import id.tg.vcv.R
import id.tg.vcv.VcvApp
import id.tg.vcv.data.BrowserChoice
import id.tg.vcv.data.Inspection
import id.tg.vcv.data.Kind
import id.tg.vcv.data.PageDefault
import id.tg.vcv.data.PrepareResult
import id.tg.vcv.data.ProjectInspector
import id.tg.vcv.data.ProjectRecord
import id.tg.vcv.data.UriInfo
import id.tg.vcv.server.ServerController
import id.tg.vcv.server.VcvServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipException

enum class Screen { HOME, QR, BROWSER, SETTINGS, ABOUT }

/** Ke mana proyek dibuka. DEFAULT = sesuai pengaturan "Peramban bawaan". */
enum class Target { DEFAULT, CHROME, INTERNAL, QR }

/** Proyek hasil import yang belum disimpan ke daftar (menunggu jawaban dialog). */
data class PendingImport(
    val draft: ProjectRecord,
    val inspection: Inspection,
    val pickedUri: Uri?,           // untuk dilepas izinnya bila batal
    val relativeRefs: Int = 0,
)

/** Lembar "Pilih halaman": untuk proyek baru (pending) atau proyek di daftar (record). */
data class PagePick(
    val title: String,
    val pages: List<String>,
    val selected: String,
    val pending: PendingImport? = null,
    val record: ProjectRecord? = null,
)

sealed class DialogState {
    data class Info(val title: String, val body: String) : DialogState()
    data class AltEntry(val pending: PendingImport) : DialogState()
    data class Replace(val pending: PendingImport, val existing: ProjectRecord) : DialogState()
    data class SuggestFolder(val pending: PendingImport) : DialogState()
    data class SourceMissing(val record: ProjectRecord) : DialogState()
    data class SharedGone(val record: ProjectRecord) : DialogState()
    data class StopSharing(val current: ProjectRecord, val next: ProjectRecord, val target: Target) : DialogState()
    data class Rename(val record: ProjectRecord) : DialogState()
    data object ConfirmClear : DialogState()
}

sealed class UiEvent {
    data class LaunchBrowser(val url: String) : UiEvent()
    data object PickZip : UiEvent()
    data object PickHtml : UiEvent()
    data object PickFolder : UiEvent()
    data class SaveZip(val suggestedName: String) : UiEvent()
    data class SaveHtml(val suggestedName: String) : UiEvent()
    data class Toast(val message: String) : UiEvent()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val vcv = app as VcvApp
    private val store = vcv.store
    private val settingsStore = vcv.settings
    private val preparer = vcv.preparer
    private val resolver = app.contentResolver

    val projects = store.items
    val settings = settingsStore.state
    val server = ServerController.state

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _dialog = MutableStateFlow<DialogState?>(null)
    val dialog: StateFlow<DialogState?> = _dialog.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _menuFor = MutableStateFlow<ProjectRecord?>(null)
    val menuFor: StateFlow<ProjectRecord?> = _menuFor.asStateFlow()

    private val _pagePick = MutableStateFlow<PagePick?>(null)
    val pagePick: StateFlow<PagePick?> = _pagePick.asStateFlow()

    private val _importSheet = MutableStateFlow(false)
    val importSheet: StateFlow<Boolean> = _importSheet.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Proyek WA yang sedang menunggu lokasi simpan. */
    private var savingRecordId: String? = null

    // ------------------------------------------------------------------ navigasi & sheet

    fun go(s: Screen) { _screen.value = s }
    fun showImportSheet(show: Boolean) { _importSheet.value = show }
    fun showMenu(r: ProjectRecord?) { _menuFor.value = r }
    fun dismissDialog() { _dialog.value = null }
    fun showDialog(d: DialogState) { _dialog.value = d }

    fun requestPick(kind: Kind) {
        _importSheet.value = false
        when (kind) {
            Kind.ZIP -> send(UiEvent.PickZip)
            Kind.HTML -> send(UiEvent.PickHtml)
            Kind.FOLDER -> send(UiEvent.PickFolder)
            Kind.SHARED -> Unit
        }
    }

    private fun send(e: UiEvent) { viewModelScope.launch { _events.send(e) } }
    private fun toast(msg: String) = send(UiEvent.Toast(msg))

    // ------------------------------------------------------------------ IMPORT dari pemilih file

    fun onPicked(kind: Kind, uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _busy.value = "Memeriksa proyek…"
            val result = withContext(Dispatchers.IO) { inspectPicked(kind, uri) }
            _busy.value = null
            result?.let { continueImport(it) }
        }
    }

    /** Mengembalikan PendingImport, atau null bila sudah menampilkan dialog error. */
    private fun inspectPicked(kind: Kind, uri: Uri): PendingImport? {
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val isTree = kind == Kind.FOLDER
        val display = UriInfo.displayName(vcv, uri, isTree)
        val label = UriInfo.sourceLabel(vcv, uri, display, isTree)
        val draft = ProjectRecord(
            id = UUID.randomUUID().toString(),
            name = UriInfo.stripExtension(display),
            fileName = display,
            kind = kind,
            uri = uri.toString(),
            entry = "index.html",
            sourceLabel = label,
            lastOpened = System.currentTimeMillis(),
        )
        return try {
            when (kind) {
                Kind.ZIP -> {
                    val names = ProjectInspector.listZip { resolver.openInputStream(uri) ?: throw IOException("Tidak bisa dibaca") }
                    if (names.isEmpty()) throw ZipException("kosong")
                    pendingOrNoHtml(draft, ProjectInspector.chooseEntry(names), uri)
                }
                Kind.FOLDER -> {
                    val root = DocumentFile.fromTreeUri(vcv, uri) ?: throw IOException("Folder tidak bisa dibaca")
                    pendingOrNoHtml(draft, ProjectInspector.chooseEntry(ProjectInspector.listFolder(root)), uri)
                }
                Kind.HTML -> {
                    val text = resolver.openInputStream(uri)?.use { headText(it.readBytes()) } ?: ""
                    PendingImport(
                        draft.copy(entry = display),
                        Inspection(display, true),
                        uri,
                        relativeRefs = ProjectInspector.countRelativeRefs(text),
                    )
                }
                Kind.SHARED -> null
            }
        } catch (e: ZipException) {
            store.releaseIfUnused(uri)
            _dialog.value = DialogState.Info("Bukan file ZIP yang valid", "File ini tidak bisa dibuka sebagai ZIP. Kembali ke beranda.")
            null
        } catch (e: Exception) {
            store.releaseIfUnused(uri)
            _dialog.value = DialogState.Info("Gagal membaca proyek", e.message ?: "Terjadi kesalahan.")
            null
        }
    }

    private fun headText(bytes: ByteArray): String =
        String(bytes, 0, minOf(bytes.size, 5_000_000), Charsets.UTF_8)

    private fun pendingOrNoHtml(draft: ProjectRecord, insp: Inspection, uri: Uri?): PendingImport? {
        if (insp.entry == null) {
            if (uri != null) store.releaseIfUnused(uri)
            if (draft.copyPath.isNotEmpty()) File(draft.copyPath).delete()
            _dialog.value = DialogState.Info(
                "File HTML tidak ditemukan",
                "Tidak ada file .html di dalam proyek ini. Kembali ke beranda."
            )
            return null
        }
        return PendingImport(draft.copy(entry = insp.entry), insp, uri)
    }

    // ------------------------------------------------------------------ IMPORT dari WhatsApp / aplikasi lain

    fun onSharedIntent(uri: Uri, mime: String?, fromWhatsApp: Boolean) {
        viewModelScope.launch {
            _busy.value = "Menerima file…"
            val result = withContext(Dispatchers.IO) { inspectShared(uri, mime, fromWhatsApp) }
            _busy.value = null
            result?.let { continueImport(it) }
        }
    }

    private fun inspectShared(uri: Uri, mime: String?, fromWhatsApp: Boolean): PendingImport? {
        val display = UriInfo.displayName(vcv, uri)
        val lower = display.lowercase()
        val dir = File(vcv.filesDir, "shared").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val tmp = File(dir, "$id.tmp")
        try {
            resolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: throw IOException("File tidak bisa dibaca")
        } catch (e: Exception) {
            tmp.delete()
            _dialog.value = DialogState.Info("Gagal menerima file", e.message ?: "File tidak bisa dibaca.")
            return null
        }
        val magicZip = runCatching {
            FileInputStream(tmp).use { s -> val b = ByteArray(4); s.read(b) == 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() }
        }.getOrDefault(false)
        val ext = when {
            lower.endsWith(".zip") || magicZip -> "zip"
            lower.endsWith(".html") || lower.endsWith(".htm") || mime == "text/html" -> "html"
            else -> null
        }
        if (ext == null) {
            tmp.delete()
            _dialog.value = DialogState.Info("File tidak didukung", "Kirim file .zip atau .html ke VcV. Kembali ke beranda.")
            return null
        }
        val copy = File(dir, "$id.$ext")
        tmp.renameTo(copy)
        val fileName = if (lower.endsWith(".$ext") || (ext == "html" && lower.endsWith(".htm"))) display
        else "$display.$ext"
        val draft = ProjectRecord(
            id = id,
            name = UriInfo.stripExtension(fileName),
            fileName = fileName,
            kind = Kind.SHARED,
            uri = "",
            entry = "index.html",
            sourceLabel = if (fromWhatsApp) "Dari WhatsApp" else "Dari aplikasi lain",
            lastOpened = System.currentTimeMillis(),
            sharedTag = if (fromWhatsApp) "WA" else "APP",
            sharedExt = ext,
            copyPath = copy.absolutePath,
        )
        return try {
            if (ext == "zip") {
                val names = ProjectInspector.listZip { FileInputStream(copy) }
                pendingOrNoHtml(draft, ProjectInspector.chooseEntry(names), null)
            } else {
                val text = headText(copy.readBytes())
                PendingImport(draft.copy(entry = fileName), Inspection(fileName, true), null, ProjectInspector.countRelativeRefs(text))
            }
        } catch (e: Exception) {
            copy.delete()
            _dialog.value = DialogState.Info("Bukan file ZIP yang valid", "File ini tidak bisa dibuka. Kembali ke beranda.")
            null
        }
    }

    // ------------------------------------------------------------------ alur dialog import

    /** Urutan: (HTML tunggal: saran folder) → (index tidak ada: tawarkan file lain) → (nama sama) → simpan & buka. */
    private fun continueImport(p: PendingImport, skipFolderHint: Boolean = false, skipAlt: Boolean = false) {
        if (!skipFolderHint && p.relativeRefs > 0) {
            _dialog.value = DialogState.SuggestFolder(p); return
        }
        if (!skipAlt && p.inspection.pages.size > 1) {
            _pagePick.value = PagePick(p.draft.name, p.inspection.pages, p.draft.entry, pending = p); return
        }
        if (!skipAlt && !p.inspection.isIndex) {
            _dialog.value = DialogState.AltEntry(p); return
        }
        val existing = store.findByName(p.draft.name)
        if (existing != null && existing.id != p.draft.id) {
            _dialog.value = DialogState.Replace(p, existing); return
        }
        store.upsert(p.draft)
        openProject(store.get(p.draft.id) ?: p.draft, Target.DEFAULT, explicitPage = true)
    }

    // ------------------------------------------------------------------ PILIH HALAMAN

    /** Dari menu proyek: tampilkan semua halaman HTML proyek. */
    fun pickPage(r: ProjectRecord) {
        _menuFor.value = null
        viewModelScope.launch {
            _busy.value = "Membaca daftar halaman…"
            val pages = withContext(Dispatchers.IO) { preparer.listPages(r) }
            _busy.value = null
            when {
                pages == null -> _dialog.value =
                    if (r.kind == Kind.SHARED) DialogState.SharedGone(r) else DialogState.SourceMissing(r)
                pages.size <= 1 -> toast("Proyek ini hanya punya satu halaman")
                else -> {
                    val sel = if (r.entry in pages) r.entry else ProjectInspector.chooseEntry(pages).entry ?: pages.first()
                    _pagePick.value = PagePick(r.name, pages, sel, record = r)
                }
            }
        }
    }

    /** page = null berarti batal. */
    fun answerPagePick(pick: PagePick, page: String?) {
        _pagePick.value = null
        val pending = pick.pending
        if (pending != null) {
            if (page == null) { cancelImport(pending); return }
            continueImport(pending.copy(draft = pending.draft.copy(entry = page)), skipFolderHint = true, skipAlt = true)
            return
        }
        val r = pick.record?.let { store.get(it.id) } ?: return
        if (page == null) return
        val updated = r.copy(entry = page)
        store.update(updated)
        val st = ServerController.state.value
        val sharingThis = st.running && st.sharing && st.project?.record?.id == r.id
        openProject(updated, if (sharingThis) Target.QR else Target.DEFAULT, force = sharingThis, explicitPage = true)
    }

    fun answerSuggestFolder(p: PendingImport, useFolder: Boolean) {
        _dialog.value = null
        if (useFolder) {
            cancelImport(p)
            send(UiEvent.PickFolder)
        } else {
            continueImport(p, skipFolderHint = true)
        }
    }

    fun answerAltEntry(p: PendingImport, open: Boolean) {
        _dialog.value = null
        if (open) continueImport(p, skipFolderHint = true, skipAlt = true) else cancelImport(p)
    }

    fun answerReplace(p: PendingImport, existing: ProjectRecord, replace: Boolean) {
        _dialog.value = null
        if (!replace) { cancelImport(p); return }
        val merged = store.replace(existing.id, p.draft)
        openProject(store.get(merged.id) ?: merged, Target.DEFAULT, explicitPage = true)
    }

    private fun cancelImport(p: PendingImport) {
        p.pickedUri?.let { store.releaseIfUnused(it) }
        if (p.draft.copyPath.isNotEmpty()) runCatching { File(p.draft.copyPath).delete() }
    }

    // ------------------------------------------------------------------ MEMBUKA proyek

    /**
     * @param explicitPage true = halaman baru saja dipilih pengguna, jadi abaikan pengaturan "Selalu index.html".
     */
    fun openProject(record: ProjectRecord, target: Target, force: Boolean = false, explicitPage: Boolean = false) {
        _menuFor.value = null
        val fresh = store.get(record.id) ?: record
        if (fresh.isSharedCopyGone) {
            _dialog.value = DialogState.SharedGone(fresh); return
        }
        val st = ServerController.state.value
        val active = st.project?.record
        if (!force && st.running && st.sharing && active != null && active.id != fresh.id) {
            _dialog.value = DialogState.StopSharing(active, fresh, target); return
        }
        // Proyek yang sama sedang dibagikan → jangan baca ulang (siswa sedang memuat), cukup tampilkan.
        if (!explicitPage && st.running && st.sharing && active?.id == fresh.id) {
            _menuFor.value = null
            store.markOpened(fresh.id)
            dispatchTarget(target)
            return
        }

        viewModelScope.launch {
            _busy.value = "Membuka ${fresh.name}…"
            val outcome = withContext(Dispatchers.IO) {
                val useDefault = !explicitPage && settingsStore.current.pageDefault == PageDefault.INDEX
                when (val r = preparer.prepare(fresh, useDefault)) {
                    is PrepareResult.Ok -> try {
                        val keepPin = st.sharing && active?.id == fresh.id
                        ServerController.start(
                            r.project,
                            settingsStore.current.port + fresh.slot.coerceAtLeast(0),
                            sharing = target == Target.QR,
                            pinOn = if (keepPin) st.pinOn else settingsStore.current.sharePin,
                        )
                        null
                    } catch (e: IOException) {
                        DialogState.Info("Server gagal berjalan", e.message ?: "Port sedang dipakai aplikasi lain.")
                    }
                    PrepareResult.SourceMissing ->
                        if (fresh.kind == Kind.SHARED) DialogState.SharedGone(fresh) else DialogState.SourceMissing(fresh)
                    PrepareResult.NoHtml ->
                        DialogState.Info("File HTML tidak ditemukan", "Tidak ada file .html di dalam proyek ini. Kembali ke beranda.")
                    is PrepareResult.Failed ->
                        DialogState.Info("Gagal membuka proyek", r.message)
                }
            }
            _busy.value = null
            if (outcome != null) {
                _dialog.value = outcome
                _screen.value = Screen.HOME
                return@launch
            }
            store.markOpened(fresh.id)
            VcvServerService.start(vcv)
            dispatchTarget(target)
        }
    }

    private fun dispatchTarget(target: Target) {
        val url = ServerController.state.value.localUrl
        when (target) {
            Target.QR -> _screen.value = Screen.QR
            Target.INTERNAL -> _screen.value = Screen.BROWSER
            Target.CHROME -> { _screen.value = Screen.HOME; send(UiEvent.LaunchBrowser(url)) }
            Target.DEFAULT -> if (settingsStore.current.browser == BrowserChoice.INTERNAL) {
                _screen.value = Screen.BROWSER
            } else {
                _screen.value = Screen.HOME
                send(UiEvent.LaunchBrowser(url))
            }
        }
    }

    fun answerStopSharing(d: DialogState.StopSharing, stop: Boolean) {
        _dialog.value = null
        if (stop) openProject(d.next, d.target, force = true)
    }

    /** Dari notifikasi "Buka lagi". */
    fun reopenActive() {
        val st = ServerController.state.value
        if (!st.running) { toast("Server sudah berhenti. Pilih proyek dari daftar."); return }
        dispatchTarget(Target.DEFAULT)
    }

    fun stopSharing() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { ServerController.setSharing(false, settingsStore.current.port) }
            }
            _screen.value = Screen.HOME
            toast("Berbagi dihentikan")
        }
    }

    fun refreshLan() { viewModelScope.launch(Dispatchers.IO) { ServerController.refreshLanIps() } }

    // ------------------------------------------------------------------ dialog file hilang

    fun answerSourceMissing(r: ProjectRecord, remove: Boolean) {
        _dialog.value = null
        if (remove) store.remove(r.id)
    }

    // ------------------------------------------------------------------ SIMPAN PROYEK (file WA)

    fun startSave(record: ProjectRecord?) {
        _menuFor.value = null
        val r = record?.let { store.get(it.id) } ?: ServerController.state.value.project?.record?.let { store.get(it.id) }
        if (r == null || r.kind != Kind.SHARED || r.isSharedCopyGone) {
            toast("Proyek ini sudah tersimpan atau salinannya sudah dihapus")
            return
        }
        savingRecordId = r.id
        if (r.sharedExt == "zip") send(UiEvent.SaveZip(r.fileName)) else send(UiEvent.SaveHtml(r.fileName))
    }

    fun onSaveTarget(uri: Uri?) {
        val id = savingRecordId ?: return
        savingRecordId = null
        if (uri == null) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val r = store.get(id) ?: return@withContext null
                runCatching {
                    resolver.openOutputStream(uri, "w")?.use { out ->
                        FileInputStream(File(r.copyPath)).use { it.copyTo(out) }
                    } ?: throw IOException("Tidak bisa menulis")
                    runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    val display = UriInfo.displayName(vcv, uri)
                    val saved = r.copy(
                        kind = if (r.sharedExt == "zip") Kind.ZIP else Kind.HTML,
                        uri = uri.toString(),
                        fileName = display,
                        sourceLabel = UriInfo.sourceLabel(vcv, uri, display),
                        sharedTag = "", sharedExt = "", copyPath = "", copyDeleted = false,
                        entry = if (r.sharedExt == "zip") r.entry else display,
                    )
                    File(r.copyPath).delete()
                    store.update(saved)
                    ServerController.updateRecord(saved)
                    saved.sourceLabel
                }.getOrNull()
            }
            if (ok != null) toast("Proyek disimpan: $ok") else toast("Gagal menyimpan proyek")
        }
    }

    // ------------------------------------------------------------------ menu lain

    fun addShortcut(r: ProjectRecord) {
        _menuFor.value = null
        val ctx = getApplication<Application>()
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
            toast("Peluncur HP ini tidak mendukung pintasan"); return
        }
        val intent = Intent(ctx, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_PROJECT)
            .putExtra(MainActivity.EXTRA_PROJECT_ID, r.id)
        val info = ShortcutInfoCompat.Builder(ctx, "p_${r.id}")
            .setShortLabel(r.name.take(24))
            .setLongLabel(r.name)
            .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
    }

    fun openById(id: String) {
        val r = store.get(id)
        if (r == null) {
            _dialog.value = DialogState.Info("Proyek tidak ada di daftar", "Proyek ini sudah keluar dari 10 terbaru atau sudah dihapus. Import ulang dari beranda.")
            return
        }
        openProject(r, Target.DEFAULT)
    }

    fun rename(r: ProjectRecord, newName: String) {
        _dialog.value = null
        store.rename(r.id, newName)
        store.get(r.id)?.let { ServerController.updateRecord(it) }
    }

    fun remove(r: ProjectRecord) {
        _menuFor.value = null
        if (ServerController.state.value.project?.record?.id == r.id) {
            ServerController.stop()
        }
        store.remove(r.id)
    }

    fun clearAll() {
        _dialog.value = null
        if (ServerController.state.value.running) ServerController.stop()
        store.clear()
        toast("Daftar dikosongkan")
    }

    // ------------------------------------------------------------------ pengaturan

    fun setBrowser(b: BrowserChoice) = settingsStore.setBrowser(b)
    fun setIdle(m: Int) = settingsStore.setIdle(m)
    fun setPort(p: Int) = settingsStore.setPort(p)
    fun setPageDefault(p: PageDefault) = settingsStore.setPageDefault(p)

    // ------------------------------------------------------------------ PIN berbagi

    fun setPin(on: Boolean) {
        settingsStore.setSharePin(on)
        ServerController.setPin(on)
    }

    fun changePin() {
        ServerController.changePin()
        toast("PIN baru dibuat. QR lama tidak berlaku")
    }
}
