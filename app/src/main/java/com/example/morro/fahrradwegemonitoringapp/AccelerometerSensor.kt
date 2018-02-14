package com.example.morro.fahrradwegemonitoringapp

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import java.lang.Math.abs
import java.lang.Math.sqrt
import kotlin.math.sqrt


/**
 *  Mit dieser Klasse können Sensordaten des Beschleunigungssensors ausgelesen werden.
 *  Genutzt werden die daten des Sensors "Accelerometer". Diese beinhalten die Beschleunigungsdaten
 *  mit Gravitationsdaten. Die Berechnung von Gier Nick Roll kommt aus diesem Tutorial:
 *  https://github.com/google-developer-training/android-advanced/tree/master/TiltSpot
 * Von Julian Magierski am 13.02.2018 erstellt.
 */

class AccelerometerSensor : SensorEventListener {

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
     * Boolean Variable die angibt ob Daten Samples gesammelt werden sollen
     */
    private var isDataGatheringActive: Boolean = false

    // System display. Need this for determining rotation.
    private var mDisplay: Display? = null

    /**
     * Zähler um due Anzahl der Daten Samples zu zählen
     */
    private var samplesCounter: Int = 0

    constructor(activity: Activity) {
        this.activity = activity
        mSensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
        mAccelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mSensorManager?.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        mMagneticField = mSensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        mSensorManager?.registerListener(this, mMagneticField, SensorManager.SENSOR_DELAY_NORMAL)
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
            val sensorType = event.sensor.getType()
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
            when (mDisplay?.getRotation()) {
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
     * Diese Funktion stopt die Datenerfassung dieser Klasse.
     *
     * Prec.:
     * Postc.: Gibt berechnete Daten als zurück, wenn isDataGatheringActive ist true
     */
    fun stopDataCollection() : String? {
        if(isDataGatheringActive) {
            isDataGatheringActive = false
            // Berechne Mittelwert, Variant und Standardabweichung
            var meanX = calculateMean(xAxisList)
            var varianceX = calculateVariance(meanX, xAxisList)
            var standardDevX = calculateStandardDeviation(varianceX)
            var meanY = calculateMean(yAxisList)
            var varianceY = calculateVariance(meanY, yAxisList)
            var standardDevY = calculateStandardDeviation(varianceY)
            var meanZ = calculateMean(zAxisList)
            var varianceZ = calculateVariance(meanZ, zAxisList)
            var standardDevZ = calculateStandardDeviation(varianceZ)
            // Berechne Mittelwert für Gier-Nick-Roll
            var meanAzimuth = calculateMean(azimuthList)
            var meanPitch = calculateMean(pitchList)
            var meanRoll = calculateMean(rollList)
            // Entfernt alle Elemente aus den Listen
            xAxisList?.clear()
            yAxisList?.clear()
            zAxisList?.clear()
            azimuthList?.clear()
            pitchList?.clear()
            rollList?.clear()

            return "$meanX,$varianceX,$standardDevX,$meanY,$varianceY,$standardDevY,$meanZ,$varianceZ,$standardDevZ," +
                    "$meanAzimuth,$meanPitch,$meanRoll"
        }
        return null
    }

    /**
     * Berechnet den  Mittelwert über die Elemente Float einer Liste
     * Prec.:
     * Postc.: Der Mittelwert über die Float Elemente der übergebenen Liste,
     * wenn samplesCounter ist größer 0
     */
    fun calculateMean(list : MutableList<Float>?) : Float {
        var sum = 0f
        if (samplesCounter > 0) {
            list?.forEach {
                sum += it
            }
            return sum / samplesCounter
        } else {
            return 0f
        }
    }

    /**
     * Berechnet die Varianz. Dieser Funktion muss als Paramter der Mittelwert (mean) und die Liste mit
     * den Float Werten übergeben werden, um die Varianz zu berechnen. Als Varianz wird der Durchschnitt der quadrierten
     * Differenzen zum Mittelwert bezeichnet.
     * Prec.:
     * Postc.: Gibt die berechnete Variance als Float zurück oder 0 wenn samplesCounter <= 0
     */
    fun calculateVariance(mean : Float, list : MutableList<Float>?) : Float {
        var variance = 0f
        var sum = 0f
        var tempDifference = 0f
        if (samplesCounter > 0) {
            list?.forEach {
                tempDifference = (it-mean)
                sum += tempDifference * tempDifference
            }
            return sum / samplesCounter
        } else {
            return 0f
        }
    }

    /**
     * Hier wird die Standardabweichung der Varianz berechnet. Dies ist die Wurzel von der Varianz.
     * Prec.:
     * Postc.:  Standardabweichung wird zurückgegeben. Für die Berechnung wird der Absolutwert von der Varianz genommen.
     */
    fun calculateStandardDeviation(variance : Float) : Float{
        var x = abs(variance)
        return sqrt(x)
    }
}
