package com.jaydeep.depthmotion

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.widget.RemoteViews
import kotlin.math.max

class DepthMotionWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, DepthMotionWidget::class.java)).forEach {
                update(context, manager, it, advance = true)
            }
        }
    }
    companion object {
        const val ACTION_REFRESH = "com.jaydeep.depthmotion.REFRESH"
        fun update(context: Context, manager: AppWidgetManager, id: Int, advance: Boolean = false) {
            val raw = if (advance) PhotoStore.next(context) else PhotoStore.current(context)
            val views = RemoteViews(context.packageName, R.layout.widget_depthmotion)
            val open = PendingIntent.getActivity(context, 0, Intent(context, SettingsActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            if (raw == null) {
                views.setViewVisibility(R.id.widget_image, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            } else try {
                val uri = Uri.parse(raw)
                val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (source != null) {
                    val framed = parallaxFrame(source)
                    views.setImageViewBitmap(R.id.widget_image, framed)
                    views.setViewVisibility(R.id.widget_image, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
                }
            } catch (_: Exception) { }
            manager.updateAppWidget(id, views)
        }
        private fun parallaxFrame(source: Bitmap): Bitmap {
            val w = 900
            val h = 900
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val scale = max(w.toFloat()/source.width, h.toFloat()/source.height) * 1.12f
            val dw = source.width * scale
            val dh = source.height * scale
            val phase = (System.currentTimeMillis() / 90000L) % 4
            val dx = when (phase.toInt()) { 0 -> -0.025f; 1 -> 0.025f; 2 -> 0.025f; else -> -0.025f }
            val dy = when (phase.toInt()) { 0 -> -0.018f; 1 -> -0.018f; 2 -> 0.018f; else -> 0.018f }
            val left = (w-dw)/2f + w*dx
            val top = (h-dh)/2f + h*dy
            val matrix = Matrix().apply { setScale(scale, scale); postTranslate(left, top) }
            canvas.drawBitmap(source, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            return out
        }
    }
}
