package com.llzx373.foldcanvas.data

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.WindowManager

/**
 * 设备档案：动态探测屏幕几何，替代硬编码分辨率。
 * 内屏取系统报告的最大窗口尺寸（展开态），书式折叠外屏近似内屏半幅（同高、半宽）。
 */
data class DeviceProfile(
    val innerWidth: Int,
    val innerHeight: Int,
    val outerWidth: Int,
    val outerHeight: Int,
) {
    /** 当前是否为书式折叠形态（内屏明显宽于常规直板）。 */
    val isBookFold: Boolean
        get() = innerWidth * 2 > innerHeight

    companion object {
        fun detect(context: Context): DeviceProfile {
            var w = 0
            var h = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    val wm = context.getSystemService(WindowManager::class.java)
                    val bounds = wm?.maximumWindowMetrics?.bounds
                    if (bounds != null && !bounds.isEmpty) {
                        w = bounds.width()
                        h = bounds.height()
                    }
                }
            }
            if (w <= 0 || h <= 0) {
                runCatching {
                    val dm = context.getSystemService(DisplayManager::class.java)
                    val display = dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
                    val p = android.graphics.Point()
                    @Suppress("DEPRECATION")
                    display?.getRealSize(p)
                    w = p.x
                    h = p.y
                }
            }
            if (w <= 0 || h <= 0) {
                val dm = context.resources.displayMetrics
                w = dm.widthPixels
                h = dm.heightPixels
            }
            return DeviceProfile(
                innerWidth = w,
                innerHeight = h,
                outerWidth = w / 2,
                outerHeight = h,
            )
        }
    }
}
