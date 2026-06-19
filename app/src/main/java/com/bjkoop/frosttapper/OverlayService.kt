package com.bjkoop.frosttapper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var controllerView: ComposeView? = null
    private var targetView: ComposeView? = null
    private var feedbackView: ComposeView? = null

    private var isRunning by mutableStateOf(false)
    private var interval by mutableIntStateOf(1000)
    private var targetX by mutableIntStateOf(500)
    private var targetY by mutableIntStateOf(1000)

    private val handler = Handler(Looper.getMainLooper())
    private val clickRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                AutoClickService.instance?.click(targetX, targetY)
                showFeedback()
                handler.postDelayed(this, interval.toLong())
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(1, createNotification())
        showController()
        showTarget()
    }

    private fun createNotification(): Notification {
        val channelId = "overlay_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Autoclicker Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Autoclicker is active")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Autoclicker is active")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
        }
    }

    private fun showController() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
        }

        controllerView = ComposeView(this).apply {
            setupComposeLayout(this)
            setContent {
                ControllerUI()
            }
        }
        windowManager.addView(controllerView, params)
    }

    private fun showTarget() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = targetX - 25.dpToPx()
            y = targetY - 25.dpToPx()
        }

        targetView = ComposeView(this).apply {
            setupComposeLayout(this)
            setContent {
                TargetUI(
                    onPositionChanged = { dx, dy ->
                        targetX += dx
                        targetY += dy
                        params.x = targetX - 25.dpToPx()
                        params.y = targetY - 25.dpToPx()
                        windowManager.updateViewLayout(targetView, params)
                    }
                )
            }
        }
        windowManager.addView(targetView, params)
    }

    private fun showFeedback() {
        val params = WindowManager.LayoutParams(
            50.dpToPx(),
            50.dpToPx(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = targetX - 25.dpToPx()
            y = targetY - 25.dpToPx()
        }

        val view = ComposeView(this).apply {
            setupComposeLayout(this)
            setContent {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = 0.5f))
                )
            }
        }
        windowManager.addView(view, params)
        handler.postDelayed({
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {}
        }, 100)
    }

    @Composable
    private fun ControllerUI() {
        MaterialTheme {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Autoclicker", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            isRunning = !isRunning
                            if (isRunning) handler.post(clickRunnable)
                            else handler.removeCallbacks(clickRunnable)
                        }) {
                            Icon(
                                if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isRunning) "Stop" else "Start",
                                tint = if (isRunning) Color.Red else Color.Green
                            )
                        }
                    }
                    Text(text = "Interval: ${interval}ms", fontSize = 12.sp)
                    Slider(
                        value = interval.toFloat(),
                        onValueChange = { interval = it.roundToInt() },
                        valueRange = 100f..5000f,
                        steps = 49
                    )
                }
            }
        }
    }

    @Composable
    private fun TargetUI(onPositionChanged: (Int, Int) -> Unit) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onPositionChanged(dragAmount.x.toInt(), dragAmount.y.toInt())
                    }
                }
                .clip(CircleShape)
                .background(Color.Blue.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun setupComposeLayout(view: ComposeView) {
        val lifecycleOwner = MyLifecycleOwner()
        lifecycleOwner.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        })
        view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
    }

    class MyLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

        fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)
        fun performRestore(savedState: android.os.Bundle?) = savedStateRegistryController.performRestore(savedState)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clickRunnable)
        controllerView?.let { windowManager.removeView(it) }
        targetView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
