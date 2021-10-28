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
import androidx.core.app.NotificationCompat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import android.widget.Toast


class ActionDetectionService: Service(), SensorEventListener, TextToSpeech.OnInitListener {
    private enum class RawDataType {
        LINEAR_X, LINEAR_Y, LINEAR_Z, GYRO_X, GYRO_Y, GYRO_Z, GRAVITY_X, GRAVITY_Y, GRAVITY_Z
    }

    private lateinit var svmModel : SVCWithParams
    private val ONGOING_NOTIFICATION_ID = 1

    private val CHANNEL_ID = "SensorServiceChannelId"
    private val CHANNEL_NAME = "SensorServiceChannel"
    private val SAMPLING_PERIOD_US = 10_000

    private val DETECTION_WINDOW_SEC = 2
    private val DETECTION_WINDOW_SIZE = (DETECTION_WINDOW_SEC * 1_000_000 / SAMPLING_PERIOD_US).toInt()
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

    private var mDetectorThread = Thread {
        // delay start
        Thread.sleep(5000)

        // cal inference delay only. Total delay will be inference delay + window buffer time
        // the delay is inference delay over 50 sample min
        var cnt = 0
        var sumDelay = 0.0

        while(isDetecting.get()) {
            // detect every 1sec regardless the window detection size
            Thread.sleep(1000)

            val startTime = System.nanoTime()

            // for each type we cal 3 features: mean, std, and energy
            val features = DoubleArray(RawDataType.values().size * 3)
            for ((idx, sensorData) in rawData.withIndex()) {
                val perTypeFeatures = sensorData.extractFeatures()
                features[idx*3] = perTypeFeatures[0]
                features[idx*3+1] = perTypeFeatures[1]
                features[idx*3+2] = perTypeFeatures[2]
                // println("${RawDataType.values()[idx]}: ${features[0]}, ${features[1]}, ${features[2]}")
            }
            val curAction = svmModel.predict(features)
            // println("current action is: ${curAction}")

            if (cnt < 50) {
                val detectionTimeSec = (System.nanoTime() - startTime) / 1_000_000_000.0
                sumDelay += detectionTimeSec
                cnt++
            } else if (cnt == 50) {
             println("average inference delay over ${cnt} detection times is: ${sumDelay/cnt}")
            }

            tts.stop()
            tts.speak(SensorCollector.ActionType.values()[curAction].toString(),
                TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

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

        svmModel = SVCWithParams(assets)

        tts = TextToSpeech(this, this)

        mWorker.start()
        mWorkerHandler = Handler(mWorker.looper)

        initSensors()
        startSensing()

        mDetectorThread.start()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isDetecting.get()) {
            stopSensing()
            isDetecting.set(false)
            tts.stop()
            tts.shutdown()
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
                    rawData[RawDataType.LINEAR_X.ordinal].append(event.values[0].toDouble())
                    rawData[RawDataType.LINEAR_Y.ordinal].append(event.values[1].toDouble())
                    rawData[RawDataType.LINEAR_Z.ordinal].append(event.values[2].toDouble())
                }
                Sensor.TYPE_GYROSCOPE -> {
                    rawData[RawDataType.GYRO_X.ordinal].append(event.values[0].toDouble())
                    rawData[RawDataType.GYRO_Y.ordinal].append(event.values[1].toDouble())
                    rawData[RawDataType.GYRO_Z.ordinal].append(event.values[2].toDouble())
                }
                Sensor.TYPE_GRAVITY -> {
                    rawData[RawDataType.GRAVITY_X.ordinal].append(event.values[0].toDouble())
                    rawData[RawDataType.GRAVITY_Y.ordinal].append(event.values[1].toDouble())
                    rawData[RawDataType.GRAVITY_Z.ordinal].append(event.values[2].toDouble())
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