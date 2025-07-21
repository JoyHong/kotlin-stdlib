package org.justalk.kotlin.stdlib.media

import android.util.Log
import com.googlecode.mp4parser.authoring.Movie
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator
import org.justalk.kotlin.stdlib.Utils.inMainThread
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
    fun parseFastStart(inputFile: File, outputFile: File) {
        assert(!inMainThread) {
            "Avoid calling this on the main thread"
        }
        val tempFile = File.createTempFile("faststart_", ".mp4", inputFile.parentFile)
        try {
            // 读取原始 MP4 文件并构建 Movie 对象
            val movie: Movie = MovieCreator.build(inputFile.absolutePath)
            // 创建新的 MP4 容器。DefaultMp4Builder 会自动将 'moov' atom 放在文件头部
            val out = DefaultMp4Builder().build(movie)
            FileOutputStream(tempFile).channel.use { fc: WritableByteChannel ->
                out.writeContainer(fc)
            }
            if (!tempFile.renameTo(outputFile)) {
                throw RuntimeException("Failed to rename temporary file to ${outputFile.name}")
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed to parse video for fast start due to IO error", e)
        } catch (e: Exception) {
            throw RuntimeException("Failed to parse video for fast start due to unexpected error", e)
        } finally {
            tempFile.delete()
        }
    }
}