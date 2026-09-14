package com.wakeupbuddy.ui

import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.wakeupbuddy.R
import com.wakeupbuddy.alarm.AlarmScheduler
import com.wakeupbuddy.data.AlarmStore
import com.wakeupbuddy.databinding.ActivityAlarmEditBinding
import com.wakeupbuddy.model.Alarm
import com.wakeupbuddy.util.TimeUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class AlarmEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmEditBinding
    private var editingId: Long = -1L

    private var hour = 7
    private var minute = 30
    private var dateY = 0
    private var dateM = 0
    private var dateD = 0

    private val dateLabelFmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        editingId = intent.getLongExtra(EXTRA_ID, -1L)
        val existing = if (editingId != -1L) AlarmStore.get(this, editingId) else null

        val now = Calendar.getInstance()
        hour = existing?.hour ?: now.get(Calendar.HOUR_OF_DAY)
        minute = existing?.minute ?: 0
        dateY = existing?.dateY ?: 0
        dateM = existing?.dateM ?: 0
        dateD = existing?.dateD ?: 0
        binding.labelInput.setText(existing?.label ?: "")

        val days = existing?.days ?: emptySet()
        chipPairs().forEach { (chip, day) ->
            chip.isChecked = days.contains(day)
            chip.setOnCheckedChangeListener { _, isChecked ->
                // Weekly repeat and a specific date are mutually exclusive
                if (isChecked && dateY != 0) clearDate()
            }
        }

        binding.timeCard.setOnClickListener { openTimePicker() }
        binding.dateCard.setOnClickListener { openDatePicker() }
        binding.clearDate.setOnClickListener { clearDate() }
        binding.saveButton.setOnClickListener { save() }

        updateTimeDisplay()
        updateDateDisplay()
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

    private fun openTimePicker() {
        val fmt = if (DateFormat.is24HourFormat(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(fmt)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText("Set alarm time")
            .build()
        picker.addOnPositiveButtonClickListener {
            hour = picker.hour
            minute = picker.minute
            updateTimeDisplay()
        }
        picker.show(supportFragmentManager, "time")
    }

    private fun openDatePicker() {
        val startSelection = if (dateY != 0) {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            utc.clear()
            utc.set(dateY, dateM, dateD)
            utc.timeInMillis
        } else {
            MaterialDatePicker.todayInUtcMilliseconds()
        }
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.now())
            .build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Ring on date")
            .setSelection(startSelection)
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { selection -> onDatePicked(selection) }
        picker.show(supportFragmentManager, "date")
    }

    private fun onDatePicked(selectionUtc: Long) {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.timeInMillis = selectionUtc
        dateY = utc.get(Calendar.YEAR)
        dateM = utc.get(Calendar.MONTH)
        dateD = utc.get(Calendar.DAY_OF_MONTH)
        // A specific date is one-time -> clear weekly repeat
        chipPairs().forEach { it.first.isChecked = false }
        updateDateDisplay()
    }

    private fun clearDate() {
        dateY = 0; dateM = 0; dateD = 0
        updateDateDisplay()
    }

    private fun updateTimeDisplay() {
        binding.timeDisplay.text = TimeUtils.formatTime(hour, minute)
    }

    private fun updateDateDisplay() {
        if (dateY != 0) {
            val c = Calendar.getInstance()
            c.set(dateY, dateM, dateD)
            binding.dateDisplay.text = dateLabelFmt.format(c.time)
            binding.clearDate.visibility = View.VISIBLE
        } else {
            binding.dateDisplay.text = "Not set — tap to pick a date"
            binding.clearDate.visibility = View.GONE
        }
    }

    private fun save() {
        val label = binding.labelInput.text?.toString()?.trim().orEmpty()
        val id = if (editingId != -1L) editingId else System.currentTimeMillis()
        val days = if (dateY != 0) emptySet() else
            chipPairs().filter { it.first.isChecked }.map { it.second }.toSet()

        val alarm = Alarm(
            id = id, hour = hour, minute = minute, label = label,
            enabled = true, days = days, dateY = dateY, dateM = dateM, dateD = dateD
        )
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
