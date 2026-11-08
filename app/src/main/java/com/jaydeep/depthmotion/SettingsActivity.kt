package com.jaydeep.depthmotion

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
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
            Toast.makeText(this, "Folder scanned", Toast.LENGTH_SHORT).show()
        }
    }
    private lateinit var count: TextView
    private lateinit var interval: Spinner
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFF171522.toInt())
        }
        fun label(s: String, size: Float = 15f) = TextView(this).apply {
            text = s; textSize = size; setTextColor(0xFFFFFFFF.toInt()); setPadding(0, 10, 0, 10)
        }
        root.addView(label("DEPTHMOTION", 25f))
        root.addView(label("Your rotating photo widget"))
        count = label("")
        root.addView(count)
        fun button(text: String, action: () -> Unit) {
            root.addView(Button(this).apply { this.text = text; setOnClickListener { action() } },
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        button("Import photos") { photoPicker.launch(arrayOf("image/*")) }
        button("Choose folders to scan") { folderPicker.launch(null) }
        root.addView(label("Image rotation interval"))
        val values = listOf("15 minutes", "30 minutes", "1 hour", "6 hours", "12 hours", "24 hours")
        interval = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, values)
            setSelection(listOf(15L,30L,60L,360L,720L,1440L).indexOf(PhotoStore.interval(this@SettingsActivity)).coerceAtLeast(0))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    PhotoStore.setInterval(this@SettingsActivity, listOf(15L,30L,60L,360L,720L,1440L)[position])
                    scheduleRotation()
                }
            }
        }
        root.addView(interval)
        val shuffle = CheckBox(this).apply {
            text = "Shuffle photos"; setTextColor(0xFFFFFFFF.toInt())
            isChecked = getSharedPreferences("depthmotion", 0).getBoolean("shuffle", false)
            setOnCheckedChangeListener { _, checked -> getSharedPreferences("depthmotion", 0).edit().putBoolean("shuffle", checked).apply() }
        }
        root.addView(shuffle)
        button("Refresh widget now") { PhotoStore.discover(this); refreshWidgets(); Toast.makeText(this, "Widget refreshed", Toast.LENGTH_SHORT).show() }
        button("Remove imported photo list") { PhotoStore.save(this, emptyList()); refreshWidgets() }
        root.addView(label("Add the DepthMotion widget from your home screen's widget picker. Resize it to fit your layout.\n\nAndroid limits background widget refreshes to conserve battery. Minimum automatic rotation interval: 15 minutes."))
        setContentView(ScrollView(this).apply { addView(root) })
        updateCount()
        scheduleRotation()
    }
    private fun updateCount() { count.text = "${PhotoStore.photos(this).size} photos in your library" }
    private fun refreshWidgets() {
        updateCount()
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(android.content.ComponentName(this, DepthMotionWidget::class.java))
        ids.forEach { DepthMotionWidget.update(this, manager, it) }
    }
    private fun scheduleRotation() {
        val request = PeriodicWorkRequestBuilder<RotateWorker>(PhotoStore.interval(this), TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("depthmotion_rotation", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
