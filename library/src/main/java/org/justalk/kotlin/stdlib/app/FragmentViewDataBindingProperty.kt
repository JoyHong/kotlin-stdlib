package org.justalk.kotlin.stdlib.app

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

@RestrictTo(RestrictTo.Scope.LIBRARY)
class FragmentViewDataBindingProperty<T : ViewDataBinding> : ReadOnlyProperty<Fragment, T> {

    private var viewBinding: T? = null
    private val lifecycleObserver = BindingLifecycleObserver()

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        return viewBinding ?: thisRef.viewLifecycleOwner.lifecycle.addObserver(lifecycleObserver).run {
            thisRef.requireView().let {
                (DataBindingUtil.bind<T>(it)!!)
            }.apply {
                viewBinding = this
            }
        }
    }

    private inner class BindingLifecycleObserver : DefaultLifecycleObserver {

        private val mainHandler = Handler(Looper.getMainLooper())

        @MainThread
        override fun onDestroy(owner: LifecycleOwner) {
            owner.lifecycle.removeObserver(this)
            // Fragment.viewLifecycleOwner call LifecycleObserver.onDestroy() before Fragment.onDestroyView().
            // That's why we need to postpone reset of the viewBinding
            mainHandler.post {
                viewBinding = null
            }
        }
    }

}