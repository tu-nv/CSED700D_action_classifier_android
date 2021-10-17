package com.example.actionclassification

import android.content.ComponentName
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.core.content.ContextCompat

import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.app.ActivityManager





class SensorDataCollectActivity : AppCompatActivity() {


    private lateinit var btnStartStop: Button
    private lateinit var btnPauseResume: Button
    private lateinit var radBtnActivitySelection: RadioGroup
    private lateinit var textViewSaveDir: TextView

    private lateinit var sensorService: SensorService
    private var mBound: Boolean = false

    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as SensorService.SensorBinder
            sensorService = binder.getService()
            restoreActivityState(sensorService)
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    private fun restoreActivityState(sensorService: SensorService) {
        // if service is running, get current sensing state to set UI accordingly
        if (!SensorService.isRunning || !sensorService.isSensing) return

        btnStartStop.text = "Stop"
        if (sensorService.isPause) {
            btnPauseResume.text = "Continue"
            btnPauseResume.isEnabled = true
        } else {
            btnPauseResume.text = "Pause"
            btnPauseResume.isEnabled = false
        }

        textViewSaveDir.text = sensorService.saveDir
        radBtnActivitySelection.check(getRadBtnIdFromActivityId(sensorService.currentActivityId))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        radBtnActivitySelection = findViewById(R.id.rad_btn_activity_selection)
        textViewSaveDir = findViewById(R.id.text_view_save_dir)
        btnStartStop = findViewById(R.id.btn_start_stop)
        btnPauseResume = findViewById(R.id.btn_pause_resume)

        val formatter = DateTimeFormatter.ofPattern("dd_HH_mm")
        val formatted = LocalDateTime.now().format(formatter)
        val saveDir = "trial_$formatted"
        textViewSaveDir.text = saveDir

        btnPauseResume.isEnabled = false

        // start sensorService
        if (!SensorService.isRunning) {
            val serviceIntent = Intent(this, SensorService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        }

//        fixedRateTimer("sensorLogTimer", true, 1000L, 1000) {
//            println("accel sensor value: " + sensorMonitor?.accelEvents?.lastOrNull())
//            println("gravity sensor value: " + sensorMonitor?.gravityEvents?.lastOrNull())
//            println("gyro sensor value: " + sensorMonitor?.gyroEvents?.lastOrNull())
//        }

        btnStartStop.setOnClickListener {
            sensorService.isSensing = !sensorService.isSensing
            if (sensorService.isSensing) {
                sensorService.startSensing()
                btnStartStop.text = "Stop"
                btnPauseResume.isEnabled = true
            } else {
                sensorService.stopSensing()
                sensorService.resetPauseState()
                btnStartStop.text = "Start"
                btnPauseResume.text = "Pause"
                btnPauseResume.isEnabled = false

                unbindService(connection)
                mBound = false
                val sensorServiceIntent = Intent(this, SensorService::class.java)
                stopService(sensorServiceIntent)
            }
        }

        btnPauseResume.setOnClickListener {
            sensorService.isPause = !sensorService.isPause
            if (sensorService.isPause) {
                btnPauseResume.text = "Continue"
                sensorService.pauseSensing()
            } else {
                btnPauseResume.text = "Pause"
                sensorService.continueSensing()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, SensorService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }

    private fun getCurrentActivityId(): Int {
        return when (radBtnActivitySelection.checkedRadioButtonId) {
            R.id.rad_btn_walking -> 1
            R.id.rad_btn_running -> 2
            R.id.rad_btn_standing -> 3
            R.id.rad_btn_sitting -> 4
            R.id.rad_btn_upstairs -> 5
            R.id.rad_btn_downstairs -> 6
            else -> 0
        }
    }

    private fun getRadBtnIdFromActivityId(activityId: Int): Int {
        return when (activityId) {
            1 -> R.id.rad_btn_walking
            2 -> R.id.rad_btn_running
            3 -> R.id.rad_btn_standing
            4 -> R.id.rad_btn_sitting
            5 -> R.id.rad_btn_upstairs
            6 -> R.id.rad_btn_downstairs
            else -> R.id.rad_btn_walking // set walking to default
        }
    }
}