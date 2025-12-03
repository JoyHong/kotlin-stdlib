package org.justalk.kotlin.stdlib.media.video

import android.util.Log
import androidx.annotation.WorkerThread
import org.justalk.kotlin.stdlib.Utils.isMainThread
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * 视频 fastStart 属性获取及转换
 *
 * 参考自 https://github.com/ypresto/qtfaststart-java/blob/master/src/main/java/net/ypresto/qtfaststart/QtFastStart.java
 * 基于FFmpeg的 qt-faststart.c 构建， https://github.com/FFmpeg/FFmpeg/blob/master/tools/qt-faststart.c
 */
object QtFastStart {

    private const val ATOM_PREAMBLE_SIZE = 8

    /**
     * 解析视频文件并移动 'moov' atom 到文件头部，以实现快速启动（ Fast Start ）
     * 使用前需通过 isFastStart 方法，先判断输入文件 fastStart 属性是否为 false
     *
     * @param inputFile 输入视频文件
     * @param outputFile 输出视频文件
     * @throws IOException 如果处理过程中发生 I/O 错误
     * @throws RuntimeException 如果输入文件已是快速启动格式，文件重命名失败，或快速启动转换失败
     */
    @JvmStatic
    @WorkerThread
    fun fastStart(inputFile: File, outputFile: File) {
        if (isFastStart(inputFile)) {
            throw RuntimeException("InputFile '${inputFile.name}' is already fast start")
        }
        assert(!isMainThread) {
            "Avoid calling this on the main thread"
        }
        var inStream: FileInputStream? = null
        var tempStream: FileOutputStream? = null
        val tempFile = File.createTempFile("faststart_", ".mp4")
        try {
            inStream = FileInputStream(inputFile)
            val infileChannel = inStream.channel
            tempStream = FileOutputStream(tempFile)
            val tempFileChannel = tempStream.channel
            if (!fastStartImpl(infileChannel, tempFileChannel)) {
                throw RuntimeException("Fast start operation failed for ${inputFile.name}")
            }
            if (!tempFile.renameTo(outputFile )) {
                throw RuntimeException("Failed to rename temporary file to ${outputFile.name}")
            }
        } finally {
            safeClose(inStream)
            safeClose(tempStream)
            tempFile.delete()
        }
    }

