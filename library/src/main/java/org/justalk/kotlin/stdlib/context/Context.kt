package org.justalk.kotlin.stdlib.context

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration

/**
 * 返回上下文对应的 Activity, 如果有的话
 */
fun Context.getActivity(): Activity? {
    if (this is Activity) {
        return this
    }
    return if (this is ContextWrapper) {
        baseContext.getActivity()
    } else {
        null
    }
}

/**
 * 检测设备是否有物理键盘
 * ChromeBook、外接键盘等场景会返回 true
 */
fun Context.hasHardwareKeyboard(): Boolean {
    return resources.configuration.keyboard == Configuration.KEYBOARD_QWERTY
}

/**
 * 打开系统的 "下载管理" 文件夹下
 */
@Throws(ActivityNotFoundException::class)
fun Context.openSystemDownloads() {
    val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // 某些 ROM (如小米/华为旧版) 需要清除任务栈顶才能正确显示，否则可能白屏或无反应
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    startActivity(intent)
}