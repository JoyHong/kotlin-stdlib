package org.justalk.kotlin.stdlib.time

/**
 * 将整数值转换为对应的毫秒数 (秒 → 毫秒)
 *
 * 示例: `5.seconds` 返回 `5000L`
 */
inline val Int.seconds: Long
    get() = this * 1000L

/**
 * 将整数值转换为对应的毫秒数 (分钟 → 毫秒)
 *
 * 示例: `2.minutes` 返回 `120000L`
 */
inline val Int.minutes: Long
    get() = this * 1000L * 60

/**
 * 将整数值转换为对应的毫秒数 (小时 → 毫秒)
 *
 * 示例: `1.hours` 返回 `3600000L`
 */
inline val Int.hours: Long
    get() = this * 1000L * 60 * 60

/**
 * 将整数值转换为对应的毫秒数 (天 → 毫秒)
 *
 * 示例: `1.days` 返回 `86400000L`
 */
inline val Int.days: Long
    get() = this * 1000L * 60 * 60 * 24