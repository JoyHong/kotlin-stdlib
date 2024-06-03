package org.justalk.kotlin.stdlib.context

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.TypedValue
import androidx.annotation.Dimension

/**
 * 返回上下文对应的 Activity, 如果有的话
 */
fun Context.getActivity(): Activity? {
    if (this is Activity) {
        return this
    }
    return if (this is ContextWrapper) {
        baseContext.getActivity()
    } else {
        null
    }
}

//fun Context.dp2px(@Dimension(unit = 0) dp: Int): Float {
//    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics)
//}