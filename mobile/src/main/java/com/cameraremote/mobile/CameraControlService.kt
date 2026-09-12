package com.cameraremote.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable

class CameraControlService : AccessibilityService() {

    companion object {
        const val TAG = "CameraControlService"
        private const val NOTIFICATION_CHANNEL_ID = "camera_remote_service"
        private const val NOTIFICATION_ID = 1001
        var instance: CameraControlService? = null
            private set
        val isRunning: Boolean get() = instance != null

        // Message/Data paths
        private const val PATH_STATUS = "/camera_remote/status"

        // Timing constants
        private const val MODE_SWITCH_DELAY_MS = 800L

        // MIUI camera UI geometry (proportional, mapped from screenshots).
        // This camera app's shutter/switch/mode controls don't reliably respond
        // to AccessibilityNodeInfo.ACTION_CLICK (same issue fixed for the shutter
        // button), so we drive them with real coordinate taps/swipes instead.
        private const val MIUI_FLIP_CAMERA_X_FRACTION = 0.902f
        private const val MIUI_MODE_ROW_Y_FRACTION = 0.7825f
        private const val MIUI_MODE_SWIPE_DURATION_MS = 250L

        // Mode row: Pro | Video | Photo | Fastshot | Portrait. Video (0.297)
        // and Photo (0.50) were measured from screenshots; Pro/Fastshot/Portrait
        // are estimated from the same ~0.20 spacing and may need tuning.
        private val MODE_NAMES = listOf("pro", "video", "photo", "fastshot", "portrait")
        private val MODE_X_FRACTIONS = listOf(0.10f, 0.297f, 0.50f, 0.70f, 0.90f)
        private const val PHOTO_MODE_INDEX = 2
        private const val VIDEO_MODE_INDEX = 1
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var messageClient: MessageClient
    private lateinit var settings: SettingsManager

    // Common content descriptions for camera controls across popular camera apps
    private val shutterDescriptions = listOf(
        "shutter", "take photo", "capture", "camera button", "take picture",
        "shoot", "snap", "photograph", "shutter button",
        "capture button", "camera shutter"
    )
    private val recordDescriptions = listOf(
        "record", "start recording", "record video", "stop recording",
        "recording", "record button"
    )
    private val switchCameraDescriptions = listOf(
        "switch camera", "flip camera", "toggle camera", "front camera",
        "rear camera", "rotate camera", "selfie",
        "change camera", "switch to front", "switch to rear",
        "camera switch", "swap camera", "camera flip",
        "facing", "switch lens", "lens switch",
        "reverse camera", "camera facing", "switch"
    )
    private val flashDescriptions = listOf(
        "flash mode", "toggle flash", "flash button",
        "flash off", "flash on", "flash auto"
    )
    private val videoDescriptions = listOf(
        "video", "record", "video mode", "switch to video",
        "start recording", "record video", "movie", "camcorder"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        messageClient = Wearable.getMessageClient(this)
        settings = SettingsManager(this)
        createNotificationChannel()
        cancelServiceNotification()
        Log.d(TAG, "CameraControlService connected and ready")
    }

    private var lastCameraDetected = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Detect when camera app opens/closes and notify watch
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val root = rootInActiveWindow ?: return
            val isCameraApp = isCameraAppInForeground(root)
            root.recycle()
            if (isCameraApp && !lastCameraDetected) {
                lastCameraDetected = true
                sendStatusToWatch("camera_detected")
            } else if (!isCameraApp && lastCameraDetected) {
                lastCameraDetected = false
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "CameraControlService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        showServiceStoppedNotification()
        Log.d(TAG, "CameraControlService destroyed")
    }

    // Track video recording state
    private var isRecording = false

    // MIUI's camera exposes no reliable accessibility signal to distinguish
    // between mode-row positions (same "Shutter button" label in Photo/Video),
    // so we track which mode the app itself last scrolled to, as an index into
    // MODE_NAMES/MODE_X_FRACTIONS, rather than trying to detect it from the
    // live UI. This can go stale if the mode is changed by hand on the phone
    // between watch commands; the next open/switch/scroll command re-syncs it.
    private var currentModeIndex = PHOTO_MODE_INDEX

    private fun requireCameraOpen(action: () -> Unit) {
        val root = rootInActiveWindow
        if (root != null && isCameraAppInForeground(root)) {
            root.recycle()
            action()
        } else {
            root?.recycle()
            sendStatusToWatch("camera_not_open")
        }
    }

    fun handleCommand(command: String) {
        Log.d(TAG, "handleCommand: $command")

        when (command) {
            "open_camera" -> openCamera()
            "capture" -> capture()
            "scroll_mode_left" -> requireCameraOpen { scrollMode(left = true) }
            "scroll_mode_right" -> requireCameraOpen { scrollMode(left = false) }
            "switch_camera" -> requireCameraOpen { switchCamera() }
            "capture_timer" -> captureWithTimer()
            else -> {
                Log.w(TAG, "Unknown command: $command")
                sendStatusToWatch("unknown_command")
            }
        }
    }

    /**
     * "Open Camera" always guarantees Photo mode on return. If the camera app
     * is already running, re-firing the launch intent would just bring the
     * existing activity forward without actually resetting its UI — so our
     * tracked currentModeIndex would say Photo while the real screen stayed
     * in whatever mode it was left in (this caused the mode desync). Instead,
     * when already open, swipe back to Photo the same way scrollMode() does.
     */
    private fun openCamera() {
        val root = rootInActiveWindow
        if (root != null && isCameraAppInForeground(root)) {
            root.recycle()
            if (currentModeIndex != PHOTO_MODE_INDEX) {
                swipeToModeIndex(PHOTO_MODE_INDEX) { sendStatusToWatch("photo_mode") }
            } else {
                sendStatusToWatch("camera_opened")
            }
            return
        }
        root?.recycle()
        try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Log.d(TAG, "openCamera: launching STILL_IMAGE_CAMERA intent")
            startActivity(intent)
            currentModeIndex = PHOTO_MODE_INDEX
            sendStatusToWatch("camera_opened")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera with STILL_IMAGE intent", e)
            try {
                val fallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                Log.d(TAG, "openCamera: trying IMAGE_CAPTURE fallback")
                startActivity(fallback)
                currentModeIndex = PHOTO_MODE_INDEX
                sendStatusToWatch("camera_opened")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open camera (fallback)", e2)
                sendStatusToWatch("camera_open_failed")
            }
        }
    }

