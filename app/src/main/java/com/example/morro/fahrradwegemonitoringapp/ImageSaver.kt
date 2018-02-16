package com.example.morro.fahrradwegemonitoringapp

import android.media.Image
import android.util.Log

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Speichert ein JPG Bild in eine Datei.
 */
class ImageSaver {

    fun saveImage(image: Image, file: File) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var output: FileOutputStream? = null
        try {
            output = FileOutputStream(file).apply {
                write(bytes)
            }
        } catch (e: IOException) {
            Log.e("ImageSaver", e.toString())
        } finally {
            image.close()
            output?.let {
                try {
                    it.close()
                } catch (e: IOException) {
                    Log.e("ImageSaver", e.toString())
                }
            }
        }
    }
}


