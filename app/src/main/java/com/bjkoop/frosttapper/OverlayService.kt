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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
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

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var controllerView: ComposeView? = null
    private var targetView: ComposeView? = null

    // Use member variables with mutableStateOf to ensure they are tracked by Compose
    private var isRunning by mutableStateOf(false)
    private var interval by mutableIntStateOf(1000)
    private var targetX by mutableIntStateOf(500)
    private var targetY by mutableIntStateOf(1000)
    private var controllerX by mutableIntStateOf(0)
    private var controllerY by mutableIntStateOf(150)

    private val handler = Handler(Looper.getMainLooper())
    private val clickRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                AutoClickService.instance?.let {
                    it.click(targetX, targetY)
                    showFeedback()
                }
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
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = controllerX
            y = controllerY
        }

        controllerView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setupComposeLayout(this)
            setContent {
                ControllerUI(
                    currentIsRunning = isRunning,
                    currentInterval = interval,
                    onClose = { stopSelf() },
                    onToggle = {
                        isRunning = !isRunning
                        if (isRunning) {
                            handler.removeCallbacks(clickRunnable)
                            handler.post(clickRunnable)
                        } else {
                            handler.removeCallbacks(clickRunnable)
                        }
                    },
                    onMove = { dx, dy ->
                        controllerX += dx
                        controllerY += dy
                        params.x = controllerX
                        params.y = controllerY
                        windowManager.updateViewLayout(this, params)
                    },
                    onIntervalChange = { delta ->
                        if (interval + delta >= 50) {
                            interval += delta
                        }
                    }
                )
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
            x = targetX - 30.dpToPx()
            y = targetY - 30.dpToPx()
        }

        targetView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setupComposeLayout(this)
            setContent {
                TargetUI(
                    onPositionChanged = { dx, dy ->
                        targetX += dx
                        targetY += dy
                        params.x = targetX - 30.dpToPx()
                        params.y = targetY - 30.dpToPx()
                        windowManager.updateViewLayout(this, params)
                    }
                )
            }
        }
        windowManager.addView(targetView, params)
    }

    private fun showFeedback() {
        val params = WindowManager.LayoutParams(
            60.dpToPx(),
            60.dpToPx(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = targetX - 30.dpToPx()
            y = targetY - 30.dpToPx()
        }

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setupComposeLayout(this)
            setContent {
                var visible by remember { mutableStateOf(true) }
                val alpha by animateFloatAsState(if (visible) 0.6f else 0f, tween(150))
                val scale by animateFloatAsState(if (visible) 1.2f else 0.8f, tween(150))
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(scale)
                        .alpha(alpha)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.Red, Color.Transparent)
                            )
                        )
                )
                
                handler.postDelayed({ visible = false }, 50)
            }
        }
        windowManager.addView(view, params)
        handler.postDelayed({
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {}
        }, 200)
    }

    @Composable
    private fun ControllerUI(
        currentIsRunning: Boolean,
        currentInterval: Int,
        onClose: () -> Unit,
        onToggle: () -> Unit,
        onMove: (Int, Int) -> Unit,
        onIntervalChange: (Int) -> Unit
    ) {
        MaterialTheme {
            Card(
                modifier = Modifier
                    .padding(8.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onMove(dragAmount.x.toInt(), dragAmount.y.toInt())
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .padding(bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp, 4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = onToggle,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (currentIsRunning) Color(0xFFD32F2F) else Color(0xFF388E3C))
                        ) {
                            Icon(
                                if (currentIsRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Play/Stop",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = onClose, 
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(4.dp)
                    ) {
                        IconButton(
                            onClick = { onIntervalChange(-50) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
                        }

                        Text(
                            text = "${currentInterval}ms",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        IconButton(
                            onClick = { onIntervalChange(50) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TargetUI(onPositionChanged: (Int, Int) -> Unit) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onPositionChanged(dragAmount.x.toInt(), dragAmount.y.toInt())
                    }
                }
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            // High contrast Zebra Outer Ring
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .border(4.dp, Color.Black, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(2.dp, Color.White, CircleShape)
            )
            
            // Outer semi-transparent glow
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .border(2.dp, Color.Cyan, CircleShape)
                    .background(Color.Cyan.copy(alpha = 0.1f))
            )
            
            // Inner target dot with contrast border
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(2.dp, Color.Black, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White, Color.Cyan)
                        )
                    )
            )
            
            // Precision Red Center
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .border(1.dp, Color.White, CircleShape)
                    .clip(CircleShape)
                    .background(Color.Red)
            )
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun setupComposeLayout(view: ComposeView) {
        val lifecycleOwner = MyLifecycleOwner()
        lifecycleOwner.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
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
        isRunning = false
        handler.removeCallbacks(clickRunnable)
        controllerView?.let { windowManager.removeView(it) }
        targetView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
