package com.wakeupbuddy.ui

import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.wakeupbuddy.R
import com.wakeupbuddy.alarm.AlarmScheduler
import com.wakeupbuddy.data.AlarmStore
import com.wakeupbuddy.databinding.ActivityAlarmEditBinding
import com.wakeupbuddy.model.Alarm
import com.wakeupbuddy.util.TimeUtils
import java.util.Calendar

class AlarmEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmEditBinding
    private var editingId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        editingId = intent.getLongExtra(EXTRA_ID, -1L)
        val existing = if (editingId != -1L) AlarmStore.get(this, editingId) else null

        val now = Calendar.getInstance()
        binding.timePicker.hour = existing?.hour ?: now.get(Calendar.HOUR_OF_DAY)
        binding.timePicker.minute = existing?.minute ?: 0
        binding.labelInput.setText(existing?.label ?: "")

        val days = existing?.days ?: emptySet()
        chipPairs().forEach { (chip, day) -> chip.isChecked = days.contains(day) }

        binding.saveButton.setOnClickListener { save() }
    }

    private fun chipPairs(): List<Pair<Chip, Int>> = listOf(
        binding.chipSun to Calendar.SUNDAY,
        binding.chipMon to Calendar.MONDAY,
        binding.chipTue to Calendar.TUESDAY,
        binding.chipWed to Calendar.WEDNESDAY,
        binding.chipThu to Calendar.THURSDAY,
        binding.chipFri to Calendar.FRIDAY,
        binding.chipSat to Calendar.SATURDAY
    )

    private fun save() {
        val hour = binding.timePicker.hour
        val minute = binding.timePicker.minute
        val days = chipPairs().filter { it.first.isChecked }.map { it.second }.toSet()
        val label = binding.labelInput.text?.toString()?.trim().orEmpty()
        val id = if (editingId != -1L) editingId else System.currentTimeMillis()

        val alarm = Alarm(id, hour, minute, label, enabled = true, days = days)
        AlarmStore.upsert(this, alarm)
        AlarmScheduler.schedule(this, alarm)

        val next = TimeUtils.nextTriggerMillis(alarm)
        val whenTxt = DateFormat.format("EEE, d MMM • h:mm a", next)
        Toast.makeText(this, getString(R.string.alarm_saved) + " • " + whenTxt, Toast.LENGTH_LONG).show()

        if (!AlarmScheduler.canScheduleExact(this)) {
            Toast.makeText(this, R.string.perm_exact_alarm, Toast.LENGTH_LONG).show()
        }
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        const val EXTRA_ID = "extra_id"
    }
}
