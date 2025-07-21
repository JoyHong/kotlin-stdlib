package org.justalk.kotlin.stdlib.media

import android.util.Log
import com.googlecode.mp4parser.authoring.Movie
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.WritableByteChannel

object VideoUtil {

    /**
     * 解析视频文件并移动 'moov' atom 到文件头部，以实现快速启动（Fast Start）
     *
     * @param inputFile 输入视频文件
     * @param outputFile 输出视频文件
     * @return 如果操作成功返回 true，否则返回 false
     */
    fun parseVideoFastStart(inputFile: File, outputFile: File): Boolean {
        val tag = "parseVideoF"
        try {
            // 读取原始 MP4 文件并构建 Movie 对象
            val movie: Movie = MovieCreator.build(inputFile.absolutePath)
            // 创建新的 MP4 容器。DefaultMp4Builder 会自动将 'moov' atom 放在文件头部
            val out = DefaultMp4Builder().build(movie)
            FileOutputStream(outputFile).channel.use { fc: WritableByteChannel ->
                out.writeContainer(fc)
            }
            return true
        } catch (e: IOException) {
            Log.e(tag, "Failed to parse video for fast start due to IO error", e)
            return false
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse video for fast start due to unexpected error", e)
            return false
        }
    }
}