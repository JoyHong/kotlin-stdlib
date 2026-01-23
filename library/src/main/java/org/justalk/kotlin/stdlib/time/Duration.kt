package org.justalk.kotlin.stdlib.time

inline val Int.seconds: Long
    get() = this * 1000L

inline val Int.minutes: Long
    get() = this * 1000L * 60

inline val Int.hours: Long
    get() = this * 1000L * 60 * 60

inline val Int.days: Long
    get() = this * 1000L * 60 * 60 * 24