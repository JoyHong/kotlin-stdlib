package org.justalk.kotlin.stdlib.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import org.justalk.kotlin.stdlib.Utils.isMainThread
import java.io.File
import java.nio.ByteBuffer

object AudioUtil {

    private const val TRIM_START_US = 150_000L // 定义修剪开始时间0.15s，单位为微秒 (microseconds)

    /**
     * Merges multiple input audio files into a single output audio file
     *
     * @param outputFile The file where the merged audio will be saved
     * @param inputFiles A variable number of input audio files to be merged
     */
    @JvmStatic
    fun mergeFiles(outputFile: File, vararg inputFiles: File) {
        assert(!isMainThread) {
            "Avoid calling this on the main thread"
        }
        if (inputFiles.isEmpty()) {
            throw RuntimeException("No input files provided to merge.")
        }
        val tempOutputFile = File.createTempFile("audio_", "m4a")
        var muxer: MediaMuxer? = null

        try {
            val firstAudioFormat = getAudioFormat(inputFiles.first())
            muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTrackIndex = muxer.addTrack(firstAudioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(firstAudioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            val bufferInfo = MediaCodec.BufferInfo()
            var accumulatedTimeUs = 0L

            inputFiles.forEachIndexed { index, inputFile ->
                val effectiveDuration = processFile(
                    muxer = muxer,
                    inputFile = inputFile,
                    buffer = buffer,
                    bufferInfo = bufferInfo,
                    outputTrackIndex = outputTrackIndex,
                    timeOffsetUs = accumulatedTimeUs,
                    isFirstFile = index == 0
                )
                accumulatedTimeUs += effectiveDuration
            }
            // Muxing successful
            muxer.stop()
            muxer.release()
            muxer = null
            if (!tempOutputFile.renameTo(outputFile)) {
                throw RuntimeException("Failed to rename temporary file to ${outputFile.name}")
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to merge audio files: ${e.message}", e)
        } finally {
            muxer?.stop()
            muxer?.release()
            tempOutputFile.delete()
        }
    }

    /**
     * Processes a single input file, writing its samples to the muxer
     */
    private fun processFile(
        muxer: MediaMuxer,
        inputFile: File,
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        outputTrackIndex: Int,
        timeOffsetUs: Long,
        isFirstFile: Boolean
    ): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(inputFile.path)
            val trackIndex = selectTrack(extractor)
            if (trackIndex == -1) {
                throw RuntimeException("No audio track found in file: ${inputFile.path}")
            }
            extractor.selectTrack(trackIndex)

            val trimUs = if (isFirstFile) 0L else TRIM_START_US
            extractor.seekTo(trimUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val startTimeUs = extractor.sampleTime
            var lastSampleTimeUs = startTimeUs

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTime = extractor.sampleTime
                if (sampleTime < startTimeUs) {
                    extractor.advance()
                    continue
                }

                bufferInfo.apply {
                    offset = 0
                    size = sampleSize
                    presentationTimeUs = timeOffsetUs + (sampleTime - startTimeUs)
                    flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                }
                muxer.writeSampleData(outputTrackIndex, buffer, bufferInfo)
                lastSampleTimeUs = sampleTime
                extractor.advance()
            }
            return (lastSampleTimeUs - startTimeUs).coerceAtLeast(0L)
        } catch (e: Exception) {
            throw RuntimeException("Error processing file ${inputFile.name}: ${e.message}", e)
        } finally {
            extractor.release()
        }
    }

    /**
     * Selects the first audio track and returns its MediaFormat
     * @return The MediaFormat of the audio track, or null if no audio track is found
     */
    private fun getAudioFormat(inputFile: File): MediaFormat {
        if (!inputFile.exists() || !inputFile.canRead() || inputFile.length() == 0L) {
            throw RuntimeException("Input file is invalid, inaccessible, or empty: ${inputFile.path}")
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(inputFile.path)
            val trackIndex = selectTrack(extractor)
            if (trackIndex == -1) {
                throw RuntimeException("No audio track found in file: ${inputFile.path}")
            }
            return extractor.getTrackFormat(trackIndex)
        } catch (e: Exception) {
            throw RuntimeException("Error getting audio format for file ${inputFile.name}: ${e.message}", e)
        } finally {
            extractor.release()
        }
    }

    /**
     * Selects the first audio track found in the given MediaExtractor
     * @return The index of the audio track, or -1 if no audio track is found
     */
    private fun selectTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }
}