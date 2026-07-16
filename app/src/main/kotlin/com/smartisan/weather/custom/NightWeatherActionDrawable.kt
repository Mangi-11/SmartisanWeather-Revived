package com.smartisan.weather.custom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * Recolors the original Smartisan action-button bitmap at draw time without changing
 * the source PNG or its selector states.
 *
 * The source artwork contains three distinct luminance ranges:
 * - bright opaque pixels: the circular button surface;
 * - mid-tone opaque pixels: the embedded add/list glyph;
 * - dark translucent pixels: the original drop shadow.
 *
 * A normal single-color tint cannot map those ranges to a dark surface and a light
 * glyph simultaneously. This drawable therefore performs a tiny cached pixel mapping
 * whenever the selector state or bounds change, while preserving every source alpha.
 */
internal class NightWeatherActionDrawable(
    private val source: Drawable,
    private val normalSurfaceColor: Int,
    private val pressedSurfaceColor: Int,
    private val disabledSurfaceColor: Int,
    private val embeddedIconColor: Int?,
) : Drawable(), Drawable.Callback {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var cachedBitmap: Bitmap? = null
    private var drawableAlpha: Int = 255
    private var outputColorFilter: ColorFilter? = null
    private var lastState: IntArray = intArrayOf()

    init {
        source.callback = this
    }

    override fun draw(canvas: Canvas) {
        val width = bounds.width()
        val height = bounds.height()
        if (width <= 0 || height <= 0) return

        val bitmap = cachedBitmap?.takeIf { it.width == width && it.height == height }
            ?: renderMappedBitmap(width, height).also { cachedBitmap = it }
        paint.alpha = drawableAlpha
        paint.colorFilter = outputColorFilter
        canvas.drawBitmap(bitmap, null, bounds, paint)
    }

    private fun renderMappedBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        source.bounds = Rect(0, 0, width, height)
        source.draw(Canvas(bitmap))

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val surfaceColor = surfaceColorForState(state)
        var maxSurfaceLuminance = 1
        for (pixel in pixels) {
            val alpha = Color.alpha(pixel)
            val luminance = luminance(pixel)
            if (alpha >= OPAQUE_THRESHOLD && luminance >= ICON_EDGE_LUMINANCE) {
                maxSurfaceLuminance = maxOf(maxSurfaceLuminance, luminance)
            }
        }

        for (index in pixels.indices) {
            val pixel = pixels[index]
            val alpha = Color.alpha(pixel)
            if (alpha == 0) continue

            val luminance = luminance(pixel)
            if (luminance < SHADOW_LUMINANCE && alpha < OPAQUE_THRESHOLD) {
                // Preserve the original black translucent shadow exactly.
                continue
            }

            val iconColor = embeddedIconColor
            pixels[index] = if (
                iconColor != null &&
                alpha >= OPAQUE_THRESHOLD &&
                luminance < ICON_EDGE_LUMINANCE
            ) {
                val coverage = when {
                    luminance <= ICON_CORE_LUMINANCE -> 255
                    else -> (
                        (ICON_EDGE_LUMINANCE - luminance) * 255 /
                            (ICON_EDGE_LUMINANCE - ICON_CORE_LUMINANCE)
                        ).coerceIn(0, 255)
                }
                blendColor(surfaceColor, iconColor, coverage, alpha)
            } else {
                val shade = (
                    luminance * 255 / maxSurfaceLuminance
                    ).coerceIn(MIN_SURFACE_SHADE, 255)
                Color.argb(
                    alpha,
                    Color.red(surfaceColor) * shade / 255,
                    Color.green(surfaceColor) * shade / 255,
                    Color.blue(surfaceColor) * shade / 255,
                )
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun surfaceColorForState(stateSet: IntArray): Int {
        val enabled = stateSet.isEmpty() || stateSet.contains(android.R.attr.state_enabled)
        return when {
            !enabled -> disabledSurfaceColor
            stateSet.contains(android.R.attr.state_pressed) -> pressedSurfaceColor
            else -> normalSurfaceColor
        }
    }

    private fun luminance(color: Int): Int =
        (
            54 * Color.red(color) +
                183 * Color.green(color) +
                19 * Color.blue(color)
            ) shr 8

    private fun blendColor(background: Int, foreground: Int, coverage: Int, alpha: Int): Int {
        val inverseCoverage = 255 - coverage
        return Color.argb(
            alpha,
            (Color.red(background) * inverseCoverage + Color.red(foreground) * coverage) / 255,
            (Color.green(background) * inverseCoverage + Color.green(foreground) * coverage) / 255,
            (Color.blue(background) * inverseCoverage + Color.blue(foreground) * coverage) / 255,
        )
    }

    private fun invalidateCache() {
        cachedBitmap = null
    }

    override fun onBoundsChange(bounds: Rect) {
        invalidateCache()
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(stateSet: IntArray): Boolean {
        val paletteChanged = !lastState.contentEquals(stateSet)
        lastState = stateSet.copyOf()
        val sourceChanged = source.setState(stateSet)
        if (paletteChanged || sourceChanged) {
            invalidateCache()
            invalidateSelf()
        }
        return paletteChanged || sourceChanged
    }

    override fun onLevelChange(level: Int): Boolean {
        val changed = source.setLevel(level)
        if (changed) {
            invalidateCache()
            invalidateSelf()
        }
        return changed
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        source.setVisible(visible, restart)
        return super.setVisible(visible, restart)
    }

    override fun setAlpha(alpha: Int) {
        if (drawableAlpha == alpha) return
        drawableAlpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        outputColorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = source.intrinsicWidth

    override fun getIntrinsicHeight(): Int = source.intrinsicHeight

    override fun getMinimumWidth(): Int = source.minimumWidth

    override fun getMinimumHeight(): Int = source.minimumHeight

    override fun getPadding(padding: Rect): Boolean = source.getPadding(padding)

    override fun invalidateDrawable(who: Drawable) {
        invalidateCache()
        invalidateSelf()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, whenMillis: Long) {
        scheduleSelf(what, whenMillis)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }

    companion object {
        private const val SHADOW_LUMINANCE = 80
        private const val OPAQUE_THRESHOLD = 200
        private const val ICON_CORE_LUMINANCE = 145
        private const val ICON_EDGE_LUMINANCE = 205
        private const val MIN_SURFACE_SHADE = 230
    }
}
