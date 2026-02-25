package org.justalk.kotlin.stdlib.net

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import java.io.File
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

/**
 * 拷贝文件到指定的 file
 */
@Throws(Exception::class)
fun Uri.copyTo(context: Context, target: File): File {
    target.parentFile?.mkdirs()
    val tmpFile = File(target.parent, "${target.name}.tmp")
    if (tmpFile.exists()) {
        tmpFile.delete()
    }
    try {
        context.contentResolver.openInputStream(this)!!.use { input ->
            tmpFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        val renameSuccess = tmpFile.renameTo(target)
        if (!renameSuccess) {
            if (target.exists()) {
                return target
            }
            tmpFile.delete()
            throw RuntimeException("renameTo failure")
        }
        return target
    } catch (e: Exception) {
        if (tmpFile.exists()) {
            tmpFile.delete()
        }
        throw e
    }
}