    private fun fastStartImpl(infile: FileChannel, outfile: FileChannel): Boolean {
        val atomBytes = ByteBuffer.allocate(ATOM_PREAMBLE_SIZE).order(ByteOrder.BIG_ENDIAN)
        var atomType = 0
        var atomSize: Long = 0 // uint64_t
        val lastOffset: Long
        val moovAtom: ByteBuffer
        var ftypAtom: ByteBuffer? = null
        var startOffset: Long = 0

        // traverse through the atoms in the file to make sure that 'moov' is at the end
        while (readAndFill(infile, atomBytes)) {
            atomSize = uint32ToLong(atomBytes.getInt()) // uint32
            atomType = atomBytes.getInt() // representing uint32_t in signed int

            // keep ftyp atom
            if (atomType == FTYP_ATOM) {
                val ftypAtomSize = uint32ToInt(atomSize) // XXX: assume in range of int32_t
                ftypAtom = ByteBuffer.allocate(ftypAtomSize).order(ByteOrder.BIG_ENDIAN)
                atomBytes.rewind()
                ftypAtom.put(atomBytes)
                if (infile.read(ftypAtom) < ftypAtomSize - ATOM_PREAMBLE_SIZE) break
                ftypAtom.flip()
                startOffset = infile.position() // after ftyp atom
            } else {
                if (atomSize == 1L) {
                    /* 64-bit special case */
                    atomBytes.clear()
                    if (!readAndFill(infile, atomBytes)) break
                    atomSize = uint64ToLong(atomBytes.getLong()) // XXX: assume in range of int64_t
                    infile.position(infile.position() + atomSize - ATOM_PREAMBLE_SIZE * 2) // seek
                } else {
                    infile.position(infile.position() + atomSize - ATOM_PREAMBLE_SIZE) // seek
                }
            }
            if ((atomType != FREE_ATOM)
                && (atomType != JUNK_ATOM)
                && (atomType != MDAT_ATOM)
                && (atomType != MOOV_ATOM)
                && (atomType != PNOT_ATOM)
                && (atomType != SKIP_ATOM)
                && (atomType != WIDE_ATOM)
                && (atomType != PICT_ATOM)
                && (atomType != UUID_ATOM)
                && (atomType != FTYP_ATOM)
            ) {
                Log.e("fastStartI","encountered non-QT top-level atom (is this a QuickTime file?)")
                break
            }

            /* The atom header is 8 (or 16 bytes), if the atom size (which
         * includes these 8 or 16 bytes) is less than that, we won't be
         * able to continue scanning sensibly after this atom, so break. */
            if (atomSize < 8) break
        }

        if (atomType != MOOV_ATOM) {
            Log.e("fastStartI","last atom in file was not a moov atom")
            return false
        }

        // moov atom was, in fact, the last atom in the chunk; load the whole moov atom

        // atomSize is uint64, but for moov uint32 should be stored.
        // XXX: assuming moov atomSize <= max vaue of int32
        // uint64_t, but assuming it is in int32 range. It is reasonable as int max is around 2GB. Such large moov is unlikely, yet unallocatable :).
        val moovAtomSize = uint32ToInt(atomSize)
        lastOffset =
            infile.size() - moovAtomSize // NOTE: assuming no extra data after moov, as qt-faststart.c
        moovAtom = ByteBuffer.allocate(moovAtomSize).order(ByteOrder.BIG_ENDIAN)
        if (!readAndFill(infile, moovAtom, lastOffset)) {
            throw RuntimeException("failed to read moov atom")
        }

        // this utility does not support compressed atoms yet, so disqualify files with compressed QT atoms
        if (moovAtom.getInt(12) == CMOV_ATOM) {
            throw RuntimeException("this utility does not support compressed moov atoms yet")
        }

        // crawl through the moov chunk in search of stco or co64 atoms
        while (moovAtom.remaining() >= 8) {
            val atomHead = moovAtom.position()
            atomType = moovAtom.getInt(atomHead + 4) // representing uint32_t in signed int
            if (!(atomType == STCO_ATOM || atomType == CO64_ATOM)) {
                moovAtom.position(moovAtom.position() + 1)
                continue
            }
            atomSize = uint32ToLong(moovAtom.getInt(atomHead)) // uint32
            if (atomSize > moovAtom.remaining()) {
                throw RuntimeException("bad atom size")
            }
            moovAtom.position(atomHead + 12) // skip size (4 bytes), type (4 bytes), version (1 byte) and flags (3 bytes)
            if (moovAtom.remaining() < 4) {
                throw RuntimeException("malformed atom")
            }
            // uint32_t, but assuming moovAtomSize is in int32 range, so this will be in int32 range
            val offsetCount = uint32ToInt(moovAtom.getInt())
            if (atomType == STCO_ATOM) {
                if (moovAtom.remaining() < offsetCount * 4) {
                    throw RuntimeException("bad atom size/element count")
                }
                for (i in 0 until offsetCount) {
                    val currentOffset = moovAtom.getInt(moovAtom.position())
                    val newOffset =
                        currentOffset + moovAtomSize // calculate uint32 in int, bitwise addition
                    // current 0xffffffff => new 0x00000000 (actual >= 0x0000000100000000L)
                    if (currentOffset < 0 && newOffset >= 0) {
                        throw RuntimeException(
                            "This is bug in original qt-faststart.c: "
                                    + "stco atom should be extended to co64 atom as new offset value overflows uint32, "
                                    + "but is not implemented."
                        )
                    }
                    moovAtom.putInt(newOffset)
                }
            } else if (atomType == CO64_ATOM) {
                if (moovAtom.remaining() < offsetCount * 8) {
                    throw RuntimeException("bad atom size/element count")
                }
                for (i in 0 until offsetCount) {
                    val currentOffset = moovAtom.getLong(moovAtom.position())
                    moovAtom.putLong(currentOffset + moovAtomSize) // calculate uint64 in long, bitwise addition
                }
            }
        }

        infile.position(startOffset) // seek after ftyp atom

        if (ftypAtom != null) {
            // dump the same ftyp atom
            ftypAtom.rewind()
            outfile.write(ftypAtom)
        }

        // dump the new moov atom
        moovAtom.rewind()
        outfile.write(moovAtom)

        // copy the remainder of the infile, from offset 0 -> (lastOffset - startOffset) - 1
        infile.transferTo(startOffset, lastOffset - startOffset, outfile)
        return true
    }

