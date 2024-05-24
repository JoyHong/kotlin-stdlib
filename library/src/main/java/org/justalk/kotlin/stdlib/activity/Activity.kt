package org.justalk.kotlin.stdlib.activity

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import org.justalk.kotlin.stdlib.view.WindowCompatCombo
import org.justalk.kotlin.stdlib.view.hideIme
import org.justalk.kotlin.stdlib.view.hideNavigationBars
import org.justalk.kotlin.stdlib.view.hideStatusBars
import org.justalk.kotlin.stdlib.view.hideSystemBars
import org.justalk.kotlin.stdlib.view.setLightNavigationBars
import org.justalk.kotlin.stdlib.view.setLightStatusBars
import org.justalk.kotlin.stdlib.view.showIme
import org.justalk.kotlin.stdlib.view.showNavigationBars
import org.justalk.kotlin.stdlib.view.showStatusBars
import org.justalk.kotlin.stdlib.view.showSystemBars

/** 设置沉浸式模式, 即内容延伸到系统栏下方 */
// 对于刘海屏的处理, 可在主题里设置以下属性
// <style name="ActivityTheme">
//  <item name="android:windowLayoutInDisplayCutoutMode">
//    shortEdges <!-- default, shortEdges, or never -->
//  </item>
//</style>
fun ComponentActivity.setImmersiveMode() {
    enableEdgeToEdge()
}

/** 显示系统栏(状态栏和导航栏) */
fun Activity.showSystemBars() {
    window.showSystemBars()
}

/** 隐藏系统栏(状态栏和导航栏) */
fun Activity.hideSystemBars() {
    window.hideSystemBars()
}

/** 显示状态栏 */
fun Activity.showStatusBars() {
    window.showStatusBars()
}

/** 隐藏状态栏 */
fun Activity.hideStatusBars() {
    window.hideStatusBars()
}

/** 显示导航栏 */
fun Activity.showNavigationBars() {
    window.showNavigationBars()
}

/** 隐藏导航栏 */
fun Activity.hideNavigationBars() {
    window.hideNavigationBars()
}

/** 显示软键盘 */
fun Activity.showIme() {
    window.showIme()
}

/** 隐藏软键盘 */
fun Activity.hideIme() {
    window.hideIme()
}

/** 设置状态栏内容是否是 light 模式 */
fun Activity.setLightStatusBars(isLight: Boolean) {
    window.setLightStatusBars(isLight)
}

/** 设置导航栏内容是否是 light 模式 */
fun Activity.setLightNavigationBars(isLight: Boolean) {
    window.setLightNavigationBars(isLight)
}

fun Activity.compatCombo(): WindowCompatCombo {
    return WindowCompatCombo(window)
}