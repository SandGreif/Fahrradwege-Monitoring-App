package com.fahrradwegemonitoringapp

import android.app.Activity
import android.content.Context
import android.hardware.*
import android.widget.Toast
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.concurrent.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt


/**
 *  Mit dieser Klasse können Sensordaten des Beschleunigungssensors ausgelesen werden.
 *  Genutzt werden die daten des "Accelerometer" und des "Magnetic Field" Sensors. Die Beschleunigungssensordaten
 *  beinhalten die Beschleunigungsdaten mit Gravitationsdaten. Die Berechnung von Gier Nick Roll kommt aus diesem Tutorial:
 *  https://github.com/google-developer-training/android-advanced/tree/master/TiltSpot
 * Von Julian Magierski am 13.02.2018 erstellt.
 */

class MotionPositionSensorData : SensorEventListener {

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

    /**
     *  In diesen Variablen stehen die Kallibrierungsoffsets für
     *  Roll und Nick Winkel sowie die Beschleunigungssensorachsen X,Y,Z
     */
    private var pitchOffset : Float = 0.0f
    private var rollOffset : Float = 0.0f
    private var xOffset : Float = 0.0f
    private var yOffset : Float = 0.0f
    private var zOffset : Float = 0.0f

    // Current data from accelerometer & magnetometer.  The arrays hold values
    // for X, Y, and Z.
    private var mAccelerometerData = FloatArray(3)
    private var mMagnetometerData = FloatArray(3)

    /**
     * Alpha Wert fuer Tiefpassfilter um Gravity Wete aus den Beschleunigungssensordaten
     * zu entfernen
     */
    private val alpha : Float = 0.8f
    private var gravity = FloatArray(3)

    /**
     * Um Datenwerte aufzurunden auf 5 Kommastellen
     */
    private var  df = DecimalFormat("#.###")

    /**
     * Boolean Variable die angibt ob Daten Samples gesammelt werden sollen.
     * Durch Volatile wird angegeben das eine Änderung dieser Variablen schnellst möglich
     * von anderen Threads gelesesn werden kann.
     */
    @Volatile private var isDataGatheringActive : Boolean = false

    /**
     * Gibt an ob innerhalb einer Datenerfassungs-Routine noch keone Sensordaten erfasst wurden
     */
    @Volatile private var firstGathering : Boolean = true

    /**
     * firstTimestamp gibt den Zeitstempel an der ersten Datenerfassung innerhalb eines Fensters.
     * Innerhalb eines Fensters werden mehrere Daten erfasst zwischen startDataCollection und stopDataCollection.
     * lastTimestamp der letzte erfasste Zeitstempel zu einem Event in einem Fenster.
     */
    private var firstTimestamp : Long = 0
    private var lastTimestamp : Long = 0

    /**
     * Wird benötigt um die Richtung des  geomagnetischen Nordpols zu bestimmen
     */
    private lateinit var location : GPSLocation

    /**
     * Für die Verwaltung eines Threadpools
     */
    private lateinit var executor : ExecutorService

    private lateinit var threadOrderedPool : CompletionService<DataValuesElement>

