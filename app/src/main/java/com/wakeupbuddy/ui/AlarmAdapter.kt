package com.wakeupbuddy.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wakeupbuddy.databinding.ItemAlarmBinding
import com.wakeupbuddy.model.Alarm
import com.wakeupbuddy.util.TimeUtils

class AlarmAdapter(
    private var items: List<Alarm>,
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onClick: (Alarm) -> Unit,
    private val onDelete: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.VH>() {

    fun submit(list: List<Alarm>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemAlarmBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val a = items[position]
        h.b.timeText.text = TimeUtils.formatTime(a.hour, a.minute)
        h.b.daysText.text = TimeUtils.scheduleLabel(a)
        if (a.label.isBlank()) {
            h.b.labelText.visibility = View.GONE
        } else {
            h.b.labelText.visibility = View.VISIBLE
            h.b.labelText.text = a.label
        }
        val alpha = if (a.enabled) 1f else 0.4f
        h.b.timeText.alpha = alpha
        h.b.daysText.alpha = alpha
        h.b.labelText.alpha = alpha

        h.b.enableSwitch.setOnCheckedChangeListener(null)
        h.b.enableSwitch.isChecked = a.enabled
        h.b.enableSwitch.setOnCheckedChangeListener { _, checked -> onToggle(a, checked) }

        h.b.root.setOnClickListener { onClick(a) }
        h.b.deleteButton.setOnClickListener { onDelete(a) }
    }
}
