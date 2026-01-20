package org.justalk.kotlin.stdlib.graphics

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 将 Bitmap 保存为临时文件
 *
 * 特性：
 * 1. 保存位置：context.cacheDir (内部缓存目录)。
 * 2. 清理机制：
 * - 调用了 deleteOnExit()，JVM 正常退出时会尝试删除。
 * - 即使 deleteOnExit 失效（如进程被杀），系统也会在存储空间不足时优先清理 cacheDir。
 * - 也可以在 App 下次启动时手动清理缓存目录。
 *
 * @param context 上下文
 * @param format 图片格式，默认 JPEG
 * @param quality 压缩质量 (0-100)，默认 100
 * @return 生成的临时文件 File 对象，如果失败返回 null
 */
@SuppressLint("NewApi")
@JvmOverloads
fun Bitmap.toTempFile(
    context: Context,
    folderName: String? = null,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    quality: Int = 100
): File? {
    return try {
        // 1. 确定文件后缀
        val extension = when (format) {
            Bitmap.CompressFormat.PNG -> ".png"
            Bitmap.CompressFormat.WEBP,
            Bitmap.CompressFormat.WEBP_LOSSY,
            Bitmap.CompressFormat.WEBP_LOSSLESS -> ".webp"
            else -> ".jpg"
        }
        // 2. 创建临时文件
        // createTempFile 会自动在文件名后生成随机数，保证唯一性
        // 存放目录：context.cacheDir
        val targetDir = folderName?.takeIf { it.isNotEmpty() }?.let { File(context.cacheDir, it) }
            ?: context.cacheDir
        if (!targetDir.exists()) targetDir.mkdirs()
        val tempFile = File.createTempFile("temp_img_", extension, targetDir)
        // 3. 注册 JVM 退出时的删除钩子 (满足你的“退出后清理”需求)
        // 注意：如果是 Force Stop 或 Crash，这个可能不会触发，但放在 cacheDir 里是安全的
        tempFile.deleteOnExit()
        // 4. 写入文件
        FileOutputStream(tempFile).use { output ->
            this.compress(format, quality, output)
            output.flush()
        }
        tempFile
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}