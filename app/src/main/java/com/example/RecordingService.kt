package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

import kotlinx.coroutines.flow.MutableStateFlow

class RecordingService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        
        val isServiceRunning = MutableStateFlow(false)
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var overlayManager: OverlayManager? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var captureJob: Job? = null
    private val isAnalyzing = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundService()
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                }
                
                if (resultCode != 0 && resultData != null) {
                    try {
                        startProjection(resultCode, resultData)
                        showOverlay()
                        isServiceRunning.value = true
                    } catch (e: Exception) {
                        Log.e("RecordingService", "Startup crash: ", e)
                        android.widget.Toast.makeText(this, "Crash: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        stopSelf()
                    }
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, "FrameAI_Channel")
            .setContentTitle("FrameAI")
            .setContentText("Recording and providing AI assistance...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "FrameAI_Channel",
                "Frame AI Recording Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showOverlay() {
        overlayManager = OverlayManager(this) {
            stopRecording()
        }
        overlayManager?.showOverlay()
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                stopRecording()
            }
        }, null)

        setupImageReader()
    }

    private fun setupImageReader() {
        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels / 2) * 2
        val height = (metrics.heightPixels / 2) * 2
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        captureJob = serviceScope.launch {
            while (isActive) {
                delay(3000) // 3 seconds interval
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    val bitmap = imageToBitmap(image)
                    image.close()
                    if (bitmap != null) {
                        if (isAnalyzing.compareAndSet(false, true)) {
                            launch {
                                try {
                                    analyzeFrame(bitmap)
                                } finally {
                                    isAnalyzing.set(false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        // Return a resized bitmap to save bandwidth and memory
        val scaledWidth = 400
        val scaledHeight = (scaledWidth.toFloat() / bitmap.width * bitmap.height).toInt()
        return Bitmap.createScaledBitmap(Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height), scaledWidth, scaledHeight, true)
    }
    
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private suspend fun analyzeFrame(bitmap: Bitmap) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey.startsWith("MY_GEMINI_API_KEY")) {
                withContext(Dispatchers.Main) {
                    overlayManager?.updateInstruction("Configure API Key")
                }
                return
            }

            val request = GenerateContentRequest(
                systemInstruction = Content(
                    parts = listOf(
                        Part(text = "You are looking at a user's phone screen while they try to take a photo. Ignore the phone's UI elements. Look at the camera viewfinder image. Is the subject centered? Is the lighting okay? Give a 3-word instruction.")
                    )
                ),
                contents = listOf(Content(
                    parts = listOf(
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = bitmap.toBase64()))
                    )
                ))
            )

            val response = RetrofitClient.service.generateContent(apiKey, request)
            val instruction = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No insight."
            
            withContext(Dispatchers.Main) {
                overlayManager?.updateInstruction(instruction.trim())
            }

        } catch (e: Exception) {
            Log.e("RecordingService", "Error analyzing frame", e)
            withContext(Dispatchers.Main) {
                overlayManager?.updateInstruction("Error connecting")
            }
        }
    }

    private fun stopRecording() {
        isServiceRunning.value = false
        captureJob?.cancel()
        overlayManager?.hideOverlay()
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        stopSelf()
    }

    override fun onDestroy() {
        stopRecording()
        serviceScope.cancel()
        super.onDestroy()
    }
}
