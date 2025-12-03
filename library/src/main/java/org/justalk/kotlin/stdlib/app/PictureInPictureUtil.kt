package org.justalk.kotlin.stdlib.app

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.net.toUri

/**
 * https://developer.android.com/develop/ui/views/picture-in-picture?hl=zh-cn
 */
object PictureInPictureUtil {

    /**
     * 检查设备硬件是否支持画中画 (部分低端设备或电视可能不支持)
     */
    @JvmStatic
    fun isSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    /**
     * 基于 isSupported 为 true 后, 再检查用户是否在设置中开启了画中画权限
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @JvmStatic
    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), context.packageName)
            } else {
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), context.packageName)
            }
            return mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            // 某些极端情况下可能会抛出 SecurityException
            return false
        }
    }

    /**
     * 当 isGranted 为 false 时, 跳转到画中画设置界面
     */
    @JvmStatic
    fun gotoSettings(context: Context) {
        val uri = "package:${context.packageName}".toUri()
        try {
            val intent = Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", uri)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            // 部分厂商可能不支持直接跳到该页面，可以跳到应用详情页作为 fallback
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = uri
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

}