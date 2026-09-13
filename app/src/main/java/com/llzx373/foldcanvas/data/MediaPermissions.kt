package com.llzx373.foldcanvas.data

/**
 * 访问相册/下载目录所需的媒体权限集合（纯 Kotlin，便于 JVM 单元测试）。
 * API 33+ 细分媒体权限；32 及以下用 READ_EXTERNAL_STORAGE。
 */
object MediaPermissions {

    const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
    const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
    const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"

    fun required(sdkInt: Int): Array<String> =
        if (sdkInt >= 33) {
            arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
        } else {
            arrayOf(READ_EXTERNAL_STORAGE)
        }
}
