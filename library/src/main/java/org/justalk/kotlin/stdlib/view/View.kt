package org.justalk.kotlin.stdlib.view

import android.view.View
import android.view.View.OnAttachStateChangeListener
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
        val typeInsets = insets.getInsets(sInsetTypes)
        val left = if (insetsLeft) typeInsets.left else 0
        val top = if (insetsTop) typeInsets.top else 0
        val right = if (insetsRight) typeInsets.right else 0
        val bottom = if (insetsBottom) typeInsets.bottom else 0
        if (updatePadding) {
            v.updatePadding(left, top, right, bottom)
        } else {
            v.updateLayoutParams<MarginLayoutParams> {
                setMargins(left, top, right, bottom)
            }
        }
        insets
        // 表示视图已经消耗了指定的窗口内边距, 当视图消耗了窗口内边距后, 它将不会再将该内边距传递给其子视图
//        WindowInsetsCompat.CONSUMED
    }
    requestApplyInsetsWhenAttached(this)
}

fun View.applyWindowInsetsListener(listener: (v: View, insets: Insets) -> Unit?) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val typeInsets = insets.getInsets(sInsetTypes)
        listener.invoke(v, typeInsets)
        insets
    }
    requestApplyInsetsWhenAttached(this)
}

private fun requestApplyInsetsWhenAttached(view: View) {
    if (ViewCompat.isAttachedToWindow(view)) {
        ViewCompat.requestApplyInsets(view)
    } else {
        view.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(v)
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
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

/**
 * 是否是从右往左的布局
 */
fun View.isLayoutRtl(): Boolean {
    return ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL
}