package org.justalk.kotlin.stdlib.compress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.annotation.IntRange
import androidx.exifinterface.media.ExifInterface
import org.justalk.kotlin.stdlib.Utils.inMainThread

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object ImageCompressor {

    /**
     * 综合压缩（尺寸+质量）
     *
     * 参考自 https://github.com/javakam/FileOperator/blob/master/library_compressor/src/main/java/ando/file/compressor/ImageCompressEngine.kt
     * @param inputFile     原始图片文件
     * @param outputFile    压缩后的目标文件
     * @param targetWidth   压缩目标宽度（像素） 如果指定，图片将按此宽度进行缩放，同时保持宽高比。为null则不限制
     * @param targetHeight  压缩目标高度（像素） 如果指定，图片将按此高度进行缩放，同时保持宽高比。为null则不限制
     * @param maxFileSize   压缩后文件大小限制（单位:B,可选，null表示不限制）
     * @param quality       初始压缩质量 (0-100) 默认80
     * @param outputFormat  输出格式 (JPEG、PNG、WEBP，默认JPEG)
     * @throws IOException 如果处理过程中发生I/O错误
     * @throws RuntimeException 如果位图无法解码或其它处理失败
     */
    @JvmOverloads
    fun compress(
        inputFile: File,
        outputFile: File,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
        maxFileSize: Int? = null,
        @IntRange(from = 0, to = 100) quality: Int = 80,
        outputFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ) {
        assert(!inMainThread) {
            "Avoid calling this on the main thread"
        }
        val srcBmp: Bitmap?  // 初始解码的Bitmap
        var processingBmp: Bitmap? = null
        try {
            // 解码并进行初始采样
            val options = BitmapFactory.Options().also { opts ->
                opts.inJustDecodeBounds = true
                FileInputStream(inputFile).use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            }
            options.inSampleSize = computeSize(options) // 计算合适的采样率
            options.inJustDecodeBounds = false

            srcBmp = FileInputStream(inputFile).use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: throw RuntimeException("Could not decode Bitmap: ${inputFile.absolutePath}")

            processingBmp = srcBmp
            // 修正JPG图片旋转角度
            if (isJPG(inputFile)) {
                val degree = getRotateDegree(inputFile)
                if (degree != 0) {
                    val rotatedBitmap = rotatingImage(processingBmp, degree)
                    if (rotatedBitmap != processingBmp && !processingBmp.isRecycled) {
                        processingBmp.recycle()
                    }
                    processingBmp = rotatedBitmap
                }
            }
            processingBmp ?: throw RuntimeException("Bitmap is null after rotation")

            val scaledBitmap = scaleBitmap(processingBmp, targetWidth, targetHeight)
            if (scaledBitmap != processingBmp) {
                processingBmp.recycle()
                processingBmp = scaledBitmap
            }

            // 执行质量压缩和最终尺寸缩放，字节数组输出流不需要释放
            val byteArray = compressToByteArray(
                processingBmp,
                quality,
                outputFormat,
                maxFileSize
            )
            // 将压缩后的数据写入文件
            val tempFile = createTempImageFile(outputFormat)
            tempFile.outputStream().use { fos ->
                fos.write(byteArray)
                fos.flush()
            }
            if (!tempFile.renameTo(outputFile)) { // 将临时文件重命名为目标文件
                throw RuntimeException("Failed to rename temp file")
            }
        } finally {
            processingBmp?.recycle()
        }
    }

    /**
     * 质量压缩
     */
    private fun compressToByteArray(
        bitmap: Bitmap,
        initialQuality: Int,
        format: Bitmap.CompressFormat,
        maxFileSize: Int?
    ): ByteArray {
        val outputStream = ByteArrayOutputStream()
        // 对于 PNG（无损压缩）或不需要限制文件大小的情况，直接压缩并返回。
        if (maxFileSize == null || format == Bitmap.CompressFormat.PNG) {
            bitmap.compress(format, initialQuality, outputStream)
            return outputStream.toByteArray()
        }
        var quality = initialQuality
        bitmap.compress(format, quality, outputStream)

        while (outputStream.size() > maxFileSize && quality > 10) {
            outputStream.reset() // 重置流
            quality -= 10 // 逐步降低质量
            bitmap.compress(format, quality, outputStream)
        }
        if (outputStream.size() > maxFileSize) {
            throw RuntimeException("Final size${outputStream.size()} exceeds maxSize. Please reduce target width and height.");
        }
        return outputStream.toByteArray()
    }

    /**
     * 将 Bitmap 缩放至给定的目标尺寸框内，同时保持其原始宽高比
     */
    private fun scaleBitmap(bitmap: Bitmap, targetWidth: Int?, targetHeight: Int?): Bitmap {
        val currentWidth = bitmap.width
        val currentHeight = bitmap.height

        val effectiveTargetWidth = targetWidth ?: currentWidth
        val effectiveTargetHeight = targetHeight ?: currentHeight

        // 如果图像已经小于或等于目标尺寸，则无需缩放
        if (currentWidth <= effectiveTargetWidth && currentHeight <= effectiveTargetHeight) {
            return bitmap
        }

        val widthRatio = effectiveTargetWidth.toFloat() / currentWidth
        val heightRatio = effectiveTargetHeight.toFloat() / currentHeight
        // 使用两个缩放比中较小的一个，确保缩放后图像能完全放入目标框内
        val ratio = min(widthRatio, heightRatio)
        val newWidth = (currentWidth * ratio).toInt()
        val newHeight = (currentHeight * ratio).toInt()

        return if (newWidth > 0 && newHeight > 0) {
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
    }

    /**
     * 智能采样率计算（仿微信算法）
     */
    private fun computeSize(
        options: BitmapFactory.Options,
    ): Int {
        val (srcWidth, srcHeight) = options.run { outWidth to outHeight }
        val reqWidth = if (srcWidth % 2 == 1) srcWidth + 1 else srcWidth
        val reqHeight = if (srcHeight % 2 == 1) srcHeight + 1 else srcHeight
        val longSide = max(reqWidth, reqHeight)
        val shortSide = min(reqWidth, reqHeight)
        val scale = shortSide.toFloat() / longSide
        return when {
            scale <= 1 && scale > 0.5625 -> {
                when {
                    longSide < 1664 -> 1
                    longSide < 4990 -> 2
                    longSide in 4991..10239 -> 4
                    else -> max(1, longSide / 1280)
                }
            }

            scale > 0.5 -> max(1, longSide / 1280)
            else -> ceil(longSide / (1280.0 / scale)).toInt()
        }
    }

    private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

    /**
     * 通过文件头判断是否是JPG/JPEG文件
     */
    private fun isJPG(file: File): Boolean {
        if (!file.exists() || !file.canRead()) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(3)
                if (fis.read(header) != 3) {  // 读取文件的前3个字节
                    return false
                }
                JPEG_SIGNATURE.contentEquals(header) // 检查文件头是否匹配JPEG签名
            }
        } catch (e: Exception) {
            Log.e("isJPG", "Error checking JPG file header: ${e.message}", e)
            false
        }
    }

    /**
     * 获取图片的旋转角度（基于EXIF信息）
     */
    private fun getRotateDegree(file: File): Int {
        return try {
            FileInputStream(file).use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                ).let {
                    when (it) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("getRotateDegree", "Failed to get image rotation angle: ${e.message}", e)
            0
        }
    }

    /**
     * 旋转Bitmap
     */
    private fun rotatingImage(bitmap: Bitmap?, angle: Int): Bitmap? {
        if (bitmap == null) {
            return null
        }
        val matrix = Matrix()
        matrix.postRotate(angle.toFloat()) // 设置旋转角度
        val rotatedBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotatedBitmap != bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }

    /**
     * 创建临时图片文件
     * @param format 图片格式(PNG/JPEG/WEBP)
     * @return 新创建的临时文件
     */
    private fun createTempImageFile(format: Bitmap.CompressFormat): File {
        val suffix = when (format) {
            Bitmap.CompressFormat.PNG -> ".png"
            Bitmap.CompressFormat.WEBP -> ".webp"
            else -> ".jpg"
        }
        return File.createTempFile("IMG_", suffix)
    }

}