    /**
     * Diese Methode muss aufgerufen werden, um die Sensor Listener zu starten
     * Prec.: activity != null
     * Postc.: Listener wurden initialisiert
     */
    fun init(activity: Activity, location : GPSLocation) {
        // Datenwerte sollen aufgerunded werden auf 5 Kommastellen
        df.roundingMode = RoundingMode.CEILING
        this.activity = activity
        this.location = location
        executor = Executors.newFixedThreadPool(6)
        threadOrderedPool = ExecutorCompletionService<DataValuesElement>(executor)
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
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor != null) {
            if(sensor.type  == Sensor.TYPE_MAGNETIC_FIELD) {
                when(accuracy) {
                    // Genauigkeit ist Unzuverlaessig
                    0 -> {
                        Toast.makeText(activity, "Magnetsensor Genauigkeit: unzuverlässig", Toast.LENGTH_SHORT).show()
                        Logger.writeToLogger(Exception().stackTrace[0],"Magnetsensor Genauigkeit: SENSOR_STATUS_UNRELIABLE\n")
                    }
                    1 -> {
                        Toast.makeText(activity, "Magnetsensor Genauigkeit: niedrig", Toast.LENGTH_SHORT).show()
                        Logger.writeToLogger(Exception().stackTrace[0],"Magnetsensor Genauigkeit: SENSOR_STATUS_ACCURACY_LOW\n")
                    }
                    2 -> {
                        Toast.makeText(activity, "Magnetsensor Genauigkeit: mittel", Toast.LENGTH_SHORT).show()
                        Logger.writeToLogger(Exception().stackTrace[0], "Magnetsensor Genauigkeit: SENSOR_STATUS_ACCURACY_MEDIUM\n")
                    }
                    3 -> {
                        Toast.makeText(activity, "Magnetsensor Genauigkeit: hoch", Toast.LENGTH_SHORT).show()
                        Logger.writeToLogger(Exception().stackTrace[0], "Magnetsensor Genauigkeit: SENSOR_STATUS_ACCURACY_HIGH\n")
                    }
                }
            }
        }
    }

    /**
     * Meldet die Listener ab. Sollte aufgerufen wenn die App geschlossen wird.
     */
    fun onStop() {
        mSensorManager!!.unregisterListener(this)
        executor.shutdown()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            val dataValues : DataValuesElement
            val sensorType = event.sensor.type
            when (sensorType) {
                Sensor.TYPE_ACCELEROMETER -> mAccelerometerData = event.values.clone()
                Sensor.TYPE_MAGNETIC_FIELD -> mMagnetometerData = event.values.clone()
            }
            // Nur bei Beschleunigungssensordaten wird mit den Datenwerten weiter gerechnet ..
            if (sensorType == Sensor.TYPE_ACCELEROMETER) {
                // .. mit Hilfe eines Threadpools und einer Warteschlange, um die Ergebnisse
                // in der richtigen Reihenfolge zu erhalten
                threadOrderedPool.submit(SensorMotionCalculator(event))
                // Dequeue das nächste Ergebnis
                dataValues =threadOrderedPool.take().get()
                gravity[0] = dataValues.getGravity()[0]
                gravity[1] = dataValues.getGravity()[1]
                gravity[2] = dataValues.getGravity()[2]
                // Nur wenn die Datenerfassung von Sensordaten angefragt wird
                if(dataValues.getWasGatheringActive()) {
                    azimuthList?.add(dataValues.getOrientationData()[0])
                    pitchList?.add(dataValues.getOrientationData()[1] - pitchOffset)
                    rollList?.add(dataValues.getOrientationData()[2] - rollOffset)
                    xAxisList?.add(dataValues.getXyzAxis()[0])
                    yAxisList?.add(dataValues.getXyzAxis()[1])
                    zAxisList?.add(dataValues.getXyzAxis()[2])
                }
            }
        }
    }


    /**
     * In dieser inneren Klasse werden die Beschleunigungsdaten X,Y,Z berechnet und
     * die Roll-Nick-Gier Winkel.
     *
     */
    inner class SensorMotionCalculator (private val event: SensorEvent) : Callable<DataValuesElement> {
        override fun call(): DataValuesElement {
            // Berechnet die Rotationsmatrix. Dabei werden die Beschleunigungssensordaten und
            // Magnetsensordaten genutzt, um die Smartphone spezifischen Koordinaten auf die Welt Koordinaten
            // zu transformieren
            val rotationMatrix = FloatArray(9)
            val gravityTemp = FloatArray(3)
            val orientationTemp = FloatArray(3)
            val xyzAxisTemp = FloatArray(3)

            val rotationOK = SensorManager.getRotationMatrix(rotationMatrix,
                    null, mAccelerometerData, mMagnetometerData)
            // Gibt die Orientierung des Smartphones in der Gier-Nick-Roll Form zurück
            val orientationValues = FloatArray(3)
            if (rotationOK) {
                SensorManager.getOrientation(rotationMatrix,
                        orientationValues)
            }
            var azimuth: Float = orientationValues[0]
            val geoField: GeomagneticField
            azimuth *= (180 / PI.toFloat())
            if (location.getLocation() != null) {
                // Wird benötigt um den magnetischen Norden in welche die Variable azimuth
                // zeigt in den geografischen Norden zu konvertieren
                geoField = GeomagneticField(location.getLocation()!!.latitude.toFloat(),
                        location.getLocation()!!.longitude.toFloat(),
                        location.getLocation()!!.altitude.toFloat(),
                        System.currentTimeMillis())
                azimuth += geoField.declination // Berechnet den geografischen Norden zu den magnetischen Norden
            }

            // Anwendung eines einfachen Tiefpassfilters, um den Gravitationsanteil
            // aus den Beschleunigungssensordaten zu entfernen
            gravityTemp[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
            gravityTemp[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
            gravityTemp[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]


            if (isDataGatheringActive) {
                if (firstGathering) {
                    firstGathering = false
                    firstTimestamp = System.nanoTime()
                } else {
                    lastTimestamp = System.nanoTime()
                }
                orientationTemp[0] = azimuth
                orientationTemp[1] = orientationValues[1] - pitchOffset
                orientationTemp[2] = orientationValues[2] - rollOffset
                // Von den Beschleunigungssensordaten wird der Gravitationsanteil und Kallibrierungsoffset subtrahiert
                xyzAxisTemp[0] = event.values[0] - gravityTemp[0] - xOffset
                xyzAxisTemp[1] = event.values[1] - gravityTemp[1] - yOffset
                xyzAxisTemp[2] = event.values[2] - gravityTemp[2] - zOffset
            }
            return DataValuesElement(isDataGatheringActive, gravityTemp, orientationTemp, xyzAxisTemp)
        }
    }

    /**
     * Ein Datenobjekt um mehrere Rückgabewerte für den Callable SensorMotionCalculator zu ermöglichen:
     */
    companion object class DataValuesElement(private val wasGatheringActive :  Boolean, private val gravity : FloatArray,
                                  private val orientationData : FloatArray, private val xyzAxis : FloatArray) {
        fun getWasGatheringActive() : Boolean {
            return wasGatheringActive
        }
        fun getGravity() : FloatArray {
            return gravity
        }
        fun getOrientationData() : FloatArray {
            return orientationData
        }
        fun getXyzAxis() : FloatArray {
            return xyzAxis
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
                // Über die Exemplarvariablen Listen kann nicht iterriert werden, weil in einem Thread
                // über den Listener paralle auf diese zugegriffen wird. Deshalb werden die Listen kopiert
                val xListFinish = xAxisList?.toMutableList()
                val yListFinish = yAxisList?.toMutableList()
                val zListFinish = zAxisList?.toMutableList()
                val azimuthListFinish = azimuthList?.toMutableList()
                val pitchListFinish = pitchList?.toMutableList()
                val rollListFinish = rollList?.toMutableList()
                // Berechne Mittelwert, Variant und Standardabweichung
                val meanX = calculateMean(xListFinish)
                val varianceX = calculateVariance(meanX, xListFinish)
                val standardDevX = calculateStandardDeviation(varianceX)
                val meanY = calculateMean(yListFinish)
                val varianceY = calculateVariance(meanY, yListFinish)
                val standardDevY = calculateStandardDeviation(varianceY)
                val meanZ = calculateMean(zListFinish)
                val varianceZ = calculateVariance(meanZ, zListFinish)
                val standardDevZ = calculateStandardDeviation(varianceZ)
                // Berechne Mittelwert für Gier-Nick-Roll
                var radAzimuth = calculateAngelChangeAzimuth(azimuthListFinish)
                radAzimuth *= (PI.toFloat() / 180)
                val meanPitch = calculateMean(pitchListFinish)
                val variancePitch = calculateVariance(meanPitch, pitchListFinish)
                val standardPitch = calculateStandardDeviation(variancePitch)
                val meanRoll = calculateMean(rollListFinish)
                val varianceRoll = calculateVariance(meanRoll, rollListFinish)
                val standardRoll = calculateStandardDeviation(varianceRoll)
                // Representation der erfassten Daten als String. Kommas werden durch Punkte ersetzt.
                return df.format(meanX).replace(',', '.') + "," + df.format(varianceX).replace(',', '.') + "," +
                        df.format(standardDevX).replace(',', '.') + "," + df.format(meanY).replace(',', '.') + "," +
                        df.format(varianceY).replace(',', '.') + "," + df.format(standardDevY).replace(',', '.') + "," +
                        df.format(meanZ).replace(',', '.') + "," + df.format(varianceZ).replace(',', '.') + "," +
                        df.format(standardDevZ).replace(',', '.') + "," + df.format(radAzimuth).replace(',', '.') + "," +
                        df.format(meanPitch).replace(',', '.') + "," + df.format(variancePitch).replace(',', '.') + "," +
                        df.format(standardPitch).replace(',', '.') + "," + df.format(meanRoll).replace(',', '.') + "," +
                        df.format(varianceRoll).replace(',', '.') + "," + df.format(standardRoll).replace(',', '.')
        }
        return null
    }


    fun getIsDataGatheringActive() : Boolean {
        return isDataGatheringActive
    }

    /**
     * Diese Methode startet die Ansammlung von Daten des linearen Beschleunigungssensors
     * Prec.:
     * Postc.: Daten werden erfasst, wenn vorher keine erfasst wurden
     */
    fun startDataCollection() {
        if(!isDataGatheringActive)
            firstGathering = true
            isDataGatheringActive = true
    }

    /**
     * Diese Funktion stopt die Datenerfassung dieser Klasse.
     * Prec.:
     * Postc.: Es werden keine Daten mehr erfasst, wenn isDataGatheringActive true ist.
     */
    fun stopDataCollection() {
        if(isDataGatheringActive) {
            isDataGatheringActive = false
        }
    }

    /**
     * Gibt den Zeitstempel des ersten Sensorevents seit Beginn von startDataCollection.
     * Prec.:
     * Postc.: Long Wert zurückgegeben des ersten Zeitstempel in Nanosekunden
     */
    fun getFirstTimestamp() : Long{
        return firstTimestamp
    }

    /**
     * Gibt den letzten Zeitstempel der Sensorevents zurück nachdem stopDataCollection aufgerufen wurde
     * Prec.:
     * Postc.: Long Wert des letzten Sensorevent Zeitstempel
     */
    fun getLastTimestamp() : Long {
        return lastTimestamp
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
     * Die Funktion berechnet die Winkeländerung zwischen den ersten Gier und den letzten Gierwinkel
     * innerhalb einer als Paramter übergebenen Liste. Der zurückgegebene Winkel ist immer positiv und
     * zeigt die relative änderung des Winkels.
     * Prec.:
     * Postc.: Winkdeländerung berechnet
     */
     fun calculateAngelChangeAzimuth(list : MutableList<Float>?) : Float {
        var result = 0.0f
        if(list?.isNotEmpty()!!) {
            val firstAzimuth = list.first()
            val lastAzimuth = list.last()
            result = abs( firstAzimuth - lastAzimuth)
            if(result > 180)
                result = 360 - result
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

    /**
     * Die Methode startet die Kalibration der Roll-Nick Winkel.
     * Ziel ist es das montierte Smartphone so zu Kalibrieren das diese Winkel in Ruhezustand 0 ergeben.
     * Hier wird dafür die Datenerfassung gestartet.
     * Prec.: -
     * Postc.: Datenerfassung wurde angestoßen
     */
    fun startCalibration()  {
        isDataGatheringActive = true
    }

    /**
     * Stopt die Datenerfassung. Der Mittelwert von den Winkel Roll und Nick wird berechnet.
     * Diese Werte werden dann Offset Variablen zugewiesen.
     * Prec.:  -
     * Postc.: Datenerfassung gestopt. Offset wurde berechnet.
     */
    fun stopCalibration() {
        isDataGatheringActive = false
        xOffset =calculateMean(xAxisList?.toMutableList())
        yOffset = calculateMean(yAxisList?.toMutableList())
        zOffset = calculateMean(zAxisList?.toMutableList())
        pitchOffset = calculateMean(pitchList?.toMutableList())
        rollOffset = calculateMean(rollList?.toMutableList())
    }
}
