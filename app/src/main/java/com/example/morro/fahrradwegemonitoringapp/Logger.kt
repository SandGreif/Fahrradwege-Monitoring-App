package com.example.morro.fahrradwegemonitoringapp

import android.os.Environment
import java.io.File

/**
 * Die Logger Klasse ist als Kotlin Singleton implementiert.
 * Created by morro on 31.01.2018.
 */
object Logger {

    /**
     * Datei in dem Logger Imformationen geschrieben werden können
     */
    private var fileLog : File? = null

    private var time : Time = Time()

    init {
        fileLog = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "${time.getDay()}_Log.csv")
    }

    /**
     * Prec.: txt ist != null
     * Postc.: Text ist geloggt indem Logger mit Zeitstempel
     */
    fun writeToLogger(txt : String) {
        fileLog?.appendText("${time.getDay()}," + txt)
    }
}