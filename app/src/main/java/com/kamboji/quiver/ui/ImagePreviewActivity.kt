package com.kamboji.quiver.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kamboji.quiver.R
import com.kamboji.quiver.data.db.AppDatabase
import com.kamboji.quiver.data.db.HistoryItem
import com.kamboji.quiver.util.TrashManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ImagePreviewActivity : AppCompatActivity() {

    private var historyItem: HistoryItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val imageView = findViewById<ImageView>(R.id.previewImage)
        val fileNameText = findViewById<TextView>(R.id.fileName)
        val deletedAtText = findViewById<TextView>(R.id.deletedAt)
        val restoreBtn = findViewById<MaterialButton>(R.id.restoreBtn)
        val deleteBtn = findViewById<MaterialButton>(R.id.deleteBtn)

        // Get data from intent
        val id = intent.getLongExtra("id", -1)
        val fileName = intent.getStringExtra("fileName") ?: ""
        val trashPath = intent.getStringExtra("trashPath")
        val deletedAt = intent.getLongExtra("deletedAt", 0)
        val originalPath = intent.getStringExtra("originalPath") ?: ""

        historyItem = HistoryItem(
            id = id,
            fileName = fileName,
            trashPath = trashPath,
            deletedAt = deletedAt,
            originalPath = originalPath,
            canRestore = true
        )

        // Set file info
        fileNameText.text = fileName
        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        deletedAtText.text = "Deleted ${dateFormat.format(Date(deletedAt))}"

        // Load full image
        trashPath?.let { path ->
            lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            BitmapFactory.decodeFile(path)
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }

                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                    Toast.makeText(this@ImagePreviewActivity, "Image file not found", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Restore button
        restoreBtn.setOnClickListener {
            historyItem?.let { item ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Restore Screenshot")
                    .setMessage("Restore '${item.fileName}' to your Screenshots folder?")
                    .setPositiveButton("Restore") { _, _ -> restoreItem(item) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        // Delete button
        deleteBtn.setOnClickListener {
            historyItem?.let { item ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Permanently Delete")
                    .setMessage("This will permanently delete '${item.fileName}'.\n\nThis action cannot be undone.")
                    .setPositiveButton("Delete") { _, _ -> permanentlyDelete(item) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        // Check if file exists
        val fileExists = trashPath != null && File(trashPath).exists()
        restoreBtn.isEnabled = fileExists
        restoreBtn.alpha = if (fileExists) 1f else 0.5f
    }

    private fun restoreItem(item: HistoryItem) {
        lifecycleScope.launch {
            val success = TrashManager.restoreFromTrash(this@ImagePreviewActivity, item)
            if (success) {
                AppDatabase.getDatabase(this@ImagePreviewActivity).historyDao().delete(item)
                Toast.makeText(this@ImagePreviewActivity, "Screenshot restored!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@ImagePreviewActivity, "Failed to restore", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun permanentlyDelete(item: HistoryItem) {
        lifecycleScope.launch {
            TrashManager.permanentlyDelete(this@ImagePreviewActivity, item)
            AppDatabase.getDatabase(this@ImagePreviewActivity).historyDao().delete(item)
            Toast.makeText(this@ImagePreviewActivity, "Permanently deleted", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

