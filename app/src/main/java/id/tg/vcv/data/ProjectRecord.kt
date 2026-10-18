package id.tg.vcv.data

import org.json.JSONObject

/** Jenis sumber proyek. */
enum class Kind { ZIP, HTML, FOLDER, SHARED }

/**
 * Satu baris di daftar "Terbaru".
 * VcV TIDAK menyimpan salinan proyek, hanya alamat (uri) file/folder asli.
 * Pengecualian: file kiriman aplikasi lain (WhatsApp) disalin sementara (copyPath)
 * dan salinan itu dihapus begitu proyek ditutup.
 */
data class ProjectRecord(
    val id: String,
    val name: String,              // nama tampilan (bisa diganti)
    val fileName: String,          // nama file/folder asli
    val kind: Kind,
    val uri: String,               // content:// asli (ZIP/HTML/FOLDER) atau kosong untuk SHARED
    val entry: String,             // jalur relatif file HTML yang dibuka, mis. "index.html" atau "game/main.html"
    val sourceLabel: String,       // petunjuk lokasi, mis. "Download/kuis.zip"
    val lastOpened: Long,
    val sharedTag: String = "",    // "WA" atau "APP" untuk SHARED
    val sharedExt: String = "",    // "zip" atau "html" untuk SHARED
    val copyPath: String = "",     // lokasi salinan sementara untuk SHARED
    val copyDeleted: Boolean = false,
    /** Nomor urut port proyek (port dasar + slot). Port berbeda = data peramban (localStorage) terpisah. */
    val slot: Int = -1,
) {
    val isSharedCopyGone: Boolean get() = kind == Kind.SHARED && (copyDeleted || copyPath.isEmpty())

    /** Kind efektif isi file (untuk SHARED dilihat dari ekstensinya). */
    val contentIsZip: Boolean
        get() = kind == Kind.ZIP || (kind == Kind.SHARED && sharedExt == "zip")

    /** Proyek berisi banyak file (ZIP/folder), jadi bisa punya lebih dari satu halaman. */
    val canHaveManyPages: Boolean
        get() = kind == Kind.FOLDER || contentIsZip

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("fileName", fileName); put("kind", kind.name)
        put("uri", uri); put("entry", entry); put("sourceLabel", sourceLabel)
        put("lastOpened", lastOpened); put("sharedTag", sharedTag); put("sharedExt", sharedExt)
        put("copyPath", copyPath); put("copyDeleted", copyDeleted)
        put("slot", slot)
    }

    companion object {
        fun fromJson(o: JSONObject): ProjectRecord = ProjectRecord(
            id = o.getString("id"),
            name = o.optString("name"),
            fileName = o.optString("fileName"),
            kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrDefault(Kind.ZIP),
            uri = o.optString("uri"),
            entry = o.optString("entry", "index.html"),
            sourceLabel = o.optString("sourceLabel"),
            lastOpened = o.optLong("lastOpened"),
            sharedTag = o.optString("sharedTag"),
            sharedExt = o.optString("sharedExt"),
            copyPath = o.optString("copyPath"),
            copyDeleted = o.optBoolean("copyDeleted"),
            slot = o.optInt("slot", -1),
        )
    }
}
