package org.justalk.kotlin.stdlib.io

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
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
 * 使用第三方应用打开此文件(不支持文件夹)
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
 * 获取不冲突的文件对象
 * 如果当前文件已存在, 会自动在文件名后追加序号（如 "name_1.jpg", "name_2.jpg"）, 直到找到一个尚不存在的文件路径。
 *
 * @return 一个不存在于文件系统的 File 对象
 */
fun File.getUniqueFile(): File {
    // 如果当前文件本来就不存在, 则直接返回自己
    if (!exists()) return this
    val parent = parentFile
    val originalName = nameWithoutExtension
    val ext = extension // 自动去掉开头的点, 例如 "jpg"
    var file = this
    var counter = 1
    // 循环检查, 直到找到一个不存在的文件名
    while (file.exists()) {
        val newName = if (ext.isNotEmpty()) {
            "${originalName}_${counter}.$ext"
        } else {
            "${originalName}_${counter}"
        }
        file = File(parent, newName)
        counter++
    }
    return file
}

/**
 * 将当前 File 保存到系统公共存储区。
 *
 * 功能点：
 * 1. Android 10+ (API 29+) 使用 MediaStore，无需存储权限。
 * 2. Android 9- (API < 29) 使用 File 操作 + MediaScanner，需要 WRITE_EXTERNAL_STORAGE 权限。
 * 3. 自动处理文件名冲突（API 29+ 系统处理，API < 29 代码处理）。
 * 4. 支持 WebP 转 JPG。
 *
 * @param context 上下文
 * @param folderName 自定义子文件夹名称 (例如 "MyAppImages")
 * @param defaultMimeType 当无法从文件后缀识别 MimeType 时的默认值 (例如 "image/jpeg")。
 * @param supportWebp 是否支持保存为 WebP 格式。默认为 true。如果传 false 且源文件是 WebP，则会自动转码为 JPG 保存。
 * @return 保存成功后的 Uri，失败返回 null
 */
fun File.saveToPublicStorage(
    context: Context,
    folderName: String? = null,
    defaultMimeType: String? = null,
    supportWebp: Boolean = true
): Uri? {
    if (!exists()) return null

    // --- 1. 预处理：WebP 转码检查 ---
    // 定义最终要保存的“源文件”和“文件名/MimeType”
    // 如果发生了转码，sourceFileToSave 会指向一个临时的 JPG 文件
    var sourceFileToSave = this
    var finalFileName = name
    var finalMimeType = mimeType()
    if (finalMimeType == "*/*" && defaultMimeType.isNullOrEmpty().not()) {
        finalMimeType = defaultMimeType
    }

    val isOriginalWebp = extension.equals("webp", ignoreCase = true) || finalMimeType.contains("webp", ignoreCase = true)
    val needConversion = isOriginalWebp && !supportWebp

    // 用于记录临时文件，以便最后删除
    var tempConvertedFile: File? = null

    if (needConversion) {
        try {
            // 在缓存目录创建一个临时的 .jpg 文件
            val newName = "${nameWithoutExtension}.jpg"
            val tempFile = File(context.cacheDir, newName)
            // 执行转码：WebP File -> Bitmap -> JPG File
            val bitmap = BitmapFactory.decodeFile(absolutePath)
            if (bitmap != null) {
                FileOutputStream(tempFile).use { output ->
                    // 质量设为 100，或者是你想要的压缩率
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
                }
                bitmap.recycle() // 释放内存
                // 更新后续逻辑使用的变量
                sourceFileToSave = tempFile
                finalFileName = newName
                finalMimeType = "image/jpeg" // 强制修正 MimeType
                tempConvertedFile = tempFile
            } else {
                // 如果解码失败（比如坏文件），依然尝试按原样保存，或者直接 return null
                // 这里选择按原样保存作为兜底
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 转码失败，降级为保存原文件
        }
    }

    // --- 2. 准备路径参数 ---
    val isImage = finalMimeType.startsWith("image", ignoreCase = true) == true
    val isVideo = !isImage && finalMimeType.startsWith("video", ignoreCase = true) == true
    val isAudio = !isImage && !isVideo && finalMimeType.startsWith("audio", ignoreCase = true) == true

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // ================== Android 10+ (API 29+) ==================
            val contentValues = ContentValues().also { values ->
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                values.put(MediaStore.MediaColumns.MIME_TYPE, finalMimeType)
                val relativePath = when {
                    isImage -> Environment.DIRECTORY_PICTURES
                    isVideo -> Environment.DIRECTORY_MOVIES
                    isAudio -> Environment.DIRECTORY_MUSIC
                    else -> Environment.DIRECTORY_DOWNLOADS
                } + (folderName?.takeIf { it.isNotEmpty() }?.let { "/$it" } ?: "")
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = when {
                isImage -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                isAudio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, contentValues) ?: return null
            return try {
                sourceFileToSave.copyTo(context, uri)
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                uri
            } catch (_: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        }
        // ================== Android 9- (API < 29) ==================
        val targetRoot = when {
            isImage -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            isVideo -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            isAudio -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }
        val targetDir = if (folderName != null) File(targetRoot, folderName) else targetRoot
        if (!targetDir.exists()) targetDir.mkdirs()
        // 使用 finalFileName (如果是转码过的，这里已经是 .jpg 结尾了)
        val initialTargetFile = File(targetDir, finalFileName)
        // 处理冲突：如果有同名文件，获取 _1, _2 新路径
        val finalTargetFile = initialTargetFile.getUniqueFile()
        return try {
            sourceFileToSave.copyTo(finalTargetFile, overwrite = true)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(finalTargetFile.absolutePath),
                arrayOf(finalMimeType)
            ) { _, uri -> }
            Uri.fromFile(finalTargetFile)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    } finally {
        // --- 3. 清理 ---
        // 如果生成了临时 JPG 文件，使用完后删除它，避免占用缓存空间
        try {
            tempConvertedFile?.delete()
        } catch (_: Exception) {
            // ignore
        }
    }
}

/**
 * 拷贝文件到指定的 uri
 */
@Throws(Exception::class)
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
@Throws(Exception::class)
fun Uri.copyTo(context: Context, target: File): File {
    if (target.exists()) {
        throw RuntimeException("The destination file already exists.")
    }
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