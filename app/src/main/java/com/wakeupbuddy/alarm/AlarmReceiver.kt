package com.wakeupbuddy.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.wakeupbuddy.data.AlarmStore

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (id == -1L) return
        val alarm = AlarmStore.get(context, id)

        // 1) Start the ringing foreground service
        val svc = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, id)
            putExtra(AlarmService.EXTRA_LABEL, alarm?.label ?: "")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }

        // 2) Repeating alarm -> schedule next; one-time -> disable it
        if (alarm != null) {
            if (alarm.days.isEmpty()) {
                AlarmStore.setEnabled(context, id, false)
            } else {
                AlarmScheduler.schedule(context, alarm)
            }
        }
    }
}
