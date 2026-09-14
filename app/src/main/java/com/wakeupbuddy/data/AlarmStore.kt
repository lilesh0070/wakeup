package com.wakeupbuddy.data

import android.content.Context
import com.wakeupbuddy.model.Alarm
import org.json.JSONArray

/** Simple offline persistence for alarms using SharedPreferences + JSON. */
object AlarmStore {
    private const val PREFS = "utho_alarms"
    private const val KEY_LIST = "list"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAll(ctx: Context): List<Alarm> {
        val raw = prefs(ctx).getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length())
                .map { Alarm.fromJson(arr.getJSONObject(it)) }
                .sortedWith(compareBy({ it.hour }, { it.minute }))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(ctx: Context, id: Long): Alarm? = getAll(ctx).firstOrNull { it.id == id }

    fun saveAll(ctx: Context, list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun upsert(ctx: Context, alarm: Alarm): List<Alarm> {
        val list = getAll(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == alarm.id }
        if (idx >= 0) list[idx] = alarm else list.add(alarm)
        saveAll(ctx, list)
        return list
    }

    fun delete(ctx: Context, id: Long): List<Alarm> {
        val list = getAll(ctx).filterNot { it.id == id }
        saveAll(ctx, list)
        return list
    }

    fun setEnabled(ctx: Context, id: Long, enabled: Boolean): Alarm? {
        val a = get(ctx, id) ?: return null
        val updated = a.copy(enabled = enabled)
        upsert(ctx, updated)
        return updated
    }
}
