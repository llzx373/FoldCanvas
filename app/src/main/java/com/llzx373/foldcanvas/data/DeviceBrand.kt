package com.llzx373.foldcanvas.data

import android.os.Build

/** 设备品牌探测（小米/MIUI 有特殊的权限与壁纸行为，需要针对性引导）。 */
object DeviceBrand {
    val isXiaomi: Boolean
        get() = Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) ||
            Build.BRAND.equals("xiaomi", ignoreCase = true) ||
            Build.BRAND.equals("redmi", ignoreCase = true)
}
