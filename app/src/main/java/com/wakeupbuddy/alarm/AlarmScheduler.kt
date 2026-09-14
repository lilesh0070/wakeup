package com.wakeupbuddy.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.wakeupbuddy.data.AlarmStore
import com.wakeupbuddy.model.Alarm
import com.wakeupbuddy.ui.MainActivity
import com.wakeupbuddy.util.TimeUtils

object AlarmScheduler {

    const val EXTRA_ALARM_ID = "alarm_id"
    private const val ACTION_FIRE = "com.wakeupbuddy.FIRE"

    private fun alarmManager(ctx: Context) =
        ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(ctx: Context, alarm: Alarm): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ALARM_ID, alarm.id)
        }
        return PendingIntent.getBroadcast(
            ctx, alarm.id.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canScheduleExact(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            alarmManager(ctx).canScheduleExactAlarms()
        else true

    fun schedule(ctx: Context, alarm: Alarm) {
        if (!alarm.enabled) {
            cancel(ctx, alarm)
            return
        }
        val am = alarmManager(ctx)
        val triggerAt = TimeUtils.nextTriggerMillis(alarm)
        val pi = pendingIntent(ctx, alarm)
        val showIntent = PendingIntent.getActivity(
            ctx, alarm.id.toInt(),
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (canScheduleExact(ctx)) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(ctx: Context, alarm: Alarm) {
        alarmManager(ctx).cancel(pendingIntent(ctx, alarm))
    }

    fun rescheduleAll(ctx: Context) {
        AlarmStore.getAll(ctx).forEach { schedule(ctx, it) }
    }
}
