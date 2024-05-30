package org.justalk.kotlin.stdlib.view

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import org.justalk.kotlin.stdlib.context.getActivity

private val sInsetTypes =
    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

@JvmOverloads
fun View.applyWindowInsets(
    insetsLeft: Boolean = true,
    insetsTop: Boolean = true,
    insetsRight: Boolean = true,
    insetsBottom: Boolean = true,
    updatePadding: Boolean = true
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val insetsSystemBars = insets.getInsets(sInsetTypes)
        val left = if (insetsLeft) insetsSystemBars.left else 0
        val top = if (insetsTop) insetsSystemBars.top else 0
        val right = if (insetsRight) insetsSystemBars.right else 0
        val bottom = if (insetsBottom) insetsSystemBars.bottom else 0
        if (updatePadding) {
            v.updatePadding(left, top, right, bottom)
        } else {
            v.updateLayoutParams<MarginLayoutParams> {
                setMargins(left, top, right, bottom)
            }
        }
        insets
        // 如果返回 CONSUMED, 即拦截了, 不会再传递下去给子控件
//        WindowInsetsCompat.CONSUMED
    }
}

@JvmOverloads
fun View.getWindowInsets(insetTypes: Int = sInsetTypes): Insets {
//    if (!ViewCompat.isLaidOut(this) || isLayoutRequested) {
//        throw RuntimeException("getWindowInsets must call after view did onLayout")
//    }
    return (context.getActivity()?.window?.decorView ?: this)
        .rootWindowInsets?.let {
            WindowInsetsCompat.toWindowInsetsCompat(it)
                .getInsets(insetTypes)
        } ?: Insets.NONE
}