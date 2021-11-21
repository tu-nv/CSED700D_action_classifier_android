package com.example.actionclassification


import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.timerTask


class ActionDetectionService: Service(), SensorEventListener, TextToSpeech.OnInitListener {
    private enum class RawDataType {
        LINEAR_X, LINEAR_Y, LINEAR_Z, GYRO_X, GYRO_Y, GYRO_Z, GRAVITY_X, GRAVITY_Y, GRAVITY_Z
    }

    private lateinit var model : Model
    private val ONGOING_NOTIFICATION_ID = 1

    private val CHANNEL_ID = "SensorServiceChannelId"
    private val CHANNEL_NAME = "SensorServiceChannel"
    private val SAMPLING_PERIOD_US = 10_000

    private val DETECTION_WINDOW_SEC = 2
    val DETECTION_WINDOW_SIZE = (DETECTION_WINDOW_SEC * 1_000_000 / SAMPLING_PERIOD_US).toInt()
    private val DETECTION_PERIOD_MS : Long = 2000

    private var rawData = Array(RawDataType.values().size) { RawDataArray(DETECTION_WINDOW_SIZE)}

    private lateinit var tts : TextToSpeech

    private val mBinder = SensorBinder()
    private lateinit var mSensorManager : SensorManager
    private lateinit var mAccel : Sensor
    private lateinit var mGravity : Sensor

    private lateinit var mGyro : Sensor
    private val mWorker = HandlerThread("WorkerThread")
    private lateinit var mWorkerHandler: Handler

    var isDetecting = AtomicBoolean(false)

    private lateinit var wakeLock: PowerManager.WakeLock

    private lateinit var timer : Timer

    // cal inference delay only. Total delay will be inference delay + window buffer time
    // the delay is inference delay over 50 sample min
    private var detectionCnt = 0
    private var sumDelay = 0.0

    inner class SensorBinder : Binder() {
        fun getService(): ActionDetectionService = this@ActionDetectionService
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        makeForeground()
        isDetecting.set(true)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ActionClassifier::WakeLockTag").apply {
                    acquire(30*60*1000L /*30 minutes*/)
                }
            }

        model = Model(this, -86.58585357666016f, 119.1611099243164f)

        tts = TextToSpeech(this, this)

        mWorker.start()
        mWorkerHandler = Handler(mWorker.looper)

        initSensors()
        startSensing()

        timer = Timer()
        timer.scheduleAtFixedRate(timerTask {
            val startTime = System.nanoTime()
            val data = rawData.map { it.getNormalizedData() }.toTypedArray()
            val curAction = model.predict(data)
            // println("current action is: ${curAction}")

            if (detectionCnt < 50) {
                val detectionTimeSec = (System.nanoTime() - startTime) / 1_000_000_000.0
                sumDelay += detectionTimeSec
                detectionCnt++
            } else if (detectionCnt == 50) {
                println("average inference delay over ${detectionCnt} detection times is: ${sumDelay/detectionCnt}")
                detectionCnt++
            }

            tts.stop()
            tts.speak(SensorCollector.ActionType.values()[curAction].toString(),
                TextToSpeech.QUEUE_FLUSH, null, null)

        },5000, DETECTION_PERIOD_MS)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isDetecting.get()) {
            stopSensing()
            isDetecting.set(false)
            timer.cancel()
            timer.purge()
            tts.stop()
            tts.shutdown()
            wakeLock.release()
        }

        mWorker.quitSafely()
    }


    private fun initSensors() {
        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        mGravity = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    private fun makeForeground() {
        val pendingIntent: PendingIntent =
            Intent(this, ActionDetectionService::class.java).let { notificationIntent ->
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

    private fun startSensing() {
        mSensorManager.registerListener(this, mAccel, SAMPLING_PERIOD_US, mWorkerHandler)
        mSensorManager.registerListener(this, mGravity, SAMPLING_PERIOD_US, mWorkerHandler)
        mSensorManager.registerListener(this, mGyro, SAMPLING_PERIOD_US, mWorkerHandler)
    }

    private fun stopSensing() {
        mSensorManager.unregisterListener(this)
    }



    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            when (event.sensor?.type) {
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    rawData[RawDataType.LINEAR_X.ordinal].append(event.values[0])
                    rawData[RawDataType.LINEAR_Y.ordinal].append(event.values[1])
                    rawData[RawDataType.LINEAR_Z.ordinal].append(event.values[2])
                }
                Sensor.TYPE_GYROSCOPE -> {
                    rawData[RawDataType.GYRO_X.ordinal].append(event.values[0])
                    rawData[RawDataType.GYRO_Y.ordinal].append(event.values[1])
                    rawData[RawDataType.GYRO_Z.ordinal].append(event.values[2])
                }
                Sensor.TYPE_GRAVITY -> {
                    rawData[RawDataType.GRAVITY_X.ordinal].append(event.values[0])
                    rawData[RawDataType.GRAVITY_Y.ordinal].append(event.values[1])
                    rawData[RawDataType.GRAVITY_Z.ordinal].append(event.values[2])
                }
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(applicationContext, "TTS: Language not supported", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(applicationContext, "Init TTS failed", Toast.LENGTH_SHORT).show()
        }
    }
}