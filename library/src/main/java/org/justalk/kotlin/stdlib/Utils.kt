package org.justalk.kotlin.stdlib

import android.os.Looper

object Utils {

    /**
     * 是否在主线程
     */
    @JvmStatic
    val isMainThread: Boolean
        get() = Looper.getMainLooper().thread === Thread.currentThread()

}