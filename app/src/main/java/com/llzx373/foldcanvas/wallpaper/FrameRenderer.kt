package com.llzx373.foldcanvas.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import android.view.SurfaceHolder
import java.io.File

/**
 * 壁纸画面渲染：根据铰链角度在外屏壁纸、展开动画帧、内屏壁纸间绘制。
 * 无动画帧时退化为外/内屏交叉淡入淡出。
 */
class FrameRenderer {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var outerFile: File? = null
    private var innerFile: File? = null
    private var frameFiles: List<File> = emptyList()

    private var outerBitmap: Bitmap? = null
    private var innerBitmap: Bitmap? = null

    private val frameBitmaps = object : LruCache<Int, Bitmap>(8) {
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            oldValue.recycle()
        }
    }

    private var lastFrameIndex = -1
    private var frameDirection = 1

    @Synchronized
    fun configure(
        width: Int,
        height: Int,
        outer: File?,
        inner: File?,
        frames: List<File>,
    ) {
        if (width != surfaceWidth || height != surfaceHeight) {
            surfaceWidth = width
            surfaceHeight = height
            outerBitmap?.recycle(); outerBitmap = null
            innerBitmap?.recycle(); innerBitmap = null
            frameBitmaps.evictAll()
        }
        if (outer != outerFile || inner != innerFile) {
            outerBitmap?.recycle(); outerBitmap = null
            innerBitmap?.recycle(); innerBitmap = null
        }
        if (frames != frameFiles) {
            frameBitmaps.evictAll()
            lastFrameIndex = -1
        }
        outerFile = outer
        innerFile = inner
        frameFiles = frames
    }

    @Synchronized
    fun draw(
        holder: SurfaceHolder,
        angle: Float,
        angleStart: Float,
        angleEnd: Float,
        animationEnabled: Boolean,
    ) {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        // 优先硬件加速画布（API 26+，部分设备/壁纸宿主不支持时回退软件画布）
        val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull()
            ?: holder.lockCanvas()
            ?: return
        try {
            canvas.drawColor(0xFF000000.toInt())
            val progress = AngleFrameMapper.progress(angle, angleStart, angleEnd)
            val useFrames = animationEnabled && frameFiles.isNotEmpty()
            if (useFrames) {
                when {
                    progress <= 0f -> drawOuter(canvas)
                    progress >= 1f -> drawInner(canvas)
                    else -> drawFrame(canvas, AngleFrameMapper.frameIndex(progress, frameFiles.size))
                }
            } else {
                // 无动画：交叉淡化过渡
                drawOuter(canvas)
                if (progress > 0f) {
                    paint.alpha = (progress * 255).toInt().coerceIn(0, 255)
                    drawInner(canvas, paint)
                    paint.alpha = 255
                }
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    @Synchronized
    fun release() {
        outerBitmap?.recycle(); outerBitmap = null
        innerBitmap?.recycle(); innerBitmap = null
        frameBitmaps.evictAll()
    }

    private fun drawOuter(canvas: Canvas, p: Paint = paint) {
        val bmp = outerBitmap ?: decode(outerFile)?.also { outerBitmap = it }
        if (bmp != null && !bmp.isRecycled) drawCenterCrop(canvas, bmp, p)
    }

    private fun drawInner(canvas: Canvas, p: Paint = paint) {
        val bmp = innerBitmap ?: decode(innerFile)?.also { innerBitmap = it }
        if (bmp != null && !bmp.isRecycled) drawCenterCrop(canvas, bmp, p)
    }

    private fun drawFrame(canvas: Canvas, index: Int) {
        var bmp = frameBitmaps.get(index)
        if (bmp == null || bmp.isRecycled) {
            bmp = decode(frameFiles.getOrNull(index))
            if (bmp != null) frameBitmaps.put(index, bmp)
        }
        if (bmp != null && !bmp.isRecycled) drawCenterCrop(canvas, bmp, paint)
        if (lastFrameIndex >= 0 && index != lastFrameIndex) {
            frameDirection = if (index > lastFrameIndex) 1 else -1
        }
        lastFrameIndex = index
    }

    /** 绘制后预取运动方向上的相邻帧进缓存，降低下次绘制的解码延迟。 */
    @Synchronized
    fun prefetchAhead() {
        if (frameFiles.isEmpty() || lastFrameIndex < 0) return
        listOf(
            lastFrameIndex + frameDirection,
            lastFrameIndex + 2 * frameDirection,
            lastFrameIndex - frameDirection,
        ).filter { it in frameFiles.indices && frameBitmaps.get(it) == null }
            .forEach { i ->
                decode(frameFiles[i])?.let { frameBitmaps.put(i, it) }
            }
    }

    private fun decode(file: File?): Bitmap? {
        if (file == null || !file.isFile || surfaceWidth <= 0 || surfaceHeight <= 0) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= surfaceWidth &&
                bounds.outHeight / (sample * 2) >= surfaceHeight
            ) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, p: Paint) {
        val scale = maxOf(
            surfaceWidth / bitmap.width.toFloat(),
            surfaceHeight / bitmap.height.toFloat(),
        )
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        val left = (surfaceWidth - dw) / 2f
        val top = (surfaceHeight - dh) / 2f
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        canvas.drawBitmap(bitmap, 0f, 0f, p)
        canvas.restore()
    }
}
