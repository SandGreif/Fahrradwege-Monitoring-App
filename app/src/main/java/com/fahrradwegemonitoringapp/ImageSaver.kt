package com.fahrradwegemonitoringapp
import android.media.Image
import android.util.Log

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Speichert ein JPG Bild in eine Datei.
 */
class ImageSaver {

    /**
     * Die Methode speichert ein Bild ab
     * Postc. Boolean == true, wenn Bild erfolgreich abgespeichert wurde
     */
    fun saveImage(image: Image, file: File): Boolean {
            var imageSaved = true
            var output: FileOutputStream? = null
            try {
                image.height // überprüfung ob das Bild noch existiert
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                output = FileOutputStream(file).apply {
                    write(bytes)
                }
            } catch (eI : IllegalStateException) {
                imageSaved = false
                Log.e("ImageSaver", eI.toString())
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
        return imageSaved
        }
    }


