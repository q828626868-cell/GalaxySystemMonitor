package com.galaxy.systemmonitor.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap

/** Circular battery gauge for widgets; Glance cannot draw arcs itself. */
object RingBitmap {

    /** @param fraction 0..1, or null to draw only the track. */
    fun render(fraction: Float?, sizePx: Int, strokePx: Float, color: Int, trackColor: Int): Bitmap {
        val px = sizePx.coerceAtLeast(1)
        val bmp = createBitmap(px, px)
        val canvas = Canvas(bmp)
        val inset = strokePx / 2
        val oval = RectF(inset, inset, px - inset, px - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
        }
        paint.color = trackColor
        canvas.drawArc(oval, 0f, 360f, false, paint)
        if (fraction != null && fraction > 0f) {
            paint.color = color
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawArc(oval, -90f, 360f * fraction.coerceIn(0f, 1f), false, paint)
        }
        return bmp
    }
}
