package com.example.actionclassification

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Environment.getExternalStoragePublicDirectory
import android.os.SystemClock.elapsedRealtimeNanos
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import java.lang.ref.WeakReference
import kotlin.concurrent.withLock
import android.view.WindowManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class MainActivity : AppCompatActivity() {
    private lateinit var mSensorManager : SensorManager
    private var mLinear : Sensor ?= null
    private var mGravity : Sensor ?= null
    private var mGyro : Sensor ?= null
    private var isSensing : Boolean = false
    private var isPause : Boolean = false
    private var sensorMonitor: SensorMonitor? = null
    private val samplingPeriodUs = 10_000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // keep screen on because somehow wakelock does not work on keep sensor reading
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mLinear = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        sensorMonitor = SensorMonitor(WeakReference<MainActivity>(this))

        val formatter = DateTimeFormatter.ofPattern("dd_HH_mm")
        val formatted = LocalDateTime.now().format(formatter)
        findViewById<TextView>(R.id.text_view_save_dir).text = "trial_$formatted"

        val btnStartStop = findViewById<Button>(R.id.btn_start_stop)
        val btnPauseResume = findViewById<Button>(R.id.btn_pause_resume)
        btnPauseResume.isEnabled = false

//        fixedRateTimer("sensorLogTimer", true, 1000L, 1000) {
//            println("accel sensor value: " + sensorMonitor?.accelEvents?.lastOrNull())
//            println("gravity sensor value: " + sensorMonitor?.gravityEvents?.lastOrNull())
//            println("gyro sensor value: " + sensorMonitor?.gyroEvents?.lastOrNull())
//        }

        btnStartStop.setOnClickListener {
            isSensing = !isSensing
            if (isSensing) {
                startSensing()
                btnStartStop.text = "Stop"
                btnPauseResume.isEnabled = true
            } else {
                stopSensing()
                resetPauseState()
                btnStartStop.text = "Start"
                btnPauseResume.text = "Pause"
                btnPauseResume.isEnabled = false
            }
        }

        btnPauseResume.setOnClickListener {
            isPause = !isPause
            if (isPause) {
                btnPauseResume.text = "Continue"
                pauseSensing()
            } else {
                btnPauseResume.text = "Pause"
                continueSensing()
            }
        }

    }

    // https://stackoverflow.com/questions/43784161/how-to-implement-finalize-in-kotlin
    protected fun finalize() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun continueSensing() {
        sensorMonitor?.isPause?.set(false)
        setDiscardUntilTime()
    }

    private fun pauseSensing() {
        sensorMonitor?.isPause?.set(true)
        discardLastEvents()
        toastDataSummary(sensorMonitor)
    }

    private fun startSensing() {
        setDiscardUntilTime()
        mSensorManager.registerListener(sensorMonitor, mLinear, samplingPeriodUs, sensorMonitor?.mWorkerHandler)
        mSensorManager.registerListener(sensorMonitor, mGravity, samplingPeriodUs, sensorMonitor?.mWorkerHandler)
        mSensorManager.registerListener(sensorMonitor, mGyro, samplingPeriodUs, sensorMonitor?.mWorkerHandler)
    }

    private fun stopSensing() {
        mSensorManager.unregisterListener(sensorMonitor)
        // if its already in pause state, then the last events already discarded
        if (!isPause) {
            discardLastEvents()
        }

        val activityId = getCurrentActivityId()
        val targetDir = createTargetDir(activityId)
        toastDataSummary(sensorMonitor)
        sensorMonitor?.lock?.withLock {
            saveSensorData(sensorMonitor?.linearEvents, activityId, targetDir, "linear.csv")
            saveSensorData(sensorMonitor?.gravityEvents, activityId, targetDir, "gravity.csv")
            saveSensorData(sensorMonitor?.gyroEvents, activityId, targetDir, "gyro.csv")
            sensorMonitor?.clearAllEvents()
        }
    }

    private fun saveSensorData(events : MutableList<String>?, activityId: Int, targetDir: File, filename : String) {
        val file = FileWriter(File(targetDir, filename), false)
        for (event in events!!) {
            file.append("$activityId,$event")
        }
        file.close()

    }

    private fun createTargetDir(activityId : Int) : File {
        val targetDir = File(getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            findViewById<TextView>(R.id.text_view_save_dir).text.toString() + "/" + activityId)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return targetDir
    }

    private fun getCurrentActivityId(): Int {
        return when (findViewById<RadioGroup>(R.id.rad_btn_activity_selection).checkedRadioButtonId) {
            R.id.rad_btn_walking -> 1
            R.id.rad_btn_running -> 2
            R.id.rad_btn_standing -> 3
            R.id.rad_btn_sitting -> 4
            R.id.rad_btn_upstairs -> 5
            R.id.rad_btn_downstairs -> 6
            R.id.rad_btn_other -> 0
            else -> 0
        }
    }

    private fun setDiscardUntilTime() {
        sensorMonitor?.discardUntilTime?.set(elapsedRealtimeNanos() + getTimeMargin().toLong() * 1_000_000_000)
    }

    private fun discardLastEvents() {
        val numEventsToRemove = getTimeMargin() * 1_000_000 / samplingPeriodUs
//        println("numEventsToRemove: $numEventsToRemove")
        sensorMonitor?.dropLastEvents(numEventsToRemove)
    }

    private fun resetPauseState() {
        isPause = false
        sensorMonitor?.isPause?.set(false)
    }

    private fun getTimeMargin() : Int {
        return Integer.valueOf(findViewById<TextView>(R.id.text_delay_s).text.toString())
    }

    private fun toastDataSummary(sensorMonitor: SensorMonitor?) {
        val toastText = "accel: " + sensorMonitor?.linearEvents?.size +
                "\ngravity: " + sensorMonitor?.gravityEvents?.size +
                "\ngyro: " + sensorMonitor?.gyroEvents?.size +
                "\ntime: " + (sensorMonitor?.linearEvents?.size!! * samplingPeriodUs / 1_000_000)
        Toast.makeText(applicationContext, toastText, Toast.LENGTH_LONG).show()
    }


}