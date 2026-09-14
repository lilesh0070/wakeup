package com.wakeupbuddy.ui

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.wakeupbuddy.R
import com.wakeupbuddy.alarm.AlarmScheduler
import com.wakeupbuddy.alarm.AlarmService
import com.wakeupbuddy.data.AlarmStore
import com.wakeupbuddy.databinding.ActivityAlarmBinding
import com.wakeupbuddy.photo.PhotoCleanup
import com.wakeupbuddy.photo.PhotoStore
import com.wakeupbuddy.util.TimeUtils
import java.io.File
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The alarm screen. Cannot be dismissed with Back or the volume keys. The ONLY
 * way out: hold a live face with BOTH EYES OPEN steady for a moment, then capture
 * a selfie that ML Kit (offline) confirms shows a face with open eyes. A sleepy,
 * closed-eye photo will NOT stop the alarm.
 */
class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private lateinit var audioManager: AudioManager
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var faceDetector: FaceDetector

    private var imageCapture: ImageCapture? = null
    @Volatile private var eyesOpenNow = false
    private var holdStart = 0L
    private var capturing = false
    private var dismissed = false

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            hideCameraPrompt(); startCamera()
        } else {
            showCameraPrompt()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindowForLockScreen()

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeControlStream = AudioManager.STREAM_ALARM

        cameraExecutor = Executors.newSingleThreadExecutor()
        faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.2f)
                .build()
        )

        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val alarm = if (id != -1L) AlarmStore.get(this, id) else null
        val now = Calendar.getInstance()
        binding.clockText.text = if (alarm != null)
            TimeUtils.formatTime(alarm.hour, alarm.minute)
        else
            TimeUtils.formatTime(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))

        val label = intent.getStringExtra(AlarmService.EXTRA_LABEL).orEmpty()
        if (label.isNotBlank()) binding.labelText.text = label

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@AlarmActivity, R.string.wake_sub, Toast.LENGTH_SHORT).show()
            }
        })

        binding.captureButton.setOnClickListener { capture() }
        binding.permissionButton.setOnClickListener {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }

        if (hasCameraPermission()) startCamera() else {
            showCameraPrompt()
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupWindowForLockScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
            .requestDismissKeyguard(this, null)
    }

    // ---- Volume keys consumed + forced to max ----
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_MUTE -> {
            try {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            } catch (e: Exception) {
            }
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    // ---- Camera ----
    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                bindUseCases(future.get())
            } catch (e: Exception) {
                binding.statusText.text = getString(R.string.need_camera)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases(provider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(cameraExecutor, FaceAnalyzer()) }

        provider.unbindAll()
        val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))
            CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            provider.bindToLifecycle(this, selector, preview, imageCapture, analysis)
        } catch (e: Exception) {
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis
            )
        }
    }

    private fun eyesOpen(face: Face): Boolean {
        val l = face.leftEyeOpenProbability ?: -1f
        val r = face.rightEyeOpenProbability ?: -1f
        return l >= EYE_THRESHOLD && r >= EYE_THRESHOLD
    }

    private inner class FaceAnalyzer : ImageAnalysis.Analyzer {
        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val media = imageProxy.image
            if (media == null) {
                imageProxy.close(); return
            }
            val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
            faceDetector.process(input)
                .addOnSuccessListener { faces ->
                    val face = faces.maxByOrNull {
                        it.boundingBox.width() * it.boundingBox.height()
                    }
                    handleFrame(face != null, face != null && eyesOpen(face))
                }
                .addOnCompleteListener { imageProxy.close() }
        }
    }

    private fun handleFrame(faceOk: Boolean, open: Boolean) {
        if (capturing || dismissed) return
        eyesOpenNow = open
        when {
            !faceOk -> {
                holdStart = 0L
                setStatus(R.string.face_searching, R.color.warn)
                binding.captureButton.isEnabled = false
            }
            !open -> {
                holdStart = 0L
                setStatus(R.string.eyes_open_hint, R.color.warn)
                binding.captureButton.isEnabled = false
            }
            else -> {
                if (holdStart == 0L) holdStart = System.currentTimeMillis()
                if (System.currentTimeMillis() - holdStart < HOLD_MS) {
                    setStatus(R.string.hold_steady, R.color.warn)
                    binding.captureButton.isEnabled = false
                } else {
                    setStatus(R.string.ready_capture, R.color.success)
                    binding.captureButton.isEnabled = true
                }
            }
        }
    }

    private fun setStatus(textRes: Int, colorRes: Int) {
        binding.statusText.setText(textRes)
        binding.statusText.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    // ---- Capture + verify (face present AND both eyes open) ----
    private fun capture() {
        val ic = imageCapture ?: return
        if (capturing) return
        capturing = true
        binding.captureButton.isEnabled = false
        setStatus(R.string.verifying, R.color.warn)

        val file = PhotoStore.newPhotoFile(this)
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        ic.takePicture(options, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                verifyFace(file)
            }

            override fun onError(exc: ImageCaptureException) {
                runOnUiThread { failRetry(R.string.face_not_clear) }
            }
        })
    }

    private fun verifyFace(file: File) {
        val input = try {
            InputImage.fromFilePath(this, Uri.fromFile(file))
        } catch (e: Exception) {
            file.delete(); runOnUiThread { failRetry(R.string.face_not_clear) }; return
        }
        faceDetector.process(input)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                when {
                    face == null -> { file.delete(); runOnUiThread { failRetry(R.string.face_not_clear) } }
                    !eyesOpen(face) -> { file.delete(); runOnUiThread { failRetry(R.string.eyes_closed) } }
                    else -> success()
                }
            }
            .addOnFailureListener {
                file.delete(); runOnUiThread { failRetry(R.string.face_not_clear) }
            }
    }

    private fun failRetry(msgRes: Int) {
        capturing = false
        holdStart = 0L
        setStatus(msgRes, R.color.danger)
        binding.captureButton.isEnabled = false
    }

    private fun success() {
        if (dismissed) return
        dismissed = true
        runOnUiThread {
            AlarmService.stop(this)
            PhotoCleanup.runNow(this)
            Toast.makeText(this, R.string.dismissed, Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
        }
    }

    // ---- Camera permission UI ----
    private fun showCameraPrompt() {
        setStatus(R.string.need_camera, R.color.danger)
        binding.captureButton.isEnabled = false
        binding.permissionButton.visibility = View.VISIBLE
    }

    private fun hideCameraPrompt() {
        binding.permissionButton.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        if (::faceDetector.isInitialized) faceDetector.close()
    }

    companion object {
        private const val EYE_THRESHOLD = 0.5f
        private const val HOLD_MS = 1500L
    }
}
