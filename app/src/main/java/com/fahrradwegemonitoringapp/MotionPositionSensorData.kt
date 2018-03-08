package com.fahrradwegemonitoringapp

import android.app.Activity
import android.content.Context
import android.hardware.*
import android.widget.Toast
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.PI


/**
 *  Mit dieser Klasse können Sensordaten des Beschleunigungssensors ausgelesen werden.
 *  Genutzt werden die daten des "Accelerometer" und des "Magnetic Field" Sensors. Die Beschleunigungssensordaten
 *  beinhalten die Beschleunigungsdaten mit Gravitationsdaten. Die Berechnung von Gier Nick Roll kommt aus diesem Tutorial:
 *  https://github.com/google-developer-training/android-advanced/tree/master/TiltSpot
 *  Durch ein Zeitfenster wird die länge der Datenerfassung bestimmt.
 * Von Julian Magierski am 13.02.2018 erstellt.
 */

class MotionPositionSensorData : SensorEventListener  {

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
     * Zeitstempel innerhalb eines Zeitfensters,
     */
    private var timestampsList: MutableList<Long>? = null

    /*
     * Zeitstempel der Sensorevents
     */
    private var timestampsSensorList: MutableList<Long>? = null


    /**
     *  In diesen Variablen stehen die Kallibrierungsoffsets für
     *  Roll und Nick Winkel sowie die Beschleunigungssensorachsen X,Y,Z
     */
    private var pitchOffset: Float = 0.0f
    private var rollOffset: Float = 0.0f
    private var xOffset: Float = 0.0f
    private var yOffset: Float = 0.0f
    private var zOffset: Float = 0.0f

    // Current data from accelerometer & magnetometer.  The arrays hold values
    // for X, Y, and Z.
    private var mAccelerometerData = FloatArray(3)
    private var mMagnetometerData = FloatArray(3)

    /**
     * Alpha Wert fuer Tiefpassfilter um Gravity Wete aus den Beschleunigungssensordaten
     * zu entfernen
     */
    private  val alpha: Float = 0.8f
    private var gravity = FloatArray(3)

    /**
     * Boolean Variable die angibt ob Daten Samples gesammelt werden sollen.
     * Durch Volatile wird angegeben das eine Änderung dieser Variablen schnellst möglich
     * von anderen Threads gelesesn werden kann.
     */
    @Volatile
    private var isDataGatheringActive: Boolean = false


    private var mSensorManager: SensorManager? = null
    private var mAccelerometer: Sensor? = null
    private var mMagneticField: Sensor? = null
    private var activity: Activity? = null

    /**
     * Um Datenwerte aufzurunden auf 5 Kommastellen
     */
    private var df = DecimalFormat("#.#####")

    /**
     * Wird benötigt um die Richtung des geomagnetischen Nordpols zu bestimmen
     */
    private lateinit var location: GPSLocation

