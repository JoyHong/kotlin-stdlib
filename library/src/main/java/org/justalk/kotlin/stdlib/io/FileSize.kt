package org.justalk.kotlin.stdlib.io

// 1 KB (Kilobyte)
inline val Int.kb: Long
    get() = this * 1024L

// 1 MB (Megabyte)
inline val Int.mb: Long
    get() = this * 1024 * 1024L

// 1 GB (Gigabyte)
inline val Int.gb: Long
    get() = this * 1024 * 1024 * 1024L