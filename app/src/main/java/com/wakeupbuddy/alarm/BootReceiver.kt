package com.wakeupbuddy.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wakeupbuddy.photo.PhotoCleanup

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                AlarmScheduler.rescheduleAll(context)
                PhotoCleanup.schedulePeriodic(context)
            }
        }
    }
}
