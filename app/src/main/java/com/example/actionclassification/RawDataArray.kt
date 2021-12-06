package com.example.actionclassification

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.sqrt

class RawDataArray(private val size : Int) {
    var data = Array<Float>(size) { 0.0f }
    private var curIdx = 0
    private val lock = ReentrantLock()

    fun append(value : Float) {
        lock.withLock {
            if(curIdx >= size) {
                curIdx = 0
            }
            data[curIdx++] = value
        }
    }

    fun getRawData(): Array<Float> {
        val orderedData = Array<Float>(size) { 0.0f }
        lock.withLock {
            for (i in data.indices) {
                orderedData[i] = data[(curIdx+i)%size]
            }
        }

        return orderedData
    }
}