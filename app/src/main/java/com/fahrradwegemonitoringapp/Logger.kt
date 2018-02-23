package com.fahrradwegemonitoringapp
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

    /**
     * Gibt an ob schon ein Log erstellt wurde
     */
    private var anyLogExists : Boolean = false

    private var time : Time = Time()

    init {
        fileLog = File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "${time.getDay()}_Log.csv")
    }

    /**
     * Ein Log wird in die Logger Datei geschrieben, wenn nock kein Log existiert werden
     * die Spaltennamen zuerst geschrieben. Zu einem Log gehört ein Zeitstempel. Als
     * Argument muss ein StackTraceElement übergeben werden sowie eine Bemerkung.
     * In die Bemerkung sollen zusätzliche Infos geschriben werden.
     * Prec.: traceElement != null,  comment != null
     * Postc.: Text ist geloggt indem Logger mit Zeitstempel
     */
    fun writeToLogger(traceElement : StackTraceElement, comment : String) {
        if(!anyLogExists) {
            fileLog?.appendText("%s,%s,%s,%s,%s\n".format("Zeitstempel","Klassenbezeichner","Methodenbezeichner","Bemerkung","Codezeile"))
            anyLogExists = true
        }
        fileLog?.appendText("%s,%s,%s,%s,%s\n".format(time.getDayMs(),traceElement.className,traceElement.methodName,comment,traceElement.lineNumber))
        }
}