    /**
     * 判断视频文件是否是“ Fast Start ”（快速启动）
     *
     * @param inputFile 输入视频文件
     * @return 视频文件 fastStart 属性
     */
    @JvmStatic
    fun isFastStart(inputFile: File): Boolean {
        var inStream: FileInputStream? = null
        try {
            inStream = FileInputStream(inputFile)
            val infile = inStream.channel
            val atomBytes = ByteBuffer.allocate(ATOM_PREAMBLE_SIZE).order(ByteOrder.BIG_ENDIAN)

            // Read the first atom
            if (!readAndFill(infile, atomBytes)) {
                // File is empty or too short to contain even a single atom header
                Log.e("isFastStart","File ${inputFile.name} is empty or too short to contain atoms.")
                return false // Cannot be fast start if no atoms
            }

            infile.position(0)

            var moovOffset: Long = -1
            var mdatOffset: Long = -1
            var currentOffset: Long = 0

            // Loop to find the positions of 'moov' and 'mdat' atoms
            while (readAndFill(infile, atomBytes)) {
                var atomSize = uint32ToLong(atomBytes.getInt())
                val atomType = atomBytes.getInt()

                if (atomSize == 1L) { // 64-bit size special case
                    atomBytes.clear()
                    if (!readAndFill(infile, atomBytes)) {
                        Log.e("isFastStart", "Incomplete 64-bit atom size encountered at offset $currentOffset.")
                        break // Malformed or truncated file
                    }
                    atomSize = uint64ToLong(atomBytes.getLong())
                    // Adjust infile position for the next read, skipping the rest of the 64-bit atom's content
                    // current position is already after the 8 bytes of initial size and 8 bytes of 64-bit size
                    infile.position(infile.position() + atomSize - (ATOM_PREAMBLE_SIZE * 2))
                } else {
                    // Adjust infile position for the next read, skipping the rest of the 32-bit atom's content
                    infile.position(infile.position() + atomSize - ATOM_PREAMBLE_SIZE)
                }

                if (atomType == MOOV_ATOM) {
                    moovOffset = currentOffset
                } else if (atomType == MDAT_ATOM) {
                    mdatOffset = currentOffset
                }

                // If both moov and mdat are found, we have enough information
                if (moovOffset != -1L && mdatOffset != -1L) {
                    break
                }

                // Update currentOffset for the next iteration (start of next atom)
                currentOffset = infile.position()

                // Break if we've read past the end of the file or encountered an invalid atom size
                if (atomSize <= 0 || currentOffset >= infile.size()) {
                    Log.e("isFastStart","Invalid atom size or end of file reached during atom scan.")
                    break
                }
            }

            val result = moovOffset != -1L && (mdatOffset == -1L || moovOffset < mdatOffset)
            return result
        } finally {
            safeClose(inStream)
        }
    }

    private fun safeClose(closeable: Closeable?) {
        if (closeable != null) {
            try {
                closeable.close()
            } catch (e: IOException) {
                throw RuntimeException("Failed to close file: ")
            }
        }
    }

    private fun uint32ToLong(int32: Int): Long {
        return int32.toLong() and 0x00000000ffffffffL
    }

    /**
     * Ensures passed uint32 value in long can be represented as Java int.
     */
    private fun uint32ToInt(uint32: Int): Int {
        if (uint32 < 0) {
            throw RuntimeException("uint32 value is too large")
        }
        return uint32
    }

