package id.tg.vcv.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/** Proyek yang sudah disiapkan (diekstrak ke folder sementara) dan siap disajikan server. */
data class PreparedProject(
    val record: ProjectRecord,
    val rootDir: File,        // folder yang menjadi akar server (folder tempat file HTML berada)
    val entryName: String,    // jalur file HTML relatif terhadap rootDir (bisa di subfolder, mis. "bab2/kuis.html")
    val totalBytes: Long,
    val externalHosts: List<String>,
)

sealed class PrepareResult {
    data class Ok(val project: PreparedProject) : PrepareResult()
    data object SourceMissing : PrepareResult()
    data object NoHtml : PrepareResult()
    data class Failed(val message: String) : PrepareResult()
}

/**
 * Membaca ulang file/folder asli setiap kali proyek dibuka, lalu mengekstraknya
 * ke cache/serve/. Folder serve dikosongkan setiap kali proyek lain dibuka.
 */
class ProjectPreparer(private val context: Context) {

    private val serveBase: File get() = File(context.cacheDir, "serve")

    fun clearServeDir() {
        runCatching { serveBase.deleteRecursively() }
    }

    /**
     * @param useDefault true = abaikan halaman terakhir dipilih, buka index.html (pengaturan "Selalu index.html").
     */
    fun prepare(record: ProjectRecord, useDefault: Boolean = false): PrepareResult {
        clearServeDir()
        val target = File(serveBase, record.id).apply { mkdirs() }
        return try {
            when {
                record.kind == Kind.FOLDER -> copyFolder(Uri.parse(record.uri), target)
                record.contentIsZip -> extractZip(openSource(record), target)
                else -> copySingle(openSource(record), target, safeName(record.fileName, "index.html"))
            }
            finish(record, target, useDefault)
        } catch (e: FileNotFoundException) {
            PrepareResult.SourceMissing
        } catch (e: SecurityException) {
            PrepareResult.SourceMissing
        } catch (e: SourceGoneException) {
            PrepareResult.SourceMissing
        } catch (e: Exception) {
            PrepareResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private class SourceGoneException : Exception()

    /** Buka sumber berupa stream (ZIP/HTML asli atau salinan WhatsApp). */
    private fun openSource(record: ProjectRecord): () -> InputStream = {
        if (record.kind == Kind.SHARED) {
            val f = File(record.copyPath)
            if (record.copyDeleted || !f.exists()) throw SourceGoneException()
            FileInputStream(f)
        } else {
            context.contentResolver.openInputStream(Uri.parse(record.uri))
                ?: throw FileNotFoundException(record.uri)
        }
    }

    private fun finish(record: ProjectRecord, target: File, useDefault: Boolean): PrepareResult {
        val entryFile: File
        var root: File
        if (record.canHaveManyPages) {
            val all = target.walkTopDown().filter { it.isFile }
                .map { it.relativeTo(target).invariantSeparatorsPath }.toList()
            val defaultRel = ProjectInspector.chooseEntry(all).entry ?: return PrepareResult.NoHtml
            val savedRel = record.entry.replace('\\', '/').trimStart('/')
            // Utamakan halaman terakhir dipilih; bila hilang atau pengaturan "Selalu index.html", pakai bawaan.
            val pickRel = if (!useDefault && savedRel.isNotEmpty() && File(target, savedRel).isFile) savedRel else defaultRel
            entryFile = File(target, pickRel)
            // Akar server = folder halaman utama proyek, agar halaman di subfolder tetap bisa
            // memanggil file di folder atasnya. Bila halaman di luar folder itu, pakai foldernya sendiri.
            val base = File(target, defaultRel).parentFile ?: target
            root = if (entryFile.canonicalPath.startsWith(base.canonicalPath + File.separator)) base
            else entryFile.parentFile ?: target
        } else {
            // HTML tunggal: satu-satunya file di target
            entryFile = target.listFiles()?.firstOrNull { it.isFile } ?: return PrepareResult.NoHtml
            root = entryFile.parentFile ?: target
        }
        if (!entryFile.isFile) return PrepareResult.NoHtml
        val entryName = entryFile.relativeTo(root).invariantSeparatorsPath
        val size = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val hosts = ProjectInspector.externalHosts(root)
        return PrepareResult.Ok(
            PreparedProject(record, root, entryName, size, hosts)
        )
    }

    /** Daftar semua halaman HTML di proyek (untuk "Pilih halaman…"). Null = sumber tidak bisa dibaca. */
    fun listPages(record: ProjectRecord): List<String>? = try {
        when {
            record.kind == Kind.FOLDER -> {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(record.uri)) ?: throw SourceGoneException()
                if (!root.exists()) throw SourceGoneException()
                ProjectInspector.chooseEntry(ProjectInspector.listFolder(root)).pages
            }
            record.contentIsZip -> ProjectInspector.chooseEntry(ProjectInspector.listZip(openSource(record))).pages
            else -> emptyList()
        }
    } catch (e: Exception) {
        null
    }

    // ---------------- ZIP ----------------

    private fun extractZip(open: () -> InputStream, target: File) {
        try {
            unzip(open, target, Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            target.deleteRecursively(); target.mkdirs()
            unzip(open, target, Charset.forName("IBM437"))
        }
    }

    private fun unzip(open: () -> InputStream, target: File, cs: Charset) {
        val canonicalTarget = target.canonicalPath + File.separator
        var total = 0L
        open().use { raw ->
            ZipInputStream(raw.buffered(), cs).use { zip ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name.replace('\\', '/').trimStart('/')
                    if (name.isEmpty() || ProjectInspector.isJunk(name)) continue
                    val out = File(target, name)
                    // Lindungi dari "zip slip" (jalur ../ keluar folder)
                    if (!out.canonicalPath.startsWith(canonicalTarget)) continue
                    if (e.isDirectory) { out.mkdirs(); continue }
                    out.parentFile?.mkdirs()
                    out.outputStream().use { os ->
                        while (true) {
                            val n = zip.read(buf)
                            if (n <= 0) break
                            total += n
                            if (total > MAX_PROJECT_BYTES) throw IllegalStateException("Proyek lebih dari 1 GB")
                            os.write(buf, 0, n)
                        }
                    }
                }
            }
        }
    }

    // ---------------- HTML tunggal ----------------

    private fun copySingle(open: () -> InputStream, target: File, name: String) {
        val out = File(target, name)
        open().use { input -> out.outputStream().use { input.copyTo(it) } }
    }

    // ---------------- Folder ----------------

    private fun copyFolder(treeUri: Uri, target: File) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: throw SourceGoneException()
        if (!root.exists() || !root.canRead()) throw SourceGoneException()
        var total = 0L
        fun walk(dir: DocumentFile, dest: File, depth: Int) {
            if (depth > 10) return
            dest.mkdirs()
            for (f in dir.listFiles()) {
                val name = f.name ?: continue
                if (ProjectInspector.isJunk(name) || name.startsWith(".")) continue
                val out = File(dest, safeName(name, "file"))
                if (f.isDirectory) {
                    walk(f, out, depth + 1)
                } else {
                    context.contentResolver.openInputStream(f.uri)?.use { input ->
                        out.outputStream().use { os ->
                            total += input.copyTo(os)
                        }
                    }
                    if (total > MAX_PROJECT_BYTES) throw IllegalStateException("Proyek lebih dari 1 GB")
                }
            }
        }
        walk(root, target, 0)
    }

    private fun safeName(name: String, fallback: String): String {
        val n = name.replace('/', '_').replace('\\', '_').trim()
        return if (n.isEmpty() || n == "." || n == "..") fallback else n
    }

    companion object {
        const val MAX_PROJECT_BYTES = 1L shl 30 // 1 GB
    }
}
