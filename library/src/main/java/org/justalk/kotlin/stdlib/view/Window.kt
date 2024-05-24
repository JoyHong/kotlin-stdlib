package org.justalk.kotlin.stdlib.view

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// https://developer.android.google.cn/develop/ui/views/layout/edge-to-edge?hl=zh-cn
// https://developer.android.google.cn/develop/ui/views/layout/immersive?hl=zh-cn

private fun Window.getInsetsControllerCompat(): WindowInsetsControllerCompat {
    return WindowCompat.getInsetsController(this, decorView)
}

/** 显示系统栏(状态栏和导航栏) */
internal fun Window.showSystemBars() {
    getInsetsControllerCompat().show(WindowInsetsCompat.Type.systemBars())
}

/** 隐藏系统栏(状态栏和导航栏) */
internal fun Window.hideSystemBars() {
    getInsetsControllerCompat().hide(WindowInsetsCompat.Type.systemBars())
}

/** 显示状态栏 */
internal fun Window.showStatusBars() {
    getInsetsControllerCompat().show(WindowInsetsCompat.Type.statusBars())
}

/** 隐藏状态栏 */
internal fun Window.hideStatusBars() {
    getInsetsControllerCompat().hide(WindowInsetsCompat.Type.statusBars())
}

/** 显示导航栏 */
internal fun Window.showNavigationBars() {
    getInsetsControllerCompat().show(WindowInsetsCompat.Type.navigationBars())
}

/** 隐藏导航栏 */
internal fun Window.hideNavigationBars() {
    getInsetsControllerCompat().hide(WindowInsetsCompat.Type.navigationBars())
}

/** 显示软键盘 */
internal fun Window.showIme() {
    getInsetsControllerCompat().show(WindowInsetsCompat.Type.ime())
}

/** 隐藏软键盘 */
internal fun Window.hideIme() {
    getInsetsControllerCompat().hide(WindowInsetsCompat.Type.ime())
}

/** 设置状态栏内容是否是 light 模式 */
internal fun Window.setLightStatusBars(isLight: Boolean) {
    getInsetsControllerCompat().isAppearanceLightStatusBars = isLight
}

/** 设置导航栏内容是否是 light 模式 */
internal fun Window.setLightNavigationBars(isLight: Boolean) {
    getInsetsControllerCompat().isAppearanceLightNavigationBars = isLight
}

internal fun Window.compatCombo(): WindowCompatCombo {
    return WindowCompatCombo(this)
}

class WindowCompatCombo(window: Window) {

    private val insetsControllerCompat = window.getInsetsControllerCompat()

    fun showSystemBars(): WindowCompatCombo {
        insetsControllerCompat.show(WindowInsetsCompat.Type.systemBars())
        return this
    }

    fun hideSystemBars(): WindowCompatCombo {
        insetsControllerCompat.hide(WindowInsetsCompat.Type.systemBars())
        return this
    }

    fun showStatusBars(): WindowCompatCombo {
        insetsControllerCompat.show(WindowInsetsCompat.Type.statusBars())
        return this
    }

    fun hideStatusBars(): WindowCompatCombo {
        insetsControllerCompat.hide(WindowInsetsCompat.Type.statusBars())
        return this
    }

    fun showNavigationBars(): WindowCompatCombo {
        insetsControllerCompat.show(WindowInsetsCompat.Type.navigationBars())
        return this
    }

    fun hideNavigationBars(): WindowCompatCombo {
        insetsControllerCompat.hide(WindowInsetsCompat.Type.navigationBars())
        return this
    }

    fun showIme(): WindowCompatCombo {
        insetsControllerCompat.show(WindowInsetsCompat.Type.ime())
        return this
    }

    fun hideIme(): WindowCompatCombo {
        insetsControllerCompat.hide(WindowInsetsCompat.Type.ime())
        return this
    }

    fun setLightStatusBars(isLight: Boolean): WindowCompatCombo {
        insetsControllerCompat.isAppearanceLightStatusBars = isLight
        return this
    }

    fun setLightNavigationBars(isLight: Boolean): WindowCompatCombo {
        insetsControllerCompat.isAppearanceLightNavigationBars = isLight
        return this
    }

}