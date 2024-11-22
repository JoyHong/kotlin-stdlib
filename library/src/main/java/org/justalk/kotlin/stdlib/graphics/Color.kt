package org.justalk.kotlin.stdlib.graphics

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red

/**
 * 返回透明度百分比是 alpha 的新的颜色值
 */
fun @receiver:ColorInt Int.withAlpha(alpha: Float) =
    Color.argb((alpha * 255).toInt(), red, green, blue)