    /**
     * Scroll the mode row one step left or right, requires the camera app to
     * already be open (see requireCameraOpen). Clamped to the ends of
     * MODE_NAMES — no wraparound.
     */
    private fun scrollMode(left: Boolean) {
        val targetIndex = if (left) currentModeIndex - 1 else currentModeIndex + 1
        if (targetIndex < 0 || targetIndex >= MODE_NAMES.size) {
            Log.d(TAG, "scrollMode: already at ${if (left) "first" else "last"} mode")
            sendStatusToWatch("mode:${MODE_NAMES[currentModeIndex]}")
            return
        }
        swipeToModeIndex(targetIndex) { sendStatusToWatch("mode:${MODE_NAMES[targetIndex]}") }
    }

    /**
     * Capture: tap the shutter/record button in the current camera app.
     * If no camera app is detected, open camera in PHOTO mode first then capture.
     * Does NOT re-launch camera if already in a camera app (preserves current mode).
     */
    private fun capture() {
        Log.d(TAG, "capture: attempting to find and click shutter/record")
        val root = rootInActiveWindow
        if (root == null) {
            Log.d(TAG, "capture: no active window, opening camera in photo mode")
            if (settings.isAutoOpenCameraEnabled()) {
                openCamera() // Uses STILL_IMAGE_CAMERA intent = photo mode
                handler.postDelayed({ captureAfterOpen() }, settings.getCameraLaunchDelayMs().toLong())
            } else {
                sendStatusToWatch("no_camera_app")
            }
            return
        }

        // Check if we're in a camera app by looking for any camera-like buttons
        val isCameraApp = isCameraAppInForeground(root)
        root.recycle()

        if (!isCameraApp) {
            Log.d(TAG, "capture: not in camera app, opening camera in photo mode")
            if (settings.isAutoOpenCameraEnabled()) {
                openCamera() // Uses STILL_IMAGE_CAMERA intent = photo mode
                handler.postDelayed({ captureAfterOpen() }, settings.getCameraLaunchDelayMs().toLong())
            } else {
                sendStatusToWatch("no_camera_app")
            }
            return
        }

        // Already in a camera app — just tap the shutter/record, don't re-launch
        doCapture()
    }

    private fun captureAfterOpen() {
        Log.d(TAG, "captureAfterOpen: retrying after camera open (photo priority)")

        // Camera might have opened in a non-photo mode despite STILL_IMAGE
        // intent. Swipe back to Photo before capturing.
        if (currentModeIndex != PHOTO_MODE_INDEX) {
            Log.d(TAG, "captureAfterOpen: not in photo mode, switching to photo")
            swipeToModeIndex(PHOTO_MODE_INDEX) {
                sendStatusToWatch("photo_mode")
                handler.postDelayed({
                    Log.d(TAG, "captureAfterOpen: capturing after mode switch")
                    if (settings.isShutterFallbackEnabled()) {
                        tapShutterFallback()
                    } else {
                        sendStatusToWatch("shutter_not_found")
                    }
                }, MODE_SWITCH_DELAY_MS)
            }
            return
        }

        // Photo shutter uses a coordinate tap, not a semantic click — see doCapture()
        if (settings.isShutterFallbackEnabled()) {
            tapShutterFallback()
        } else {
            sendStatusToWatch("shutter_not_found")
        }
    }

