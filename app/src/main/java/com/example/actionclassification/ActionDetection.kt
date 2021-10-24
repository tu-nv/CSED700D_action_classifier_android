package com.example.actionclassification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import androidx.core.content.ContextCompat

class ActionDetection : AppCompatActivity() {
    private lateinit var actionDetectionService: ActionDetectionService
    private lateinit var detectionServiceIntent: Intent
    private var isBound : Boolean = false

    /** Defines callbacks for service binding, passed to bindService()  */
    private val detectionServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ActionDetectionService.SensorBinder
            actionDetectionService = binder.getService()
            isBound = true

            val btnActionDetection = findViewById<Button>(R.id.btn_action_detection)
            if(actionDetectionService.isDetecting.get()) {
                btnActionDetection.text = "Stop detecting"
            } else {
                btnActionDetection.text = "Start detecting"
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_action_detection)

        detectionServiceIntent = Intent(this, ActionDetectionService::class.java)

        val btnActionDetection = findViewById<Button>(R.id.btn_action_detection)
        btnActionDetection.setOnClickListener {
            if(!isBound || !actionDetectionService.isDetecting.get()) {
                ContextCompat.startForegroundService(this, detectionServiceIntent)
                doBindDetectionService()
                btnActionDetection.text = "Stop detecting"
            } else {
                doUnbindDetectionService()
                stopService(detectionServiceIntent)
                btnActionDetection.text = "Start detecting"
            }
        }

        val btnCollectDataActivity = findViewById<Button>(R.id.btn_collect_data_activity)
        btnCollectDataActivity.setOnClickListener {
            val intent = Intent(this, SensorDataCollector::class.java)
            startActivity(intent)
        }

    }

    override fun onStart() {
        super.onStart()
        doBindDetectionService()
    }

    override fun onStop() {
        super.onStop()
        doUnbindDetectionService()
    }

    private fun doBindDetectionService() {
        if(!isBound) {
            bindService(detectionServiceIntent, detectionServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun doUnbindDetectionService() {
        if(isBound) {
            unbindService(detectionServiceConnection)
            isBound = false
        }
    }
}