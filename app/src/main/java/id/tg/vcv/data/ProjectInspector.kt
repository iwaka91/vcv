package id.tg.vcv.data

import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/** Hasil pemeriksaan isi proyek. */
data class Inspection(
    /** Jalur relatif file HTML yang akan dibuka; null = tidak ada HTML sama sekali. */
    val entry: String?,
    /** true jika entry adalah index.html (tidak perlu tanya pengguna). */
    val isIndex: Boolean,
    /** Semua halaman HTML (sudah disaring dari file sampah), urut per folder. */
    val pages: List<String> = emptyList(),
)

object ProjectInspector {

    private val HTML_EXT = Regex("""\.html?$""", RegexOption.IGNORE_CASE)

    /** Pilih file HTML terbaik dari daftar jalur relatif. */
    fun chooseEntry(paths: List<String>): Inspection {
        val html = paths
            .map { it.replace('\\', '/').trimStart('/') }
            .filter { HTML_EXT.containsMatchIn(it) && !isJunk(it) }
        if (html.isEmpty()) return Inspection(null, false)
        val pages = sortPages(html)
        val depth = { p: String -> p.count { it == '/' } }
        val index = html.filter { it.substringAfterLast('/').lowercase().let { n -> n == "index.html" || n == "index.htm" } }
        if (index.isNotEmpty()) {
            return Inspection(index.minWith(compareBy<String>({ depth(it) }, { it.length })), true, pages)
        }
        return Inspection(html.minWith(compareBy<String>({ depth(it) }, { it.lowercase() })), false, pages)
    }

    /** Urutkan halaman: per folder (yang dangkal dulu), index.html paling atas di tiap folder. */
    fun sortPages(paths: List<String>): List<String> = paths.distinct().sortedWith(
        compareBy<String>(
            { it.count { c -> c == '/' } },
            { it.substringBeforeLast('/', "").lowercase() },
            { !it.substringAfterLast('/').lowercase().startsWith("index.") },
            { it.substringAfterLast('/').lowercase() },
        )
    )

    fun isJunk(path: String): Boolean {
        val p = path.replace('\\', '/')
        return p.startsWith("__MACOSX/") || p.contains("/__MACOSX/") ||
            p.split('/').any { it.startsWith("._") || it == ".git" || it == "node_modules" || it == ".vscode" || it == ".idea" }
    }

    /** Daftar nama entri di dalam ZIP (dengan cadangan charset untuk ZIP buatan Windows lama). */
    fun listZip(open: () -> InputStream): List<String> {
        return try {
            readZipNames(open, Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            readZipNames(open, Charset.forName("IBM437"))
        }
    }

    private fun readZipNames(open: () -> InputStream, cs: Charset): List<String> {
        val names = mutableListOf<String>()
        open().use { raw ->
            ZipInputStream(raw.buffered(), cs).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory) names += e.name
                }
            }
        }
        return names
    }

    /** Daftar file HTML di dalam folder (maks. kedalaman 6). */
    fun listFolder(root: DocumentFile): List<String> {
        val out = mutableListOf<String>()
        fun walk(dir: DocumentFile, prefix: String, depth: Int) {
            if (depth > 6) return
            for (f in dir.listFiles()) {
                val name = f.name ?: continue
                val path = prefix + name
                if (isJunk(path)) continue
                if (f.isDirectory) walk(f, "$path/", depth + 1)
                else if (HTML_EXT.containsMatchIn(name)) out += path
            }
        }
        walk(root, "", 0)
        return out
    }

    // ---------- Deteksi file pendamping pada HTML tunggal ----------

    private val TAG_REF = Regex(
        """<(img|script|link|audio|video|source|iframe|embed|track)\b[^>]*?\b(src|href)\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val CSS_URL = Regex("""url\(\s*['"]?([^'")]+)['"]?\s*\)""", RegexOption.IGNORE_CASE)

    private fun isRelative(ref: String): Boolean {
        val r = ref.trim().lowercase()
        if (r.isEmpty()) return false
        return !(r.startsWith("http:") || r.startsWith("https:") || r.startsWith("//") ||
            r.startsWith("data:") || r.startsWith("blob:") || r.startsWith("mailto:") ||
            r.startsWith("tel:") || r.startsWith("javascript:") || r.startsWith("#") ||
            r.startsWith("about:") || r.contains("{{") || r.contains("\${"))
    }

    /** Jumlah file pendamping relatif (gambar/CSS/JS) yang dipanggil oleh HTML tunggal. */
    fun countRelativeRefs(html: String): Int {
        val refs = mutableSetOf<String>()
        TAG_REF.findAll(html).forEach { m -> m.groupValues[3].let { if (isRelative(it)) refs += it } }
        CSS_URL.findAll(html).forEach { m -> m.groupValues[1].let { if (isRelative(it)) refs += it } }
        return refs.size
    }

    // ---------- Cek kesiapan offline ----------

    private val TAG_EXT = Regex(
        """<(script|link|img|audio|video|source|iframe|embed)\b[^>]*?\b(src|href)\s*=\s*["'](?:https?:)?//([^/"':?#]+)""",
        RegexOption.IGNORE_CASE
    )
    private val CSS_EXT = Regex("""(?:url\(\s*['"]?|@import\s+['"])(?:https?:)?//([^/"')?#:]+)""", RegexOption.IGNORE_CASE)
    private val JS_EXT = Regex(
        """(?:\bimport\b[^'";]*?|\bimport\s*\(\s*|\bfetch\s*\(\s*)['"`]https?://([^/"'`?#:]+)""",
        RegexOption.IGNORE_CASE
    )

    /** Kumpulkan nama host luar yang dibutuhkan proyek (butuh internet). */
    fun externalHosts(root: File): List<String> {
        val hosts = sortedSetOf<String>()
        root.walkTopDown()
            .onEnter { !isJunk(it.name) }
            .filter { it.isFile && it.length() <= 3_000_000 }
            .forEach { f ->
                val ext = f.extension.lowercase()
                if (ext !in setOf("html", "htm", "css", "js", "mjs")) return@forEach
                val text = runCatching { f.readText() }.getOrNull() ?: return@forEach
                if (ext == "html" || ext == "htm") {
                    TAG_EXT.findAll(text).forEach { hosts += it.groupValues[3].lowercase() }
                    CSS_EXT.findAll(text).forEach { hosts += it.groupValues[1].lowercase() }
                    JS_EXT.findAll(text).forEach { hosts += it.groupValues[1].lowercase() }
                } else if (ext == "css") {
                    CSS_EXT.findAll(text).forEach { hosts += it.groupValues[1].lowercase() }
                } else {
                    JS_EXT.findAll(text).forEach { hosts += it.groupValues[1].lowercase() }
                }
            }
        return hosts.filter { it.contains('.') && it != "localhost" }.toList()
    }
}
