package com.example.actionclassification

import android.content.Context
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class Model(context: Context, minVal: Float, maxVal: Float, private val inputDim: Long) {
    private val model: Module
    private val valRange = maxVal - minVal

    init {
        model = LiteModuleLoader.load(assetFilePath(context, "model.ptl"))
    }

    fun predict(data: Array<Array<Float>>): Int {
        val normalizedData = data.flatten().map { it / valRange }.toFloatArray()
        val input = Tensor.fromBlob(normalizedData, longArrayOf(1, 1, 9, inputDim))
        val output = model.forward(IValue.from(input)).toTensor().dataAsFloatArray
        return output.indexOfFirst { it == output.maxOfOrNull { it2 -> it2 } }
    }

    private fun assetFilePath(context: Context, asset: String): String {
        val file = File(context.filesDir, asset)

        try {
            val inpStream: InputStream = context.assets.open(asset)
            try {
                val outStream = FileOutputStream(file, false)
                val buffer = ByteArray(4 * 1024)
                var read: Int

                while (true) {
                    read = inpStream.read(buffer)
                    if (read == -1) {
                        break
                    }
                    outStream.write(buffer, 0, read)
                }
                outStream.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

}