    /**
     * The MIUI camera's mode row (Pro/Video/Photo/Fastshot/Portrait) is a
     * horizontal scroll carousel, not discrete buttons — the active mode is
     * whichever label sits centered. A semantic click on the mode text does
     * nothing useful here; only a real horizontal swipe scrolls the carousel.
     * Swiping from the current mode's x-position to the target mode's
     * x-position moves that target into the centered/active slot.
     */
    private fun swipeToModeIndex(targetIndex: Int, onDone: () -> Unit) {
        val (screenWidth, screenHeight) = getScreenSize()
        val fromX = screenWidth * MODE_X_FRACTIONS[currentModeIndex]
        val toX = screenWidth * MODE_X_FRACTIONS[targetIndex]
        val y = screenHeight * MIUI_MODE_ROW_Y_FRACTION

        val path = Path().apply {
            moveTo(fromX, y)
            lineTo(toX, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, MIUI_MODE_SWIPE_DURATION_MS))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "swipeToModeIndex: swipe completed ($fromX -> $toX)")
                currentModeIndex = targetIndex
                onDone()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "swipeToModeIndex: swipe cancelled")
            }
        }, null)
    }

    private fun doCapture() {
        if (!settings.isShutterFallbackEnabled()) {
            Log.d(TAG, "doCapture: shutter fallback disabled, cannot capture")
            sendStatusToWatch("shutter_not_found")
            return
        }
        // The shutter/record button sits at the same screen position in both
        // photo and video mode on MIUI (only its icon changes, white ring vs.
        // red circle) and neither responds reliably to a semantic ACTION_CLICK,
        // so a single coordinate tap drives capture, record-start, and
        // record-stop alike.
        if (isRecording) {
            tapShutterFallback(onCapturedStatus = "recording_stopped")
            isRecording = false
            return
        }
        if (currentModeIndex == VIDEO_MODE_INDEX) {
            tapShutterFallback(onCapturedStatus = "recording_started")
            isRecording = true
            return
        }
        tapShutterFallback()
    }

    private fun isCameraAppInForeground(root: AccessibilityNodeInfo): Boolean {
        // Check if any shutter/capture-like button exists in the current window
        for (desc in shutterDescriptions) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            if (nodes.isNotEmpty()) {
                for (node in nodes) node.recycle()
                return true
            }
        }
        // Also check for video record buttons
        for (desc in recordDescriptions + videoDescriptions) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            if (nodes.isNotEmpty()) {
                for (node in nodes) node.recycle()
                return true
            }
        }
        // Check for flash button (indicates camera app)
        for (desc in flashDescriptions) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            if (nodes.isNotEmpty()) {
                for (node in nodes) node.recycle()
                return true
            }
        }
        // Check for switch camera button
        for (desc in switchCameraDescriptions) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            if (nodes.isNotEmpty()) {
                for (node in nodes) node.recycle()
                return true
            }
        }
        return false
    }

    private fun switchCamera() {
        // The flip-camera icon shares the shutter-button unreliability (MIUI's
        // custom view accepts ACTION_CLICK without actually flipping the camera),
        // so we tap its known screen position instead of a semantic click.
        val (screenWidth, screenHeight) = getScreenSize()
        val x = screenWidth * MIUI_FLIP_CAMERA_X_FRACTION
        val fallbackPercent = settings.getShutterFallbackPosition() / 100f
        val y = screenHeight * fallbackPercent

        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, settings.getGestureTapDurationMs().toLong()))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "switchCamera: tap gesture completed at ($x, $y)")
                sendStatusToWatch("camera_switched")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "switchCamera: tap gesture cancelled")
                sendStatusToWatch("switch_not_found")
            }
        }, null)
    }

    private fun getScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val bounds = wm.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun tapShutterFallback(onCapturedStatus: String = "captured") {
        val (screenWidth, screenHeight) = getScreenSize()
        val x = screenWidth / 2f
        val fallbackPercent = settings.getShutterFallbackPosition() / 100f
        val y = screenHeight * fallbackPercent

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, settings.getGestureTapDurationMs().toLong()))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap gesture completed at ($x, $y)")
                sendStatusToWatch(onCapturedStatus)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap gesture cancelled")
                sendStatusToWatch("capture_failed")
            }
        }, null)
    }

    private fun captureWithTimer() {
        val timerSec = settings.getDefaultTimerSeconds()
        sendStatusToWatch("timer_${timerSec}s")
        handler.postDelayed({ capture() }, timerSec * 1000L)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Service Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about the Camera Remote accessibility service"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun showServiceStoppedNotification() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Camera Remote")
                .setContentText("Accessibility service stopped. Tap to re-enable.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show service stopped notification", e)
        }
    }

    private fun cancelServiceNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel notification", e)
        }
    }

    private fun sendStatusToWatch(status: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                messageClient.sendMessage(
                    node.id,
                    PATH_STATUS,
                    status.toByteArray()
                ).addOnSuccessListener {
                    Log.d(TAG, "Status '$status' sent to watch ${node.displayName}")
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send status to watch", e)
                }
            }
        }
    }
}
