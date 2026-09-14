package com.wakeupbuddy.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.wakeupbuddy.R
import com.wakeupbuddy.ui.AlarmActivity

/**
 * Foreground service that keeps the alarm ringing LOUD and non-stop until the
 * AlarmActivity confirms a valid selfie. Forces STREAM_ALARM to max repeatedly
 * so volume-down cannot silence it, holds a wake lock, and shows a full-screen
 * intent so the alarm screen appears even over the lock screen.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var savedAlarmVolume: Int = -1

    private val handler = Handler(Looper.getMainLooper())
    private val volumeEnforcer = object : Runnable {
        override fun run() {
            forceAlarmVolumeMax()
            handler.postDelayed(this, 700)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        val id = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: ""

        startForeground(NOTIF_ID, buildNotification(label, id))
        acquireWakeLock()
        startRinging()
        launchAlarmScreen(id, label)
        return START_REDELIVER_INTENT
    }

    private fun buildNotification(label: String, id: Long): Notification {
        val fsIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, id)
            putExtra(EXTRA_LABEL, label)
        }
        val pi = PendingIntent.getActivity(
            this, 1001, fsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (label.isBlank()) getString(R.string.wake_title) else label
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(getString(R.string.wake_sub))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .build()
    }

    private fun launchAlarmScreen(id: Long, label: String) {
        val i = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, id)
            putExtra(EXTRA_LABEL, label)
        }
        try {
            startActivity(i)
        } catch (e: Exception) {
            // If a background activity start is blocked, the full-screen notification handles it.
        }
    }

    private fun startRinging() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager = am
        savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        forceAlarmVolumeMax()
        handler.post(volumeEnforcer)

        var uri: Uri? =
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
        if (uri == null)
            uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
        if (uri == null)
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        try {
            player = MediaPlayer().apply {
                setDataSource(this@AlarmService, uri!!)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setVolume(1f, 1f)
                prepare()
                start()
            }
        } catch (e: Exception) {
            player = null
        }
        startVibration()
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 600, 500, 600, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun forceAlarmVolumeMax() {
        val am = audioManager ?: return
        try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        } catch (e: Exception) {
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UthoAlarm::Ring").apply {
            acquire(10 * 60 * 1000L)
        }
    }

    private fun stopEverything() {
        handler.removeCallbacks(volumeEnforcer)
        try { player?.stop() } catch (e: Exception) {}
        try { player?.release() } catch (e: Exception) {}
        player = null
        vibrator?.cancel()
        if (savedAlarmVolume >= 0) {
            try {
                audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0)
            } catch (e: Exception) {}
        }
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(volumeEnforcer)
        try { player?.release() } catch (e: Exception) {}
        vibrator?.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    companion object {
        const val CHANNEL_ID = "utho_alarm_channel"
        const val NOTIF_ID = 42
        const val EXTRA_LABEL = "label"
        const val ACTION_STOP = "com.wakeupbuddy.STOP"

        fun stop(ctx: Context) {
            try {
                ctx.startService(
                    Intent(ctx, AlarmService::class.java).apply { action = ACTION_STOP }
                )
            } catch (e: Exception) {
            }
        }
    }
}
