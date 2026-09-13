package com.llzx373.foldcanvas.theme

import android.content.Context
import java.io.File

class FrameCache(context: Context) {

    private val root = File(context.filesDir, "framecache")

    fun framesDir(themeId: String): File = File(root, themeId)

    fun isReady(themeId: String): Boolean = File(framesDir(themeId), DONE_MARKER).isFile

    fun markReady(themeId: String) {
        File(framesDir(themeId).apply { mkdirs() }, DONE_MARKER).writeText("ok")
    }

    fun frames(themeId: String): List<File> {
        val dir = framesDir(themeId)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.extension == "jpg" }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    fun prepareDir(themeId: String): File = framesDir(themeId).apply {
        mkdirs()
        listFiles()?.forEach { it.delete() }
    }

    fun clear(themeId: String) {
        framesDir(themeId).deleteRecursively()
    }

    /** 全部帧缓存占用字节数（递归统计，含 .done 标记）。 */
    fun totalSize(): Long {
        fun walk(f: File): Long = when {
            f.isFile -> f.length()
            f.isDirectory -> f.listFiles()?.sumOf { walk(it) } ?: 0L
            else -> 0L
        }
        return walk(root)
    }

    fun clearAll() {
        root.deleteRecursively()
    }

    companion object {
        const val FRAME_COUNT = 45
        const val FRAME_WIDTH = 1440

        /** 展屏模糊主题外屏帧缓存的 themeId 后缀（与内屏帧缓存区分）。 */
        const val COVER_SUFFIX = "_cover"
        private const val DONE_MARKER = ".done"

        /** 同一主题只允许一个执行体抽帧（桌面/锁屏引擎、详情页可能并发）。 */
        private val extractLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

        fun extractLock(themeId: String): Any = extractLocks.getOrPut(themeId) { Any() }
    }
}
