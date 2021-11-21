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
            data[curIdx++] = value
        }
        if(curIdx >= size) {
            curIdx = 0
        }
    }

    fun getNormalizedData(): Array<Float> {
        lock.withLock {
            // this is converted to primitive double in java, so clone here does provide deep copy
            return data.clone()
        }
    }
}