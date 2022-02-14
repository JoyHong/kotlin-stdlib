package org.justalk.kotlin.stdlib.app

import android.os.Parcelable
import androidx.annotation.RestrictTo
import androidx.fragment.app.Fragment
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

@RestrictTo(RestrictTo.Scope.LIBRARY)
class FragmentArgumentBooleanProperty(
    private val key: String,
    private val defaultValue: Boolean = false
) : ReadOnlyProperty<Fragment, Boolean> {

    private var value: Boolean? = null

    override fun getValue(thisRef: Fragment, property: KProperty<*>): Boolean {
        return value ?: thisRef.requireArguments().getBoolean(key, defaultValue)
            .apply {
                value = this
            }
    }

}

@RestrictTo(RestrictTo.Scope.LIBRARY)
class FragmentArgumentIntProperty(
    private val key: String,
    private val defaultValue: Int = 0
) : ReadOnlyProperty<Fragment, Int> {

    private var value: Int? = null

    override fun getValue(thisRef: Fragment, property: KProperty<*>): Int {
        return value ?: thisRef.requireArguments().getInt(key, defaultValue)
            .apply {
                value = this
            }
    }

}

@RestrictTo(RestrictTo.Scope.LIBRARY)
class FragmentArgumentLongProperty(
    private val key: String,
    private val defaultValue: Long = 0
) : ReadOnlyProperty<Fragment, Long> {

    private var value: Long? = null

    override fun getValue(thisRef: Fragment, property: KProperty<*>): Long {
        return value ?: thisRef.requireArguments().getLong(key, defaultValue)
            .apply {
                value = this
            }
    }

}

@RestrictTo(RestrictTo.Scope.LIBRARY)
class FragmentArgumentStringProperty(
    private val key: String,
    private val defaultValue: String = ""
) : ReadOnlyProperty<Fragment, String> {

    private var value: String? = null

    override fun getValue(thisRef: Fragment, property: KProperty<*>): String {
        return value ?: thisRef.requireArguments().getString(key, defaultValue)
            .apply {
                value = this
            }
    }

}

@RestrictTo(RestrictTo.Scope.LIBRARY)
class FragmentArgumentParcelableProperty<T : Parcelable>(
    private val key: String
) : ReadOnlyProperty<Fragment, T> {

    private var value: T? = null

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        return value ?: thisRef.requireArguments().getParcelable<T>(key)!!
            .apply {
                value = this
            }
    }

}