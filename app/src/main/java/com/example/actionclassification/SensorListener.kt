package com.example.actionclassification

import android.app.Service
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.Handler
import com.google.android.material.theme.MaterialComponentsViewInflater
import java.lang.ref.WeakReference
import android.os.HandlerThread
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class SensorListener : SensorEventListener {
    private val mWorker = HandlerThread("WorkerThread")
    var mWorkerHandler: Handler
    var accelEvents: MutableList<String> = ArrayList()
    var gravityEvents: MutableList<String> = ArrayList()
    var gyroEvents: MutableList<String> = ArrayList()

    var isPause: AtomicBoolean = AtomicBoolean(false)
    var discardUntilTime: AtomicLong = AtomicLong(0)

    val lock = ReentrantLock()

    init {
        mWorker.start()
        mWorkerHandler = Handler(mWorker.looper)
    }

    // https://stackoverflow.com/questions/43784161/how-to-implement-finalize-in-kotlin
    protected fun finalize() {
        mWorker.quitSafely()
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (isPause.get()) return
        if (event == null) return
        if (event.timestamp < discardUntilTime.get()) return


        if (lock.tryLock()) {
            try {
                when (event.sensor?.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        accelEvents.add(formatSensorEvent(event))
                    }
                    Sensor.TYPE_GRAVITY -> {
                        gravityEvents.add(formatSensorEvent(event))
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        gyroEvents.add(formatSensorEvent(event))
                    }
                }
            } finally {
                lock.unlock()
            }
        }
    }

    private fun formatSensorEvent(event: SensorEvent): String {
        return String.format(
            "%d,%.9e,%.9e,%.9e\n",
            // save timestamp in millisecond unit
            event.timestamp/1000000,
            event.values[0],
            event.values[1],
            event.values[2],
        )
    }

    fun dropLastEvents(numEventsToRemove: Int) {
        lock.withLock {
            accelEvents = accelEvents.dropLast(numEventsToRemove).toMutableList()
            gravityEvents = gravityEvents.dropLast(numEventsToRemove).toMutableList()
            gyroEvents = gyroEvents.dropLast(numEventsToRemove).toMutableList()
        }
    }

    fun clearAllEvents() {
        accelEvents = ArrayList()
        gravityEvents = ArrayList()
        gyroEvents = ArrayList()
    }
}