package org.justalk.kotlin.stdlib.net

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import androidx.core.graphics.scale
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import org.justalk.kotlin.stdlib.context.ContextUtils
import org.justalk.kotlin.stdlib.media.ImageUtil
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 将 Uri 解码为方向修正后的 Bitmap，并根据指定的 [scaleType] 缩放到目标尺寸。
 *
 * 内部会自动根据 EXIF 信息修正 JPEG 图片的旋转/翻转，然后按 [scaleType] 进行缩放或裁剪。
 * 使用 inSampleSize 进行高效初始采样，避免加载原始大图导致 OOM。
 *
 * 支持的 ScaleType：
 * - [ImageView.ScaleType.CENTER_CROP]：等比缩放使短边 ≥ 目标，然后居中裁剪，输出精确 width × height
 * - [ImageView.ScaleType.FIT_CENTER]：等比缩放使长边 = 目标（可放大可缩小）
 * - 其它（含 CENTER_INSIDE、FIT_XY、FIT_START、FIT_END 等）：按 CENTER_INSIDE 处理，等比缩小使长边 ≤ 目标（仅缩小，不放大）
 *
 * @param width     目标宽度（像素）
 * @param height    目标高度（像素）
 * @param scaleType 缩放模式，默认 [ImageView.ScaleType.CENTER_INSIDE]
 * @return 方向修正且按 scaleType 缩放后的 Bitmap
 * @throws RuntimeException 如果无法解码图片
 */
fun Uri.toBitmap(
    width: Int = Int.MAX_VALUE,
    height: Int = Int.MAX_VALUE,
    scaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_INSIDE
): Bitmap {
    val context = ContextUtils.getApplication()
    val resolver = context.contentResolver

    fun calculateInSampleSize(rawWidth: Int, rawHeight: Int): Int {
        var inSampleSize = 1
        if (rawWidth > width || rawHeight > height) {
            val halfWidth = rawWidth / 2
            val halfHeight = rawHeight / 2
            while (halfWidth / inSampleSize >= width && halfHeight / inSampleSize >= height) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun applyScaleType(source: Bitmap): Bitmap {
        val srcW = source.width
        val srcH = source.height

        return when (scaleType) {
            ImageView.ScaleType.FIT_CENTER -> {
                // 等比缩放使长边 = 目标（可放大可缩小）
                val ratio = min(width.toFloat() / srcW, height.toFloat() / srcH)
                val newW = (srcW * ratio).roundToInt()
                val newH = (srcH * ratio).roundToInt()
                if (newW == srcW && newH == srcH) source
                else source.scale(newW, newH)
                    .also { if (it != source) source.recycle() }
            }

            ImageView.ScaleType.CENTER_CROP -> {
                // 等比缩放使短边 ≥ 目标，然后居中裁剪
                val ratio = max(width.toFloat() / srcW, height.toFloat() / srcH)
                val scaledW = (srcW * ratio).roundToInt()
                val scaledH = (srcH * ratio).roundToInt()
                val scaled = if (scaledW == srcW && scaledH == srcH) source
                else source.scale(scaledW, scaledH)
                    .also { if (it != source) source.recycle() }

                // 居中裁剪
                val cropW = min(width, scaled.width)
                val cropH = min(height, scaled.height)
                val x = (scaled.width - cropW) / 2
                val y = (scaled.height - cropH) / 2
                val cropped = Bitmap.createBitmap(scaled, x, y, cropW, cropH)
                if (cropped != scaled) scaled.recycle()
                cropped
            }

            // CENTER_INSIDE 以及其它不适用的类型（CENTER, MATRIX）均按 CENTER_INSIDE 处理
            else -> {
                // 等比缩小使长边 ≤ 目标（仅缩小，不放大）
                if (srcW <= width && srcH <= height) return source
                val ratio = min(width.toFloat() / srcW, height.toFloat() / srcH)
                val newW = (srcW * ratio).roundToInt()
                val newH = (srcH * ratio).roundToInt()
                source.scale(newW, newH)
                    .also { if (it != source) source.recycle() }
            }
        }
    }

    // 1. 获取原始尺寸
    val options = BitmapFactory.Options().also { opts ->
        opts.inJustDecodeBounds = true
        resolver.openInputStream(this)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }

    // 2. 计算 inSampleSize（基于目标尺寸，高效采样避免 OOM）
    options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight)
    options.inJustDecodeBounds = false

    // 3. 解码 Bitmap
    var bitmap = resolver.openInputStream(this)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    } ?: throw RuntimeException("Could not decode Bitmap: $this")

    // 4. EXIF 方向修正
    if (ImageUtil.isJPG(this)) {
        val rotated = ImageUtil.rotateImage(bitmap, this)
        if (rotated != bitmap) {
            bitmap = rotated
        }
    }

    // 5. 按 scaleType 缩放/裁剪
    return applyScaleType(bitmap)
}

/**
 * 判断是否是 Gif 格式
 */
fun Uri.isGif(): Boolean {
    val documentFile = if (scheme == ContentResolver.SCHEME_CONTENT) {
        DocumentFile.fromSingleUri(ContextUtils.getApplication(), this)
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
fun Uri.copyTo(target: File): File {
    target.parentFile?.mkdirs()
    val tmpFile = File(target.parent, "${target.name}.tmp")
    if (tmpFile.exists()) {
        tmpFile.delete()
    }
    try {
        ContextUtils.getApplication().contentResolver.openInputStream(this)!!.use { input ->
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