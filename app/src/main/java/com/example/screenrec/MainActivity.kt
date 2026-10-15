package com.example.screenrec

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

class MainActivity : Activity() {

    private lateinit var btn: Button
    private var audio = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(48, 48, 48, 48)
        btn = Button(this)
        btn.textSize = 20f
        btn.setOnClickListener { onButton() }
        layout.addView(btn)
        setContentView(layout)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        btn.text = if (RecordService.recording) "Stop recording" else "Start recording"
    }

    private fun onButton() {
        if (RecordService.recording) {
            startService(Intent(this, RecordService::class.java).setAction("STOP"))
            RecordService.recording = false
            refresh()
        } else {
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            requestPermissions(perms.toTypedArray(), 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        audio = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), 2)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2 && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, RecordService::class.java)
            i.putExtra("code", resultCode)
            i.putExtra("data", data)
            i.putExtra("audio", audio)
            startForegroundService(i)
            RecordService.recording = true
            refresh()
            moveTaskToBack(true)
        }
    }
}
