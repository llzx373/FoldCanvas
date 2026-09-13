package com.llzx373.foldcanvas.theme

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.llzx373.foldcanvas.convert.FrameExtractor
import com.llzx373.foldcanvas.data.DeviceProfile
import com.llzx373.foldcanvas.theme.duo.DuoFrameGenerator
import com.llzx373.foldcanvas.theme.model.FoldTheme
import com.llzx373.foldcanvas.theme.model.OuterAutoMode
import com.llzx373.foldcanvas.theme.model.ThemeCategory
import com.llzx373.foldcanvas.ui.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ThemeRepository(private val context: Context) {

    private val builtinRoot = File(context.filesDir, "builtin")
    private val customRoot = File(context.filesDir, "themes")

    fun listThemes(): List<FoldTheme> = builtinThemes() + customThemes()

    fun findTheme(id: String): FoldTheme? = listThemes().firstOrNull { it.id == id }

    fun builtinThemes(): List<FoldTheme> = ProceduralThemeFactory.specs.map { spec ->
        val dir = File(builtinRoot, spec.id)
        val animation = File(dir, spec.animationFile).takeIf { it.isFile }
        FoldTheme(
            id = spec.id,
            name = spec.name,
            isBuiltin = true,
            outerWallpaper = Uri.fromFile(File(dir, spec.outerFile)),
            innerWallpaper = Uri.fromFile(File(dir, spec.innerFile)),
            unfoldAnimation = animation?.let { Uri.fromFile(it) },
            category = spec.category,
        )
    }

    fun customThemes(): List<FoldTheme> {
        val dirs = customRoot.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val propsFile = File(dir, THEME_PROPS)
            if (!propsFile.isFile) return@mapNotNull null
            val meta = CustomThemeProps.decode(propsFile.readText()) ?: return@mapNotNull null
            val category = ThemeCategory.fromKey(meta.category)
            if (category == ThemeCategory.DUO_BLUR) migrateDuoOuter(dir)
            val outer = File(dir, meta.outerFile)
            val inner = File(dir, meta.innerFile)
            if (!outer.isFile || !inner.isFile) return@mapNotNull null
            val animation = meta.animationFile?.let { File(dir, it) }?.takeIf { it.isFile }
            FoldTheme(
                id = meta.id,
                name = meta.name,
                isBuiltin = false,
                outerWallpaper = Uri.fromFile(outer),
                innerWallpaper = Uri.fromFile(inner),
                unfoldAnimation = animation?.let { Uri.fromFile(it) },
                category = category,
            )
        }.sortedBy { it.name }
    }

    /**
     * 旧版自定义展屏模糊主题的 outer.png 是 centerCrop 策略（竖向裁剪 + 铰链边被切），
     * 加载时按当前策略（右半幅完整拉伸铺满）自动重建一次，以 outer.gen 标记版本。
     */
    private fun migrateDuoOuter(dir: File) {
        val marker = File(dir, OUTER_GEN_MARKER)
        val current = marker.isFile &&
            runCatching { marker.readText().trim() }.getOrNull() == OUTER_GEN_VERSION
        if (current) return
        val innerFile = File(dir, INNER_FILE)
        val innerBitmap = runCatching {
            android.graphics.BitmapFactory.decodeFile(innerFile.absolutePath)
        }.getOrNull() ?: return
        val profile = DeviceProfile.detect(context)
        ImageUtils.scaleFill(
            ImageUtils.rightHalf(innerBitmap),
            profile.outerWidth, profile.outerHeight,
        ).writeTo(File(dir, OUTER_FILE))
        innerBitmap.recycle()
        marker.writeText(OUTER_GEN_VERSION)
        // 外屏帧派生自 outer.png，策略更新后需重建
        FrameCache(context).clear(dir.name + FrameCache.COVER_SUFFIX)
    }

    /** 首次启动时为内置主题准备素材：asset 主题直接拷贝，渐变主题程序化生成。 */
    suspend fun ensureBuiltinAssets() = withContext(Dispatchers.IO) {
        val profile by lazy { DeviceProfile.detect(context) }
        ProceduralThemeFactory.specs.forEach { spec ->
            val dir = File(builtinRoot, spec.id).apply { mkdirs() }
            if (spec.assetDir != null) {
                listOf(spec.outerFile, spec.innerFile, spec.animationFile).forEach { name ->
                    val target = File(dir, name)
                    if (!target.isFile) runCatching {
                        context.assets.open("${spec.assetDir}/$name").use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
                return@forEach
            }
            val inner = File(dir, spec.innerFile)
            if (!inner.isFile) {
                ProceduralThemeFactory.gradientBitmap(
                    profile.innerWidth,
                    profile.innerHeight,
                    spec.innerColors,
                ).writeTo(inner)
            }
            val outer = File(dir, spec.outerFile)
            val outerGen = File(dir, OUTER_GEN_MARKER)
            val outerGenOk = outerGen.isFile &&
                runCatching { outerGen.readText().trim() }.getOrNull() == OUTER_GEN_VERSION
            if (!outer.isFile || (spec.category == ThemeCategory.DUO_BLUR && !outerGenOk)) {
                if (spec.category == ThemeCategory.DUO_BLUR) {
                    // 展屏模糊：外屏 = 内屏右半幅完整拉伸铺满（Duo 外屏 UV 窗口），不 centerCrop
                    val innerBitmap = android.graphics.BitmapFactory.decodeFile(inner.absolutePath)
                    if (innerBitmap != null) {
                        ImageUtils.scaleFill(
                            ImageUtils.rightHalf(innerBitmap),
                            profile.outerWidth, profile.outerHeight,
                        ).writeTo(outer)
                        innerBitmap.recycle()
                        outerGen.writeText(OUTER_GEN_VERSION)
                    }
                } else {
                    ProceduralThemeFactory.gradientBitmap(
                        profile.outerWidth,
                        profile.outerHeight,
                        spec.outerColors,
                    ).writeTo(outer)
                }
            }
        }
    }

    /**
     * 内置渐变主题展开动画帧：程序化生成到帧缓存。
     * asset 主题走视频抽帧、展屏模糊主题走 DuoFrameGenerator（统一按类别分支处理）、
     * 内外图片类别无动画帧，均不在此处理。
     */
    suspend fun ensureBuiltinFrames(
        themeId: String,
        frameCache: FrameCache,
        onProgress: (Float) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val spec = ProceduralThemeFactory.specs.firstOrNull { it.id == themeId }
            ?: return@withContext false
        if (spec.assetDir != null) return@withContext false
        if (spec.category != ThemeCategory.ANIMATION) return@withContext true
        if (frameCache.isReady(themeId)) return@withContext true
        val dir = frameCache.prepareDir(themeId)
        val profile = DeviceProfile.detect(context)
        val height = FrameCache.FRAME_WIDTH * profile.innerHeight / profile.innerWidth
        val count = FrameCache.FRAME_COUNT
        for (i in 0 until count) {
            val progress = i / (count - 1).toFloat()
            val frame = ProceduralThemeFactory.animationFrame(
                FrameCache.FRAME_WIDTH, height, progress, spec,
            )
            frame.writeTo(File(dir, frameName(i)), Bitmap.CompressFormat.JPEG, 85)
            frame.recycle()
            onProgress((i + 1) / count.toFloat())
        }
        frameCache.markReady(themeId)
        true
    }

    /**
     * 保存自定义主题。
     * 展屏动画：视频必选（编辑时已存在视频可沿用）；缺内屏取视频末帧，缺外屏取内屏右半。
     * 内外图片：内屏必选、无视频；外屏缺省时按 outerAutoMode（左半/中间/右半）从内屏派生。
     * 展屏模糊：内屏必选、无视频；外屏固定取内屏右半，动画帧由 DuoFrameGenerator 离线生成。
     * existingId 非空时为编辑：沿用主题目录，素材与动画帧缓存整体失效重建。
     */
    fun saveCustomTheme(
        name: String,
        category: ThemeCategory = ThemeCategory.ANIMATION,
        normalizedVideo: File? = null,
        outerBitmap: Bitmap? = null,
        innerBitmap: Bitmap? = null,
        outerAutoMode: OuterAutoMode = OuterAutoMode.RIGHT,
        profile: DeviceProfile,
        existingId: String? = null,
    ): FoldTheme {
        val existingDir = existingId?.let { File(customRoot, it) }
        val existingVideo = existingDir?.let { File(it, ANIMATION_FILE) }?.takeIf { it.isFile }
        val videoSource = normalizedVideo ?: existingVideo

        val inner = innerBitmap ?: if (category == ThemeCategory.ANIMATION && videoSource != null) {
            FrameExtractor(context).lastFrame(videoSource)
                ?.let { ImageUtils.centerCrop(it, profile.innerWidth, profile.innerHeight) }
        } else null
        require(inner != null) { "inner wallpaper unavailable" }
        if (category == ThemeCategory.ANIMATION) {
            require(videoSource != null) { "unfold animation video required" }
        }
        val outer = outerBitmap ?: run {
            // 展屏模糊：外屏 = 内屏右半幅完整拉伸铺满（Duo 外屏 UV 窗口）；
            // 其他类别：按 outerAutoMode 取半幅后居中裁剪
            if (category == ThemeCategory.DUO_BLUR) {
                ImageUtils.scaleFill(
                    ImageUtils.rightHalf(inner),
                    profile.outerWidth, profile.outerHeight,
                )
            } else {
                val half = when (outerAutoMode) {
                    OuterAutoMode.LEFT -> ImageUtils.leftHalf(inner)
                    OuterAutoMode.CENTER -> ImageUtils.centerHalf(inner)
                    else -> ImageUtils.rightHalf(inner)
                }
                ImageUtils.centerCrop(half, profile.outerWidth, profile.outerHeight)
            }
        }

        val id = existingId ?: "custom_" + UUID.randomUUID().toString().substring(0, 8)
        val dir = File(customRoot, id).apply { mkdirs() }
        outer.writeTo(File(dir, OUTER_FILE))
        inner.writeTo(File(dir, INNER_FILE))
        val animationFile = if (videoSource != null) {
            if (normalizedVideo != null) {
                normalizedVideo.copyTo(File(dir, ANIMATION_FILE), overwrite = true)
            }
            ANIMATION_FILE
        } else {
            File(dir, ANIMATION_FILE).delete()
            null
        }
        val meta = CustomThemeProps.CustomThemeMeta(
            id = id,
            name = name,
            outerFile = OUTER_FILE,
            innerFile = INNER_FILE,
            animationFile = animationFile,
            formFactor = if (profile == DeviceProfile.WIDE_FOLD) "wide" else "normal",
            category = category.key,
            outerMode = outerAutoMode.name,
        )
        File(dir, THEME_PROPS).writeText(CustomThemeProps.encode(meta))
        if (category == ThemeCategory.DUO_BLUR) {
            File(dir, OUTER_GEN_MARKER).writeText(OUTER_GEN_VERSION)
        }
        // 素材已变：动画帧缓存（含外屏帧）失效，下次应用时重建
        if (existingId != null) {
            FrameCache(context).clear(id)
            FrameCache(context).clear(id + FrameCache.COVER_SUFFIX)
        }
        return FoldTheme(
            id = id,
            name = name,
            isBuiltin = false,
            outerWallpaper = Uri.fromFile(File(dir, OUTER_FILE)),
            innerWallpaper = Uri.fromFile(File(dir, INNER_FILE)),
            unfoldAnimation = animationFile?.let { Uri.fromFile(File(dir, it)) },
            category = category,
        )
    }

    /** 读取自定义主题元数据（编辑时回填用）。 */
    fun customThemeMeta(id: String): CustomThemeProps.CustomThemeMeta? {
        val propsFile = File(File(customRoot, id), THEME_PROPS)
        if (!propsFile.isFile) return null
        return CustomThemeProps.decode(propsFile.readText())
    }

    /**
     * 展屏模糊主题的外屏动画帧（右半幅窗口 + 铰链→外缘渐进模糊压暗）。
     * 阻塞式实现，调用方需自行持有 FrameCache.extractLock 并在后台线程调用。
     */
    fun ensureDuoCoverFrames(
        theme: FoldTheme,
        frameCache: FrameCache,
        onProgress: (Float) -> Unit = {},
    ): Boolean {
        val coverId = theme.id + FrameCache.COVER_SUFFIX
        if (frameCache.isReady(coverId)) return true
        val outer = theme.outerWallpaper.path?.let { File(it) }?.takeIf { it.isFile }
            ?: return false
        val profile = DeviceProfile.detect(context)
        val ok = DuoFrameGenerator().generateCover(
            outerFile = outer,
            outDir = frameCache.prepareDir(coverId),
            frameCount = FrameCache.FRAME_COUNT,
            targetWidth = profile.outerWidth,
            targetHeight = profile.outerHeight,
            onProgress = onProgress,
        )
        if (ok) frameCache.markReady(coverId)
        return ok
    }

    fun deleteCustomTheme(id: String) {
        File(customRoot, id).deleteRecursively()
    }

    private fun Bitmap.writeTo(
        file: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100,
    ) {
        FileOutputStream(file).use { compress(format, quality, it) }
    }

    companion object {
        const val OUTER_FILE = "outer.png"
        const val INNER_FILE = "inner.png"
        const val ANIMATION_FILE = "animation.mp4"
        const val THEME_PROPS = "theme.properties"

        /** 内置展屏模糊主题外屏派生策略版本，升级后旧 outer.png 自动重建。 */
        const val OUTER_GEN_MARKER = "outer.gen"
        const val OUTER_GEN_VERSION = "2"

        fun frameName(index: Int): String = "frame_%03d.jpg".format(index)
    }
}
