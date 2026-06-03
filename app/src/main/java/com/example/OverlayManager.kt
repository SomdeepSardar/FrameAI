package com.example

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.UUID

class OverlayManager(private val context: Context, private val onStop: () -> Unit) : SensorEventListener {
    private var windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    
    // State to pass Gemini feedback
    private val geminiInstruction = mutableStateOf("Waiting for AI...")
    // State to draw level
    private val pitch = mutableStateOf(0f)
    private val roll = mutableStateOf(0f)

    private var sensorManager: SensorManager? = null
    private var lifecycleOwner: MyLifecycleOwner? = null

    fun showOverlay() {
        if (composeView != null) return

        composeView = ComposeView(context).apply {
            setContent {
                MaterialTheme {
                    OverlayUI(geminiInstruction.value, pitch.value, roll.value, onStop)
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // some offset
        }

        lifecycleOwner = MyLifecycleOwner()
        lifecycleOwner?.performRestore(null)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        composeView?.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView?.setViewTreeViewModelStoreOwner(lifecycleOwner)
        composeView?.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        windowManager.addView(composeView, params)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        sensorManager?.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun updateInstruction(text: String) {
        geminiInstruction.value = text
    }

    fun hideOverlay() {
        composeView?.let {
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            windowManager.removeView(it)
            composeView = null
        }
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_GRAVITY) {
                // Gravity components: x, y, z
                // Normalize and calculate pitch and roll roughly
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]
                
                val rollVal = Math.atan2(y.toDouble(), z.toDouble()) * 180 / Math.PI
                val pitchVal = Math.atan2(-x.toDouble(), Math.sqrt((y*y + z*z).toDouble())) * 180 / Math.PI

                pitch.value = pitchVal.toFloat()
                roll.value = rollVal.toFloat()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun OverlayUI(instruction: String, pitch: Float, roll: Float, onStop: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .border(1.dp, Color(0xFFD0BCFF), androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
        color = Color(0xFFEADDFF),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Level indicator as per Sleek Mock
            val isLevel = Math.abs(pitch) < 5f && Math.abs(roll) < 5f
            val dotColor = if (isLevel) Color.Green else Color(0xFF6750A4)

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White, CircleShape)
                    .border(2.dp, Color(0xFF6750A4), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    val xOffset = (roll / 90f) * (size.width / 2)
                    val yOffset = (pitch / 90f) * (size.height / 2)
                    val cx = center.x + xOffset
                    val cy = center.y - yOffset
                    
                    val cxc = cx.coerceIn(0f, size.width)
                    val cyc = cy.coerceIn(0f, size.height)

                    drawLine(
                        color = dotColor,
                        start = Offset(cxc - 8f, cyc),
                        end = Offset(cxc + 8f, cyc),
                        strokeWidth = 3f
                    )
                    drawLine(
                        color = dotColor,
                        start = Offset(cxc, cyc - 8f),
                        end = Offset(cxc, cyc + 8f),
                        strokeWidth = 3f
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier
                .weight(1f)
                .heightIn(max = 400.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Text(
                    text = "GEMINI FLASH AI",
                    color = Color(0xFF6750A4),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "\"$instruction\"",
                    color = Color(0xFF21005D),
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))
            
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(Color(0xFFD0BCFF))
            )
            
            Spacer(modifier = Modifier.width(16.dp))

            IconButton(
                onClick = onStop,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFB3261E), CircleShape)
            ) {
                Text("X", color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

// Helper to provide Lifecycle and SavedStateRegistry to Compose View
class MyLifecycleOwner : androidx.lifecycle.LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, androidx.activity.OnBackPressedDispatcherOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val dispatcher = androidx.activity.OnBackPressedDispatcher()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val onBackPressedDispatcher: androidx.activity.OnBackPressedDispatcher get() = dispatcher

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    fun performRestore(savedState: android.os.Bundle?) {
        savedStateRegistryController.performRestore(savedState)
    }
}

interface LifecycleOwner : androidx.lifecycle.LifecycleOwner
