package id.tg.vcv.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile

object UriInfo {

    fun displayName(context: Context, uri: Uri, isTree: Boolean = false): String {
        if (isTree) {
            return runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
                ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
                ?: "Folder"
        }
        val fromQuery = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return fromQuery
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "proyek"
    }

    /** Petunjuk lokasi yang ramah dibaca, mis. "Download/kuis.zip". */
    fun sourceLabel(context: Context, uri: Uri, displayName: String, isTree: Boolean = false): String {
        return runCatching {
            when (uri.authority) {
                "com.android.externalstorage.documents" -> {
                    val docId = if (isTree) DocumentsContract.getTreeDocumentId(uri)
                    else DocumentsContract.getDocumentId(uri)
                    docId.substringAfter(':').ifEmpty { displayName }
                }
                "com.android.providers.downloads.documents" -> "Download/$displayName"
                else -> displayName
            }
        }.getOrDefault(displayName)
    }

    fun stripExtension(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".html") -> name.dropLast(5)
            lower.endsWith(".htm") -> name.dropLast(4)
            lower.endsWith(".zip") -> name.dropLast(4)
            else -> name
        }.ifBlank { name }
    }
}
