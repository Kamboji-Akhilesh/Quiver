package com.kamboji.quiver.screenshots.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kamboji.quiver.R
import com.kamboji.quiver.screenshots.data.db.AppDatabase
import com.kamboji.quiver.screenshots.data.db.HistoryItem
import com.kamboji.quiver.screenshots.util.TrashManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // Setup toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)

        adapter = HistoryAdapter(
            onItemClick = { item -> openPreview(item) },
            onRestoreClick = { item -> confirmRestore(item) },
            onDeleteClick = { item -> confirmPermanentDelete(item) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Observe history from database
        lifecycleScope.launch {
            AppDatabase.getDatabase(this@HistoryActivity)
                .historyDao()
                .getAllHistory()
                .collectLatest { items ->
                    adapter.submitList(items)
                    emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                }
        }

        // Clean up old trash on activity start
        lifecycleScope.launch {
            TrashManager.cleanOldTrash(this@HistoryActivity)
        }
    }

    private fun confirmRestore(item: HistoryItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Restore Screenshot")
            .setMessage("Restore '${item.fileName}' to your Screenshots folder?")
            .setPositiveButton("Restore") { _, _ -> restoreItem(item) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmPermanentDelete(item: HistoryItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permanently Delete")
            .setMessage("This will permanently delete '${item.fileName}'.\n\nThis action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> permanentlyDelete(item) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restoreItem(item: HistoryItem) {
        lifecycleScope.launch {
            val success = TrashManager.restoreFromTrash(this@HistoryActivity, item)
            if (success) {
                AppDatabase.getDatabase(this@HistoryActivity).historyDao().delete(item)
                Toast.makeText(this@HistoryActivity, "Screenshot restored!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@HistoryActivity, "Failed to restore", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun permanentlyDelete(item: HistoryItem) {
        lifecycleScope.launch {
            TrashManager.permanentlyDelete(this@HistoryActivity, item)
            AppDatabase.getDatabase(this@HistoryActivity).historyDao().delete(item)
            Toast.makeText(this@HistoryActivity, "Permanently deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPreview(item: HistoryItem) {
        val intent = Intent(this, ImagePreviewActivity::class.java).apply {
            putExtra("id", item.id)
            putExtra("fileName", item.fileName)
            putExtra("trashPath", item.trashPath)
            putExtra("deletedAt", item.deletedAt)
            putExtra("originalPath", item.originalPath)
        }
        startActivity(intent)
    }
}

class HistoryAdapter(
    private val onItemClick: (HistoryItem) -> Unit,
    private val onRestoreClick: (HistoryItem) -> Unit,
    private val onDeleteClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var items = listOf<HistoryItem>()

    fun submitList(newItems: List<HistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
        private val fileName: TextView = view.findViewById(R.id.fileName)
        private val deletedAt: TextView = view.findViewById(R.id.deletedAt)
        private val restoreBtn: MaterialButton = view.findViewById(R.id.restoreBtn)
        private val deleteBtn: MaterialButton = view.findViewById(R.id.deleteBtn)

        fun bind(item: HistoryItem) {
            fileName.text = item.fileName

            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            deletedAt.text = "Deleted ${dateFormat.format(Date(item.deletedAt))}"

            // Load thumbnail from trash file
            item.trashPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    try {
                        // Decode with sample size to avoid OOM
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 4
                        }
                        val bitmap = BitmapFactory.decodeFile(path, options)
                        thumbnail.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        thumbnail.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                } else {
                    thumbnail.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } ?: run {
                thumbnail.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            val canRestore = item.canRestore && item.trashPath != null && File(item.trashPath).exists()
            restoreBtn.isEnabled = canRestore
            restoreBtn.alpha = if (canRestore) 1f else 0.5f

            // Click on card/thumbnail to open preview
            itemView.setOnClickListener { onItemClick(item) }
            thumbnail.setOnClickListener { onItemClick(item) }

            restoreBtn.setOnClickListener { onRestoreClick(item) }
            deleteBtn.setOnClickListener { onDeleteClick(item) }
        }
    }
}

