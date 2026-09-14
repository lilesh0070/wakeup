package com.wakeupbuddy.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wakeupbuddy.databinding.ItemPhotoBinding
import com.wakeupbuddy.photo.PhotoStore
import com.wakeupbuddy.util.TimeUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoAdapter(
    private var files: List<File>,
    private val onDelete: (File) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.VH>() {

    private val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    fun submit(list: List<File>) {
        files = list
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemPhotoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = files.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val f = files[position]
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        h.b.imageView.setImageBitmap(BitmapFactory.decodeFile(f.absolutePath, opts))
        h.b.timeText.text = fmt.format(Date(f.lastModified()))
        h.b.expiryText.text = "Deletes in " + TimeUtils.humanDuration(PhotoStore.expiresInMs(f))
        h.b.deleteButton.setOnClickListener { onDelete(f) }
    }
}
