package com.cameraremote.wear

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.cameraremote.wear.databinding.ActivityRemoteBinding
import com.google.android.gms.wearable.DataClient
import com.google.android.material.color.DynamicColors
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RemoteActivity : AppCompatActivity(), MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "RemoteActivity"

        // Defaults
        private const val DEFAULT_HAPTIC_DURATION_MS = 15
        private const val DEFAULT_TIMER_SECONDS = 3

        // Timing
        private const val RECORDING_TIMER_INTERVAL_MS = 500L
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val COUNTDOWN_TICK_MS = 1000L
        private const val MS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60

        // Timer duration cycle (long-press to cycle)
        private val TIMER_DURATION_OPTIONS = intArrayOf(3, 5, 10)

        // SharedPreferences
        private const val PREFS_NAME = "watch_settings"
        private const val KEY_HAPTIC_DURATION = "haptic_duration_ms"
        private const val KEY_TIMER_SECONDS = "default_timer_seconds"
        private const val KEY_VIBRATE_COUNTDOWN = "vibrate_on_countdown"

        // Message/Data paths
        private const val PATH_STATUS = "/camera_remote/status"
        private const val PATH_SETTINGS = "/camera_remote/settings"
    }

    private lateinit var binding: ActivityRemoteBinding
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + scopeJob)
    private var vibrator: Vibrator? = null
    private var messageClient: MessageClient? = null
    private var timerSeconds = DEFAULT_TIMER_SECONDS
    private var countdownTimer: CountDownTimer? = null
    private var isCountdownActive = false
    private var hapticDurationMs = DEFAULT_HAPTIC_DURATION_MS.toLong()
    private var vibrateOnCountdown = true
    private var captureCount = 0
    private var isRecording = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var recordingStartTime = 0L
    private val recordingTimerHandler = Handler(Looper.getMainLooper())
    private val recordingTimerRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                val elapsed = (System.currentTimeMillis() - recordingStartTime) / MS_PER_SECOND
                val mins = elapsed / SECONDS_PER_MINUTE
                val secs = elapsed % SECONDS_PER_MINUTE
                binding.tvStatus.text = "\u25CF REC ${String.format("%02d:%02d", mins, secs)}"
                recordingTimerHandler.postDelayed(this, RECORDING_TIMER_INTERVAL_MS)
            }
        }
    }
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            checkConnection()
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        binding = ActivityRemoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply Material You dynamic color to background only
        applyDynamicBackground()

        vibrator = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibrator not available", e)
            null
        }

        messageClient = try {
            Wearable.getMessageClient(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MessageClient", e)
            null
        }
        Log.d(TAG, "MessageClient initialized: ${messageClient != null}")

        wakeLock = try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CameraRemote::WakeLock")
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock not available", e)
            null
        }

        // Keep screen on while app is open
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        loadSyncedSettings()
        setupButtons()
        Log.d(TAG, "onCreate completed")
    }

    private fun loadSyncedSettings() {
        // Load from local cache first
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        hapticDurationMs = prefs.getInt(KEY_HAPTIC_DURATION, DEFAULT_HAPTIC_DURATION_MS).toLong()
        timerSeconds = prefs.getInt(KEY_TIMER_SECONDS, DEFAULT_TIMER_SECONDS)
        vibrateOnCountdown = prefs.getBoolean(KEY_VIBRATE_COUNTDOWN, true)

        // Then try to load latest from DataClient
        try {
            Wearable.getDataClient(this).getDataItems()
                .addOnSuccessListener { dataItems ->
                    for (item in dataItems) {
                        if (item.uri.path == PATH_SETTINGS) {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            hapticDurationMs = dataMap.getInt(KEY_HAPTIC_DURATION, DEFAULT_HAPTIC_DURATION_MS).toLong()
                            timerSeconds = dataMap.getInt(KEY_TIMER_SECONDS, DEFAULT_TIMER_SECONDS)
                            vibrateOnCountdown = dataMap.getBoolean(KEY_VIBRATE_COUNTDOWN, true)
                            saveSettingsLocally()
                            Log.d(TAG, "Loaded settings: haptic=${hapticDurationMs}ms, timer=${timerSeconds}s, vibrateCountdown=$vibrateOnCountdown")
                        }
                    }
                    dataItems.release()
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load synced settings", e)
        }
    }

    private fun saveSettingsLocally() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putInt(KEY_HAPTIC_DURATION, hapticDurationMs.toInt())
            .putInt(KEY_TIMER_SECONDS, timerSeconds)
            .putBoolean(KEY_VIBRATE_COUNTDOWN, vibrateOnCountdown)
            .apply()
    }

    private fun setupButtons() {
        // Shutter: tap to capture
        binding.btnCapture.setOnClickListener {
            vibrate()
            Log.d(TAG, "Shutter clicked: capture")
            sendCommand("capture")
        }

        // Flip / switch camera
        binding.btnSwitch.setOnClickListener {
            vibrate()
            sendCommand("switch_camera")
        }

        // Scroll the mode row (Pro | Video | Photo | Fastshot | Portrait)
        binding.btnScrollLeft.setOnClickListener {
            vibrate()
            sendCommand("scroll_mode_right")
        }
        binding.btnScrollRight.setOnClickListener {
            vibrate()
            sendCommand("scroll_mode_left")
        }

        // Timer: tap to start/cancel countdown, long-press to change duration
        binding.btnTimer.setOnClickListener {
            vibrate()
            if (isCountdownActive) {
                Log.d(TAG, "Timer clicked: cancelling countdown")
                cancelCountdown()
            } else {
                Log.d(TAG, "Timer clicked: starting ${timerSeconds}s countdown")
                startCountdown()
            }
        }
        binding.btnTimer.setOnLongClickListener {
            timerSeconds = when (timerSeconds) {
                3 -> 5
                5 -> 10
                else -> 3
            }
            vibrate()
            true
        }
    }

    private fun startCountdown() {
        countdownTimer?.cancel()
        isCountdownActive = true
        binding.tvStatus.text = "$timerSeconds"
        countdownTimer = object : CountDownTimer(timerSeconds * COUNTDOWN_TICK_MS, COUNTDOWN_TICK_MS) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / MS_PER_SECOND).toInt() + 1
                runOnUiThread {
                    binding.tvStatus.text = "$secondsLeft"
                }
                if (vibrateOnCountdown) vibrate()
            }
            override fun onFinish() {
                isCountdownActive = false
                runOnUiThread {
                    binding.tvStatus.text = ""
                }
                vibrate()
                sendCommand("capture")
            }
        }.start()
    }

    private fun cancelCountdown() {
        countdownTimer?.cancel()
        countdownTimer = null
        isCountdownActive = false
        binding.tvStatus.text = ""
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        try {
            wakeLock?.acquire()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock", e)
        }
        try {
            messageClient?.addListener(this)
            Wearable.getDataClient(this).addListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add listeners", e)
        }
        checkConnection()
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wake lock", e)
        }
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        try {
            messageClient?.removeListener(this)
            Wearable.getDataClient(this).removeListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove listeners", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        recordingTimerHandler.removeCallbacks(recordingTimerRunnable)
        countdownTimer?.cancel()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wake lock", e)
        }
        scopeJob.cancel()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            vibrate()
            sendCommand("capture")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun checkConnection() {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@RemoteActivity).connectedNodes.await()
                Log.d(TAG, "Connected nodes: ${nodes.size}")
                runOnUiThread {
                    if (nodes.isNotEmpty()) {
                        binding.tvConnection.setBackgroundResource(R.drawable.bg_status_active)
                        binding.tvConnection.text = "Connected to ${nodes.first().displayName}"
                    } else {
                        binding.tvConnection.setBackgroundResource(R.drawable.bg_status_inactive)
                        binding.tvConnection.text = "No phone connected"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check connection", e)
                runOnUiThread {
                    binding.tvConnection.setBackgroundResource(R.drawable.bg_status_inactive)
                    binding.tvConnection.text = "Connection error"
                }
            }
        }
    }

    private fun sendCommand(command: String) {
        Log.d(TAG, "sendCommand: $command")

        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@RemoteActivity).connectedNodes.await()
                Log.d(TAG, "Sending '$command' to ${nodes.size} node(s)")
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No connected nodes for command: $command")
                    return@launch
                }
                val mc = messageClient ?: run {
                    Log.e(TAG, "MessageClient is null")
                    return@launch
                }
                for (node in nodes) {
                    mc.sendMessage(node.id, "/camera_remote", command.toByteArray()).await()
                    Log.d(TAG, "Command '$command' sent to ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command: $command", e)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == PATH_STATUS) {
            val status = String(messageEvent.data)
            Log.d(TAG, "Status received from phone: $status")
            if (status == "captured") captureCount++

            // Handle recording state \u2014 the REC timer is functional, not just
            // informational, so it's the one status text that stays visible.
            if (status == "recording_started") {
                isRecording = true
                recordingStartTime = System.currentTimeMillis()
                runOnUiThread {
                    binding.tvStatus.text = "\u25CF REC 00:00"
                    recordingTimerHandler.removeCallbacks(recordingTimerRunnable)
                    recordingTimerHandler.postDelayed(recordingTimerRunnable, RECORDING_TIMER_INTERVAL_MS)
                    vibrate()
                }
                return
            }
            if (status == "recording_stopped") {
                isRecording = false
                recordingTimerHandler.removeCallbacks(recordingTimerRunnable)
                runOnUiThread {
                    binding.tvStatus.text = ""
                    vibrate()
                }
                return
            }

            if (isRecording && status != "recording_started") {
                isRecording = false
                recordingTimerHandler.removeCallbacks(recordingTimerRunnable)
            }

            vibrate()
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                when (event.dataItem.uri.path) {
                    PATH_SETTINGS -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        hapticDurationMs = dataMap.getInt(KEY_HAPTIC_DURATION, DEFAULT_HAPTIC_DURATION_MS).toLong()
                        timerSeconds = dataMap.getInt(KEY_TIMER_SECONDS, DEFAULT_TIMER_SECONDS)
                        vibrateOnCountdown = dataMap.getBoolean(KEY_VIBRATE_COUNTDOWN, true)
                        saveSettingsLocally()
                        Log.d(TAG, "Settings updated: haptic=${hapticDurationMs}ms, timer=${timerSeconds}s, vibrateCountdown=$vibrateOnCountdown")
                    }
                }
            }
        }
    }

    private fun applyDynamicBackground() {
        try {
            val typedArray = obtainStyledAttributes(intArrayOf(
                com.google.android.material.R.attr.colorSurface
            ))
            val surfaceColor = typedArray.getColor(0, Color.BLACK)
            typedArray.recycle()
            binding.root.setBackgroundColor(surfaceColor)
            Log.d(TAG, "Applied Material You dynamic background")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply dynamic background, using black", e)
            binding.root.setBackgroundColor(Color.BLACK)
        }
    }

    private fun vibrate() {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(hapticDurationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }
}
