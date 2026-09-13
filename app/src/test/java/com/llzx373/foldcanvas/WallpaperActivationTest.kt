package com.llzx373.foldcanvas

import android.app.WallpaperManager
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import com.llzx373.foldcanvas.wallpaper.FoldWallpaperService
import com.llzx373.foldcanvas.wallpaper.WallpaperActivation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperActivationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `isActive is false when no live wallpaper is set`() {
        assertFalse(WallpaperActivation.isActive(context))
    }

    @Test
    fun `changeIntent targets our wallpaper service component`() {
        val intent = WallpaperActivation.changeIntent(context)
        assertEquals(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER, intent.action)
        val component = intent.getParcelableExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName::class.java,
        )
        assertEquals(context.packageName, component?.packageName)
        assertEquals(FoldWallpaperService::class.java.name, component?.className)
    }

    @Test
    fun `wallpaperPermissionGranted is true on non-Xiaomi devices`() {
        // Robolectric 默认 Build.MANUFACTURER 非小米，应直接放行
        assertTrue(WallpaperActivation.wallpaperPermissionGranted(context))
    }

    @Test
    fun `miuiPermissionEditorIntent carries package name extra`() {
        val intent = WallpaperActivation.miuiPermissionEditorIntent(context)
        assertEquals("miui.intent.action.APP_PERM_EDITOR", intent.action)
        assertEquals(context.packageName, intent.getStringExtra("extra_pkgname"))
    }
}
