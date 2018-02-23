package com.fahrradwegemonitoringapp

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
     * Post.: return String in the date form "YEAR_MONTH_DAY_HOUR_MINUTE"
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
     * Prec.: /
     * Post.: return String in the date form "YEAR_MONTH_DAY_HOUR_MINUTE_SECOND_MS"
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
     * Prec.: /
     * Postc.: return ms since 1970
     */
    fun getTime(): Long{
        return Date().time
    }


}


