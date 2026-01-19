package org.justalk.kotlin.stdlib.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException

/**
 * 获取文件的 MimeType 信息
 */
fun File.mimeType(): String {
    val extension = extension
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"
}

/**
 * 分享文件到第三方应用
 *
 * <provider
 *         android:name="androidx.core.content.FileProvider"
 *         android:authorities="${applicationId}.fileprovider"
 *         android:exported="false"
 *         android:grantUriPermissions="true">
 *         <meta-data
 *             android:name="android.support.FILE_PROVIDER_PATHS"
 *             android:resource="@xml/file_paths" />
 *     </provider>
 *
 *     <?xml version="1.0" encoding="utf-8"?>
 * <paths>
 *     <external-cache-path name="my_external_cache" path="." />
 *     <external-files-path name="my_external_files" path="." />
 *     <cache-path name="my_cache" path="." />
 *     <files-path name="my_files" path="." />
 *     <external-path name="external_root" path="." />
 * </paths>
 */
fun File.share(context: Context, title: CharSequence) {
    if (!exists()) {
        throw NoSuchFileException(file = this, reason = "The source file doesn't exist.")
    }
    val authority = "${context.packageName}.fileprovider"
    val contentUri = FileProvider.getUriForFile(context, authority, this)
    val shareIntent = Intent(Intent.ACTION_SEND).also { intent ->
        intent.type = mimeType()
        intent.putExtra(Intent.EXTRA_STREAM, contentUri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val intent = Intent.createChooser(shareIntent, title).also { intent ->
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/**
 * 使用第三方应用打开此文件(包含文件夹)
 *
 * apk 安装包需要申明权限 <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
 */
fun File.open(context: Context) {
    if (!exists()) {
        throw NoSuchFileException(file = this, reason = "The source file doesn't exist.")
    }
    assert(!isDirectory) {
        "Directory file is not supported"
    }
    val authority = "${context.packageName}.fileprovider"
    val contentUri = FileProvider.getUriForFile(context, authority, this)
    val intent = Intent(Intent.ACTION_VIEW).also { intent ->
        intent.setDataAndType(contentUri, mimeType())
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/**
 * 确保当前目录下存在 .nomedia 文件。
 *
 * @return true 表示操作成功（文件已存在或创建成功）；
 * false 表示操作失败（例如当前对象是文件而非目录、创建目录失败、或无写入权限）。
 */
fun File.ensureNomedia(): Boolean {
    if (!exists() && !mkdirs()) {
        return false
    }
    if (!isDirectory) {
        return false
    }
    val nomediaFile = File(this, ".nomedia")
    if (nomediaFile.exists()) {
        return true
    }
    return try {
        nomediaFile.createNewFile()
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }
}

/**
 * 拷贝文件到指定的 uri
 */
@Throws(Throwable::class)
fun File.copyTo(context: Context, target: Uri): Uri {
    if (!this.exists()) {
        throw NoSuchFileException(file = this, reason = "The source file doesn't exist.")
    }
    this.inputStream().use { input ->
        context.contentResolver.openOutputStream(target)!!.use { output ->
            input.copyTo(output)
        }
    }
    return target
}

/**
 * 拷贝文件到指定的 file
 */
@Throws(Throwable::class)
fun DocumentFile.copyTo(context: Context, target: File): File {
    if (target.exists()) {
        throw RuntimeException("The destination file already exists.")
    }
    target.parentFile?.mkdirs()
    val tmpFile = File(target.parent, "${target.name}.tmp")
    if (tmpFile.exists()) {
        tmpFile.delete()
    }
    try {
        val inputStream = context.contentResolver.openInputStream(uri)!!
        inputStream.use { input ->
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