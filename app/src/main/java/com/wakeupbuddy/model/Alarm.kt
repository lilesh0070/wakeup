package com.wakeupbuddy.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * @param days java.util.Calendar day-of-week values (1=Sunday .. 7=Saturday).
 *             Empty set means a one-time alarm.
 * @param dateY/dateM/dateD an optional specific calendar date for a one-time alarm
 *        (dateM is 0-based like Calendar.MONTH). dateY == 0 means "no specific date".
 */
data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    val days: Set<Int> = emptySet(),
    val dateY: Int = 0,
    val dateM: Int = 0,
    val dateD: Int = 0
) {
    val hasDate: Boolean get() = dateY != 0

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("enabled", enabled)
        put("days", JSONArray(days.toList()))
        put("dateY", dateY)
        put("dateM", dateM)
        put("dateD", dateD)
    }

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val arr = o.optJSONArray("days") ?: JSONArray()
            val days = mutableSetOf<Int>()
            for (i in 0 until arr.length()) days.add(arr.getInt(i))
            return Alarm(
                id = o.getLong("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                label = o.optString("label", ""),
                enabled = o.optBoolean("enabled", true),
                days = days,
                dateY = o.optInt("dateY", 0),
                dateM = o.optInt("dateM", 0),
                dateD = o.optInt("dateD", 0)
            )
        }
    }
}
