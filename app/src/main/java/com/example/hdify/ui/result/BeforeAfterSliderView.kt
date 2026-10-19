package com.example.hdify.ui.result

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs

class BeforeAfterSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var beforeBitmap: Bitmap? = null
    private var afterBitmap: Bitmap? = null

    // Slider position between 0.0 (all after) and 1.0 (all before)
    var sliderPosition: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    // Zoom & Pan state
    private var currentScale: Float = 1.0f
    private val minScale = 1.0f
    private val maxScale = 6.0f

    private var panX: Float = 0f
    private var panY: Float = 0f

    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var isDraggingSlider: Boolean = false
    private var isPanning: Boolean = false

    private val density = resources.displayMetrics.density
    private val touchSlop = 36f * density

    // Paints
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#151515")
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 3f * density
        style = Paint.Style.STROKE
        setShadowLayer(4f * density, 0f, 0f, Color.argb(120, 0, 0, 0))
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(6f * density, 0f, 2f * density, Color.argb(140, 0, 0, 0))
    }

    private val handleInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.FILL
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 20, 20, 20)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val zoomBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 20, 20, 20)
        style = Paint.Style.FILL
        setShadowLayer(4f * density, 0f, 1f * density, Color.argb(100, 0, 0, 0))
    }

    private val zoomTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val srcBeforeRect = Rect()
    private val srcAfterRect = Rect()
    private val dstRect = RectF()
    private val badgeRect = RectF()

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

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > 1.2f) {
                // Reset to 1.0x
                currentScale = 1.0f
                panX = 0f
                panY = 0f
            } else {
                // Zoom in to 2.5x centered on the tap position
                currentScale = 2.5f
                val focusX = e.x
                val focusY = e.y
                panX = (width / 2f - focusX) * (currentScale - 1f)
                panY = (height / 2f - focusY) * (currentScale - 1f)
                clampPan()
            }
            invalidate()
            return true
        }
    })

    fun setBitmaps(before: Bitmap?, after: Bitmap?) {
        this.beforeBitmap = before
        this.afterBitmap = after
        currentScale = 1.0f
        panX = 0f
        panY = 0f
        invalidate()
    }

    fun toggleZoom(toActualPixel: Boolean? = null) {
        val bmp = afterBitmap ?: beforeBitmap ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val shouldZoom = toActualPixel ?: (currentScale <= 1.1f)
        if (shouldZoom) {
            val imgW = bmp.width.toFloat()
            val imgH = bmp.height.toFloat()
            val imgAspect = imgW / imgH
            val viewAspect = w / h
            val fitW = if (imgAspect > viewAspect) w else h * imgAspect
            val ratio = if (fitW > 0f) (imgW / fitW).coerceIn(2.0f, maxScale) else 2.5f
            currentScale = ratio
            panX = 0f
            panY = 0f
            clampPan()
        } else {
            currentScale = 1.0f
            panX = 0f
            panY = 0f
        }
        invalidate()
    }

    fun isZoomed(): Boolean = currentScale > 1.1f

    private fun clampPan() {
        val bmp = afterBitmap ?: beforeBitmap ?: return
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

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (scaleDetector.isInProgress) {
            isDraggingSlider = false
            isPanning = false
            return true
        }

        val w = width.toFloat()
        val splitX = w * sliderPosition

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y

                val distFromDivider = abs(event.x - splitX)
                if (distFromDivider <= touchSlop || currentScale <= 1.05f) {
                    isDraggingSlider = true
                    isPanning = false
                    sliderPosition = (event.x / w).coerceIn(0f, 1f)
                } else {
                    isDraggingSlider = false
                    isPanning = true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y

                if (isDraggingSlider) {
                    sliderPosition = (event.x / w).coerceIn(0f, 1f)
                } else if (isPanning && currentScale > 1.0f) {
                    panX += dx
                    panY += dy
                    clampPan()
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingSlider = false
                isPanning = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 0. Letterbox background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val bmp = afterBitmap ?: beforeBitmap ?: return

        // Aspect-ratio preserving base dimension (FIT_CENTER)
        val imgW = bmp.width.toFloat()
        val imgH = bmp.height.toFloat()
        val imgAspect = imgW / imgH
        val viewAspect = w / h

        val fitW: Float
        val fitH: Float
        val fitX: Float
        val fitY: Float

        if (imgAspect > viewAspect) {
            fitW = w
            fitH = w / imgAspect
            fitX = 0f
            fitY = (h - fitH) / 2f
        } else {
            fitH = h
            fitW = h * imgAspect
            fitX = (w - fitW) / 2f
            fitY = 0f
        }

        // Apply zoom scale and pan offset
        val scaledW = fitW * currentScale
        val scaledH = fitH * currentScale

        val cx = fitX + fitW / 2f + panX
        val cy = fitY + fitH / 2f + panY

        val dstLeft = cx - scaledW / 2f
        val dstTop = cy - scaledH / 2f
        val dstRight = cx + scaledW / 2f
        val dstBottom = cy + scaledH / 2f
        dstRect.set(dstLeft, dstTop, dstRight, dstBottom)

        val splitX = w * sliderPosition

        // 1. Draw "After" (result) bitmap across dstRect
        afterBitmap?.let { afterBmp ->
            srcAfterRect.set(0, 0, afterBmp.width, afterBmp.height)
            canvas.drawBitmap(afterBmp, srcAfterRect, dstRect, bitmapPaint)
        }

        // 2. Draw "Before" (source) bitmap clipped to the left of the slider
        beforeBitmap?.let { beforeBmp ->
            canvas.save()
            canvas.clipRect(0f, 0f, splitX, h)
            srcBeforeRect.set(0, 0, beforeBmp.width, beforeBmp.height)
            canvas.drawBitmap(beforeBmp, srcBeforeRect, dstRect, bitmapPaint)
            canvas.restore()
        }

        // 3. Draw vertical divider line
        canvas.drawLine(splitX, 0f, splitX, h, linePaint)

        // 4. Draw center handle
        val handleRadius = 20f * density
        val centerY = h / 2f
        canvas.drawCircle(splitX, centerY, handleRadius, handlePaint)
        canvas.drawCircle(splitX, centerY, handleRadius - 3.5f * density, handleInnerPaint)

        // 5. Draw text badges ("Sebelum" on left, "Sesudah" on right)
        val badgeW = 74f * density
        val badgeH = 26f * density
        val badgeMargin = 10f * density

        if (splitX > badgeW + badgeMargin) {
            badgeRect.set(badgeMargin, badgeMargin, badgeMargin + badgeW, badgeMargin + badgeH)
            canvas.drawRoundRect(badgeRect, 8f * density, 8f * density, badgePaint)
            canvas.drawText("Sebelum", badgeRect.centerX(), badgeRect.centerY() + 4f * density, textPaint)
        }

        if (w - splitX > badgeW + badgeMargin) {
            badgeRect.set(w - badgeMargin - badgeW, badgeMargin, w - badgeMargin, badgeMargin + badgeH)
            canvas.drawRoundRect(badgeRect, 8f * density, 8f * density, badgePaint)
            canvas.drawText("Sesudah", badgeRect.centerX(), badgeRect.centerY() + 4f * density, textPaint)
        }

        // 6. Draw Zoom Indicator if zoomed in
        if (currentScale > 1.05f) {
            val zoomStr = "%.1fx • Ketuk 2x untuk reset".format(currentScale)
            val zoomBadgeW = 160f * density
            val zoomBadgeH = 26f * density
            val zoomBadgeLeft = (w - zoomBadgeW) / 2f
            val zoomBadgeTop = h - zoomBadgeH - 12f * density
            badgeRect.set(zoomBadgeLeft, zoomBadgeTop, zoomBadgeLeft + zoomBadgeW, zoomBadgeTop + zoomBadgeH)
            canvas.drawRoundRect(badgeRect, 13f * density, 13f * density, zoomBadgePaint)
            canvas.drawText(zoomStr, badgeRect.centerX(), badgeRect.centerY() + 4.5f * density, zoomTextPaint)
        }
    }
}
