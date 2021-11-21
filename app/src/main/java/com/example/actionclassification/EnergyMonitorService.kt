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
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.timerTask
import android.view.LayoutInflater
import androidx.lifecycle.MutableLiveData

class EnergyMonitorService: Service() {

    private val ONGOING_NOTIFICATION_ID = 2

    private val CHANNEL_ID = "EnergyMonitorServiceChannelId"
    private val CHANNEL_NAME = "EnergyMonitorServiceChannel"

    var isDetecting = AtomicBoolean(false)
    private lateinit var wakeLock: PowerManager.WakeLock

    private lateinit var timer : Timer
    private lateinit var mBatteryManager : BatteryManager

    private val ENERGY_SAMPLE_PERIOD_MS : Long = 500
    private val ENERGY_MEASUREMENT_TIME_SEC : Int = 200
    private var energy_me_start_ns : Long = 0
    private var energy_me_samples = mutableListOf<Int>()

    companion object {
        var energyMonResult = MutableLiveData<String>()
    }

    override fun onBind(intent: Intent): IBinder? {return null}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || isDetecting.get()) return START_NOT_STICKY

        makeForeground()
        isDetecting.set(true)

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ActionClassifier::EnergyMonitorTag").apply {
                acquire(30*60*1000L /*30 minutes*/)
            }
        }

        mBatteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager

        timer = Timer()
        timer.scheduleAtFixedRate(timerTask {
            if (energy_me_start_ns == 0L) {
                energy_me_start_ns = System.nanoTime()
                energyMonResult.postValue("Finish delay start. Monitoring now ...")
            }

            val currentMicroA = -mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            energy_me_samples.add(currentMicroA)
            // energyMonResult.postValue("current: ${currentMicroA} uA")

            if ((System.nanoTime() - energy_me_start_ns) / 1_000_000_000 >= ENERGY_MEASUREMENT_TIME_SEC) {
                val powerConsMicroW = energy_me_samples.average() * 3.8f
                val num_samples = energy_me_samples.size
                energyMonResult.postValue("${powerConsMicroW} uW over ${ENERGY_MEASUREMENT_TIME_SEC} secs with ${num_samples} samples")

                timer.cancel()
                super.stopSelf()
            }

        },8000, ENERGY_SAMPLE_PERIOD_MS)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isDetecting.get()) {
            isDetecting.set(false)
            timer.cancel()
            timer.purge()
            wakeLock.release()
        }
    }


    private fun makeForeground() {
        val pendingIntent: PendingIntent =
            Intent(this, EnergyMonitorService::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }
        val channel = NotificationChannel(CHANNEL_ID,
            CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setContentText("Energy Monitoring running ...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }
}