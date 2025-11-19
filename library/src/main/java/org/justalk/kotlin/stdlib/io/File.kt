package org.justalk.kotlin.stdlib.io

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

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
    assert(exists()) {
        "File is not exist"
    }
    val authority = "${context.packageName}.fileprovider"
    val contentUri = FileProvider.getUriForFile(context, authority, this)
    val shareIntent = Intent(Intent.ACTION_SEND).also { intent ->
        intent.type = mimeType()
        intent.putExtra(Intent.EXTRA_STREAM, contentUri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, title)
    if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}