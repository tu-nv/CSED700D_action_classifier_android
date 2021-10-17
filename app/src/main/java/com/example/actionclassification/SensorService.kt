package com.example.actionclassification

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.os.Binder
import android.os.Environment
import android.os.SystemClock
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import kotlin.concurrent.withLock

class SensorService: Service() {
    private val ONGOING_NOTIFICATION_ID = 1
    private val CHANNEL_ID = "SensorServiceChannelId"
    private val CHANNEL_NAME = "SensorServiceChannel"
    companion object {
        var isRunning = false
    }

    private val mBinder = SensorBinder()
    private lateinit var mSensorManager : SensorManager
    private lateinit var sensorListener: SensorListener
    private lateinit var mAccel : Sensor
    private lateinit var mGravity : Sensor
    private lateinit var mGyro : Sensor

    lateinit var saveDir : String
    var currentActivityId = 0
    var isSensing : Boolean = false
    var isPause : Boolean = false

    private val timeMargin = 5
    private val samplingPeriodUs = 10_000

    inner class SensorBinder : Binder() {
        fun getService(): SensorService = this@SensorService
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || isRunning) return START_NOT_STICKY
        makeForeground()
        initSensors()
        isRunning = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    private fun initSensors() {
        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        sensorListener = SensorListener()
    }

    private fun makeForeground() {
        val pendingIntent: PendingIntent =
            Intent(this, SensorDataCollectActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }
        val channel = NotificationChannel(CHANNEL_ID,
            CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setContentText("Action Classifier running ...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }

    fun continueSensing() {
        sensorListener.isPause.set(false)
        setDiscardUntilTime()
    }

    fun pauseSensing() {
        sensorListener.isPause.set(true)
        discardLastEvents()
    }

    fun startSensing() {
        setDiscardUntilTime()
        mSensorManager.registerListener(sensorListener, mAccel, samplingPeriodUs,
            sensorListener.mWorkerHandler
        )
        mSensorManager.registerListener(sensorListener, mGravity, samplingPeriodUs,
            sensorListener.mWorkerHandler
        )
        mSensorManager.registerListener(sensorListener, mGyro, samplingPeriodUs,
            sensorListener.mWorkerHandler
        )
    }

    fun stopSensing() {
        mSensorManager.unregisterListener(sensorListener)
        // if its already in pause state, then the last events already discarded
        if (!isPause) {
            discardLastEvents()
        }

        val targetDir = createTargetDir(currentActivityId)
        var toastText = ""
        sensorListener.lock.withLock {
            saveSensorData(sensorListener.accelEvents, currentActivityId, targetDir, "linear.csv")
            saveSensorData(sensorListener.gravityEvents, currentActivityId, targetDir, "gravity.csv")
            saveSensorData(sensorListener.gyroEvents, currentActivityId, targetDir, "gyro.csv")
            toastText = "accel: " + sensorListener.accelEvents.size +
                    "\ngravity: " + sensorListener.gravityEvents.size +
                    "\ngyro: " + sensorListener.gyroEvents.size +
                    "\ntime: " + (sensorListener.accelEvents.size * samplingPeriodUs / 1_000_000)
            sensorListener.clearAllEvents()
        }
        Toast.makeText(applicationContext, toastText, Toast.LENGTH_LONG).show()
    }

    private fun saveSensorData(events : MutableList<String>?, activityId: Int, targetDir: File, filename : String) {
        val file = FileWriter(File(targetDir, filename), false)
        for (event in events!!) {
            file.append("$activityId,$event")
        }
        file.close()

    }

    private fun createTargetDir(activityId : Int) : File {
        val targetDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "$saveDir/$activityId"
        )
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return targetDir
    }

    private fun setDiscardUntilTime() {
        sensorListener.discardUntilTime.set(SystemClock.elapsedRealtimeNanos() + timeMargin.toLong() * 1_000_000_000)
    }

    private fun discardLastEvents() {
        val numEventsToRemove = timeMargin * 1_000_000 / samplingPeriodUs
        sensorListener.dropLastEvents(numEventsToRemove)
    }

    fun resetPauseState() {
        isPause = false
        sensorListener.isPause.set(false)
    }
}