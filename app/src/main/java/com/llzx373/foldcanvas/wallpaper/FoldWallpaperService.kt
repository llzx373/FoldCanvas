package com.llzx373.foldcanvas.wallpaper

import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.llzx373.foldcanvas.convert.FrameExtractor
import com.llzx373.foldcanvas.data.SettingsStore
import com.llzx373.foldcanvas.theme.FrameCache
import com.llzx373.foldcanvas.theme.ThemeRepository
import com.llzx373.foldcanvas.theme.duo.DuoFrameGenerator
import com.llzx373.foldcanvas.theme.model.FoldTheme
import com.llzx373.foldcanvas.theme.model.ThemeCategory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * 折叠屏联动动态壁纸引擎。
 * 铰链角度（Sensor.TYPE_HINGE_ANGLE，API 30+）实时驱动：
 * 角度 < angleStart 显示外屏壁纸；角度区间播放展开动画帧；>= angleEnd 显示内屏壁纸。
 * 非折叠设备无铰链传感器，恒按展开态（内屏壁纸）渲染。
 */
class FoldWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = FoldEngine()

    inner class FoldEngine : Engine(), SensorEventListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

        private val thread = HandlerThread("fold-wallpaper").apply { start() }
        private val handler = Handler(thread.looper)
        private val sensorManager = getSystemService(SensorManager::class.java)
        private val hingeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)

        private val renderer = FrameRenderer()
        private val settings by lazy { SettingsStore(applicationContext) }
        private val repository by lazy { ThemeRepository(applicationContext) }
        private val frameCache by lazy { FrameCache(applicationContext) }

        /** 当前 surface 是否为外屏（展屏模糊主题下播放外屏动画帧）。 */
        private var coverMode = false

        /** 传感器原始角度。 */
        private var angle = 180f
        /** 实际绘制角度：开启去抖平滑时为 EMA 滤波值，否则等于原始角度。 */
        private var displayedAngle = 180f
        private var visible = false
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var drawPending = false

        private var demoRunning = false
        private var demoStartMs = 0L

        private val demoRunnable = object : Runnable {
            override fun run() {
                if (!demoRunning || !visible || !settings.demoMode) {
                    demoRunning = false
                    return
                }
                val elapsed = SystemClock.uptimeMillis() - demoStartMs
                val phase = (elapsed % DEMO_PERIOD_MS).toFloat() / DEMO_PERIOD_MS
                // 余弦开合：0°→180°→0° 循环
                angle = 180f * (1f - cos(2f * PI.toFloat() * phase)) / 2f
                displayedAngle = angle
                drawNow()
                renderer.prefetchAhead()
                handler.postDelayed(this, DEMO_FRAME_MS)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            settings.registerListener(this)
            logDisplays()
            handler.post { reloadTheme() }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            Log.i(TAG, "surface changed: ${width}x$height")
            surfaceWidth = width
            surfaceHeight = height
            handler.post {
                reloadTheme()
                drawNow()
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                hingeSensor?.let {
                    sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, handler)
                }
                handler.post {
                    reloadTheme()
                    drawNow()
                }
            } else {
                sensorManager?.unregisterListener(this)
            }
            syncDemo()
        }

        override fun onDestroy() {
            demoRunning = false
            handler.removeCallbacks(demoRunnable)
            settings.unregisterListener(this)
            sensorManager?.unregisterListener(this)
            handler.post { renderer.release() }
            thread.quitSafely()
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
            handler.post {
                if (!settings.smoothingEnabled) displayedAngle = angle
                syncDemo()
                scheduleDraw()
            }
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_HINGE_ANGLE) return
            if (settings.demoMode) return
            // 只记录最新角度，不直接绘制：快速开合时事件会积压，
            // 若每个事件各画一帧，会把快速动作按慢速重放（追赶播放）
            angle = event.values[0]
            scheduleDraw()
        }

        /** 合并绘制：任意时刻最多一个待绘制任务，且永远使用当时的最新角度。 */
        private fun scheduleDraw() {
            if (!visible || drawPending || demoRunning) return
            drawPending = true
            handler.post {
                drawPending = false
                if (settings.smoothingEnabled) {
                    displayedAngle = AngleFrameMapper.lowPass(
                        displayedAngle, angle, settings.smoothingAlpha,
                    )
                } else {
                    displayedAngle = angle
                }
                drawNow()
                renderer.prefetchAhead()
                // 铰链静止后传感器不再发事件，滤波值须自驱收敛到目标角度
                if (settings.smoothingEnabled && abs(angle - displayedAngle) > CONVERGE_EPSILON) {
                    scheduleDraw()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        private fun syncDemo() {
            val shouldRun = settings.demoMode && visible
            if (shouldRun && !demoRunning) {
                demoRunning = true
                demoStartMs = SystemClock.uptimeMillis()
                handler.post(demoRunnable)
            } else if (!shouldRun && demoRunning) {
                demoRunning = false
                handler.removeCallbacks(demoRunnable)
            }
        }

        /** 诊断日志：枚举系统 display，辅助判断外屏是否为独立 display（多引擎适配依据）。 */
        private fun logDisplays() {
            runCatching {
                val dm = getSystemService(DisplayManager::class.java) ?: return@runCatching
                dm.displays.forEach { d ->
                    val p = Point()
                    @Suppress("DEPRECATION")
                    d.getRealSize(p)
                    Log.i(
                        TAG,
                        "display id=${d.displayId} name=${d.name} " +
                            "realSize=${p.x}x${p.y} flags=${d.flags}",
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    val id = displayContext?.display?.displayId
                    Log.i(TAG, "engine displayContext displayId=$id")
                }
            }
        }

        /** 在壁纸工作线程执行。 */
        private fun reloadTheme() {
            // 直接设壁纸而未打开过 App 时，内置素材可能尚未落盘
            kotlinx.coroutines.runBlocking { repository.ensureBuiltinAssets() }
            val theme = resolveActiveTheme() ?: run {
                renderer.configure(surfaceWidth, surfaceHeight, null, null, emptyList())
                return
            }
            coverMode = theme.category == ThemeCategory.DUO_BLUR && isCoverSurface()
            if (coverMode) {
                reloadCoverFrames(theme)
                return
            }
            val frames = frameCache.frames(theme.id)
            renderer.configure(
                surfaceWidth, surfaceHeight,
                outer = fileOf(theme.outerWallpaper),
                inner = fileOf(theme.innerWallpaper),
                frames = frames,
            )
            if (!frameCache.isReady(theme.id)) prepareFrames(theme)
        }

        /**
         * 外屏 surface 判定：独立 display（API 31+ displayContext）即为外屏；
         * 单引擎折叠设备（MIUI 等 surface 随开合缩放）用宽高比启发式——
         * 阈值为当前设备档案内/外屏宽高比的几何中值（NORMAL≈1.63，WIDE≈1.02），
         * 比全局写死值对档案偏差更稳健；仅在有铰链传感器时启用，直板机不受影响。
         */
        private fun isCoverSurface(): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val id = displayContext?.display?.displayId
                if (id != null && id != android.view.Display.DEFAULT_DISPLAY) return true
            }
            if (hingeSensor == null || surfaceWidth <= 0) return false
            val profile = com.llzx373.foldcanvas.data.DeviceProfile.detect(applicationContext)
            val innerRatio = profile.innerHeight.toFloat() / profile.innerWidth
            val coverRatio = profile.outerHeight.toFloat() / profile.outerWidth
            val threshold = kotlin.math.sqrt(innerRatio * coverRatio)
            val surfaceRatio = surfaceHeight.toFloat() / surfaceWidth
            return surfaceRatio > threshold
        }

        /** 外屏模式：配置外屏动画帧（右半幅窗口 + 铰链→外缘渐进模糊压暗）。 */
        private fun reloadCoverFrames(theme: FoldTheme) {
            val coverId = theme.id + FrameCache.COVER_SUFFIX
            synchronized(FrameCache.extractLock(coverId)) {
                repository.ensureDuoCoverFrames(theme, frameCache)
                val frames = frameCache.frames(coverId)
                if (frames.isNotEmpty()) {
                    // 复用三段式渲染：outer=首帧、inner=末帧、0°→90° 映射播放
                    renderer.configure(
                        surfaceWidth, surfaceHeight,
                        outer = frames.first(),
                        inner = frames.last(),
                        frames = frames,
                    )
                } else {
                    // 帧生成失败：退化为静态外屏图
                    renderer.configure(
                        surfaceWidth, surfaceHeight,
                        outer = fileOf(theme.outerWallpaper),
                        inner = null,
                        frames = emptyList(),
                    )
                }
            }
            drawNow()
        }

        private fun resolveActiveTheme(): FoldTheme? {
            val id = settings.activeThemeId
            val themes = repository.listThemes()
            return themes.firstOrNull { it.id == id } ?: themes.firstOrNull()
        }

        private fun prepareFrames(theme: FoldTheme) {
            synchronized(FrameCache.extractLock(theme.id)) {
                // 内外图片类别无动画帧，渲染时退化为外/内屏交叉淡化
                if (theme.category == ThemeCategory.IMAGES) return
                if (frameCache.isReady(theme.id)) {
                    renderer.configure(
                        surfaceWidth, surfaceHeight,
                        outer = fileOf(theme.outerWallpaper),
                        inner = fileOf(theme.innerWallpaper),
                        frames = frameCache.frames(theme.id),
                    )
                    drawNow()
                    return
                }
                val video = theme.unfoldAnimation
                val ok = if (theme.category == ThemeCategory.DUO_BLUR) {
                    val inner = fileOf(theme.innerWallpaper)
                    inner != null && DuoFrameGenerator().generate(
                        innerFile = inner,
                        outDir = frameCache.prepareDir(theme.id),
                        frameCount = FrameCache.FRAME_COUNT,
                        targetWidth = FrameCache.FRAME_WIDTH,
                    )
                } else if (video != null) {
                    FrameExtractor(applicationContext).extract(
                        video = video,
                        outDir = frameCache.prepareDir(theme.id),
                        frameCount = FrameCache.FRAME_COUNT,
                        targetWidth = FrameCache.FRAME_WIDTH,
                    )
                } else if (theme.isBuiltin) {
                    // 阻塞式调用：仓库的 suspend 版本走 Dispatchers.IO，这里已在后台线程
                    kotlinx.coroutines.runBlocking {
                        repository.ensureBuiltinFrames(theme.id, frameCache)
                    }
                } else {
                    return
                }
                if (ok) {
                    frameCache.markReady(theme.id)
                    renderer.configure(
                        surfaceWidth, surfaceHeight,
                        outer = fileOf(theme.outerWallpaper),
                        inner = fileOf(theme.innerWallpaper),
                        frames = frameCache.frames(theme.id),
                    )
                    drawNow()
                } else {
                    Log.w(TAG, "frames not ready for ${theme.id}, fallback to crossfade")
                }
            }
        }

        private fun drawNow() {
            if (!visible && surfaceWidth == 0) return
            renderer.draw(
                surfaceHolder, displayedAngle,
                // 外屏模式：0°→90° 映射播放外屏帧；内屏：设置的角度区间
                if (coverMode) COVER_ANGLE_START else settings.angleStart,
                if (coverMode) COVER_ANGLE_END else settings.angleEnd,
                settings.animationEnabled,
            )
        }

        private fun fileOf(uri: Uri?): java.io.File? =
            uri?.path?.let { java.io.File(it) }?.takeIf { it.isFile }
    }

    companion object {
        private const val TAG = "FoldWallpaper"
        private const val DEMO_PERIOD_MS = 4_000L
        private const val DEMO_FRAME_MS = 16L
        private const val CONVERGE_EPSILON = 0.1f

        /** 外屏动画角度区间：0°→90°（与 DuoFoldMath.coverProgress 一致）。 */
        private const val COVER_ANGLE_START = 0f
        private const val COVER_ANGLE_END = 90f
    }
}
