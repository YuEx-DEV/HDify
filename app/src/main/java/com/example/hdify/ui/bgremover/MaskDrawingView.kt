package com.example.hdify.ui.bgremover

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.util.ArrayDeque
import kotlin.math.max

enum class BrushMode {
    PAN_ZOOM,
    MAGIC_COLOR,
    ERASE,
    RESTORE
}

class MaskDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null
    private var maskBitmap: Bitmap? = null
    private var maskCanvas: Canvas? = null
    private var initialMaskBitmap: Bitmap? = null

    // Magic Color parameters
    var magicTolerance: Int = 35
    var magicContiguous: Boolean = true


    var bgColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            invalidate()
        }

    var brushMode: BrushMode = BrushMode.PAN_ZOOM
        set(value) {
            field = value
            invalidate()
        }

    // Brush radius in screen pixels
    var brushRadius: Float = 30f * resources.displayMetrics.density
        set(value) {
            field = value.coerceIn(8f * resources.displayMetrics.density, 90f * resources.displayMetrics.density)
            eraseStrokePaint.strokeWidth = field * 2f
            restoreStrokePaint.strokeWidth = field * 2f
            invalidate()
        }

    // Undo & Redo stacks (capped at 5 states to conserve heap memory & eliminate GC lag)
    private val maxHistory = 5
    private val undoStack = ArrayDeque<Bitmap>()
    private val redoStack = ArrayDeque<Bitmap>()

    var onHistoryChangedListener: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    // Zoom & Pan state
    private var currentScale: Float = 1.0f
    private val minScale = 1.0f
    private val maxScale = 6.0f
    private var panX: Float = 0f
    private var panY: Float = 0f

    // Touch & Cursor Tracking
    private var cursorX: Float = -1000f
    private var cursorY: Float = -1000f
    private var showCursor: Boolean = false
    private var lastImgX: Float = 0f
    private var lastImgY: Float = 0f
    private var isPainting: Boolean = false

    // Geometry rects
    private val dstRect = RectF()
    private val srcRect = Rect()

    // Paints
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val maskCompositePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    private val eraseStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        color = Color.TRANSPARENT
        strokeWidth = brushRadius * 2f
    }

    private val eraseFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        color = Color.TRANSPARENT
    }

    private val restoreStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
        alpha = 255
        strokeWidth = brushRadius * 2f
    }

    private val restoreFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        alpha = 255
    }

    // Cursor indicator paints
    private val cursorOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2.5f * resources.displayMetrics.density
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
    }

    private val cursorInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(160, 0, 0, 0)
        strokeWidth = 1f * resources.displayMetrics.density
    }

    // Checkerboard shader for transparent background
    private val checkerboardPaint = Paint().apply {
        val size = (12f * resources.displayMetrics.density).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p1 = Paint().apply { color = Color.parseColor("#E0E0E0") }
        val p2 = Paint().apply { color = Color.parseColor("#FFFFFF") }
        c.drawRect(0f, 0f, size * 2f, size * 2f, p1)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p2)
        c.drawRect(size.toFloat(), size.toFloat(), size * 2f, size * 2f, p2)
        shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private val solidBgPaint = Paint()

    // Gestures
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prevScale = currentScale
            currentScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
            val focusX = detector.focusX
            val focusY = detector.focusY
            val scaleFactor = currentScale / prevScale
            panX = (panX - (focusX - width / 2f)) * scaleFactor + (focusX - width / 2f)
            panY = (panY - (focusY - height / 2f)) * scaleFactor + (focusY - height / 2f)
            clampPan()
            invalidate()
            return true
        }
    })

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    fun setup(source: Bitmap, mask: Bitmap) {
        this.sourceBitmap = source
        this.initialMaskBitmap = mask.copy(Bitmap.Config.ARGB_8888, false)
        this.maskBitmap = mask.copy(Bitmap.Config.ARGB_8888, true)
        this.maskCanvas = Canvas(this.maskBitmap!!)
        undoStack.clear()
        redoStack.clear()
        currentScale = 1.0f
        panX = 0f
        panY = 0f
        notifyHistory()
        invalidate()
    }

    fun getMask(): Bitmap? = maskBitmap

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        val current = maskBitmap ?: return
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeLast()
        pushRedo(current)
        maskBitmap = prev.copy(Bitmap.Config.ARGB_8888, true)
        try { prev.recycle() } catch (_: Throwable) {}
        maskCanvas = Canvas(maskBitmap!!)
        notifyHistory()
        invalidate()
    }

    fun redo() {
        val current = maskBitmap ?: return
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        pushUndo(current)
        maskBitmap = next.copy(Bitmap.Config.ARGB_8888, true)
        try { next.recycle() } catch (_: Throwable) {}
        maskCanvas = Canvas(maskBitmap!!)
        notifyHistory()
        invalidate()
    }

    fun resetToAuto() {
        val init = initialMaskBitmap ?: return
        val current = maskBitmap ?: return
        pushUndo(current)
        clearStack(redoStack)
        maskBitmap = init.copy(Bitmap.Config.ARGB_8888, true)
        maskCanvas = Canvas(maskBitmap!!)
        notifyHistory()
        invalidate()
    }

    fun applyNewMask(newMask: Bitmap) {
        val current = maskBitmap
        if (current != null) {
            pushUndo(current)
            clearStack(redoStack)
        }
        this.initialMaskBitmap = newMask.copy(Bitmap.Config.ARGB_8888, false)
        this.maskBitmap = newMask.copy(Bitmap.Config.ARGB_8888, true)
        this.maskCanvas = Canvas(this.maskBitmap!!)
        notifyHistory()
        invalidate()
    }

    private fun pushUndo(bmp: Bitmap) {
        if (undoStack.size >= maxHistory) {
            val old = undoStack.removeFirst()
            try { old.recycle() } catch (_: Throwable) {}
        }
        undoStack.addLast(bmp.copy(Bitmap.Config.ARGB_8888, false))
    }

    private fun pushRedo(bmp: Bitmap) {
        if (redoStack.size >= maxHistory) {
            val old = redoStack.removeFirst()
            try { old.recycle() } catch (_: Throwable) {}
        }
        redoStack.addLast(bmp.copy(Bitmap.Config.ARGB_8888, false))
    }

    private fun clearStack(stack: ArrayDeque<Bitmap>) {
        while (stack.isNotEmpty()) {
            val bmp = stack.removeFirst()
            try { bmp.recycle() } catch (_: Throwable) {}
        }
    }

    private fun notifyHistory() {
        onHistoryChangedListener?.invoke(canUndo(), canRedo())
    }

    private fun clampPan() {
        val bmp = sourceBitmap ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val imgW = bmp.width.toFloat()
        val imgH = bmp.height.toFloat()
        val imgAspect = imgW / imgH
        val viewAspect = w / h

        val fitW = if (imgAspect > viewAspect) w else h * imgAspect
        val fitH = if (imgAspect > viewAspect) w / imgAspect else h

        val scaledW = fitW * currentScale
        val scaledH = fitH * currentScale

        val maxPanX = if (scaledW > w) (scaledW - w) / 2f else 0f
        val maxPanY = if (scaledH > h) (scaledH - h) / 2f else 0f

        panX = panX.coerceIn(-maxPanX, maxPanX)
        panY = panY.coerceIn(-maxPanY, maxPanY)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

        // Always pass to scale detector when 2 fingers are touching
        if (event.pointerCount > 1) {
            isPainting = false
            showCursor = false
            scaleDetector.onTouchEvent(event)
            return true
        }

        if (scaleDetector.isInProgress) {
            isPainting = false
            showCursor = false
            return true
        }

        when (brushMode) {
            BrushMode.PAN_ZOOM -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        panX += dx
                        panY += dy
                        lastTouchX = event.x
                        lastTouchY = event.y
                        clampPan()
                        invalidate()
                    }
                }
                return true
            }
            BrushMode.MAGIC_COLOR -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    val (imgX, imgY) = viewToImageCoords(event.x, event.y)
                    executeMagicColorRemoval(imgX.toInt(), imgY.toInt())
                }
                return true
            }
            BrushMode.ERASE, BrushMode.RESTORE -> {
                cursorX = event.x
                cursorY = event.y
                showCursor = (event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)

                val (imgX, imgY) = viewToImageCoords(event.x, event.y)

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        maskBitmap?.let { pushUndo(it) }
                        redoStack.clear()
                        notifyHistory()

                        isPainting = true
                        lastImgX = imgX
                        lastImgY = imgY
                        applyBrush(imgX, imgY, isPoint = true)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isPainting) {
                            applyBrushSegment(lastImgX, lastImgY, imgX, imgY)
                            lastImgX = imgX
                            lastImgY = imgY
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPainting = false
                        showCursor = false
                        notifyHistory()
                    }
                }
                invalidate()
                return true
            }
        }
    }

    fun executeMagicColorRemoval(startX: Int, startY: Int) {
        val src = sourceBitmap ?: return
        val mask = maskBitmap ?: return
        val w = src.width
        val h = src.height
        if (startX !in 0 until w || startY !in 0 until h) return

        val total = w * h
        val srcPixels = IntArray(total)
        src.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val targetPixel = srcPixels[startY * w + startX]
        val targetAlpha = (targetPixel shr 24) and 0xFF
        if (targetAlpha < 10) return

        val maskPixels = IntArray(total)
        mask.getPixels(maskPixels, 0, w, 0, 0, w, h)

        // Save current mask state for Undo
        pushUndo(mask)
        redoStack.clear()

        val tolFactor = magicTolerance * 3.2f
        val thresholdSq = (tolFactor * tolFactor).toLong()

        if (magicContiguous) {
            val visited = java.util.BitSet(total)
            val queue = IntArray(total)
            var head = 0
            var tail = 0

            val startIdx = startY * w + startX
            queue[tail++] = startIdx
            visited.set(startIdx)

            val dx = intArrayOf(0, 0, 1, -1)
            val dy = intArrayOf(1, -1, 0, 0)

            while (head < tail) {
                val idx = queue[head++]
                val px = idx % w
                val py = idx / w

                maskPixels[idx] = Color.TRANSPARENT

                for (d in 0 until 4) {
                    val nx = px + dx[d]
                    val ny = py + dy[d]
                    if (nx in 0 until w && ny in 0 until h) {
                        val nIdx = ny * w + nx
                        if (!visited.get(nIdx)) {
                            visited.set(nIdx)
                            if (isColorSimilar(srcPixels[nIdx], targetPixel, thresholdSq)) {
                                queue[tail++] = nIdx
                            }
                        }
                    }
                }
            }
        } else {
            for (i in 0 until total) {
                if (maskPixels[i] != Color.TRANSPARENT && isColorSimilar(srcPixels[i], targetPixel, thresholdSq)) {
                    maskPixels[i] = Color.TRANSPARENT
                }
            }
        }

        mask.setPixels(maskPixels, 0, w, 0, 0, w, h)
        maskCanvas = Canvas(mask)
        notifyHistory()
        invalidate()
    }

    private fun isColorSimilar(c1: Int, c2: Int, thresholdSq: Long): Boolean {
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF

        val rmean = (r1 + r2) / 2L
        val dr = (r1 - r2).toLong()
        val dg = (g1 - g2).toLong()
        val db = (b1 - b2).toLong()

        val distSq = (((512L + rmean) * dr * dr) shr 8) + 4L * dg * dg + (((767L - rmean) * db * db) shr 8)
        return distSq <= thresholdSq
    }

    private fun viewToImageCoords(vx: Float, vy: Float): Pair<Float, Float> {
        val bmp = sourceBitmap ?: return Pair(0f, 0f)
        if (dstRect.width() <= 0f || dstRect.height() <= 0f) return Pair(0f, 0f)
        val imgX = ((vx - dstRect.left) / dstRect.width()) * bmp.width
        val imgY = ((vy - dstRect.top) / dstRect.height()) * bmp.height
        return Pair(imgX, imgY)
    }

    private fun getImageBrushRadius(): Float {
        val bmp = sourceBitmap ?: return brushRadius
        if (dstRect.width() <= 0f) return brushRadius
        val scale = bmp.width.toFloat() / dstRect.width()
        return brushRadius * scale
    }

    private fun applyBrush(imgX: Float, imgY: Float, isPoint: Boolean) {
        val canvas = maskCanvas ?: return
        val r = getImageBrushRadius()
        val fillP = if (brushMode == BrushMode.ERASE) eraseFillPaint else restoreFillPaint
        canvas.drawCircle(imgX, imgY, r, fillP)
    }

    private fun applyBrushSegment(x1: Float, y1: Float, x2: Float, y2: Float) {
        val canvas = maskCanvas ?: return
        val r = getImageBrushRadius()
        val strokeP = if (brushMode == BrushMode.ERASE) eraseStrokePaint else restoreStrokePaint
        val fillP = if (brushMode == BrushMode.ERASE) eraseFillPaint else restoreFillPaint

        strokeP.strokeWidth = r * 2f
        canvas.drawLine(x1, y1, x2, y2, strokeP)
        canvas.drawCircle(x2, y2, r, fillP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val srcBmp = sourceBitmap ?: return
        val maskBmp = maskBitmap ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val imgW = srcBmp.width.toFloat()
        val imgH = srcBmp.height.toFloat()
        val imgAspect = imgW / imgH
        val viewAspect = w / h

        val fitW = if (imgAspect > viewAspect) w else h * imgAspect
        val fitH = if (imgAspect > viewAspect) w / imgAspect else h

        val fitX = (w - fitW) / 2f
        val fitY = (h - fitH) / 2f

        val scaledW = fitW * currentScale
        val scaledH = fitH * currentScale

        val cx = fitX + fitW / 2f + panX
        val cy = fitY + fitH / 2f + panY

        dstRect.set(cx - scaledW / 2f, cy - scaledH / 2f, cx + scaledW / 2f, cy + scaledH / 2f)
        srcRect.set(0, 0, srcBmp.width, srcBmp.height)

        // 1. Draw Background (Checkerboard for Transparent, or Solid Color)
        if (bgColor == Color.TRANSPARENT) {
            canvas.drawRect(dstRect, checkerboardPaint)
        } else {
            solidBgPaint.color = bgColor
            canvas.drawRect(dstRect, solidBgPaint)
        }

        // 2. Draw Masked Subject using hardware-accelerated layer
        val saveCount = canvas.saveLayer(dstRect, null)
        canvas.drawBitmap(srcBmp, srcRect, dstRect, bitmapPaint)
        canvas.drawBitmap(maskBmp, srcRect, dstRect, maskCompositePaint)
        canvas.restoreToCount(saveCount)

        // 3. Draw Brush Cursor Ring when painting
        if (showCursor && (brushMode == BrushMode.ERASE || brushMode == BrushMode.RESTORE)) {
            canvas.drawCircle(cursorX, cursorY, brushRadius, cursorOuterPaint)
            canvas.drawCircle(cursorX, cursorY, brushRadius, cursorInnerPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearAllBitmaps()
    }

    fun clearAllBitmaps() {
        clearStack(undoStack)
        clearStack(redoStack)
        notifyHistory()
    }
}
