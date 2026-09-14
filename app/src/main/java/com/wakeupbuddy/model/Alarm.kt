package com.wakeupbuddy.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * @param days java.util.Calendar day-of-week values (1=Sunday .. 7=Saturday).
 *             Empty set means a one-time alarm.
 */
data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    val days: Set<Int> = emptySet()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("enabled", enabled)
        put("days", JSONArray(days.toList()))
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
                days = days
            )
        }
    }
}
