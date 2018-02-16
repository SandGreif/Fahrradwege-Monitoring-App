package com.fahrradwegemonitoringapp

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager import android.view.Display
import android.view.Surface
import android.view.WindowManager
import java.lang.Math.abs
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.sqrt


/**
 *  Mit dieser Klasse können Sensordaten des Beschleunigungssensors ausgelesen werden.
 *  Genutzt werden die daten des "Accelerometer" und des "Magnetic Field" Sensors. Die Beschleunigungssensordaten
 *  beinhalten die Beschleunigungsdaten mit Gravitationsdaten. Die Berechnung von Gier Nick Roll kommt aus diesem Tutorial:
 *  https://github.com/google-developer-training/android-advanced/tree/master/TiltSpot
 * Von Julian Magierski am 13.02.2018 erstellt.
 */

class AccelerometerData : SensorEventListener {

    private var mSensorManager: SensorManager? = null
    private var mAccelerometer: Sensor? = null
    private var mMagneticField: Sensor? = null
    private var activity: Activity? = null
    /**
     * Listen zum zwischenspeichern der Beschleunigungssensordaten x,y,z
     */
    private var xAxisList: MutableList<Float>? = null
    private var yAxisList: MutableList<Float>? = null
    private var zAxisList: MutableList<Float>? = null

    private var azimuthList: MutableList<Float>? = null
    private var pitchList: MutableList<Float>? = null
    private var rollList: MutableList<Float>? = null

    // Current data from accelerometer & magnetometer.  The arrays hold values
    // for X, Y, and Z.
    private var mAccelerometerData = FloatArray(3)
    private var mMagnetometerData = FloatArray(3)

    /**
     * Um Datenwerte aufzurunden auf 5 Kommastellen
     */
    private var  df = DecimalFormat("#.#####")

    /**
     * Boolean Variable die angibt ob Daten Samples gesammelt werden sollen
     */
    private var isDataGatheringActive: Boolean = false

    // System display. Need this for determining rotation.
    private var mDisplay: Display? = null

    /**
     * Zähler um due Anzahl der Daten Samples zu zählen
     */
    private var samplesCounter: Int = 0

    private var lock = ReentrantLock()