    /**
     * Diese Methode muss aufgerufen werden, um die Sensor Listener zu starten
     * Prec.: activity != null
     * Postc.: Listener wurden initialisiert
     */
    fun init(activity: Activity, location: GPSLocation) {
        // Datenwerte sollen aufgerunded werden auf 5 Kommastellen
        df.roundingMode = RoundingMode.CEILING
        this.activity = activity
        this.location = location
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
        timestampsList = mutableListOf()
        timestampsSensorList = mutableListOf()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor != null) {
            if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                when (accuracy) {
                // Genauigkeit ist Unzuverlaessig
                    0 -> {
                        Toast.makeText(activity, "Magnetsensor Genauigkeit: unzuverlässig", Toast.LENGTH_SHORT).show()
                        Logger.writeToLogger(Exception().stackTrace[0], "Magnetsensor Genauigkeit: SENSOR_STATUS_UNRELIABLE\n")
                    }
                    1 -> {
                        Toast.makeText(activity, "Magnetsensor Genauigkeit: niedrig", Toast.LENGTH_SHORT).show()
                        Logger.writeToLogger(Exception().stackTrace[0], "Magnetsensor Genauigkeit: SENSOR_STATUS_ACCURACY_LOW\n")
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

    override fun onSensorChanged(event: SensorEvent?) {
        val timestamp = System.nanoTime()
        val sensorType = event?.sensor?.type
        when (sensorType) {
            Sensor.TYPE_ACCELEROMETER -> mAccelerometerData = event.values.clone()
            Sensor.TYPE_MAGNETIC_FIELD -> mMagnetometerData = event.values.clone()
        }
        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            // Berechnet die Rotationsmatrix. Dabei werden die Beschleunigungssensordaten und
            // Magnetsensordaten genutzt, um die Smartphone spezifischen Koordinaten auf die Welt Koordinaten
            // zu transformieren
            val rotationMatrix = FloatArray(9)
            val rotationOK = SensorManager.getRotationMatrix(rotationMatrix,
                    null, mAccelerometerData, mMagnetometerData)
            // Gibt die Orientierung des Smartphones in der Gier-Nick-Roll Form zurück
            val orientationValues = FloatArray(3)
            if (rotationOK) {
                SensorManager.getOrientation(rotationMatrix, orientationValues)
            }
            // Anwendung eines einfachen Tiefpassfilters, um den Gravitationsanteil
            // aus den Beschleunigungssensordaten zu entfernen
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
            if (isDataGatheringActive) {
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
                    azimuth += geoField.declination // Berechnet den magnetischen Norden zu den geografischen Norden
                }
                azimuthList?.add(azimuth * (PI.toFloat() / 180))
                pitchList?.add(orientationValues[1] - pitchOffset)
                rollList?.add(orientationValues[2] - rollOffset)
                xAxisList?.add(event.values[0] - gravity[0] - xOffset)
                yAxisList?.add(event.values[1] - gravity[1] - yOffset)
                zAxisList?.add(event.values[2] - gravity[2] - zOffset)
                timestampsList?.add(timestamp)
                timestampsSensorList?.add(event.timestamp)
            }
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
    fun getData( startExposureTime : Long, exposureTime : Long) : String? {
        if(!isDataGatheringActive && exposureTime <= MAX_EXPOSURE_TIME) {
            val indecis = getTimeframeIndecis(startExposureTime,exposureTime)
            // Über die Exemplarvariablen Listen kann nicht iterriert werden, weil in einem Thread
            // über den Listener paralle auf diese zugegriffen wird. Deshalb werden die Listen kopiert
            val xListFinish = xAxisList?.toMutableList()?.subList(indecis[0], indecis[1])
            val timestampsFinish = timestampsSensorList?.toMutableList()?.subList(indecis[0], indecis[1])
            val yListFinish = yAxisList?.toMutableList()?.subList(indecis[0], indecis[1])
            val zListFinish = zAxisList?.toMutableList()?.subList(indecis[0], indecis[1])
            val azimuthListFinish = azimuthList?.toMutableList()?.subList(indecis[0], indecis[1])
            val pitchListFinish = pitchList?.toMutableList()?.subList(indecis[0], indecis[1])
            val rollListFinish = rollList?.toMutableList()?.subList(indecis[0], indecis[1])
            return "%s,%s,%s,%s,%s,%s,%s,%s".format(
                    calcStringList(xListFinish),
                    calcStringList(yListFinish),
                    calcStringList(zListFinish),
                    calcStringList(azimuthListFinish),
                    calcStringList(pitchListFinish),
                    calcStringList(rollListFinish),
                    calcTimeStringList(timestampsFinish),
                    "${xListFinish?.size}")
        }
        return null
    }

    /**
     * Diese Funktion gibt einen String einer Float Liste zurück.
     * Dabei soll dient dies als Vorverarbeitung, um diesen in eine CSV Datei
     * zu schreiben
     * Prec. Anzahl der Elmenete in Liste > 0
     * Postc. String wird zurück gegeben
     */
    private fun calcStringList(list : MutableList<Float>?) : String {
        var result = ""
        list?.forEach {
            result += df.format(it).replace(",", ".") + " "
        }
        return result.dropLast(1)
    }

    /**
     * Gibt die Sensorzeitstempel als Nanosekunden in String Form zurück
     * dabei ist der Erste Messwert 0
     * Prec. Anzahl der Elmenete in Liste > 0
     * Postc. String wird zurück gegeben
     */
    private fun calcTimeStringList(list : MutableList<Long>?) : String {
        var result = ""
        val startTimeNs = list?.first()
        list?.forEach {
            result += "${it-startTimeNs!!} "
        }
        return result.dropLast(1)
    }

    /**
     * Diese Funktion berechnet den Startindex und den Stoppindex eines Zeitfensterns.
     * Die länge des Zeitfensters ist abhängig von der Belichtungszeit.
     * Prec.: exposureTime <= MAX_EXPOSURE_TIME
     * Postc.: Array mit den zwei Indexen
     */
    private fun getTimeframeIndecis( startExposureTime : Long, exposureTime : Long) : IntArray {
        val indecis = IntArray(2)
        val exposureDiff = MAX_EXPOSURE_TIME - exposureTime
        val offsetExposure = exposureDiff / 2 // Berechnet Offset-Zeit
        val timestampFinish = timestampsList?.toMutableList()
        val startExposureTimeOffset = startExposureTime - offsetExposure
        var i = 0
        var startFound = false
        timestampFinish?.forEach {
            if (!startFound && it >= startExposureTimeOffset) {
                indecis[0] = i
                startFound = true
            } else if (startFound &&
                    // Diese Abfrage ist wichtig weil die beiden Zeitstempel eine andere Zeitbasis nutzen
                    (timestampsSensorList?.get(i)!! - timestampsSensorList?.get(indecis[0])!!) <= MAX_EXPOSURE_TIME ) {
                indecis[1] = i
            } else if(startFound){
                return@forEach
            }
            i++
        }
        return indecis
    }

    /**
     * Meldet die Listener ab. Sollte aufgerufen wenn die App geschlossen wird.
     */
    fun onStop() {
        mSensorManager!!.unregisterListener(this)
    }

    /**
     * Diese Methode startet die Ansammlung von Daten des linearen Beschleunigungssensors
     * Prec.:
     * Postc.: Daten werden erfasst, wenn vorher keine erfasst wurden
     */
    fun startDataCollection() {
        if(!isDataGatheringActive)
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
     * Die Funktion entfernt alle Elemente aus den Datenlisten:
     * xAxisList, yAxisList, zAxisList, azimuthList, pitchList, rollList
     * Prec.:
     * Postc.: Die angegebenen Listen haben keine Elemente mehr
     */
    fun clearData() {
        timestampsSensorList?.clear()
        timestampsList?.clear()
        xAxisList?.clear()
        yAxisList?.clear()
        zAxisList?.clear()
        azimuthList?.clear()
        pitchList?.clear()
        rollList?.clear()
    }

    fun getFirstTimestamp() : Long? {
        var result : Long? = -1
        if(timestampsList?.isNotEmpty()!!)
            result = timestampsList?.first()
        return result
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
