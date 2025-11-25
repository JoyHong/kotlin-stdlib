package org.justalk.kotlin.stdlib.net

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

/**
 * 判断是否是 Gif 格式
 */
fun Uri.isGif(context: Context): Boolean {
    val documentFile = if (scheme == ContentResolver.SCHEME_CONTENT) {
        DocumentFile.fromSingleUri(context, this)
    } else if (scheme == ContentResolver.SCHEME_FILE) {
        DocumentFile.fromFile(toFile())
    } else {
        null
    }
    return documentFile?.type?.lowercase(Locale.US)?.let { mimeType ->
        mimeType == "image/gif"
    } ?: toString().endsWith(".gif", ignoreCase = true)
}