package com.llzx373.foldcanvas.wallpaper

import android.app.AppOpsManager
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.llzx373.foldcanvas.data.DeviceBrand

/** 动态壁纸激活状态检查与系统选择页跳转（含 MIUI 权限与回退路径）。 */
object WallpaperActivation {

    /** MIUI 的「壁纸」AppOps 权限（被禁止时设置壁纸静默失败）。 */
    private const val OP_SET_WALLPAPER = "android:set_wallpaper"

    /** 当前桌面动态壁纸是否为本服务。 */
    fun isActive(context: Context): Boolean {
        val info = runCatching {
            WallpaperManager.getInstance(context).wallpaperInfo
        }.getOrNull()
        return info != null && info.packageName == context.packageName
    }

    /** 小米/MIUI 的「壁纸」权限是否已授予（非小米设备恒为 true）。 */
    fun wallpaperPermissionGranted(context: Context): Boolean {
        if (!DeviceBrand.isXiaomi) return true
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return true
        val mode = runCatching {
            ops.checkOp(OP_SET_WALLPAPER, Process.myUid(), context.packageName)
        }.getOrNull() ?: return true
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** MIUI 应用权限编辑器（可直接开启「壁纸」权限），回退到应用详情页。 */
    fun miuiPermissionEditorIntent(context: Context): Intent =
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .putExtra("extra_pkgname", context.packageName)

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))

    /** 打开 MIUI 权限编辑器，失败回退应用详情页。 */
    fun launchMiuiPermissionEditor(context: Context): Boolean {
        for (intent in listOf(miuiPermissionEditorIntent(context), appDetailsIntent(context))) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
            } catch (e: SecurityException) {
            }
        }
        return false
    }

    /** 系统"更换动态壁纸"预览页（可指定组件）。 */
    fun changeIntent(context: Context): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(context, FoldWallpaperService::class.java),
        )

    /** 系统动态壁纸列表页（无组件指定的回退）。 */
    fun chooserIntent(): Intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)

    /** 依次尝试可用的跳转 intent，全部不可用返回 false。 */
    fun launchPicker(context: Context): Boolean {
        val candidates = listOf(changeIntent(context), chooserIntent())
        for (intent in candidates) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                // 尝试下一个回退
            } catch (e: SecurityException) {
                // MIUI 限制跳转时尝试下一个回退
            }
        }
        return false
    }
}
