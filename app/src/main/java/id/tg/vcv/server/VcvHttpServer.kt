package id.tg.vcv.server

import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.AsyncRunner
import fi.iki.elonen.NanoHTTPD.ClientHandler
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream

/**
 * Server statis mini untuk satu proyek.
 * - File kecil disimpan di RAM (sekali baca, dipakai semua siswa).
 * - File teks (HTML/CSS/JS/JSON/SVG) dikirim terkompresi gzip bila browser mendukung.
 * - ETag + "no-cache": muat ulang cukup dijawab 304 tanpa kirim ulang isi file.
 * - Antrean terkendali: maksimal [THREADS] koneksi dilayani bersamaan, sisanya menunggu.
 */
class VcvHttpServer(
    host: String?,
    port: Int,
    private val root: File,
    private val entryName: String,
    private val onRequest: (remoteIp: String) -> Unit,
    private val gate: PinGate,
) : NanoHTTPD(host, port) {

    private class CachedFile(
        val bytes: ByteArray,
        val gz: ByteArray?,
        val length: Long,
        val lastModified: Long,
    )

    private val canonicalRoot: String = root.canonicalPath
    private val cache = ConcurrentHashMap<String, CachedFile>()
    private val cacheUsed = AtomicLong(0)
    private val runner = BoundedRunner(THREADS)

    init {
        setAsyncRunner(runner)
    }

    override fun stop() {
        super.stop()
        runner.shutdown()
        cache.clear()
    }

    /** Muat semua file kecil ke RAM sekaligus (dipanggil saat mulai berbagi). Mengembalikan perkiraan byte yang dikirim per siswa. */
    fun warmUp(): Long {
        var transfer = 0L
        root.walkTopDown().filter { it.isFile }.forEach { f ->
            val c = getCached(f)
            transfer += c?.gz?.size?.toLong() ?: f.length()
        }
        return transfer
    }

    override fun useGzipWhenAccepted(r: Response): Boolean = false // gzip kita tangani sendiri

    override fun serve(session: IHTTPSession): Response {
        val remote = session.headers["remote-addr"] ?: ""
        onRequest(remote)

        if (session.method != Method.GET && session.method != Method.HEAD) {
            return text(Response.Status.METHOD_NOT_ALLOWED, "Metode tidak didukung")
        }

        // Kunci PIN: hanya untuk HP lain, HP ini sendiri tidak ditanya.
        if (gate.enabled && !isLocal(remote)) {
            val token = runCatching { session.cookies?.read(PinGate.COOKIE) }.getOrNull()
            if (!gate.isAllowed(token)) return pinResponse(session, remote)
        }

        var path = (session.uri ?: "/").substringBefore('?').replace('\\', '/').trimStart('/')
        if (path.isEmpty()) path = entryName

        var file = File(root, path)
        val canon = runCatching { file.canonicalPath }.getOrNull()
            ?: return text(Response.Status.BAD_REQUEST, "Alamat tidak valid")
        if (canon != canonicalRoot && !canon.startsWith(canonicalRoot + File.separator)) {
            return text(Response.Status.FORBIDDEN, "Akses ditolak")
        }
        if (file.isDirectory) file = File(file, "index.html")
        if (!file.isFile) return notFound(path)

        val mime = mimeOf(file.name)
        val etag = "\"" + file.length().toString(16) + "-" + file.lastModified().toString(16) + "\""
        if (session.headers["if-none-match"] == etag) {
            return newFixedLengthResponse(Response.Status.NOT_MODIFIED, mime, "").also { commonHeaders(it, etag) }
        }

        val range = session.headers["range"]
        if (range != null && range.startsWith("bytes=")) {
            return partial(file, mime, etag, range)
        }

        val cached = getCached(file)
        val acceptGzip = session.headers["accept-encoding"]?.contains("gzip") == true
        val response = when {
            cached != null && cached.gz != null && acceptGzip ->
                newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(cached.gz), cached.gz.size.toLong())
                    .also { it.addHeader("Content-Encoding", "gzip") }
            cached != null ->
                newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(cached.bytes), cached.length)
            else ->
                newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(file), file.length())
        }
        commonHeaders(response, etag)
        return response
    }

    private fun isLocal(ip: String): Boolean =
        ip.isEmpty() || ip == "127.0.0.1" || ip == "::1" || ip.startsWith("0:0:0:0:0:0:0:1")

    private fun pinResponse(session: IHTTPSession, ip: String): Response {
        val input = session.parameters["pin"]?.firstOrNull()?.trim().orEmpty()
        if (input.isEmpty()) {
            val left = gate.lockLeftMs(ip)
            return pinPage(if (left > 0) "Terlalu banyak salah. Coba lagi ${(left + 999) / 1000} detik lagi." else null)
        }
        return when (val c = gate.check(ip, input)) {
            is PinGate.Check.Ok -> {
                val others = (session.queryParameterString ?: "").split('&')
                    .filter { it.isNotEmpty() && !it.startsWith("pin=") }.joinToString("&")
                val loc = Uri.encode(session.uri ?: "/", "/") + if (others.isNotEmpty()) "?$others" else ""
                newFixedLengthResponse(Response.Status.REDIRECT_SEE_OTHER, "text/plain", "").also {
                    it.addHeader("Location", loc)
                    it.addHeader("Set-Cookie", "${PinGate.COOKIE}=${c.token}; Path=/; HttpOnly; SameSite=Lax")
                    it.addHeader("Cache-Control", "no-store")
                }
            }
            is PinGate.Check.Wrong -> pinPage("PIN salah. Sisa ${c.left} kali coba.")
            is PinGate.Check.Locked -> pinPage("Terlalu banyak salah. Coba lagi ${c.seconds} detik lagi.")
        }
    }

    private fun pinPage(error: String?): Response {
        val err = error?.let { """<p class="err">$it</p>""" } ?: ""
        val html = """<!doctype html><html lang="id"><head><meta charset="utf-8">
            |<meta name="viewport" content="width=device-width,initial-scale=1">
            |<title>Masukkan PIN</title><style>
            |body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;font-family:sans-serif;background:#101017;color:#ECEAF2}
            |.box{width:min(320px,88vw);text-align:center}
            |.logo{font-size:40px;font-weight:800;letter-spacing:-2px}.logo i{color:#FF7A1A;display:inline-block;transform:rotate(-12deg)}
            |h1{font-size:20px;margin:18px 0 6px}p{color:#C9C5D6;font-size:14px;line-height:1.5;margin:0 0 18px}
            |input{width:100%;box-sizing:border-box;font-size:34px;letter-spacing:14px;text-align:center;padding:14px;border-radius:16px;border:2px solid #3A3A4A;background:#1C1C26;color:#FFB067;outline:none}
            |input:focus{border-color:#FF7A1A}
            |button{margin-top:14px;width:100%;padding:16px;border:0;border-radius:16px;background:#FF7A1A;color:#17121F;font-size:17px;font-weight:700}
            |.err{color:#FF8A7A;font-weight:700;margin:14px 0 0}
            |</style></head><body><form class="box" method="get" action="" autocomplete="off">
            |<div class="logo">V<i>c</i>V</div>
            |<h1>Masukkan PIN dari guru</h1><p>PIN 4 angka tertera di layar HP guru.</p>
            |<input name="pin" inputmode="numeric" pattern="[0-9]*" maxlength="4" autofocus required>
            |<button type="submit">Masuk</button>$err
            |</form></body></html>""".trimMargin()
        return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/html; charset=utf-8", html).also {
            it.addHeader("Cache-Control", "no-store")
        }
    }

    private fun commonHeaders(r: Response, etag: String) {
        r.addHeader("ETag", etag)
        r.addHeader("Cache-Control", "no-cache")
        r.addHeader("Vary", "Accept-Encoding")
        r.addHeader("Accept-Ranges", "bytes")
        r.addHeader("X-Content-Type-Options", "nosniff")
    }

    private fun partial(file: File, mime: String, etag: String, header: String): Response {
        val total = file.length()
        val spec = header.removePrefix("bytes=").substringBefore(',').trim()
        val startStr = spec.substringBefore('-')
        val endStr = spec.substringAfter('-', "")
        var start: Long
        var end: Long
        if (startStr.isEmpty()) { // bytes=-500 (500 byte terakhir)
            val suffix = endStr.toLongOrNull() ?: 0L
            start = (total - suffix).coerceAtLeast(0)
            end = total - 1
        } else {
            start = startStr.toLongOrNull() ?: 0L
            end = endStr.toLongOrNull() ?: (total - 1)
        }
        if (end >= total) end = total - 1
        if (start > end || start >= total) {
            return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "")
                .also { it.addHeader("Content-Range", "bytes */$total") }
        }
        val len = end - start + 1
        val cached = getCached(file)
        val stream: InputStream = if (cached != null) {
            ByteArrayInputStream(cached.bytes, start.toInt(), len.toInt())
        } else {
            val raf = RandomAccessFile(file, "r")
            raf.seek(start)
            FileInputStream(raf.fd).let { RangeStream(it, raf) }
        }
        val r = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, stream, len)
        r.addHeader("Content-Range", "bytes $start-$end/$total")
        commonHeaders(r, etag)
        return r
    }

    /** Stream yang menutup RandomAccessFile sekalian. */
    private class RangeStream(private val inner: InputStream, private val raf: RandomAccessFile) : InputStream() {
        override fun read(): Int = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len)
        override fun close() { runCatching { inner.close() }; runCatching { raf.close() } }
    }

    private fun getCached(file: File): CachedFile? {
        val key = file.canonicalPath
        val len = file.length()
        val mod = file.lastModified()
        cache[key]?.let { if (it.length == len && it.lastModified == mod) return it }
        if (len > MAX_FILE_IN_RAM) return null
        if (cacheUsed.get() + len > MAX_RAM) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val gz = if (len >= 512 && isCompressible(file.name)) gzip(bytes).takeIf { it.size < bytes.size } else null
        val c = CachedFile(bytes, gz, len, mod)
        val prev = cache.put(key, c)
        cacheUsed.addAndGet(len + (gz?.size ?: 0) - (prev?.let { it.length + (it.gz?.size ?: 0) } ?: 0))
        return c
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(bytes.size / 3 + 64)
        GZIPOutputStream(bos).use { it.write(bytes) }
        return bos.toByteArray()
    }

    private fun text(status: Response.Status, msg: String): Response =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", msg)

    private fun notFound(path: String): Response {
        val safe = path.replace("<", "&lt;").replace(">", "&gt;")
        val html = """<!doctype html><html lang="id"><meta charset="utf-8">
            |<meta name="viewport" content="width=device-width,initial-scale=1">
            |<title>Tidak ditemukan</title>
            |<body style="font-family:sans-serif;background:#101017;color:#ECEAF2;padding:24px">
            |<h2 style="color:#FF7A1A">File tidak ditemukan</h2><p>$safe</p></body></html>""".trimMargin()
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html; charset=utf-8", html)
    }

    /** Pelaksana koneksi dengan jumlah thread terbatas + antrean. */
    private class BoundedRunner(threads: Int) : AsyncRunner {
        private val running = Collections.synchronizedList(mutableListOf<ClientHandler>())
        private val pool = ThreadPoolExecutor(
            threads, threads, 30, TimeUnit.SECONDS, LinkedBlockingQueue(1024)
        ).apply { allowCoreThreadTimeOut(true) }

        override fun closeAll() {
            val copy = synchronized(running) { running.toList() }
            copy.forEach { runCatching { it.close() } }
        }

        override fun closed(clientHandler: ClientHandler) {
            running.remove(clientHandler)
        }

        override fun exec(code: ClientHandler) {
            running.add(code)
            try {
                pool.execute(code)
            } catch (e: RejectedExecutionException) {
                running.remove(code)
                runCatching { code.close() }
            }
        }

        fun shutdown() {
            pool.shutdownNow()
        }
    }

    companion object {
        const val THREADS = 24
        const val SOCKET_TIMEOUT_MS = 4000
        private const val MAX_FILE_IN_RAM = 8L * 1024 * 1024     // 8 MB per file
        private const val MAX_RAM = 96L * 1024 * 1024            // total 96 MB

        private val MIME = mapOf(
            "html" to "text/html; charset=utf-8", "htm" to "text/html; charset=utf-8",
            "css" to "text/css; charset=utf-8", "js" to "text/javascript; charset=utf-8",
            "mjs" to "text/javascript; charset=utf-8", "json" to "application/json; charset=utf-8",
            "map" to "application/json; charset=utf-8", "txt" to "text/plain; charset=utf-8",
            "csv" to "text/csv; charset=utf-8", "xml" to "application/xml; charset=utf-8",
            "svg" to "image/svg+xml", "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
            "gif" to "image/gif", "webp" to "image/webp", "ico" to "image/x-icon", "avif" to "image/avif",
            "bmp" to "image/bmp",
            "mp3" to "audio/mpeg", "wav" to "audio/wav", "ogg" to "audio/ogg", "m4a" to "audio/mp4",
            "mp4" to "video/mp4", "webm" to "video/webm",
            "woff" to "font/woff", "woff2" to "font/woff2", "ttf" to "font/ttf", "otf" to "font/otf",
            "wasm" to "application/wasm", "pdf" to "application/pdf",
            "webmanifest" to "application/manifest+json",
        )
        private val COMPRESSIBLE = setOf(
            "html", "htm", "css", "js", "mjs", "json", "map", "txt", "csv", "xml", "svg", "wasm", "webmanifest", "ttf", "otf"
        )

        fun mimeOf(name: String): String =
            MIME[name.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

        fun isCompressible(name: String): Boolean =
            name.substringAfterLast('.', "").lowercase() in COMPRESSIBLE
    }
}
