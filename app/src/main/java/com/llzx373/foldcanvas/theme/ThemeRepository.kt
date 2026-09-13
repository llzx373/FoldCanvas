package com.llzx373.foldcanvas.theme

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.llzx373.foldcanvas.convert.FrameExtractor
import com.llzx373.foldcanvas.data.DeviceProfile
import com.llzx373.foldcanvas.theme.model.FoldTheme
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
        )
    }

    fun customThemes(): List<FoldTheme> {
        val dirs = customRoot.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val propsFile = File(dir, THEME_PROPS)
            if (!propsFile.isFile) return@mapNotNull null
            val meta = CustomThemeProps.decode(propsFile.readText()) ?: return@mapNotNull null
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
            )
        }.sortedBy { it.name }
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
            val outer = File(dir, spec.outerFile)
            if (!outer.isFile) {
                ProceduralThemeFactory.gradientBitmap(
                    profile.outerWidth,
                    profile.outerHeight,
                    spec.outerColors,
                ).writeTo(outer)
            }
            val inner = File(dir, spec.innerFile)
            if (!inner.isFile) {
                ProceduralThemeFactory.gradientBitmap(
                    profile.innerWidth,
                    profile.innerHeight,
                    spec.innerColors,
                ).writeTo(inner)
            }
        }
    }

    /** 内置渐变主题展开动画帧：程序化生成到帧缓存（asset 主题走视频抽帧，不在此处理）。 */
    suspend fun ensureBuiltinFrames(
        themeId: String,
        frameCache: FrameCache,
        onProgress: (Float) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val spec = ProceduralThemeFactory.specs.firstOrNull { it.id == themeId }
            ?: return@withContext false
        if (spec.assetDir != null) return@withContext false
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
     * 保存自定义主题。视频必选；外屏/内屏图可缺省：
     * 缺内屏 → 取视频末帧居中裁剪；缺外屏 → 取内屏右半部分居中裁剪。
     */
    fun saveCustomTheme(
        name: String,
        normalizedVideo: File,
        outerBitmap: Bitmap?,
        innerBitmap: Bitmap?,
        profile: DeviceProfile,
    ): FoldTheme {
        val inner = innerBitmap ?: FrameExtractor(context).lastFrame(normalizedVideo)
            ?.let { ImageUtils.centerCrop(it, profile.innerWidth, profile.innerHeight) }
        val outer = outerBitmap ?: inner
            ?.let { ImageUtils.centerCrop(ImageUtils.rightHalf(it), profile.outerWidth, profile.outerHeight) }
        require(inner != null && outer != null) { "wallpapers unavailable" }

        val id = "custom_" + UUID.randomUUID().toString().substring(0, 8)
        val dir = File(customRoot, id).apply { mkdirs() }
        outer.writeTo(File(dir, OUTER_FILE))
        inner.writeTo(File(dir, INNER_FILE))
        val target = File(dir, ANIMATION_FILE)
        normalizedVideo.copyTo(target, overwrite = true)
        val meta = CustomThemeProps.CustomThemeMeta(
            id = id,
            name = name,
            outerFile = OUTER_FILE,
            innerFile = INNER_FILE,
            animationFile = ANIMATION_FILE,
            formFactor = if (profile == DeviceProfile.WIDE_FOLD) "wide" else "normal",
        )
        File(dir, THEME_PROPS).writeText(CustomThemeProps.encode(meta))
        return FoldTheme(
            id = id,
            name = name,
            isBuiltin = false,
            outerWallpaper = Uri.fromFile(File(dir, OUTER_FILE)),
            innerWallpaper = Uri.fromFile(File(dir, INNER_FILE)),
            unfoldAnimation = Uri.fromFile(File(dir, ANIMATION_FILE)),
        )
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

        fun frameName(index: Int): String = "frame_%03d.jpg".format(index)
    }
}
