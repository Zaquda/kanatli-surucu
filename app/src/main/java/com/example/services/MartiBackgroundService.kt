package com.example.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.MartiDatabase
import com.example.data.MartiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MartiBackgroundService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isOverlayShowing = false

    private val database by lazy { MartiDatabase.getDatabase(applicationContext) }
    private val repository by lazy { MartiRepository(database.martiDao()) }

    companion object {
        const val CHANNEL_ID = "MartiBackgroundServiceChannel"
        const val NOTIFICATION_ID = 101
        
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_SHOW_OVERLAY = "ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "ACTION_HIDE_OVERLAY"
    }

    private var trackingJob: kotlinx.coroutines.Job? = null
    private val locationTracker by lazy { com.example.utils.LocationTracker.getInstance(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startDistanceObservation()
    }

    private fun startDistanceObservation() {
        trackingJob = CoroutineScope(Dispatchers.IO).launch {
            // Observe the active shift to start/stop the tracker independently of the UI
            launch {
                repository.getActiveShift().collect { active ->
                    if (active != null && !active.isPaused) {
                        locationTracker.startTracking()
                    } else {
                        locationTracker.stopTracking()
                    }
                }
            }

            // Observe distance changes and update the database
            launch {
                locationTracker.totalDistanceKm.collect { dist ->
                    val active = repository.getActiveShiftSync()
                    if (active != null && !active.isPaused && dist > active.totalGpsKm) {
                        repository.updateShiftDistance(dist)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE
        
        when (action) {
            ACTION_START_SERVICE -> {
                showNotification("Arka Planda Çalışıyor", "Asistanınız arka planda çalışmaya devam ediyor.")
            }
            ACTION_STOP_SERVICE -> {
                hideOverlay()
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_OVERLAY -> {
                showNotification("Arka Planda Çalışıyor", "Asistanınız arka planda çalışmaya devam ediyor.")
                CoroutineScope(Dispatchers.IO).launch {
                    val active = repository.getActiveShiftSync()
                    if (active != null) {
                        withContext(Dispatchers.Main) {
                            showFloatingOverlay()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            stopSelf()
                        }
                    }
                }
            }
            ACTION_HIDE_OVERLAY -> {
                showNotification("Vardiya Aktif", "Arka planda konum ve mesafe takibi yapılıyor.")
                hideOverlay()
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showNotification(title: String, text: String) {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasFine || hasCoarse) {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Marti Tag Asistanı Servisi",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private var shiftObservationJob: kotlinx.coroutines.Job? = null

    private fun showFloatingOverlay() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            return
        }

        if (isOverlayShowing) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        floatingView = ImageView(this).apply {
            setImageResource(com.example.R.mipmap.ic_launcher)
            layoutParams = android.view.ViewGroup.LayoutParams(180, 180)
            scaleType = ImageView.ScaleType.CENTER_CROP
            
            // Make it round
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
            elevation = 16f
            
            // Initial color grey
            val matrix = android.graphics.ColorMatrix()
            matrix.setSaturation(0f)
            colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoved = false

        floatingView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoved = false
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoved) {
                        val intent = Intent(this@MartiBackgroundService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isMoved = true
                    }

                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(floatingView, params)
            isOverlayShowing = true
            
            // Observe shift status to update color
            shiftObservationJob?.cancel()
            shiftObservationJob = CoroutineScope(Dispatchers.IO).launch {
                repository.getActiveShift().collect { shift ->
                    withContext(Dispatchers.Main) {
                        if (shift != null && !shift.isPaused) {
                            // Active shift -> Remove grayscale
                            (floatingView as? ImageView)?.clearColorFilter()
                        } else {
                            // Inactive/Paused -> Grayscale
                            val matrix = android.graphics.ColorMatrix()
                            matrix.setSaturation(0f)
                            (floatingView as? ImageView)?.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideOverlay() {
        shiftObservationJob?.cancel()
        if (isOverlayShowing && floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {}
            floatingView = null
            isOverlayShowing = false
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        hideOverlay()
        // If there is no active shift, we can also stop the service completely
        CoroutineScope(Dispatchers.IO).launch {
            val active = repository.getActiveShiftSync()
            if (active == null || active.isPaused) {
                stopForeground(true)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        trackingJob?.cancel()
    }
}
