package com.wakeupbuddy

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import com.wakeupbuddy.alarm.AlarmService
import com.wakeupbuddy.photo.PhotoCleanup

class AlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createAlarmChannel()
        PhotoCleanup.schedulePeriodic(this)
    }

    private fun createAlarmChannel() {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            AlarmService.CHANNEL_ID,
            "Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "WakeUp Buddy alarm"
            setSound(null, null) // sound is driven by the service's MediaPlayer instead
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        mgr.createNotificationChannel(ch)
    }
}
