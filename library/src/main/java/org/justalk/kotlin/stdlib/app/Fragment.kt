package org.justalk.kotlin.stdlib.app

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