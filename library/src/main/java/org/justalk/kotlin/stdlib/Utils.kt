package org.justalk.kotlin.stdlib

import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object Utils {

    /**
     * 是否在主线程
     */
    @JvmStatic
    val isMainThread: Boolean
        get() = Looper.getMainLooper().thread === Thread.currentThread()

    /**
     * 获取当前时区字符串, eg. UTC+8
     */
    @JvmStatic
    val timeZone: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getTimeZoneOffsetString()
        } else {
            getTimeZoneOffsetStringLegacy()
        }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getTimeZoneOffsetString(): String {
        // 1. 获取当前系统默认时区ID
        val zoneId = ZoneId.systemDefault()
        // 2. 获取当前时刻的偏移量
        val offset = zoneId.rules.getOffset(Instant.now())
        // 3. 获取总的偏移秒数
        val totalSeconds = offset.totalSeconds
        // 4. 将秒数转换为小时和分钟
        val hours = TimeUnit.SECONDS.toHours(totalSeconds.toLong())
        val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds.toLong()) % 60
        // 5. 格式化字符串
        return if (minutes == 0L) {
            // 如果是整点小时，格式化为 "UTC+8"
            String.format(Locale.US, "UTC%+d", hours)
        } else {
            // 如果包含分钟，格式化为 "UTC+05:30"
            String.format(Locale.US, "UTC%+03d:%02d", hours, abs(minutes))
        }
    }

    private fun getTimeZoneOffsetStringLegacy(): String {
        // 1. 获取默认的 TimeZone 实例
        val timeZone = TimeZone.getDefault()
        // 2. 获取当前时间的毫秒偏移量（包含夏令时）
        // 如果你不想考虑夏令时，可以使用 timeZone.rawOffset
        val offsetInMillis = timeZone.getOffset(System.currentTimeMillis()).toLong()
        // 3. 将毫秒转换为小时和分钟
        val hours = TimeUnit.MILLISECONDS.toHours(offsetInMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(offsetInMillis) % 60
        // 4. 格式化字符串
        return if (minutes == 0L) {
            // 如果是整点小时，格式化为 "UTC+8"
            String.format(Locale.US, "UTC%+d", hours)
        } else {
            // 如果包含分钟，格式化为 "UTC+05:30"
            String.format(Locale.US, "UTC%+03d:%02d", hours, abs(minutes))
        }
    }

}