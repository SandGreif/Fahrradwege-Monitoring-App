package com.fahrradwegemonitoringapp

import android.app.Activity
import android.content.Context
import android.hardware.*
import android.widget.TextView
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
     * Gibt für den Benutzer eine Warnung aus, wenn
     * die Magnetsensorgenauigkeit ungenau ist
     */
    private lateinit var magnetTxt : TextView

    /**
     * Offset Index um schnellen Zugriff auf die Messwerte zu ermöglichen
     * Dies ist eine Optimierung
     */
    private var offsetIndexList: Int = 0
    /**
     * Klasse um die aktuelle Zeit und Datum zu erhalten
     */
    private lateinit var time: Time

    /**
     * letzte Zeitstempel der Sublist
     */
    private var lastTimeStamp : Long = 0

    /**
     * Erste Zeitstempel der Sublist
     */
    private var firstTimeStamp : Long = 0

    /**
     * Listen zum zwischenspeichern der Beschleunigungssensordaten y,z
     */
    private var zAxisList: MutableList<Float>? = null
    private var yAxisList: MutableList<Float>? = null

    private var pitchList: MutableList<Float>? = null

    /**
     * Zeitstempel innerhalb eines Zeitfensters in Nanosekunden seit Start der Java Vm,
     */
    private var timestampsNsList: MutableList<Long>? = null

    /**
     *  In diesen Variablen stehen die Kallibrierungsoffsets für
     *  Roll und Nick Winkel sowie die Beschleunigungssensorachsen Y,Z
     */
    private var pitchOffset: Float = 0.0f
    private var zOffset: Float = 0.0f
    private var yOffset: Float = 0.0f

    // Current data from accelerometer & magnetometer.  The arrays hold values
    // for X, Y, and Z.
    private var mAccelerometerData = FloatArray(3)
    private var mMagnetometerData = FloatArray(3)
    private var linearAccelerometerData = FloatArray(3)

    /**
     * Boolean Variable die angibt ob Daten Samples gesammelt werden sollen.
     * Durch Volatile wird angegeben das eine Änderung dieser Variablen schnellst möglich
     * von anderen Threads gelesesn werden kann.
     */
    @Volatile
    private var isDataGatheringActive: Boolean = false


    private var mSensorManager: SensorManager? = null
    private var mAccelerometer: Sensor? = null
    private var linearAccelerometer: Sensor? = null
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
        val samplingPeriodMicroS = 8000
        df.roundingMode = RoundingMode.CEILING
        this.activity = activity
        this.location = location
        time = Time()
        mSensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
        mAccelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mSensorManager?.registerListener(this, mAccelerometer, samplingPeriodMicroS)
        mMagneticField = mSensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        mSensorManager?.registerListener(this, mMagneticField, samplingPeriodMicroS)
        linearAccelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        mSensorManager?.registerListener(this, linearAccelerometer, samplingPeriodMicroS)
        yAxisList = mutableListOf()
        zAxisList = mutableListOf()
        pitchList = mutableListOf()
        timestampsNsList = mutableListOf()
        magnetTxt = activity.findViewById(R.id.magnetTxt) as TextView
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor != null) {
            if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                when (accuracy) {
                // Genauigkeit ist Unzuverlaessig
                    0 -> {
                        activity?.runOnUiThread({
                            run {
                                magnetTxt.text = "%s".format("Magnetsensorgenauigkeit: unzuverlässig")
                            }
                        })
                        Logger.writeToLogger(Exception().stackTrace[0], "Magnetsensorgenauigkeit: SENSOR_STATUS_UNRELIABLE\n")
                    }
                    1 -> {
                        activity?.runOnUiThread({
                            run {
                                magnetTxt.text = "%s".format("Magnetsensorgenauigkeit: niedrig")
                            }
                        })
                        Logger.writeToLogger(Exception().stackTrace[0], "Magnetsensorgenauigkeit: SENSOR_STATUS_ACCURACY_LOW\n")
                    }
                    2 -> {
                        activity?.runOnUiThread({
                            run {
                                magnetTxt.text = "%s".format("Magnetsensorgenauigkeit: mittel")
                            }
                        })
                        Logger.writeToLogger(Exception().stackTrace[0], "Magnetsensorgenauigkeit: SENSOR_STATUS_ACCURACY_MEDIUM\n")
                    }
                    3 -> {
                        Toast.makeText(activity, "Magnetsensorgenauigkeit: hoch", Toast.LENGTH_LONG).show()
                        Logger.writeToLogger(Exception().stackTrace[0], "Magnetsensorgenauigkeit: SENSOR_STATUS_ACCURACY_HIGH\n")
                    }
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val timestampNs = time.getTimeNanoSec()
        val timestampMs = time.getTime()
        val sensorType = event?.sensor?.type
        when (sensorType) {
            Sensor.TYPE_ACCELEROMETER -> mAccelerometerData = event.values.clone()
            Sensor.TYPE_LINEAR_ACCELERATION -> linearAccelerometerData = event.values.clone()
            Sensor.TYPE_MAGNETIC_FIELD -> mMagnetometerData = event.values.clone()
        }
        if (sensorType == Sensor.TYPE_LINEAR_ACCELERATION) {
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
                pitchList?.add(orientationValues[1] - pitchOffset)
                yAxisList?.add(event.values[1] - yOffset)
                zAxisList?.add(event.values[2] - zOffset)
                timestampsNsList?.add(timestampNs)
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
    fun getData(startExposureTime : Long, exposureTime : Long, dynamicTimeframe : Long) : String? {
        if(exposureTime <= dynamicTimeframe) {
            val timestampGetDataMs = time.getTime()
            val indecis = getTimeframeIndecis(timestampsNsList,startExposureTime,exposureTime, dynamicTimeframe)
            // Über die Exemplarvariablen Listen kann nicht iterriert werden, weil in einem Thread
            // über den Listener parall auf diese zugegriffen wird. Deshalb werden die Listen kopiert
            val timestampsFinish = timestampsNsList?.toMutableList()?.subList(indecis[0], indecis[1])
            val yListFinish = yAxisList?.toMutableList()?.subList(indecis[0], indecis[1])
            val zListFinish = zAxisList?.toMutableList()?.subList(indecis[0], indecis[1])
            val pitchListFinish = pitchList?.toMutableList()?.subList(indecis[0], indecis[1])
            val startTimeframe = startExposureTime - calcOffsetExposure(exposureTime, dynamicTimeframe)
            return "%s,%s,%s,%s,%s,%s,%s".format(
                    calcStringList(zListFinish),
                    calcStringList(yListFinish),
                    calcStringList(pitchListFinish),
                    calcTimeStringList(timestampsFinish,exposureTime,startExposureTime,dynamicTimeframe),
                    "${zListFinish?.size}",
                    "$startTimeframe",
                    "$timestampGetDataMs")
        }
        Logger.writeToLogger(Exception().stackTrace[0],"Zeitfenster Dauer in ns: " + dynamicTimeframe)
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
     * Prec. Anzahl der Elmenete in Liste > 0
     * Postc. String wird zurück gegeben
     */
    private fun calcTimeStringList(list : MutableList<Long>?, exposureTime : Long, startExposureTime : Long, dynamicTimeframe : Long) : String {
        var result = ""
        if(list?.isEmpty()!!)
            return result
        val startTimeNs = list.first()
        val offsetExposure = calcOffsetExposure(exposureTime, dynamicTimeframe)
        val startTimeframe = startExposureTime - offsetExposure
        val diffStart = startTimeNs - startTimeframe
        list.forEach {
            result += "${it-startTimeNs+diffStart} "
        }
        return result.dropLast(1)
    }

    /**
     * Berechnet den Offset von der Differenz des Zeitfensters zu der Belichtungszeit
     * Prec. exposureTime > 0
     * Postc. berechneter Offset
     */
    private fun calcOffsetExposure(exposureTime : Long, dynamicTimeframe : Long) : Long {
        val exposureDiff = dynamicTimeframe - exposureTime
        return exposureDiff / 2
    }

    /**
     * Diese Funktion berechnet den Startindex und den Stoppindex eines Zeitfensterns.
     * Die länge des Zeitfensters ist abhängig von der Belichtungszeit.
     * Prec.: exposureTime <= MAX_EXPOSURE_TIME && startExposureTime >= 0 && exposureTime > 0
     * Postc.: Array mit den zwei Indexen
     */
    fun getTimeframeIndecis( list : MutableList<Long>?, startExposureTime : Long, exposureTime : Long, dynamicTimeframe : Long) : IntArray {
        val indecis = IntArray(2)
        var i = 0
        var iWorstCase = 0
        var listTime = list?.toMutableList()
        var startFound = false
        var worstCaseFound = false
        lastTimeStamp = listTime?.last()!!
        listTime = listTime.subList(offsetIndexList,listTime.size -1)
        firstTimeStamp  = listTime.first()
        val offsetExposure = calcOffsetExposure(exposureTime, dynamicTimeframe) // Berechnet Offset-Zeit
        val offsetExposureWorstCase = WORSTCASETIMEFRAME/2
        val startTimeframeWorstCast = startExposureTime - offsetExposureWorstCase
        val startTimeframe = startExposureTime - offsetExposure
        val stopTimeframe = startExposureTime + exposureTime + offsetExposure
        listTime.forEach {
            if (!worstCaseFound && it >= startTimeframeWorstCast) {
                worstCaseFound = true
                iWorstCase = i
            }
            if (!startFound && it >= startTimeframe) {
                indecis[0] = i
                startFound = true
            } else if (startFound && it <= stopTimeframe) {
                indecis[1] = i
            } else if(startFound){
                return@forEach
            }
            i++
        }
        indecis[0] = indecis[0] + offsetIndexList
        indecis[1] = indecis[1] + offsetIndexList
        offsetIndexList = iWorstCase + offsetIndexList
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
        offsetIndexList = 0
        timestampsNsList?.clear()
        lastTimeStamp = 0
        yAxisList?.clear()
        zAxisList?.clear()
        pitchList?.clear()
    }

    /**
     * Gibt den ersten Zeitstempel der Subliste in ns seit Start der Java VM zurück
     */
    fun getFirstTimestampSubList() : Long? {
        return firstTimeStamp
    }

    fun getFirstTimestamp() : Long? {
        var result : Long? = -1
        if(timestampsNsList?.isNotEmpty()!!)
            result = timestampsNsList?.first()
        return result
    }

    fun getLastTimestamp() : Long? {
        return lastTimeStamp
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
        var result  = 0f
        if (samples != null) {
            if (samples > 0) {
                list.forEach {
                    sum += it
                }
                result =  sum / samples
            }
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
        yOffset = calculateMean(zAxisList?.toMutableList())
        zOffset = calculateMean(zAxisList?.toMutableList())
        pitchOffset = calculateMean(pitchList?.toMutableList())
    }
}
