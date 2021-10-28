package com.example.actionclassification

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.sqrt

class RawDataArray(private val size : Int) {
    private var data = DoubleArray(size)
    private var curSize = 0
    private var curIdx = 0
    private val lock = ReentrantLock()

    fun append(value : Double) {
        lock.withLock {
            data[curIdx++] = value
        }
        if(curIdx >= size) {
            curIdx = 0
        }
    }

    fun extractFeatures(): Array<Double> {
        var dataCopy: DoubleArray

        lock.withLock {
            // this is converted to primitive double in java, so clone here does provide deep copy
            dataCopy = data.clone()
        }
        val mean = calMean(dataCopy)
        val std = calStd(dataCopy, mean)
        val energy = calEnergy(dataCopy)

        return arrayOf(mean, std, energy)
    }

    private fun calMean(arr : DoubleArray): Double {
        var sum = 0.0
        for (x : Double in arr) {
            sum += x
        }
        return sum/arr.size
    }

    private fun calStd(arr : DoubleArray, mean: Double): Double {
        var sum = 0.0
        for (x : Double in arr) {
            sum += (x - mean) * (x - mean)
        }
        return sqrt(sum/arr.size)
    }

    private fun calEnergy(arr : DoubleArray): Double {
        var sum = 0.0
        for (x : Double in arr) {
            sum += x*x
        }
        return sum/arr.size
    }
}