package com.jaydeep.depthmotion

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class RotateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            PhotoStore.discover(applicationContext)
            val manager = AppWidgetManager.getInstance(applicationContext)
            manager.getAppWidgetIds(ComponentName(applicationContext, DepthMotionWidget::class.java))
                .forEach { DepthMotionWidget.update(applicationContext, manager, it, advance = true) }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
