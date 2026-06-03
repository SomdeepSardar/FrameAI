package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*

import androidx.compose.material.icons.filled.Close

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BackgroundSleek
                ) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    @Composable
    fun MainScreen(modifier: Modifier = Modifier) {
        var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(this)) }
        val isRecording by RecordingService.isServiceRunning.collectAsState()

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val projectionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this@MainActivity, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_START
                    putExtra(RecordingService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(RecordingService.EXTRA_RESULT_DATA, result.data)
                }
                androidx.core.content.ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
            }
        }

        val overlayLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            overlayGranted = Settings.canDrawOverlays(this)
            if (overlayGranted) {
                projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(TopBarIconSleek, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Logo", tint = Purple40)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Vision Guide AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimarySleek
                    )
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = TextPrimarySleek)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Status Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackgroundSleek, RoundedCornerShape(28.dp))
                    .border(1.dp, BorderSleek, RoundedCornerShape(28.dp))
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(ErrorDotSleek, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "SYSTEM READY",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondarySleek
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Grant Display over other apps permission to enable the AI floating assistant while you're in the camera.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondarySleek,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Main Action Area
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    // Glow effect
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .background(Purple80.copy(alpha = 0.2f), CircleShape)
                    )
                    
                    Button(
                        onClick = {
                            if (isRecording) {
                                val stopIntent = Intent(this@MainActivity, RecordingService::class.java).apply {
                                    action = RecordingService.ACTION_STOP
                                }
                                startService(stopIntent)
                            } else {
                                if (!overlayGranted) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    )
                                    overlayLauncher.launch(intent)
                                } else {
                                    projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                                }
                            }
                        },
                        modifier = Modifier
                            .size(192.dp)
                            .shadow(16.dp, CircleShape),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color(0xFFB3261E) else Purple40,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (isRecording) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isRecording) "STOP RECORDING" else "START AI GUIDE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                
                Text(
                    "Recording starts immediately. Your personal AI framing assistant will appear on screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondarySleek,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(240.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
