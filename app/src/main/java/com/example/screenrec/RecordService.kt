package com.example.screenrec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast

class RecordService : Service() {

    companion object {
        @Volatile
        var recording = false
    }

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var display: VirtualDisplay? = null
    private var pfd: ParcelFileDescriptor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopRecording()
            stopSelf()
            return START_NOT_STICKY
        }

        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")
        val audio = intent?.getBooleanExtra("audio", false) ?: false

        val channelId = "rec"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Recording", NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, RecordService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(this, channelId)
            .setContentTitle("Screen recording")
            .setContentText("Tap Stop to finish")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .build()

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (audio) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        startForeground(1, notif, type)

        if (data == null) {
            fail("No permission data")
            return START_NOT_STICKY
        }

        try {
            startRecording(code, data, audio)
        } catch (e: Exception) {
            fail("Error: " + e.message)
        }
        return START_NOT_STICKY
    }

    private fun startRecording(code: Int, data: Intent, audio: Boolean) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = mpm.getMediaProjection(code, data)
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopRecording()
                stopSelf()
            }
        }, null)

        val dm = resources.displayMetrics
        val w = dm.widthPixels / 2 * 2
        val h = dm.heightPixels / 2 * 2

        val values = ContentValues()
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "ScreenRec_" + System.currentTimeMillis() + ".mp4")
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenRec")
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("Cannot create file")
        pfd = contentResolver.openFileDescriptor(uri, "w")

        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        if (audio) r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setOutputFile(pfd!!.fileDescriptor)
        r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        if (audio) {
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128000)
            r.setAudioSamplingRate(44100)
        }
        r.setVideoSize(w, h)
        r.setVideoFrameRate(30)
        r.setVideoEncodingBitRate(8000000)
        r.prepare()
        recorder = r

        display = p.createVirtualDisplay(
            "ScreenRec", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, null
        )
        r.start()
        recording = true
    }

    private fun stopRecording() {
        val r = recorder
        val d = display
        val p = projection
        recorder = null
        display = null
        projection = null
        try { r?.stop() } catch (e: Exception) { }
        try { r?.release() } catch (e: Exception) { }
        try { d?.release() } catch (e: Exception) { }
        try { p?.stop() } catch (e: Exception) { }
        try { pfd?.close() } catch (e: Exception) { }
        pfd = null
        if (recording) {
            Toast.makeText(this, "Saved to Movies/ScreenRec", Toast.LENGTH_LONG).show()
        }
        recording = false
    }

    private fun fail(msg: String) {
        recording = false
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        stopRecording()
        stopSelf()
    }
}
