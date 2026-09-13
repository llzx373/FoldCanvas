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

    companion object {
        const val FRAME_COUNT = 45
        const val FRAME_WIDTH = 1440
        private const val DONE_MARKER = ".done"

        /** 同一主题只允许一个执行体抽帧（桌面/锁屏引擎、详情页可能并发）。 */
        private val extractLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

        fun extractLock(themeId: String): Any = extractLocks.getOrPut(themeId) { Any() }
    }
}
