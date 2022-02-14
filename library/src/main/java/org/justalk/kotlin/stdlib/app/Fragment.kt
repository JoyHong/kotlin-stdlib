package org.justalk.kotlin.stdlib.app

import android.os.Parcelable
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import kotlin.properties.ReadOnlyProperty

/**
 * ViewDataBinding 代理
 */
@Suppress("unused")
inline fun <reified T : ViewDataBinding> Fragment.viewDataBindingDelegate(): ReadOnlyProperty<Fragment, T> {
    return FragmentViewDataBindingProperty()
}

/**
 * Argument 懒初始化代理
 */

@Suppress("unused")
fun Fragment.argumentBooleanDelegate(key: String, defaultValue: Boolean = false): ReadOnlyProperty<Fragment, Boolean> {
    return FragmentArgumentBooleanProperty(key, defaultValue)
}

@Suppress("unused")
fun Fragment.argumentIntDelegate(key: String, defaultValue: Int = 0): ReadOnlyProperty<Fragment, Int> {
    return FragmentArgumentIntProperty(key, defaultValue)
}

@Suppress("unused")
fun Fragment.argumentLongDelegate(key: String, defaultValue: Long = 0): ReadOnlyProperty<Fragment, Long> {
    return FragmentArgumentLongProperty(key, defaultValue)
}

@Suppress("unused")
fun Fragment.argumentStringDelegate(key: String, defaultValue: String = ""): ReadOnlyProperty<Fragment, String> {
    return FragmentArgumentStringProperty(key, defaultValue)
}

@Suppress("unused")
inline fun <reified T : Parcelable> Fragment.argumentParcelableDelegate(key: String): ReadOnlyProperty<Fragment, T> {
    return FragmentArgumentParcelableProperty(key)
}