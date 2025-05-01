package com.example.agoradualcamera.host

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.agoradualcamera.BuildConfig
import com.example.agoradualcamera.camera.DualCameraHelper
import com.example.agoradualcamera.databinding.ActivityHostBinding
import com.example.agoradualcamera.media.RtcTokenBuilder2
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.AgoraVideoFrame
import io.agora.rtc2.video.VideoCanvas

class HostActivity : ComponentActivity() {

    private val appId = BuildConfig.AGORA_APP_ID
    var appCertificate = BuildConfig.AGORA_CERTIFICATE
    private var channelName: String = "dualCamera"
    private var token: String = ""

    private val uid = 0
    var expirationTimeInSeconds = 3600

    private lateinit var rtcEngine: RtcEngine
    private lateinit var cameraHelper: DualCameraHelper

    private lateinit var binding: ActivityHostBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ), 101)
            return
        }

        val tokenBuilder = RtcTokenBuilder2()
        val timestamp = (System.currentTimeMillis() / 1000 + expirationTimeInSeconds).toInt()

        val result = tokenBuilder.buildTokenWithUid(
            appId, appCertificate,
            channelName, uid, RtcTokenBuilder2.Role.ROLE_PUBLISHER, timestamp, timestamp
        )

        token = result

        setupVideoSDKEngine()
        initAgora()
        binding.buttonJoin.setOnClickListener {
            startDualCameraCapture()
        }

        binding.buttonLeave.setOnClickListener {
            leaveChannel()
        }
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupVideoSDKEngine() {
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = appId
            config.mEventHandler = mRtcEventHandler
            rtcEngine = RtcEngine.create(config)
            rtcEngine.enableVideo()
            rtcEngine.setupLocalVideo(VideoCanvas(binding.localSurfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
            rtcEngine.setExternalVideoSource(true, false, Constants.ExternalVideoSourceType.VIDEO_FRAME)

        } catch (e: Exception) {
            showMessage(e.toString())
        }
    }

    private fun initAgora() {
        val options = ChannelMediaOptions()
        options.autoSubscribeAudio = true
        options.autoSubscribeVideo = true
        options.publishCameraTrack = true
        options.publishMicrophoneTrack = true
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER

        rtcEngine.startPreview()
        // rtcEngine.joinChannel(token, channelName, uid, options)  // Now, we will join the channel in startDualCameraCapture()
    }

    private fun startDualCameraCapture() {
        cameraHelper = DualCameraHelper(this) { combinedBitmap ->
            val frame = AgoraVideoFrame().apply {
                format = AgoraVideoFrame.FORMAT_RGBA
                timeStamp = System.currentTimeMillis()
                val rgba = bitmapToRGBA(combinedBitmap)
                buf = rgba
                rotation = 0
                stride = combinedBitmap.width
                height = combinedBitmap.height
            }
            rtcEngine.pushExternalVideoFrame(frame)
        }
        cameraHelper.startCapture()

        // Now join the channel when dual camera starts
        val options = ChannelMediaOptions().apply {
            autoSubscribeAudio = true
            autoSubscribeVideo = true
            publishCameraTrack = true
            publishMicrophoneTrack = true
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
        }
        rtcEngine.joinChannel(token, channelName, uid, options)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraHelper.stopCapture()
        rtcEngine.leaveChannel()
        RtcEngine.destroy()
    }

    private fun bitmapToRGBA(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val rgba = ByteArray(width * height * 4)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            rgba[i * 4] = ((pixel shr 16) and 0xFF).toByte() // R
            rgba[i * 4 + 1] = ((pixel shr 8) and 0xFF).toByte() // G
            rgba[i * 4 + 2] = (pixel and 0xFF).toByte() // B
            rgba[i * 4 + 3] = ((pixel shr 24) and 0xFF).toByte() // A
        }
        return rgba
    }

    fun showMessage(message: String?) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) {
            showMessage("Remote user joined $uid")
        }

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            showMessage("Joined Channel $channel")
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            showMessage("Remote user offline $uid $reason")
        }

        override fun onError(err: Int) {
            super.onError(err)
            showMessage("onError  $err")
        }
    }

    fun leaveChannel() {

            rtcEngine!!.leaveChannel()
            showMessage("You left the channel")

    }
}

