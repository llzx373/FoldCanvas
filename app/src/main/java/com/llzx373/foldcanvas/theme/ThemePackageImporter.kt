package com.llzx373.foldcanvas.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.llzx373.foldcanvas.data.DeviceProfile
import com.llzx373.foldcanvas.theme.model.FoldTheme
import com.llzx373.foldcanvas.theme.model.OuterAutoMode
import com.llzx373.foldcanvas.theme.model.ThemeCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 导入电脑端制作的主题包（.foldtheme，zip 结构：theme.properties + outer.png +
 * inner.png + 可选 animation.mp4）。素材视为已按目标机型规格化，直接落盘，不转码；
 * 缺失素材沿用 [ThemeRepository.saveCustomTheme] 的派生规则补齐。
 */
class ThemePackageImporter(private val context: Context) {

    private val repository = ThemeRepository(context)

    suspend fun import(uri: Uri): Result<FoldTheme> = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "pkg_import_${System.currentTimeMillis()}")
        try {
            extract(uri, workDir).getOrElse { return@withContext Result.failure(it) }

            val propsFile = File(workDir, ThemeRepository.THEME_PROPS)
            if (!propsFile.isFile) {
                return@withContext Result.failure(Exception("无效的主题包：缺少 theme.properties"))
            }
            val meta = CustomThemeProps.decode(propsFile.readText())
                ?: return@withContext Result.failure(Exception("无效的主题包：元数据解析失败"))

            val category = ThemeCategory.fromKey(meta.category)
            val outerBitmap = File(workDir, meta.outerFile).takeIf { it.isFile }
                ?.let { BitmapFactory.decodeFile(it.absolutePath) }
            val innerBitmap = File(workDir, meta.innerFile).takeIf { it.isFile }
                ?.let { BitmapFactory.decodeFile(it.absolutePath) }
            val video = meta.animationFile?.let { File(workDir, it) }?.takeIf { it.isFile }

            val outerMode = meta.outerMode?.let {
                runCatching { OuterAutoMode.valueOf(it) }.getOrNull()
            } ?: OuterAutoMode.RIGHT

            try {
                Result.success(
                    repository.saveCustomTheme(
                        name = meta.name,
                        category = category,
                        normalizedVideo = video,
                        outerBitmap = outerBitmap,
                        innerBitmap = innerBitmap,
                        outerAutoMode = outerMode,
                        profile = DeviceProfile.detect(context),
                    )
                )
            } catch (e: IllegalArgumentException) {
                val reason = when (category) {
                    ThemeCategory.ANIMATION -> "展屏动画主题缺少可用的展开视频"
                    ThemeCategory.IMAGES -> "内外图片主题缺少内屏壁纸"
                    ThemeCategory.DUO_BLUR -> "展屏模糊主题缺少内屏壁纸"
                }
                Result.failure(Exception("导入失败：$reason"))
            } finally {
                outerBitmap?.recycle()
                innerBitmap?.recycle()
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun extract(uri: Uri, outDir: File): Result<Unit> = runCatching {
        outDir.mkdirs()
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("无法读取主题包文件")
        var total = 0L
        stream.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = File(entry.name).name // 只取文件名，忽略包内目录结构与穿越路径
                    if (!entry.isDirectory && name in ALLOWED_FILES) {
                        val target = File(outDir, name)
                        total += target.outputStream().use { zip.copyTo(it) }
                        if (total > MAX_PACKAGE_BYTES) error("主题包体积超限")
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    companion object {
        private val ALLOWED_FILES = setOf(
            ThemeRepository.THEME_PROPS,
            ThemeRepository.OUTER_FILE,
            ThemeRepository.INNER_FILE,
            ThemeRepository.ANIMATION_FILE,
        )

        /** 防解压炸弹：包内容解压上限 512MB。 */
        private const val MAX_PACKAGE_BYTES = 512L * 1024 * 1024
    }
}
