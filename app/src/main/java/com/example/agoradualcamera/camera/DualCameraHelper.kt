package com.example.agoradualcamera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import java.io.ByteArrayOutputStream

class DualCameraHelper(
    private val context: Context,
    private val onCombinedBitmapReady: (Bitmap) -> Unit
) {

    private lateinit var cameraManager: CameraManager
    private var frontCameraId: String? = null
    private var backCameraId: String? = null

    private var frontImageReader: ImageReader? = null
    private var backImageReader: ImageReader? = null

    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    private val previewSize = Size(320, 240) // adjust as needed

    fun startCapture() {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        startBackgroundThread()
        findCameras()
        openCamera(frontCameraId, isFront = true)
        openCamera(backCameraId, isFront = false)
    }

    fun stopCapture() {
        stopBackgroundThread()
        frontImageReader?.close()
        backImageReader?.close()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread.quitSafely()
        backgroundThread.join()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(cameraId: String?, isFront: Boolean) {
        if (cameraId == null) return

        val imageReader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.YUV_420_888, 2)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val bitmap = yuvToBitmap(image)
            image.close()

            synchronized(this) {
                if (isFront) {
                    frontBitmap = bitmap
                } else {
                    backBitmap = bitmap
                }

                if (frontBitmap != null && backBitmap != null) {
                    val combined = combineBitmaps(frontBitmap!!, backBitmap!!)
                    onCombinedBitmapReady(combined)
                }
            }
        }, backgroundHandler)

        if (isFront) frontImageReader = imageReader else backImageReader = imageReader

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                val targetSurface = imageReader.surface
                val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                captureRequestBuilder.addTarget(targetSurface)

                camera.createCaptureSession(
                    listOf(targetSurface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    },
                    backgroundHandler
                )
            }

            override fun onDisconnected(camera: CameraDevice) {}
            override fun onError(camera: CameraDevice, error: Int) {}
        }, backgroundHandler)
    }

    private fun findCameras() {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            when (facing) {
                CameraCharacteristics.LENS_FACING_FRONT -> frontCameraId = id
                CameraCharacteristics.LENS_FACING_BACK -> backCameraId = id
            }
        }
    }

    private var frontBitmap: Bitmap? = null
    private var backBitmap: Bitmap? = null

    private fun combineBitmaps(back: Bitmap, front: Bitmap): Bitmap {
        // Final canvas is the size of the back camera
        val result = Bitmap.createBitmap(back.width, back.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Draw back camera full screen
        canvas.drawBitmap(back, 0f, 0f, null)

        // Define PiP window size and position (e.g., bottom-right corner)
        val pipWidth = back.width / 4
        val pipHeight = back.height / 4
        val left = back.width - pipWidth - 16f  // 16px padding
        val top = back.height - pipHeight - 16f

        // Scale and draw the front camera as PiP
        val pipBitmap = Bitmap.createScaledBitmap(front, pipWidth, pipHeight, true)
        canvas.drawBitmap(pipBitmap, left, top, null)

        return result
    }


    private fun yuvToBitmap(image: android.media.Image): Bitmap {
        val nv21 = yuv420ToNV21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
        val yuvBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(yuvBytes, 0, yuvBytes.size)
    }

    private fun yuv420ToNV21(image: android.media.Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4

        val nv21 = ByteArray(ySize + uvSize * 2)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        yBuffer.get(nv21, 0, ySize)

        val chromaRowStride = image.planes[1].rowStride
        val chromaPixelStride = image.planes[1].pixelStride

        var offset = ySize
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val uIndex = row * chromaRowStride + col * chromaPixelStride
                val vIndex = row * image.planes[2].rowStride + col * image.planes[2].pixelStride
                nv21[offset++] = vBuffer.get(vIndex)
                nv21[offset++] = uBuffer.get(uIndex)
            }
        }

        return nv21
    }
}
