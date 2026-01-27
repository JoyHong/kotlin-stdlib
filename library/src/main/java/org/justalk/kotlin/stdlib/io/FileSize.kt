package org.justalk.kotlin.stdlib.io

import java.util.Locale

// 1 KB (Kilobyte)
inline val Int.kb: Long
    get() = this * 1024L

// 1 MB (Megabyte)
inline val Int.mb: Long
    get() = this * 1024 * 1024L

// 1 GB (Gigabyte)
inline val Int.gb: Long
    get() = this * 1024 * 1024 * 1024L

// 1.0 KB (Kilobyte)
inline val Double.kb: Long
    get() = (this * 1024L).toLong()

// 1.0 MB (Megabyte)
inline val Double.mb: Long
    get() = (this * 1024 * 1024L).toLong()

// 1.0 GB (Gigabyte)
inline val Double.gb: Long
    get() = (this * 1024 * 1024 * 1024L).toLong()

fun Long.formatSize(): String {
    val bytes = this.toDouble()
    return when {
        bytes >= 1.gb -> String.format(Locale.US, "%.1f GB", bytes / 1.gb)
        bytes >= 1.mb -> String.format(Locale.US, "%.1f MB", bytes / 1.mb)
        bytes >= 1.kb -> String.format(Locale.US, "%.1f KB", bytes / 1.kb)
        else -> String.format(Locale.US, "%.0f B", bytes)
    }
}