package com.jaydeep.depthmotion

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import java.util.concurrent.TimeUnit

class SettingsActivity : AppCompatActivity() {
    private val photoPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            PhotoStore.add(this, uri)
        }
        refreshWidgets()
    }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
            PhotoStore.addTree(this, uri)
            refreshWidgets()
            toast("Folder scanned")
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startMotion() else {
            motionSwitch.isChecked = false
            toast("Allow notifications to enable motion mode and its persistent stop control.")
        }
    }

    private lateinit var photoCount: TextView
    private lateinit var librarySummary: TextView
    private lateinit var motionSwitch: Switch
    private lateinit var motionStatus: TextView
    private lateinit var intervalSpinner: Spinner
    private val bg = Color.rgb(17, 16, 27)
    private val surface = Color.rgb(29, 27, 43)
    private val muted = Color.rgb(174, 170, 194)
    private val accent = Color.rgb(190, 171, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildDashboard()
        scheduleRotation()
    }

    override fun onResume() {
        super.onResume()
        if (::photoCount.isInitialized) updateLibrary()
        if (::motionSwitch.isInitialized) syncMotionState()
    }

    private fun buildDashboard() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(36))
        }
        scroll.addView(root)
        root.addView(text("DEPTHMOTION", 12f, accent, true).apply { letterSpacing = .18f })
        root.addView(text("Your photos,\nwith a sense of depth.", 30f, Color.WHITE, true).apply {
            setPadding(0, dp(10), 0, dp(6))
        })
        root.addView(text("A living photo widget for your Android home screen.", 14f, muted))
        root.addView(space(20))

        val hero = card(0xFF29243F.toInt(), 22)
        hero.addView(text("YOUR WIDGET STUDIO", 11f, 0xFFC8B7FF.toInt(), true))
        hero.addView(text("Make your home screen feel personal.", 20f, Color.WHITE, true).apply {
            setPadding(0, dp(8), 0, dp(6))
        })
        hero.addView(text("Import a collection, choose the rhythm, then place the widget wherever it belongs.", 13f, 0xFFD0CADF.toInt()))
        hero.addView(space(14))
        val preview = FrameLayout(this).apply {
            background = rounded(0xFF171522.toInt(), 18)
            minimumHeight = dp(126)
        }
        val previewText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(16), dp(12), dp(16))
        }
        previewText.addView(text("✦", 25f, accent, true).apply { gravity = Gravity.CENTER })
        previewText.addView(text("DEPTHMOTION", 15f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        previewText.addView(text("Your next favourite view", 11f, muted).apply { gravity = Gravity.CENTER })
        preview.addView(previewText, FrameLayout.LayoutParams(-1, -1))
        hero.addView(preview, LinearLayout.LayoutParams(-1, dp(126)))
        root.addView(hero)

        root.addView(space(16))
        val library = card(surface, 20)
        library.addView(sectionTitle("PHOTO LIBRARY", "Build the collection your widget rotates through."))
        photoCount = text("0 photos", 24f, Color.WHITE, true)
        photoCount.setPadding(0, dp(12), 0, dp(2))
        library.addView(photoCount)
        librarySummary = text("Import individual images or scan a folder you choose.", 12f, muted)
        library.addView(librarySummary)
        library.addView(space(14))
        library.addView(actionButton("＋   Import photos", true) { photoPicker.launch(arrayOf("image/*")) })
        library.addView(space(8))
        library.addView(actionButton("▤   Choose folders to scan", false) { folderPicker.launch(null) })
        root.addView(library)

        root.addView(space(16))
        val motion = card(surface, 20)
        motion.addView(sectionTitle("LIVE MOTION", "Let your phone's movement shift the photo."))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val motionCopy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        motionCopy.addView(text("Gyroscope parallax", 16f, Color.WHITE, true))
        motionStatus = text("Off · saves battery", 12f, muted)
        motionCopy.addView(motionStatus)
        row.addView(motionCopy)
        motionSwitch = Switch(this).apply {
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
            isChecked = getSharedPreferences("depthmotion", 0).getBoolean("motion_enabled", false)
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                    if (sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) == null &&
                        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null &&
                        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) == null) {
                        isChecked = false
                        toast("No compatible motion sensor was found.")
                    } else if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else startMotion()
                } else {
                    GyroMotionService.stop(this@SettingsActivity)
                    motionStatus.text = "Off · saves battery"
                }
            }
        }
        row.addView(motionSwitch)
        motion.addView(row)
        motion.addView(space(12))
        val sensitivityLabel = text("Motion sensitivity · ${getSharedPreferences("depthmotion", 0).getInt("sensitivity", 55)}%", 12f, muted)
        motion.addView(sensitivityLabel)
        val sensitivity = SeekBar(this).apply {
            max = 100
            progress = getSharedPreferences("depthmotion", 0).getInt("sensitivity", 55)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress.coerceAtLeast(10)
                    sensitivityLabel.text = "Motion sensitivity · $value%"
                    getSharedPreferences("depthmotion", 0).edit().putInt("sensitivity", value).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        motion.addView(sensitivity)
        motion.addView(space(8))
        motion.addView(text("When enabled, DepthMotion keeps a foreground service active to read device orientation and refresh the widget. A persistent notification provides a stop control. Frequent image updates use additional battery and may be rate-limited by some launchers.", 12f, muted))
        root.addView(motion)

        root.addView(space(16))
        val rotation = card(surface, 20)
        rotation.addView(sectionTitle("ROTATION & PLAYBACK", "Set how your collection changes."))
        rotation.addView(text("Change photo every", 13f, muted).apply { setPadding(0, dp(10), 0, dp(6)) })
        val choices = listOf("15 minutes", "30 minutes", "1 hour", "6 hours", "12 hours", "24 hours")
        val minutes = listOf(15L, 30L, 60L, 360L, 720L, 1440L)
        intervalSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, choices).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(minutes.indexOf(PhotoStore.interval(this@SettingsActivity)).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    PhotoStore.setInterval(this@SettingsActivity, minutes[position])
                    scheduleRotation()
                }
            }
        }
        rotation.addView(intervalSpinner, LinearLayout.LayoutParams(-1, dp(52)))
        val shuffle = Switch(this).apply {
            text = "Shuffle collection"
            setTextColor(Color.WHITE)
            isChecked = getSharedPreferences("depthmotion", 0).getBoolean("shuffle", false)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences("depthmotion", 0).edit().putBoolean("shuffle", checked).apply()
            }
        }
        rotation.addView(shuffle)
        rotation.addView(space(8))
        rotation.addView(actionButton("↻   Refresh widget now", false) {
            PhotoStore.discover(this)
            refreshWidgets(advance = true)
            toast("Widget refreshed")
        })
        root.addView(rotation)

        root.addView(space(16))
        val footer = card(0xFF211F30.toInt(), 18)
        footer.addView(text("ADD TO HOME SCREEN", 11f, accent, true))
        footer.addView(text("Touch and hold an empty area on your home screen, open Widgets, then drag DepthMotion onto the page. Resize it to fit your layout.", 13f, Color.WHITE).apply {
            setPadding(0, dp(8), 0, 0)
        })
        root.addView(footer)
        root.addView(space(12))
        root.addView(actionButton("Clear photo library", false) {
            PhotoStore.save(this, emptyList())
            refreshWidgets()
            toast("Photo list cleared")
        })
        setContentView(scroll)
        updateLibrary()
        syncMotionState()
    }

    private fun sectionTitle(title: String, subtitle: String): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text(title, 11f, accent, true).apply { letterSpacing = .12f })
        box.addView(text(subtitle, 13f, muted).apply { setPadding(0, dp(5), 0, 0) })
        return box
    }

    private fun card(color: Int, radius: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(color, radius)
        elevation = dp(2).toFloat()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = true
    }

    private fun actionButton(label: String, primary: Boolean, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (primary) 0xFF191525.toInt() else Color.WHITE)
        setPadding(dp(14), dp(15), dp(14), dp(15))
        background = rounded(if (primary) 0xFFC3B0FF.toInt() else 0xFF39354D.toInt(), 14)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun startMotion() {
        try {
            GyroMotionService.start(this)
            syncMotionState()
        } catch (e: Exception) {
            motionSwitch.isChecked = false
            toast("Motion could not start: ${e.localizedMessage ?: "permission or device restriction"}")
        }
    }
    private fun syncMotionState() {
        val enabled = getSharedPreferences("depthmotion", 0).getBoolean("motion_enabled", false)
        if (::motionSwitch.isInitialized && motionSwitch.isChecked != enabled) {
            motionSwitch.setOnCheckedChangeListener(null)
            motionSwitch.isChecked = enabled
            motionSwitch.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else startMotion()
                } else GyroMotionService.stop(this)
            }
        }
        if (::motionStatus.isInitialized) motionStatus.text = if (enabled) "On · widget follows device tilt" else "Off · saves battery"
    }
    private fun updateLibrary() {
        val count = PhotoStore.photos(this).size
        photoCount.text = "$count ${if (count == 1) "photo" else "photos"}"
        librarySummary.text = if (count == 0) "Your collection is empty. Import photos or scan a folder." else "Your widget has $count images ready to rotate."
    }
    private fun refreshWidgets(advance: Boolean = false) {
        updateLibrary()
        val manager = AppWidgetManager.getInstance(this)
        manager.getAppWidgetIds(ComponentName(this, DepthMotionWidget::class.java)).forEach {
            DepthMotionWidget.update(this, manager, it, advance)
        }
    }
    private fun scheduleRotation() {
        val request = PeriodicWorkRequestBuilder<RotateWorker>(PhotoStore.interval(this), TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("depthmotion_rotation", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
