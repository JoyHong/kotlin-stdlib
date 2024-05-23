package org.justalk.kotlin.stdlib.view

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import org.justalk.kotlin.stdlib.context.getActivity

private val sInsetTypes =
    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

@JvmOverloads
fun View.applyWindowInsets(
    insetsLeft: Boolean = false,
    insetsTop: Boolean = false,
    insetsRight: Boolean = false,
    insetsBottom: Boolean = false
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val insetsSystemBars = insets.getInsets(sInsetTypes)
        val left = if (insetsLeft) insetsSystemBars.left else 0
        val top = if (insetsTop) insetsSystemBars.top else 0
        val right = if (insetsRight) insetsSystemBars.right else 0
        val bottom = if (insetsBottom) insetsSystemBars.bottom else 0
        v.updatePadding(left, top, right, bottom)
        WindowInsetsCompat.CONSUMED
    }
}

@JvmOverloads
fun View.getWindowInsets(insetTypes: Int = sInsetTypes): Insets {
    if (!ViewCompat.isLaidOut(this) || isLayoutRequested) {
        throw RuntimeException("getWindowInsets must call after view did onLayout")
    }
    return (context.getActivity()?.window?.decorView ?: this)
        .rootWindowInsets?.let {
            WindowInsetsCompat.toWindowInsetsCompat(it)
                .getInsets(insetTypes)
        } ?: Insets.NONE
}