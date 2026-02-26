package org.justalk.kotlin.stdlib.io

import java.util.Locale

/**
 * 将整数值转换为对应的字节数 (KB)
 *
 * 示例: `5.kb` 返回 `5120L`
 */
inline val Int.kb: Long
    get() = this * 1024L

/**
 * 将整数值转换为对应的字节数 (MB)
 *
 * 示例: `2.mb` 返回 `2097152L`
 */
inline val Int.mb: Long
    get() = this * 1024 * 1024L

/**
 * 将整数值转换为对应的字节数 (GB)
 *
 * 示例: `1.gb` 返回 `1073741824L`
 */
inline val Int.gb: Long
    get() = this * 1024 * 1024 * 1024L

/**
 * 将浮点数值转换为对应的字节数 (KB)
 *
 * 示例: `1.5.kb` 返回 `1536L`
 */
inline val Double.kb: Long
    get() = (this * 1024L).toLong()

/**
 * 将浮点数值转换为对应的字节数 (MB)
 *
 * 示例: `1.5.mb` 返回 `1572864L`
 */
inline val Double.mb: Long
    get() = (this * 1024 * 1024L).toLong()

/**
 * 将浮点数值转换为对应的字节数 (GB)
 *
 * 示例: `0.5.gb` 返回 `536870912L`
 */
inline val Double.gb: Long
    get() = (this * 1024 * 1024 * 1024L).toLong()

/**
 * 将字节数格式化为人类可读的文件大小字符串
 *
 * 会自动选择合适的单位 (B / KB / MB / GB), 保留一位小数。
 *
 * 示例:
 * - `1024L.formatSize()` 返回 `"1.0 KB"`
 * - `1572864L.formatSize()` 返回 `"1.5 MB"`
 */
fun Long.formatSize(): String {
    val bytes = this.toDouble()
    return when {
        bytes >= 1.gb -> String.format(Locale.US, "%.1f GB", bytes / 1.gb)
        bytes >= 1.mb -> String.format(Locale.US, "%.1f MB", bytes / 1.mb)
        bytes >= 1.kb -> String.format(Locale.US, "%.1f KB", bytes / 1.kb)
        else -> String.format(Locale.US, "%.0f B", bytes)
    }
}