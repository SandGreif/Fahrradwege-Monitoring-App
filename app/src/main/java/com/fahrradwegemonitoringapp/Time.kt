package com.fahrradwegemonitoringapp

import java.util.*

/**
 * Diese Klasse beinhaltet bietet die Möglichkeit verschiedene Zeitwerte Zeitformate zu erhalten.
 * Wie das aktuelle Datum und die Unixzeit
 * Created by morro on 29.01.2018.
 */

class Time {

    private var calendar : Calendar = Calendar.getInstance()

    /**
     * Aktuelles Datum des gregorianischen Kalenders in dem Format "YEAR_MONTH_DAY_HOUR_MINUTE"
     * Prec.: /
     * Post.: Gibt Datum als String zurück
     */
    fun getDay(): String{
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return "%d_%d_%d_%d_%d".format(year,month,day,hour,minute)
    }

    /**
     * Aktuelles Datum des gregorianischen Kalenders in dem Format "YEAR_MONTH_DAY_HOUR_MINUTE_SECOND_MS"
     * Post.: Gibt Datum als String zurück
     */
    fun getDayMs(): String{
        calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        val ms = calendar.get(Calendar.MILLISECOND)
        return "%d_%d_%d_%d_%d_%d_%d".format(year,month,day,hour,minute,second,ms)
    }

    /**
     * Gibt die Unixzeit zurück oder auch "Epoch" genannt
     * Prec.: /
     * Postc.: Gibt die vergangene Zeit in Ms als String zurück
     */
    fun getTime(): Long{
        return Date().time
    }

}


