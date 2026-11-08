package com.jaydeep.depthmotion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import android.widget.RemoteViews
import kotlin.math.max

class GyroMotionService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var orientationSensor: Sensor? = null
    private var sourceBitmap: Bitmap? = null
    private var lastFrame = 0L
    private var lastSensorTimestamp = 0L
    private var loadedUri: String? = null
    private var roll = 0f
    private var pitch = 0f

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val stop = PendingIntent.getService(this, 9,
            Intent(this, GyroMotionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("DepthMotion is active")
            .setContentText("Gyroscope parallax is moving your home-screen photo")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop motion", stop)
            .build()
        else Notification.Builder(this)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("DepthMotion is active")
            .setContentText("Gyroscope parallax is enabled")
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, notification)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        orientationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        loadPhoto()
    }

    private fun loadPhoto() {
        val raw = PhotoStore.current(this)
        if (raw == loadedUri && sourceBitmap != null) return
        sourceBitmap?.recycle()
        loadedUri = raw
        sourceBitmap = try {
            raw?.let { uri ->
                contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (_: Exception) { null }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            if (lastSensorTimestamp != 0L) {
                val dt = (event.timestamp - lastSensorTimestamp) / 1_000_000_000f
                roll = (roll + event.values[1] * dt * 0.85f).coerceIn(-1f, 1f)
                pitch = (pitch + event.values[0] * dt * 0.85f).coerceIn(-1f, 1f)
            }
            lastSensorTimestamp = event.timestamp
        } else {
            val matrix = FloatArray(9)
            val angles = FloatArray(3)
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            SensorManager.getOrientation(matrix, angles)
            // Fused game rotation vector avoids unbounded gyro drift on supported phones.
            roll = (angles[2] / 0.55f).coerceIn(-1f, 1f)
            pitch = (angles[1] / 0.55f).coerceIn(-1f, 1f)
            lastSensorTimestamp = event.timestamp
        }
        val now = System.currentTimeMillis()
        if (now - lastFrame >= 220L) {
            lastFrame = now
            renderWidget()
        }
    }

    private fun renderWidget() {
        loadPhoto()
        val source = sourceBitmap ?: return
        val width = 720
        val height = 720
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val scale = max(width.toFloat() / source.width, height.toFloat() / source.height) * 1.14f
        val dx = roll * width * 0.035f
        val dy = pitch * height * 0.035f
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((width - source.width * scale) / 2f + dx,
                (height - source.height * scale) / 2f + dy)
        }
        canvas.drawBitmap(source, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, DepthMotionWidget::class.java))
        ids.forEach { id ->
            val views = RemoteViews(packageName, R.layout.widget_depthmotion)
            val open = PendingIntent.getActivity(this, id, Intent(this, SettingsActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setImageViewBitmap(R.id.widget_image, output)
            views.setViewVisibility(R.id.widget_image, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_motion, android.view.View.VISIBLE)
            manager.updateAppWidget(id, views)
        }
        output.recycle()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }
    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        sourceBitmap?.recycle()
        sourceBitmap = null
        loadedUri = null
        getSharedPreferences("depthmotion", 0).edit().putBoolean("motion_enabled", false).apply()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "DepthMotion motion", NotificationManager.IMPORTANCE_LOW))
        }
    }
    companion object {
        private const val CHANNEL = "depthmotion_motion"
        private const val NOTIFICATION_ID = 41
        const val ACTION_STOP = "com.jaydeep.depthmotion.STOP_MOTION"
        fun start(context: Context) {
            context.startForegroundService(Intent(context, GyroMotionService::class.java))
            context.getSharedPreferences("depthmotion", 0).edit().putBoolean("motion_enabled", true).apply()
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, GyroMotionService::class.java))
            context.getSharedPreferences("depthmotion", 0).edit().putBoolean("motion_enabled", false).apply()
        }
    }
}
