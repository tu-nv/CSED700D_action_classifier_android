package com.example.actionclassification

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.Handler
import java.lang.ref.WeakReference
import android.os.HandlerThread
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class SensorMonitor(mActivity : WeakReference<MainActivity>): SensorEventListener {
    private val mWorker = HandlerThread("WorkerThread")
    var mWorkerHandler: Handler? = null
    var linearEvents: MutableList<String> = ArrayList()
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
                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        linearEvents.add(formatSensorEvent(event))
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
            // default sensor timepstamp is nanosec, but we want microsec
            event.timestamp/1000,
            event.values[0],
            event.values[1],
            event.values[2],
        )
    }

    fun dropLastEvents(numEventsToRemove: Int) {
        lock.withLock {
            linearEvents = linearEvents.dropLast(numEventsToRemove).toMutableList()
            gravityEvents = gravityEvents.dropLast(numEventsToRemove).toMutableList()
            gyroEvents = gyroEvents.dropLast(numEventsToRemove).toMutableList()
        }
    }

    fun clearAllEvents() {
        linearEvents = ArrayList()
        gravityEvents = ArrayList()
        gyroEvents = ArrayList()
    }
}