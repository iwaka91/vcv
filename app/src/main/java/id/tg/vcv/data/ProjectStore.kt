package id.tg.vcv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

enum class PinResult { OK, FULL, MISSING }

/**
 * Daftar proyek di SharedPreferences (JSON): 10 proyek terbaru + proyek yang disematkan (maks 10 pin).
 * Proyek yang disematkan tidak pernah tertindih, walau sudah keluar dari 10 terbaru.
 */
class ProjectStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("vcv_projects", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<ProjectRecord>> = _items.asStateFlow()

    fun get(id: String): ProjectRecord? = _items.value.firstOrNull { it.id == id }

    fun findByName(name: String): ProjectRecord? =
        _items.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    /** Pisahkan: yang dipertahankan (10 terbaru + semua pin) dan yang keluar dari daftar. */
    private fun split(all: List<ProjectRecord>): Pair<List<ProjectRecord>, List<ProjectRecord>> {
        val sorted = all.sortedByDescending { it.lastOpened }
        val recentIds = sorted.take(MAX).map { it.id }.toSet()
        val kept = sorted.filter { it.pinned || it.id in recentIds }
        val dropped = sorted.filterNot { it.pinned || it.id in recentIds }
        return kept to dropped
    }

    /** Tambah/ganti proyek, letakkan paling atas, buang yang di luar 10 terbaru dan tidak disematkan. */
    @Synchronized
    fun upsert(record: ProjectRecord) {
        val existing = _items.value.firstOrNull { it.id == record.id }
        val others = _items.value.filterNot { it.id == record.id }
        val base = if (existing != null) record.copy(pinned = existing.pinned, pinnedAt = existing.pinnedAt) else record
        val withSlot = if (base.slot >= 0) base else base.copy(slot = nextSlot(others))
        val (kept, dropped) = split(listOf(withSlot) + others)
        save(kept)
        dropped.forEach { cleanup(it, kept) }
    }

    /** Sematkan proyek. Gagal (FULL) bila sudah ada 10 pin. */
    @Synchronized
    fun pin(id: String): PinResult {
        val r = get(id) ?: return PinResult.MISSING
        if (r.pinned) return PinResult.OK
        if (_items.value.count { it.pinned } >= MAX_PINS) return PinResult.FULL
        update(r.copy(pinned = true, pinnedAt = System.currentTimeMillis()))
        return PinResult.OK
    }

    /** Lepas sematan. Proyek yang sudah di luar 10 terbaru ikut keluar dari daftar (file asli tidak dihapus). */
    @Synchronized
    fun unpin(id: String) {
        if (get(id) == null) return
        val all = _items.value.map { if (it.id == id) it.copy(pinned = false, pinnedAt = 0L) else it }
        val (kept, dropped) = split(all)
        save(kept)
        dropped.forEach { cleanup(it, kept) }
    }

    /** "Timpa": proyek lama (nama sama) diganti proyek baru. Mengembalikan record baru. */
    @Synchronized
    fun replace(oldId: String, newRecord: ProjectRecord): ProjectRecord {
        val old = get(oldId)
        val merged = newRecord.copy(
            id = oldId, slot = old?.slot ?: -1,
            pinned = old?.pinned ?: false, pinnedAt = old?.pinnedAt ?: 0L,
        )
        upsert(merged)
        if (old != null) {
            if (old.uri.isNotEmpty() && old.uri != merged.uri && _items.value.none { it.uri == old.uri }) {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(old.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            if (old.copyPath.isNotEmpty() && old.copyPath != merged.copyPath) {
                runCatching { File(old.copyPath).delete() }
            }
        }
        return merged
    }

    @Synchronized
    fun update(record: ProjectRecord) {
        if (_items.value.none { it.id == record.id }) return
        save(_items.value.map { if (it.id == record.id) record else it }.sortedByDescending { it.lastOpened })
    }

    @Synchronized
    fun markOpened(id: String) {
        val r = get(id) ?: return
        update(r.copy(lastOpened = System.currentTimeMillis()))
    }

    @Synchronized
    fun rename(id: String, newName: String) {
        val r = get(id) ?: return
        val clean = newName.trim()
        if (clean.isEmpty()) return
        update(r.copy(name = clean))
    }

    /** Hapus dari daftar. File asli di HP TIDAK ikut dihapus. */
    @Synchronized
    fun remove(id: String) {
        val r = get(id) ?: return
        val kept = _items.value.filterNot { it.id == id }
        save(kept)
        cleanup(r, kept)
    }

    @Synchronized
    fun clear() {
        val old = _items.value
        save(emptyList())
        old.forEach { cleanup(it, emptyList()) }
    }

    /** Lepas izin baca & hapus salinan sementara milik proyek yang keluar dari daftar. */
    private fun cleanup(r: ProjectRecord, kept: List<ProjectRecord>) {
        if (r.uri.isNotEmpty() && kept.none { it.uri == r.uri }) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(r.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        if (r.copyPath.isNotEmpty()) runCatching { File(r.copyPath).delete() }
    }

    /** Dipakai saat import dibatalkan: lepas izin jika uri itu tidak dipakai proyek lain. */
    fun releaseIfUnused(uri: Uri) {
        val s = uri.toString()
        if (_items.value.none { it.uri == s }) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun load(): List<ProjectRecord> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val list = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { ProjectRecord.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList()).let { split(it).first }
        // Proyek dari versi 1.0 belum punya slot: beri slot, proyek paling baru tetap slot 0 (port lama).
        if (list.none { it.slot < 0 }) return list
        val fixed = mutableListOf<ProjectRecord>()
        list.forEach { r -> fixed += if (r.slot >= 0) r else r.copy(slot = nextSlot(fixed + list.filter { it.slot >= 0 })) }
        val arr = JSONArray(); fixed.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
        return fixed
    }

    /**
     * Slot baru berputar 0..[SLOTS]-1 dan melewati slot yang sedang dipakai,
     * agar proyek baru tidak mewarisi data peramban proyek yang baru dihapus.
     */
    private fun nextSlot(used: List<ProjectRecord>): Int {
        val taken = used.map { it.slot }.toSet()
        var s = prefs.getInt(KEY_SLOT, 0)
        repeat(SLOTS) {
            val cand = s % SLOTS
            s = cand + 1
            if (cand !in taken) {
                prefs.edit().putInt(KEY_SLOT, s % SLOTS).apply()
                return cand
            }
        }
        return 0
    }

    private fun save(list: List<ProjectRecord>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
        _items.value = list
    }

    companion object {
        const val MAX = 10
        const val MAX_PINS = 10
        private const val KEY = "recent"
        private const val KEY_SLOT = "next_slot"
        const val SLOTS = 100
    }
}
