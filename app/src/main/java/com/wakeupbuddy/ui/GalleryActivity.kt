package com.wakeupbuddy.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.wakeupbuddy.R
import com.wakeupbuddy.databinding.ActivityGalleryBinding
import com.wakeupbuddy.photo.PhotoStore
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var adapter: PhotoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.gallery_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete_all -> {
                    confirmDeleteAll(); true
                }
                else -> false
            }
        }

        adapter = PhotoAdapter(emptyList()) { file -> confirmDeleteOne(file) }
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        PhotoStore.deleteExpired(this)
        reload()
    }

    private fun reload() {
        val files = PhotoStore.list(this)
        adapter.submit(files)
        binding.emptyView.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDeleteOne(file: File) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_photo_msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                PhotoStore.delete(file)
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteAll() {
        if (PhotoStore.list(this).isEmpty()) return
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_all_msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                PhotoStore.deleteAll(this)
                reload()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
