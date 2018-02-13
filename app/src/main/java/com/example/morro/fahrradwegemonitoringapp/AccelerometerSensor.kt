package com.example.morro.fahrradwegemonitoringapp

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast


/**
 *  Mit dieser Klasse können Sensordaten des Beschleunigungssensors ausgelesen werden.
 *  Genutzt werden die daten des Software-Sensors "Linear Accelerometer". Diese beinhalten die Beschleunigungsdaten
 *  ohne Gravitationsdaten.
 * Von Julian Magierski am 13.02.2018 erstellt.
 */

class AccelerometerSensor : SensorEventListener {

    private var mSensorManager: SensorManager? = null
    private var mSensor: Sensor? = null
    private var activity: Activity? = null
    /**
     * Listen zum zwischenspeichern der Beschleunigungssensordaten x,y,z
     */
    private var xAxisList: MutableList<Float>? = null
    private var yAxisList: MutableList<Float>? = null
    private var zAxisList: MutableList<Float>? = null

    /**
     * Boolean Variable die angibt ob Daten Samples gesammelt werden sollen
     */
    private var isDataGatheringActive: Boolean = false

    /**
     * Zähler um due Anzahl der Daten Samples zu zählen
     */
    private var samplesCounter: Int = 0

    constructor(activity: Activity) {
        this.activity = activity
        mSensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
        mSensor = mSensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        mSensorManager?.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL)
        xAxisList = mutableListOf()
        yAxisList = mutableListOf()
        zAxisList = mutableListOf()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    override fun onSensorChanged(event: SensorEvent?) {
        if(event != null && isDataGatheringActive)
            if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                xAxisList?.add(event.values.get(0))
                yAxisList?.add(event.values.get(1))
                zAxisList?.add(event.values.get(2))
                samplesCounter++
            }
    }

    /**
     * Diese Methode startet die Ansammlung von Daten des linearen Beschleunigungssensors
     * Prec.:
     * Postc.: Daten werden erfasst
     */
    fun startDataCollection() {
        if(isDataGatheringActive == false)
            samplesCounter = 0
            isDataGatheringActive = true
    }

    /**
     *
     */
    fun stopDataCollection() : String {
        isDataGatheringActive = false
        return ""
    }
}
