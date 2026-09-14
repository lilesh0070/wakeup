package com.wakeupbuddy.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.wakeupbuddy.BuildConfig
import com.wakeupbuddy.Config
import com.wakeupbuddy.QrUtil
import com.wakeupbuddy.R
import com.wakeupbuddy.Updater
import com.wakeupbuddy.alarm.AlarmScheduler
import com.wakeupbuddy.data.AlarmStore
import com.wakeupbuddy.databinding.ActivityMainBinding
import com.wakeupbuddy.model.Alarm
import com.wakeupbuddy.photo.PhotoCleanup
import com.wakeupbuddy.util.TimeUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AlarmAdapter
    private var permissionDialogShown = false

    private val dateFmt = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            updateHeader()
            clockHandler.postDelayed(this, 1000)
        }
    }

    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge so the gradient fills behind the status bar / notch
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerContent) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        // Toolbar: left = Update, right = Proof Photos (menu)
        binding.toolbar.setNavigationOnClickListener { showUpdateDialog() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_share -> { showQrDialog(); true }
                R.id.action_photos -> {
                    startActivity(Intent(this, GalleryActivity::class.java)); true
                }
                else -> false
            }
        }

        adapter = AlarmAdapter(
            emptyList(),
            onToggle = { alarm, checked ->
                AlarmStore.setEnabled(this, alarm.id, checked)
                AlarmStore.get(this, alarm.id)?.let {
                    if (checked) AlarmScheduler.schedule(this, it)
                    else AlarmScheduler.cancel(this, it)
                }
                refresh()
            },
            onClick = { alarm -> openEdit(alarm.id) },
            onDelete = { alarm -> confirmDelete(alarm) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener { openEdit(null) }
        binding.emptyAddButton.setOnClickListener { openEdit(null) }

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        PhotoCleanup.runNow(this)
        maybeShowPermissionDialog()
        clockHandler.post(clockTick)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTick)
    }

    // ---- Live header ----
    private fun updateHeader() {
        val now = Calendar.getInstance()
        val h = now.get(Calendar.HOUR_OF_DAY)
        val m = now.get(Calendar.MINUTE)
        val s = now.get(Calendar.SECOND)
        var hr = h % 12
        if (hr == 0) hr = 12

        binding.clockText.text = String.format(Locale.getDefault(), "%d:%02d", hr, m)
        binding.secondsText.text = String.format(Locale.getDefault(), ":%02d", s)
        binding.ampmText.text = if (h < 12) "AM" else "PM"
        binding.dateText.text = dateFmt.format(Date())
        binding.greetingText.setText(greetingRes(h))

        val enabled = AlarmStore.getAll(this).filter { it.enabled }
        if (enabled.isEmpty()) {
            binding.nextAlarmText.text = getString(R.string.no_active_alarm)
        } else {
            val nowMs = System.currentTimeMillis()
            val next = enabled.minByOrNull { TimeUtils.nextTriggerMillis(it, nowMs) }!!
            val trigger = TimeUtils.nextTriggerMillis(next, nowMs)
            binding.nextAlarmText.text = "⏰ " + TimeUtils.formatTime(next.hour, next.minute) +
                "  •  in " + TimeUtils.humanDuration(trigger - nowMs)
        }
    }

    private fun greetingRes(hour: Int): Int = when (hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..16 -> R.string.greeting_afternoon
        in 17..20 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }

    private fun refresh() {
        val list = AlarmStore.getAll(this)
        adapter.submit(list)
        val empty = list.isEmpty()
        binding.emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        binding.sectionLabel.visibility = if (empty) View.GONE else View.VISIBLE
        updateHeader()
    }

    private fun openEdit(id: Long?) {
        val i = Intent(this, AlarmEditActivity::class.java)
        if (id != null) i.putExtra(AlarmEditActivity.EXTRA_ID, id)
        editLauncher.launch(i)
    }

    private fun confirmDelete(alarm: Alarm) {
        AlertDialog.Builder(this)
            .setTitle(TimeUtils.formatTime(alarm.hour, alarm.minute))
            .setMessage(R.string.delete_this_alarm)
            .setPositiveButton(R.string.delete) { _, _ ->
                AlarmScheduler.cancel(this, alarm)
                AlarmStore.delete(this, alarm.id)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Update: download + install INSIDE the app (no browser). Net used only here. ----
    private fun showUpdateDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_title)
            .setMessage(getString(R.string.update_body) + "\n\nCurrent version: " + BuildConfig.VERSION_NAME)
            .setPositiveButton(R.string.update_now) { _, _ -> startInAppUpdate() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun startInAppUpdate() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_update, null)
        val status = v.findViewById<TextView>(R.id.update_status)
        val bar = v.findViewById<ProgressBar>(R.id.update_progress)
        val dlg = AlertDialog.Builder(this).setView(v).setCancelable(false).create()
        dlg.show()

        Updater.run(this, BuildConfig.VERSION_NAME, object : Updater.Callback {
            override fun onStatus(msg: String) { status.text = msg }
            override fun onProgress(percent: Int) {
                if (percent < 0) {
                    bar.isIndeterminate = true
                } else {
                    bar.isIndeterminate = false
                    bar.progress = percent
                    status.text = getString(R.string.download_update) + "  " + percent + "%"
                }
            }
            override fun onUpToDate(latest: String) {
                dlg.dismiss()
                Toast.makeText(this@MainActivity,
                    getString(R.string.up_to_date) + " (" + latest + ")", Toast.LENGTH_LONG).show()
            }
            override fun onReadyToInstall(file: File) {
                dlg.dismiss()
                Updater.install(this@MainActivity, file)
            }
            override fun onError(msg: String) {
                dlg.dismiss()
                showUpdateErrorDialog(msg)
            }
        })
    }

    private fun showUpdateErrorDialog(msg: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_failed)
            .setMessage(msg + "\n\nYou can also download it in your browser.")
            .setPositiveButton(R.string.open_in_browser) { _, _ -> openUrl(Config.UPDATE_DOWNLOAD_URL) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showQrDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_qr, null)
        val url = Config.UPDATE_DOWNLOAD_URL
        try {
            view.findViewById<ImageView>(R.id.qr_image).setImageBitmap(QrUtil.make(url))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not generate QR", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<TextView>(R.id.qr_url).text = url

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(R.string.share_link) { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Download WakeUp Buddy: $url")
                }
                startActivity(Intent.createChooser(send, getString(R.string.share_link)))
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun maybeShowPermissionDialog() {
        if (permissionDialogShown) return
        val needExact = !AlarmScheduler.canScheduleExact(this)
        val needOverlay = !Settings.canDrawOverlays(this)
        if (!needExact && !needOverlay) return
        permissionDialogShown = true

        val sb = StringBuilder()
        if (needExact) sb.append("• ").append(getString(R.string.perm_exact_alarm)).append("\n")
        if (needOverlay) sb.append("• ").append(getString(R.string.perm_overlay)).append("\n")
        sb.append("• ").append(getString(R.string.perm_battery))

        val b = AlertDialog.Builder(this)
            .setTitle(R.string.perm_title)
            .setMessage(sb.toString())
            .setNegativeButton(R.string.not_now, null)
        if (needExact) b.setPositiveButton("Alarm") { _, _ -> openExactAlarmSettings() }
        if (needOverlay) b.setNeutralButton("Overlay") { _, _ -> openOverlaySettings() }
        b.show()
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                openAppSettings()
            }
        }
    }

    private fun openOverlaySettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }
}
