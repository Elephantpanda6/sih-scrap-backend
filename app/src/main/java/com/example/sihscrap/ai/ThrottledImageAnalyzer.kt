package com.example.sihscrap.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class ThrottledImageAnalyzer(
    private val classifier: YoloScrapClassifier,
    private val onResult: (ClassificationResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val TAG = "ThrottledAnalyzer"
    private var lastAnalyzedTimestamp: Long = 0L
    private val THROTTLE_INTERVAL_MS: Long = 1500L // 1 frame per 1.5 seconds

    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalyzedTimestamp < THROTTLE_INTERVAL_MS) {
            // Strictly dispose imageProxy immediately to prevent heap spikes on Android Go
            imageProxy.close()
            return
        }

        try {
            lastAnalyzedTimestamp = currentTimestamp
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                val result = classifier.analyzeBitmap(bitmap)
                onResult(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during throttled frame analysis: ${e.message}", e)
        } finally {
            // Crucial: explicit close in finally block
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val planes = imageProxy.planes
        if (planes.isEmpty()) return null

        val yBuffer: ByteBuffer = planes[0].buffer
        val uBuffer: ByteBuffer? = if (planes.size > 1) planes[1].buffer else null
        val vBuffer: ByteBuffer? = if (planes.size > 2) planes[2].buffer else null

        val ySize = yBuffer.remaining()
        val uSize = uBuffer?.remaining() ?: 0
        val vSize = vBuffer?.remaining() ?: 0

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)

        if (uBuffer != null && vBuffer != null) {
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 75, out)
        val imageBytes = out.toByteArray()

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(imageProxy.width, imageProxy.height, 224, 224)
        }
        val fullBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options) ?: return null
        return Bitmap.createScaledBitmap(fullBitmap, 224, 224, false)
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
