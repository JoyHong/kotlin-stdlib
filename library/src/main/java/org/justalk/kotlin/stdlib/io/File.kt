package org.justalk.kotlin.stdlib.io

import android.content.ActivityNotFoundException
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
import android.util.Size
import android.webkit.MimeTypeMap
import android.widget.ImageView
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import org.justalk.kotlin.stdlib.context.ContextUtils
import org.justalk.kotlin.stdlib.net.copyTo
import org.justalk.kotlin.stdlib.net.getImageSize
import org.justalk.kotlin.stdlib.net.toBitmap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 获取图片文件的尺寸（已根据 EXIF 方向信息修正宽高）。
 *
 * 不会解码完整 Bitmap，仅读取图片头信息和 EXIF，非常轻量。
 *
 * @return 修正方向后的图片尺寸 [Size]（width, height）
 * @throws RuntimeException 如果无法读取图片尺寸
 * @see [Uri.getImageSize]
 */
fun File.getImageSize(): Size = Uri.fromFile(this).getImageSize()

/**
 * 将图片文件解码为方向修正后的 Bitmap，并根据指定的 [scaleType] 缩放到目标尺寸。
 *
 * @param width     目标宽度（像素），默认 [Int.MAX_VALUE] 表示不限制
 * @param height    目标高度（像素），默认 [Int.MAX_VALUE] 表示不限制
 * @param scaleType 缩放模式，默认 [ImageView.ScaleType.CENTER_INSIDE]
 * @return 方向修正且按 scaleType 缩放后的 Bitmap
 * @throws RuntimeException 如果无法解码图片
 * @see [Uri.toBitmap]
 */
@JvmOverloads
fun File.toBitmap(
    width: Int = Int.MAX_VALUE,
    height: Int = Int.MAX_VALUE,
    scaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_INSIDE
): Bitmap = Uri.fromFile(this).toBitmap(width, height, scaleType)


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
 * @param context 上下文
 * @param title 分享弹窗的标题
 *
 * @throws NoSuchFileException 当文件不存在时抛出
 * @throws IllegalArgumentException 当 FileProvider 配置错误（manifest没配或paths没包含该路径）时抛出
 * @throws ActivityNotFoundException 当手机上没有 APP 能处理分享请求时抛出
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
@Throws(
    NoSuchFileException::class,
    IllegalArgumentException::class,
    ActivityNotFoundException::class
)
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
 * 使用第三方应用打开此文件(不支持文件夹) apk 安装包需要申明权限 <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
 *
 * @param context 上下文
 *
 * @throws NoSuchFileException 当文件不存在时抛出
 * @throws IllegalArgumentException 当传入的是文件夹，或者 FileProvider 配置错误时抛出
 * @throws ActivityNotFoundException 当系统找不到能打开该文件类型的 App 时抛出
 * @throws SecurityException 当文件是 APK 且 App 没有“安装未知应用”权限时抛出 (Android 8.0+)
 */