    fun init(activity: Activity) {
        // Datenwerte sollen aufgerunded werden auf 5 Kommastellen
        df.roundingMode = RoundingMode.CEILING
        this.activity = activity
        mSensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
        mAccelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mSensorManager?.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST)
        mMagneticField = mSensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        mSensorManager?.registerListener(this, mMagneticField, SensorManager.SENSOR_DELAY_FASTEST)
        xAxisList = mutableListOf()
        yAxisList = mutableListOf()
        zAxisList = mutableListOf()
        azimuthList = mutableListOf()
        pitchList = mutableListOf()
        rollList = mutableListOf()
        // Get the display from the window manager (for rotation).
        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager?
        mDisplay = wm!!.defaultDisplay
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && isDataGatheringActive) {
            lock.lock()
            try {
                val sensorType = event.sensor.type
                when (sensorType) {
                    Sensor.TYPE_ACCELEROMETER -> mAccelerometerData = event.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD -> mMagnetometerData = event.values.clone()
                    else -> return
                }
                // Compute the rotation matrix: merges and translates the data
                // from the accelerometer and magnetometer, in the device coordinate
                // system, into a matrix in the world's coordinate system.
                //
                // The second argument is an inclination matrix, which isn't
                // used in this example.
                val rotationMatrix = FloatArray(9)
                val rotationOK = SensorManager.getRotationMatrix(rotationMatrix,
                        null, mAccelerometerData, mMagnetometerData)

                // Remap the matrix based on current device/activity rotation.
                var rotationMatrixAdjusted = FloatArray(9)
                when (mDisplay?.rotation) {
                    Surface.ROTATION_0 -> rotationMatrixAdjusted = rotationMatrix.clone()
                    Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(rotationMatrix,
                            SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X,
                            rotationMatrixAdjusted)
                    Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(rotationMatrix,
                            SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y,
                            rotationMatrixAdjusted)
                    Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(rotationMatrix,
                            SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X,
                            rotationMatrixAdjusted)
                }

                // Get the orientation of the device (azimuth, pitch, roll) based
                // on the rotation matrix. Output units are radians.
                val orientationValues = FloatArray(3)
                if (rotationOK) {
                    SensorManager.getOrientation(rotationMatrixAdjusted,
                            orientationValues)
                }

                // Pull out the individual values from the array.
                azimuthList?.add(orientationValues[0])
                pitchList?.add(orientationValues[1])
                rollList?.add(orientationValues[2])
                xAxisList?.add(event.values[0])
                yAxisList?.add(event.values[1])
                zAxisList?.add(event.values[2])
                samplesCounter++
            } finally {
                lock.unlock()
            }
        }
    }

    /**
     * Diese Methode startet die Ansammlung von Daten des linearen Beschleunigungssensors
     * Prec.:
     * Postc.: Daten werden erfasst
     */
    fun startDataCollection() {
        if(!isDataGatheringActive)
            samplesCounter = 0
        isDataGatheringActive = true
    }

    /**
     * Diese Funktion stopt die Datenerfassung dieser Klasse.
     * Prec.:
     * Postc.: Es werden keine Daten mehr erfasst
     */
    fun stopDataCollection() {
        if(isDataGatheringActive) {
            isDataGatheringActive = false
        }
    }

    /*
    * Von den erfasten Daten wird der Mittelwert die Varianz und die Standardabweichung berechnet.
    * Der Rückgabe-Sting hat folgende Formattierung: Mittelwert von X, Varianz von X, Standardabweichung von X,
    * Mittelwert von Y, Varianz von Y, Standardabweichung von Y, Mittelwert von Z, Varianz von Z, Standardabweichung von Z,
    * Azimuth, Pitch, Roll
    *  Prec.:
     * Postc.: Gibt berechnete Daten als String zurück, wenn isDataGatheringActive ist true ansonsten null
    */
    fun getData() : String? {
        if(!isDataGatheringActive) {
            lock.lock()
            try {
                // Berechne Mittelwert, Variant und Standardabweichung
                val meanX = calculateMean(xAxisList)
                val varianceX = calculateVariance(meanX, xAxisList)
                val standardDevX = calculateStandardDeviation(varianceX)
                val meanY = calculateMean(yAxisList)
                val varianceY = calculateVariance(meanY, yAxisList)
                val standardDevY = calculateStandardDeviation(varianceY)
                val meanZ = calculateMean(zAxisList)
                val varianceZ = calculateVariance(meanZ, zAxisList)
                val standardDevZ = calculateStandardDeviation(varianceZ)
                // Berechne Mittelwert für Gier-Nick-Roll
                val meanAzimuth = calculateMean(azimuthList)
                val meanPitch = calculateMean(pitchList)
                val meanRoll = calculateMean(rollList)
                // Representation der erfassten Daten als String. Kommas werden durch Punkte ersetzt.
                return df.format(meanX).replace(',', '.') + "," + df.format(varianceX).replace(',', '.') + "," +
                        df.format(standardDevX).replace(',', '.') + "," + df.format(meanY).replace(',', '.') + "," +
                        df.format(varianceY).replace(',', '.') + "," + df.format(standardDevY).replace(',', '.') + "," +
                        df.format(meanZ).replace(',', '.') + "," + df.format(varianceZ).replace(',', '.') + "," +
                        df.format(standardDevZ).replace(',', '.') + "," + df.format(meanAzimuth).replace(',', '.') + "," +
                        df.format(meanPitch).replace(',', '.') + "," + df.format(meanRoll).replace(',', '.')
            } finally {
                lock.unlock()
            }
        }
        return null
    }

    /**
     * Die Funktion entfernt alle Elemente aus den Datenlisten:
     * xAxisList, yAxisList, zAxisList, azimuthList, pitchList, rollList
     * Prec.:
     * Postc.: Die angegebenen Listen haben keine Elemente mehr
     */
    fun clearData() {
        xAxisList?.clear()
        yAxisList?.clear()
        zAxisList?.clear()
        azimuthList?.clear()
        pitchList?.clear()
        rollList?.clear()
    }

    /**
     * Berechnet den  Mittelwert über die Float Elemente einer Liste.
     * Prec.:
     * Postc.: Der Mittelwert über die Float Elemente der übergebenen Liste,
     * wenn Anzahl der Elemente größer 0 ist
     */
    fun calculateMean(list : MutableList<Float>?) : Float {
        var sum = 0f
        val samples = list?.size
        val result : Float
        if (samples != null) {
            if (samples > 0) {
                list.forEach {
                    sum += it
                }
                result =  sum / samples
            } else {
                result = 0f
            }
        } else {
            result = 0f
        }
        return result
    }

    /**
     * Berechnet die Varianz. Dieser Funktion muss als Paramter der Mittelwert (mean) und die Liste mit
     * den Float Werten übergeben werden, um die Varianz zu berechnen. Als Varianz wird der Durchschnitt der quadrierten
     * Differenzen zum Mittelwert bezeichnet.
     * Prec.:
     * Postc.: Gibt die berechnete Variance als Float zurück oder 0 wenn die Anzahl der Werte <= 0
     */
    fun calculateVariance(mean : Float, list : MutableList<Float>?) : Float {
        var sum = 0f
        val samples = list?.size
        var tempDifference : Float
        val result : Float
        if (samples != null) {
            if (samples > 0) {
                list.forEach {
                    tempDifference = it-mean
                    sum += tempDifference * tempDifference
                }
                result = sum / samples
            } else {
                result = 0f
            }} else {
            result = 0f
        }
        return result
    }

    /**
     * Hier wird die Standardabweichung der Varianz berechnet. Dies ist die Wurzel von der Varianz.
     * Prec.:
     * Postc.:  Standardabweichung wird zurückgegeben. Für die Berechnung wird der Absolutwert von der Varianz genommen.
     */
    fun calculateStandardDeviation(variance : Float) : Float{
        val x = abs(variance)
        var result : Float
        var wasNegative = false
        if(variance < 0.0)
            wasNegative = true
        result = sqrt(x)
        if(wasNegative)
            result *= -1
        return result
    }
}
