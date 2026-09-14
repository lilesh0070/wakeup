package com.wakeupbuddy.util

import com.wakeupbuddy.model.Alarm
import java.util.Calendar

object TimeUtils {

    /** Next trigger time in millis for this alarm relative to [now]. */
    fun nextTriggerMillis(alarm: Alarm, now: Long = System.currentTimeMillis()): Long {
        val base = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (alarm.days.isEmpty()) {
            val c = base.clone() as Calendar
            c.set(Calendar.HOUR_OF_DAY, alarm.hour)
            c.set(Calendar.MINUTE, alarm.minute)
            if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
            return c.timeInMillis
        }
        for (offset in 0..7) {
            val c = base.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, offset)
            c.set(Calendar.HOUR_OF_DAY, alarm.hour)
            c.set(Calendar.MINUTE, alarm.minute)
            val dow = c.get(Calendar.DAY_OF_WEEK)
            if (alarm.days.contains(dow) && c.timeInMillis > now) return c.timeInMillis
        }
        val c = base.clone() as Calendar
        c.add(Calendar.DAY_OF_YEAR, 1)
        c.set(Calendar.HOUR_OF_DAY, alarm.hour)
        c.set(Calendar.MINUTE, alarm.minute)
        return c.timeInMillis
    }

    fun formatTime(hour: Int, minute: Int): String {
        var h = hour % 12
        if (h == 0) h = 12
        val suffix = if (hour < 12) "AM" else "PM"
        return String.format("%d:%02d %s", h, minute, suffix)
    }

    private val dayNames = mapOf(
        Calendar.SUNDAY to "Sun",
        Calendar.MONDAY to "Mon",
        Calendar.TUESDAY to "Tue",
        Calendar.WEDNESDAY to "Wed",
        Calendar.THURSDAY to "Thu",
        Calendar.FRIDAY to "Fri",
        Calendar.SATURDAY to "Sat"
    )

    fun daysLabel(days: Set<Int>): String {
        if (days.isEmpty()) return "Once"
        if (days.size == 7) return "Every day"
        val weekdays = setOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY
        )
        if (days == weekdays) return "Mon–Fri"
        val order = listOf(
            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        )
        return order.filter { days.contains(it) }.joinToString(", ") { dayNames[it] ?: "" }
    }

    /** Human-readable "2h 15m" / "45m" text. */
    fun humanDuration(ms: Long): String {
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