@Throws(
    NoSuchFileException::class,
    IllegalArgumentException::class,
    ActivityNotFoundException::class,
    SecurityException::class
)
fun File.open(context: Context) {
    if (!exists()) {
        throw NoSuchFileException(file = this, reason = "The source file doesn't exist.")
    }
    if (isDirectory) {
        throw IllegalArgumentException("Directory file is not supported.")
    }
    val mimeType = mimeType()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (mimeType == "application/vnd.android.package-archive") {
            // 检查是否有安装权限
            if (!context.packageManager.canRequestPackageInstalls()) {
                throw SecurityException("Permission REQUEST_INSTALL_PACKAGES not granted.")
            }
        }
    }
    val authority = "${context.packageName}.fileprovider"
    val contentUri = FileProvider.getUriForFile(context, authority, this)
    val intent = Intent(Intent.ACTION_VIEW).also { intent ->
        intent.setDataAndType(contentUri, mimeType)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/**
 * 确保当前目录下存在 .nomedia 文件。
 *
 * Android 的 MediaScanner 只扫描外部存储（如 getExternalFilesDir()、/sdcard/ 等路径）。
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
 * @param folderName 自定义子文件夹名称 (例如 "MyAppImages")
 * @param forceDownloadFolder 是否强制保存到系统的 Download 目录。
 *                            默认为 false (自动归类到 Pictures/Movies 等)。
 *                            如果不为 true, 则 folderName 就不生效了, 建议配合 openSystemDownloads 能够更大概率实现“打开文件夹”功能。
 * @param defaultMimeType 当无法从文件后缀识别 MimeType 时的默认值 (例如 "image/jpeg")。
 * @param supportWebp 是否支持保存为 WebP 格式。默认为 true。如果传 false 且源文件是 WebP，则会自动转码为 JPG 保存。
 * @return 保存成功后的 Uri，失败返回 null
 *
 * @throws NoSuchFileException 当源文件不存在时抛出
 * @throws IOException 当文件读写失败（如磁盘已满、无权限、MediaStore插入失败）时抛出
 */
@JvmOverloads
@Throws(NoSuchFileException::class, IOException::class)
fun File.saveToPublicStorage(
    folderName: String? = null,
    forceDownloadFolder: Boolean = false,
    defaultMimeType: String? = null,
    supportWebp: Boolean = false
): Uri {
    if (!exists()) {
        throw NoSuchFileException(file = this, reason = "The source file doesn't exist.")
    }

    // --- 1. 预处理：WebP 转码检查 ---
    // 定义最终要保存的“源文件”和“文件名/MimeType”
    // 如果发生了转码，sourceFileToSave 会指向一个临时的 JPG 文件
    var sourceFileToSave = this
    var finalFileName = name
    var finalMimeType = mimeType()
    if (finalMimeType == "*/*" && defaultMimeType.isNullOrEmpty().not()) {
        finalMimeType = defaultMimeType
        MimeTypeMap.getSingleton().getExtensionFromMimeType(defaultMimeType)?.takeIf { it.isNotEmpty() }?.let { extension ->
            finalFileName = "${nameWithoutExtension}.$extension"
        }
    }

    val isOriginalWebp = extension.equals("webp", ignoreCase = true) || finalMimeType.contains("webp")
    val needConversion = isOriginalWebp && !supportWebp

    // 用于记录临时文件，以便最后删除
    var tempConvertedFile: File? = null

    if (needConversion) {
        try {
            // 在缓存目录创建一个临时的 .jpg 文件
            val newName = "${nameWithoutExtension}.jpg"
            val tempFile = File(ContextUtils.getApplication().cacheDir, newName)
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
    val isImage = !forceDownloadFolder && finalMimeType.startsWith("image") == true
    val isVideo = !forceDownloadFolder && !isImage && finalMimeType.startsWith("video") == true
    val isAudio = !forceDownloadFolder && !isImage && !isVideo && finalMimeType.startsWith("audio") == true

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
                } + (folderName?.takeIf { !forceDownloadFolder && it.isNotEmpty() }?.let { "/$it" } ?: "")
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = when {
                isImage -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                isAudio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
            val resolver = ContextUtils.getApplication().contentResolver
            val uri = resolver.insert(collection, contentValues)
                ?: throw IOException("Failed to create MediaStore record. Uri is null.")
            return try {
                sourceFileToSave.copyTo(uri)
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw IOException("Failed to write data to MediaStore uri.", e)
            }
        }
        // ================== Android 9- (API < 29) ==================
        val targetRoot = when {
            isImage -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            isVideo -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            isAudio -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }
        val targetDir = if (!forceDownloadFolder && folderName != null) File(targetRoot, folderName) else targetRoot
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw IOException("Failed to create directory: ${targetDir.absolutePath}")
        }
        // 使用 finalFileName (如果是转码过的，这里已经是 .jpg 结尾了)
        val initialTargetFile = File(targetDir, finalFileName)
        // 处理冲突：如果有同名文件，获取 _1, _2 新路径
        val finalTargetFile = initialTargetFile.getUniqueFile()
        return try {
            sourceFileToSave.copyTo(finalTargetFile, overwrite = true)
            MediaScannerConnection.scanFile(
                ContextUtils.getApplication(),
                arrayOf(finalTargetFile.absolutePath),
                arrayOf(finalMimeType)
            ) { _, uri -> }
            Uri.fromFile(finalTargetFile)
        } catch (e: IOException) {
            e.printStackTrace()
            throw e
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
 * 将当前文件的内容拷贝到目标 Uri (通常是 MediaStore Uri)
 *
 * @param target 目标文件的 Uri
 * @return 返回目标 Uri (方便链式调用)
 *
 * @throws NoSuchFileException 当源文件不存在时抛出
 * @throws IOException 当读写过程中发生错误（如：目标 Uri 无法打开、磁盘空间不足、IO中断）时抛出
 */
@Throws(NoSuchFileException::class, IOException::class)
fun File.copyTo(target: Uri): Uri {
    if (!this.exists()) {
        throw NoSuchFileException(file = this, reason = "The source file doesn't exist.")
    }
    this.inputStream().use { input ->
        val output = ContextUtils.getApplication().contentResolver.openOutputStream(target)
            ?: throw IOException("Failed to open output stream for target Uri: $target")
        output.use { output ->
            input.copyTo(output)
        }
    }
    return target
}

/**
 * 将当前文件的内容拷贝到目标 File
 *
 * 内部通过 [Uri.copyTo] 实现, 会先创建临时文件再重命名, 保证写入的原子性。
 *
 * @param target 目标文件
 * @return 返回目标 File (方便链式调用)
 *
 * @throws NoSuchFileException 当源文件不存在时抛出
 * @throws Exception 当读写过程中发生错误时抛出
 */
@Throws(NoSuchFileException::class, Exception::class)
fun File.copyTo(target: File): File {
    if (!this.exists()) {
        throw NoSuchFileException(file = this, reason = "The source file doesn't exist.")
    }
    return toUri().copyTo(target)
}

/**
 * 将当前文件移动到目标路径
 *
 * 内部使用 [File.renameTo] 实现, 如果目标目录不存在会自动创建。
 * 注意: renameTo 在跨文件系统/跨挂载点时可能失败, 此时返回 null。
 *
 * @param target 目标文件
 * @return 移动成功返回目标 File, 失败返回 null
 */
fun File.moveTo(target: File): File? {
    target.parentFile?.takeIf { !it.exists() }?.mkdirs()
    return if (renameTo(target)) {
        target
    } else {
        null
    }
}

/**
 * 根据路径返回有效的后缀
 */
fun String.extension(): String {
    // 1. 去掉查询参数（如 ..mp4?token=123）
    val path = this.substringBefore('?')
    // 2. 获取最后一个点之后的内容
    val ext = path.substringAfterLast('.', "")
    // 3. 规避路径中有点但文件名没点的情况（如 /folder.name/file）
    return if (ext.contains('/')) "" else ext
}