    /**
     * Ensures passed uint32 value in long can be represented as Java int.
     */
    private fun uint32ToInt(uint32: Long): Int {
        if (uint32 > Int.MAX_VALUE || uint32 < 0) {
            throw RuntimeException("uint32 value is too large")
        }
        return uint32.toInt()
    }

    /**
     * Ensures passed uint64 value can be represented as Java long.
     */
    private fun uint64ToLong(uint64: Long): Long {
        if (uint64 < 0) throw RuntimeException("uint64 value is too large")
        return uint64
    }

    private fun fourCcToInt(byteArray: ByteArray): Int {
        return ByteBuffer.wrap(byteArray).order(ByteOrder.BIG_ENDIAN).getInt()
    }

    private fun readAndFill(infile: FileChannel, buffer: ByteBuffer): Boolean {
        buffer.clear()
        val size = infile.read(buffer)
        buffer.flip()
        return size == buffer.capacity()
    }

    private fun readAndFill(infile: FileChannel, buffer: ByteBuffer, position: Long): Boolean {
        buffer.clear()
        val size = infile.read(buffer, position)
        buffer.flip()
        return size == buffer.capacity()
    }

    /* top level atoms */
    private val FREE_ATOM = fourCcToInt(
        byteArrayOf(
            'f'.code.toByte(),
            'r'.code.toByte(),
            'e'.code.toByte(),
            'e'.code.toByte()
        )
    )
    private val JUNK_ATOM = fourCcToInt(
        byteArrayOf(
            'j'.code.toByte(),
            'u'.code.toByte(),
            'n'.code.toByte(),
            'k'.code.toByte()
        )
    )
    private val MDAT_ATOM = fourCcToInt(
        byteArrayOf(
            'm'.code.toByte(),
            'd'.code.toByte(),
            'a'.code.toByte(),
            't'.code.toByte()
        )
    )
    private val MOOV_ATOM = fourCcToInt(
        byteArrayOf(
            'm'.code.toByte(),
            'o'.code.toByte(),
            'o'.code.toByte(),
            'v'.code.toByte()
        )
    )
    private val PNOT_ATOM = fourCcToInt(
        byteArrayOf(
            'p'.code.toByte(),
            'n'.code.toByte(),
            'o'.code.toByte(),
            't'.code.toByte()
        )
    )
    private val SKIP_ATOM = fourCcToInt(
        byteArrayOf(
            's'.code.toByte(),
            'k'.code.toByte(),
            'i'.code.toByte(),
            'p'.code.toByte()
        )
    )
    private val WIDE_ATOM = fourCcToInt(
        byteArrayOf(
            'w'.code.toByte(),
            'i'.code.toByte(),
            'd'.code.toByte(),
            'e'.code.toByte()
        )
    )
    private val PICT_ATOM = fourCcToInt(
        byteArrayOf(
            'P'.code.toByte(),
            'I'.code.toByte(),
            'C'.code.toByte(),
            'T'.code.toByte()
        )
    )
    private val FTYP_ATOM = fourCcToInt(
        byteArrayOf(
            'f'.code.toByte(),
            't'.code.toByte(),
            'y'.code.toByte(),
            'p'.code.toByte()
        )
    )
    private val UUID_ATOM = fourCcToInt(
        byteArrayOf(
            'u'.code.toByte(),
            'u'.code.toByte(),
            'i'.code.toByte(),
            'd'.code.toByte()
        )
    )

    private val CMOV_ATOM = fourCcToInt(
        byteArrayOf(
            'c'.code.toByte(),
            'm'.code.toByte(),
            'o'.code.toByte(),
            'v'.code.toByte()
        )
    )
    private val STCO_ATOM = fourCcToInt(
        byteArrayOf(
            's'.code.toByte(),
            't'.code.toByte(),
            'c'.code.toByte(),
            'o'.code.toByte()
        )
    )
    private val CO64_ATOM = fourCcToInt(
        byteArrayOf(
            'c'.code.toByte(),
            'o'.code.toByte(),
            '6'.code.toByte(),
            '4'.code.toByte()
        )
    )

}