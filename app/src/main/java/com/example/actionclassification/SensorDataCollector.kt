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


class SensorDataCollector : AppCompatActivity() {
    private lateinit var mSensorManager : SensorManager
    private lateinit var mLinear : Sensor
    private lateinit var mGravity : Sensor
    private lateinit var mGyro : Sensor
    private var isSensing : Boolean = false
    private var isPause : Boolean = false
    private lateinit var sensorCollectorListener: SensorCollectorListener
    private val samplingPeriodUs = 10_000
    private val delayStartEarlyStopTimeSec = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_data_collector)
        // keep screen on because somehow wakelock does not work on keep sensor reading
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mLinear = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        sensorCollectorListener = SensorCollectorListener()

        val formatter = DateTimeFormatter.ofPattern("dd_HH_mm")
        val formatted = LocalDateTime.now().format(formatter)
        findViewById<TextView>(R.id.text_view_save_dir).text = "trial_$formatted"

        val btnStartStop = findViewById<Button>(R.id.btn_start_stop)
        val btnPauseResume = findViewById<Button>(R.id.btn_pause_resume)
        btnPauseResume.isEnabled = false

        btnStartStop.setOnClickListener {
            if (!isSensing) {
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
            isSensing = !isSensing
        }

        btnPauseResume.setOnClickListener {
            if (!isPause) {
                btnPauseResume.text = "Continue"
                pauseSensing()
            } else {
                btnPauseResume.text = "Pause"
                continueSensing()
            }
            isPause = !isPause
        }

    }

    // https://stackoverflow.com/questions/43784161/how-to-implement-finalize-in-kotlin
    protected fun finalize() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (isSensing) {
            stopSensing()
        }
    }

    private fun continueSensing() {
        sensorCollectorListener.isPause.set(false)
        setDiscardUntilTime()
    }

    private fun pauseSensing() {
        sensorCollectorListener.isPause.set(true)
        discardLastEvents()
        toastDataSummary(sensorCollectorListener)
    }

    private fun startSensing() {
        setDiscardUntilTime()
        mSensorManager.registerListener(sensorCollectorListener, mLinear, samplingPeriodUs, sensorCollectorListener?.mWorkerHandler)
        mSensorManager.registerListener(sensorCollectorListener, mGravity, samplingPeriodUs, sensorCollectorListener?.mWorkerHandler)
        mSensorManager.registerListener(sensorCollectorListener, mGyro, samplingPeriodUs, sensorCollectorListener?.mWorkerHandler)
    }

    private fun stopSensing() {
        mSensorManager.unregisterListener(sensorCollectorListener)
        // if its already in pause state, then the last events already discarded
        if (!isPause) {
            discardLastEvents()
        }

        val activityId = getCurrentActivityId()
        val targetDir = createTargetDir(activityId)
        toastDataSummary(sensorCollectorListener)
        sensorCollectorListener.lock.withLock {
            saveSensorData(sensorCollectorListener.linearEvents, activityId, targetDir, "linear.csv")
            saveSensorData(sensorCollectorListener.gravityEvents, activityId, targetDir, "gravity.csv")
            saveSensorData(sensorCollectorListener.gyroEvents, activityId, targetDir, "gyro.csv")
            sensorCollectorListener.clearAllEvents()
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
        sensorCollectorListener.discardUntilTime.set(elapsedRealtimeNanos() + delayStartEarlyStopTimeSec.toLong() * 1_000_000_000)
    }

    private fun discardLastEvents() {
        val numEventsToRemove = delayStartEarlyStopTimeSec * 1_000_000 / samplingPeriodUs
//        println("numEventsToRemove: $numEventsToRemove")
        sensorCollectorListener.dropLastEvents(numEventsToRemove)
    }

    private fun resetPauseState() {
        isPause = false
        sensorCollectorListener.isPause.set(false)
    }


    private fun toastDataSummary(sensorCollectorListener: SensorCollectorListener?) {
        val toastText = "accel: " + sensorCollectorListener?.linearEvents?.size +
                "\ngravity: " + sensorCollectorListener?.gravityEvents?.size +
                "\ngyro: " + sensorCollectorListener?.gyroEvents?.size +
                "\ntime: " + (sensorCollectorListener?.linearEvents?.size!! * samplingPeriodUs / 1_000_000)
        Toast.makeText(applicationContext, toastText, Toast.LENGTH_LONG).show()
    }


}