package com.example.morro.fahrradwegemonitoringapp

import java.time.ZoneId
import java.util.*

/**
 * This class include all time relatet implementation.
 * Such as Calender and Date abstraction
 * Created by morro on 29.01.2018.
 */

class Time {

    private var calendar : Calendar = Calendar.getInstance()

    /**
     * Prec.: /
     * Post.: return String in the date form "YEAR_MONTH_DAY_HOUR"
     */
    fun getDay(): String{
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return "$year" + "_" + "$month" + "_" + "$day" + "_" + "$hour" + "_" + "$minute"
    }

    /**
     * Prec.: /
     * Postc.: return ms since 1970
     */
    fun getTime(): Long{
        return Date().getTime()
    }


}


