package org.justalk.kotlin.stdlib.graphics

import android.content.res.Resources
import android.util.TypedValue

/**
 * 扩展属性：将 dp 转为 px (Int)
 * 用法: val width = 10.dp
 */
val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()

/**
 * 扩展属性：将 px 转为 dp (Int)
 * 用法: val dpValue = 100.pxToDp
 */
val Int.pxToDp: Int
    get() = (this / Resources.getSystem().displayMetrics.density + 0.5f).toInt()

/**
 * 扩展属性：将 dp 转为 px (Float)
 * 适用于 Canvas 绘制或动画精细控制
 * 用法: val strokeWidth = 1.5f.dp
 */
val Float.dp: Float
    get() = this * Resources.getSystem().displayMetrics.density

/**
 * 扩展属性：将 px 转为 dp (Float)
 * 用法: val dpValue = 100f.pxToDp
 */
val Float.pxToDp: Float
    get() = this / Resources.getSystem().displayMetrics.density

/**
 * 扩展属性
 */
val Int.sp: Int
    get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this.toFloat(), Resources.getSystem().displayMetrics).toInt()