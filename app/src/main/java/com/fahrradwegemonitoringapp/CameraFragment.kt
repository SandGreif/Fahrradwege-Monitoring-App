/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * Die Camera2 Anwendung basiert auf den Beispiel von:
 * Basic Camera2 example https://github.com/googlesamples/android-Camera2Basic/tree/master/kotlinApp
 */

package com.fahrradwegemonitoringapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.location.Location
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.*
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import android.util.Range
import android.util.Size
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import java.io.File
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.*
import java.util.Collections.max
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.PI

class CameraFragment : Fragment(), View.OnClickListener,
        ActivityCompat.OnRequestPermissionsResultCallback {

    /**
     * Länge des dynamischen Zeitfensters in Nanosekunden
     */
    private  var dynamicTimeframe: Long = 0
    /**
     * ID der Kamera [CameraDevice].
     */
    private lateinit var cameraId: String

    /**
     * Gibt an ob der Nutzer möchte das Daten weiterhin erfasst werden
     */
    @Volatile private var buttonCaptureRequestActive : Boolean = false

    /**
     * True wenn aktuell Bilder angefragt und verarbeitet werden
     */
    @Volatile private var  activeImageCapturing : Boolean = false

    /**
     * Um die aktuelle Geschwindigkeit für den Nutzer sichtbar zu machen
     */
    private lateinit var speedTxt : TextView

    /**
     * Um den aktuellen Nick-Winkel dem Nutzer sichtbar zu machen
     */
    private lateinit var pitchTxt : TextView

    /**
     * Um die Anzahl der aufgenommenen Bilder für den Nutzer sichtbar zu machen
     */
    private lateinit var imageCounterTxt : TextView

    /**
     * Anzahl der gespeicherten Bilder
     */
    private var imageCounter : Int = 0

    private var fpsRange: Array<Range<Int>>? = null

    /**
     * Anzahl der Ordner die Bilder enthalten
     */
    private var directoriesCounter : Int = 1

    private val captureHeight: Int = 1960

    private val captureWidth: Int = 1080

    /**
     * An [AutoFitTextureView] Für die Kamera Preview.
     */
    private lateinit var textureView: AutoFitTextureView

    /**
     * A [CameraCaptureSession] for camera preview.
     */
    private var captureSession: CameraCaptureSession? = null

    /**
     * Hält Referenz zu der geöffneten Kamera [CameraDevice].
     */
    private var cameraDevice: CameraDevice? = null

    /**
     * Die Bildgröße [android.util.Size] der Preview
     */
    private lateinit var previewSize: Size

    /**
     * Klassen Objekt um die Beschleunigungssensordaten zu erhalten
     */
    private var motionPositionSensorData: MotionPositionSensorData? = null

    /**
     * Klasse um  GPS Anfragen abzuhandeln
     */
    private var gpsLocation: GPSLocation? = null

    private var startTime: Long = 0

    /**
     * Referenz auf die GPS Position der letzten Aufnahme
     */
    private var location : Location? = null

    /**
     * Aktuelle angenährte Geschwindigkeit in km/h
     */
    private var speed: Float = 0.0f

    /**
     * Die Belichtungszeit der letzten Aufnahme
     */
    @Volatile private var exposureTime: Long = 0
        @Synchronized get() {
            return field
        }
        @Synchronized set(value) {
            field = value
        }

    /**
     * Die Belichtungsstartzeit der letzten Aufnahme in Nanosekunden seit Start der Java Virtual Machine(JVM).
     */
    private var exposureTimeStart: Long = 0

    /**
     * Klasse um die aktuelle Zeit und Datum zu erhalten
     */
    private lateinit var time: Time

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var backgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var backgroundHandler: Handler? = null

    /**
     * Ein [ImageReader] welcher aufgenommene Bilder abhandelt
     */
    private var imageReader: ImageReader? = null

    /**
     * Um Datenwerte aufzurunden auf 2 Kommastellen
     */
    private var  df = DecimalFormat("#.##")

    /**
     * Wurzelverzeichnis um Dateien abzuspeichern
     */
    private lateinit var letDirectory: File

    /**
     * Das aktuell verwendete Verzeichnis
     */
    private lateinit var actualDirectory: File

    /**
     * Referenz auf Datei um Features abzuspeichern
     *  */
    private lateinit var fileLocation: File

    /**
     * [CaptureRequest.Builder] Für die Kamera Preview
     */
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    /**
     * [CaptureRequest] generiert von by [.previewRequestBuilder]
     */
    private lateinit var previewRequest: CaptureRequest

    /**
     * Aktuelle Zustand um Bilder aufzunehmen.
     * Der Anfangszustandand ist Preview. In welchem für die Preview Bilder aufgenommen werden.
     * Auf diese Variable wird von mehereren Eventhandlern zugegriffen, wehshalb der Synchronized ist,
     * um "Race Conditions" zu vermeiden
     * @see .captureCallback
     */
    private var state = STATE_PREVIEW
        @Synchronized get() {
            return field
        }
        @Synchronized set(value) {
            field = value
        }

    /**
     * [Semaphore] Wird benötigt um die Anwendung daran zu hindern die App zu schließen ohne die Kamera vorher zu schließen
     */
    private val cameraOpenCloseLock = Semaphore(1)

    /**
     * Lage des Kamerasensors
     */
    private var sensorOrientation = 0


    /**
     * Die [CameraCaptureSession.CaptureCallback] welche Events abarbeitet für die Aufnahme eines Bildes
     */
    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        /**
         * Die Methode implementiert mit einem "switch case" eine Zustandmaschine. Dabei ändert sich der
         * Zustand der Variablen state. Es gibt fünf Zustände [STATE_PREVIEW], [STATE_TAKE_PICTURE], [STATE_WAITING_PRECAPTURE],
         * [STATE_WAITING_NON_PRECAPTURE], [STATE_PICTURE_TAKEN]} Der Zustand wird auch in unterschiedlichen Methoden dieser Klasse
         * gesetzt. Dabei wird diese Methode aufgerufen sobald ein Bild fertig aufgenommen [onCaptureCompleted] oder teilweise
         * verarbeitet [onCaptureProgressed] wurde.
         * Siehe auch die Anlagen UML -> CameraStateDiagram
         */
        private fun process(result: CaptureResult) {
            when (state) {
                STATE_PREVIEW -> Unit
                STATE_TAKE_PICTURE -> capturePicture(result)
                STATE_WAITING_PRECAPTURE -> {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE
                    || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                        state = STATE_WAITING_NON_PRECAPTURE
                    }
                }
                STATE_WAITING_NON_PRECAPTURE -> {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        state = STATE_PICTURE_TAKEN
                        captureStillPicture()
                    }
                }
            }
        }

        private fun capturePicture(result: CaptureResult) {
            val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
            if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                if(imageCounter % 100 == 0 ) {
                    motionPositionSensorData?.clearData() // Vor der Aufnahme werden die letzten erfassten Sensordaten(Beschl./Gier-Roll-Nick) entfernt
                    motionPositionSensorData?.startDataCollection()
                    try {
                        Thread.sleep(250)
                    } catch (e: IllegalArgumentException) {
                        Logger.writeToLogger(Exception().stackTrace[0], e.toString())
                    }
                }
                state = STATE_PICTURE_TAKEN
                captureStillPicture()
            } else {
                runPrecaptureSequence()
            }
        }

        override fun onCaptureProgressed(session: CameraCaptureSession,
                                         request: CaptureRequest,
                                         partialResult: CaptureResult) {
            process(partialResult)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        result: TotalCaptureResult) {
            if(gpsLocation?.getLocation() != null)  { // Geschwindigkeit auf dem UI ausgeben
                val speedOnComplete = (gpsLocation!!.getLocation()?.speed!! * 60 * 60) / 1000 // Umrechnung von m/s in km/h
                activity.runOnUiThread({
                    run {
                        speedTxt.text = "%s %s".format(df.format(speedOnComplete), " km/h")
                        pitchTxt.text = "%s %s".format(df.format(motionPositionSensorData?.getLastPitchValue()!! *(180 / PI.toFloat())), "Nick-Winkel Grad")
                    }
                })
            }
            process(result)
        }
    }

    /**
     * [CameraDevice.StateCallback] wird aufgerufen wenn der Zustand von [CameraDevice] sich ändert
     */
    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@CameraFragment.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            Logger.writeToLogger(Exception().stackTrace[0],"Disconnected")
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@CameraFragment.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            Logger.writeToLogger(Exception().stackTrace[0],"onError")
            onDisconnected(cameraDevice)
            this@CameraFragment.activity?.finish()
        }

    }

    /**
     * [TextureView.SurfaceTextureListener] Handler um [TextureView] Events zu verarbeiten
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true
        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    }

    /**
     * Dies ist die callback Funktion für den [ImageReader]. Diese wurd aufgerufen, wenn eine  Aufnahme bereit ist
     * zum abspeichern. Zu den Bild werden Features in eine Datei geschrieben. Es wird für den Nutzer auch eine Text Ausgabe erstellt,
     * um diesen die aktuelle Geschwindigkeit und die Anzahl der bereits aufgenommen Bilder mitzuteilen.
     */
    private val onImageAvailableListener = OnImageAvailableListener {
        val timeMs = time.getTime()
        val image = it.acquireLatestImage()
        if (image != null) {
            if (location != null) {
                speed = (location?.speed!! * 60 * 60) / 1000 // Umrechnung von m/s in km/h
               if ((speed - 5.0f) > 0.0001) {  // Geschwindigkeit muss zwischen 5-25km/h liegen
                // Berechnung des dynamischen Zeitfensters
                calcDynamicTimeframe()
                if(stopDataCapturing()) {
                        if (imageCounter % (2000 * directoriesCounter) == 0) {
                            newFolder()
                        }
                    if(!(motionPositionSensorData?.isTimestampListEmpty()!!)) {
                        if (saveImage(image, timeMs)) {
                            saveFeatures(timeMs)
                        }
                    } else {
                        image.close()
                    }
                        activity.runOnUiThread({
                            run {
                                imageCounterTxt.text = "%d %s".format(imageCounter, "Bilder")
                                speedTxt.text = "%s %s".format(df.format(speed), " km/h")
                            }
                        })
                    } else {
                        Logger.writeToLogger(Exception().stackTrace[0],"Belichtungszeit war 0 ns")
                        image.close()
                    }
                } else {
                    image.close()
                }
             } else {
            image.close()
           }
        } else {
            image?.close()
      }
        requestNextImage()
    }

    /**
     * Stoppt die erfassung der Bewegungssensor und Positionsdaten
     * Prec.: sollte aufgerufen werden von der Methode onImageAvailableListener
     * Postc.: Datenerfassung gestoppt. Rückgabewert True, wenn Belichtungszeit größer als 0 war
     *        Die Belichtung kann 0 sein, wenn der Listener onImageAvailableListener vor den
     *        Handler onCaptureComplete aufgerufen wurde
     */
    private fun stopDataCapturing() : Boolean {
        var exposerTimeGreaterZero = false
        val waitingTime : Long
        // Die verstrichene Zeit muss mindestens der MAX_EXPOSURE_TIME entsprechen
        if((System.nanoTime() - exposureTimeStart) < ((((dynamicTimeframe-exposureTime)/2) + exposureTime))) {
            // Ausreichend Zeit für die Bewegungssensordatenerfassung gewährleisten
            try {
                waitingTime = ((((dynamicTimeframe-exposureTime)/2) + exposureTime) - (System.nanoTime() - exposureTimeStart))/1000000
                Thread.sleep(Math.abs(waitingTime))
            } catch (e: IllegalArgumentException) {
                Logger.writeToLogger(Exception().stackTrace[0],e.toString())
            }
        }
        if(exposureTime > 0) {
            exposerTimeGreaterZero =  true
        }
        return exposerTimeGreaterZero
    }

    /**
     * Die Funktion speichert ein Bild ab
     * Prec.: Image != null
     * Post.: Bild ist abgespeichert. Boolean == True, wenn Bild abgespeichert wurde
     */
    private fun saveImage(image: Image, ms: Long): Boolean {
        val imageWasSaved = ImageSaver().saveImage(image, File(actualDirectory, ( "$ms.jpg")))
        if(imageWasSaved) {
            imageCounter++
        }
        return imageWasSaved
    }

    /**
     * Speichert Merkmale ab: Zeit in ms/ GPS Längengrad / Breitengrad / Geschwindigkeit /
     * Beschleunigungssensordaten (X,Y,Z) mit den jeweiligen Mittelwert, Varianz und Standardabweichung /
     * Gier / Nick Mittelwert / Roll Mittelwert /
     * Prec.:  timestamp > 0
     * Postc.: Merkmale wurden in eine Datei geschrieben
     */
    @SuppressLint("NewApi")
    private fun saveFeatures(timestamp : Long) {
        val latitude = location?.latitude?.toFloat()
        val longitude = location?.longitude?.toFloat()
        val motionDataString = motionPositionSensorData?.getData(exposureTimeStart,exposureTime,dynamicTimeframe)
        fileLocation.appendText("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n".format(
                "$timestamp","$latitude","$longitude","$speed","${location?.speedAccuracyMetersPerSecond}","${location?.time}","$motionDataString","${motionPositionSensorData?.getFirstTimestamp()}",
                "${motionPositionSensorData?.getFirstTimestampSubList()}",
                "$exposureTimeStart","$exposureTime","${motionPositionSensorData?.getLastTimestamp()}",
                "${time.getTime()}"))
    }

    /**
     * Prec.: - Alle Bilder vom ImageReader sind abgearbeitet
     * Postc.: Wenn actualTakingPictures wahr ist wurde die Methode actualTakingPictures aufgerufen
     */
    private fun requestNextImage() {
        exposureTime = 0
        if(buttonCaptureRequestActive) {
            if(state == STATE_PICTURE_TAKEN) { // Nach der Aufnahme eines einzelnen Bildes muss sichergestellt werden, dass
                try {                          // wieder Kontinuirlich Bilder Aufgenommen werden
                    captureSession?.setRepeatingRequest(previewRequest, captureCallback,
                            backgroundHandler)
                } catch (e: CameraAccessException) {
                    Logger.writeToLogger(Exception().stackTrace[0], e.toString())
                    Log.e(TAG, e.toString())
                }
            }
            state = STATE_TAKE_PICTURE
        } else {
            activeImageCapturing = false
        }
    }

    /**
     * Die Methode berechnet das dynamische Zeitfenster
     * Prec.: (location != null) &&((speed - 5.0f) > 0.0001)
     * Postc.: Dynamisches Zeitfenster berechnet
     */
    private fun calcDynamicTimeframe() {
        val actualSpeed : Float
        if(location?.hasSpeed()!! && location?.speed!! > 0) {
            actualSpeed = location?.speed!!
            dynamicTimeframe = ((1 / actualSpeed) * 1000000000).toLong()
        } else {
            dynamicTimeframe = WORSTCASETIMEFRAME
        }
        if(dynamicTimeframe > WORSTCASETIMEFRAME){
            dynamicTimeframe = WORSTCASETIMEFRAME
        }
    }

    /**
     * Mit dieser Methode kann ein neuer Ordner erstellt werden, um Merkmale in eine Daten
     * zu schreiben.
     * Prec.:
     * Postc.: Ein neuer Ordner für die Merkmale wurde erstellt
     */
    private fun newFolder() {
        if(imageCounter>1) {
            directoriesCounter++
        }
        actualDirectory = File(letDirectory, "$directoriesCounter")
        actualDirectory.mkdir()
        fileLocation = File(actualDirectory, ("merkmaleRoh.csv"))
        fileLocation.appendText("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n".format(
                "Zeitstempel in Unixzeit","Breitengrad","Laengengrad","Geschwindigkeit in km/h","Genauigkeit der Geschwindigkeit speedAccuracyMetersPerSecond","Lokations Zeitstempel in Unixzeit","Z-Achse Beschleunigungswerte in m/s^2","Y-Achse Beschleunigungswerte in m/s^2",
                "Nick Messwerte in rad","Zeitstempel der Messwerte in ns","Anzahl der Messwerte","Start des Zeitfensters in ns seit Start der JVM","Zeitstempel Messwertdaten anfordern in Unixzeit",
                "Start der Messwerterfassung in ns seit Start der JVM","Erster Zeitstempel der Teilliste in ns seit Start der JVM","Start der Belichtung in ns seit Start der JVM","Belichtungszeit in ns",
                "Letzter Zeitstempel der Messwerterfassung in ns seit Start der JVM","Speicherzeitpunkt der Merkmale in Unixzeit"))
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera2_fma, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = view.findViewById(R.id.texture)
        imageCounterTxt = view.findViewById(R.id.imageCounterTxt) as TextView
        speedTxt = view.findViewById(R.id.speedTxt) as TextView
        pitchTxt = view.findViewById(R.id.pitchTxt) as TextView
        val toggleButton = view.findViewById(R.id.toggleButtonStart) as ToggleButton
        val toggleButtonCalibration = view.findViewById(R.id.toggleButtonCalibration) as ToggleButton
        toggleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!takePictures())
                    toggleButton.toggle()
            } else {
                stopPictures()
            }
        }
        toggleButtonCalibration.setOnCheckedChangeListener { _, isChecked ->
            if (motionPositionSensorData != null) {
                if (isChecked) {
                    motionPositionSensorData?.startCalibration()
                } else {
                    motionPositionSensorData?.stopCalibration()
                }
            }
        }
    }

    /**
     * Hier werden alle Objekte initialisiert.
     * Zudem wird die erlaubnis abgefragt den Externen Speicher zu nutzen.
     */
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        df.roundingMode = RoundingMode.CEILING
          time = Time()
          startTime = time.getTime()
          letDirectory = File(Environment.getExternalStoragePublicDirectory(
                 Environment.DIRECTORY_PICTURES), time.getDay())
          if(!letDirectory.mkdirs())
            closeCamera()
    }

    override fun onResume() {
        motionPositionSensorData?.clearData()
        motionPositionSensorData?.startDataCollection()
        startBackgroundThread()
        Logger.writeToLogger(Exception().stackTrace[0], "Nach den Starten des Backgroundthread")
        try {
            Thread.sleep(250)
        } catch (e: IllegalArgumentException) {
            Logger.writeToLogger(Exception().stackTrace[0], e.toString())
        }
        if (textureView.isAvailable) {
            Logger.writeToLogger(Exception().stackTrace[0], "Vor den Öffnen der Kamera Klasse")
            openCamera(textureView.width, textureView.height)
        } else {
            Logger.writeToLogger(Exception().stackTrace[0], "textureView ist nicht verfügbar")
            textureView.surfaceTextureListener = surfaceTextureListener
        }
        super.onResume()
    }

    override fun onPause() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            Logger.writeToLogger(Exception().stackTrace[0],"")
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }


    /**
     * Mit dieser Methode werden mehere Anfragen an den Nutzer gestellt, wenn die jeweilige Erlaubnis nicht gegeben ist,
     * für den Zugriff auf Kamera, externer Schreibzugriff sowie GPS Sensor.
     */
    private fun requestPermissions() {
        val permissionRequest: MutableList<String> = mutableListOf()
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            permissionRequest.add(Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permissionRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            gpsLocation = GPSLocation(activity)
            gpsLocation?.init()
            motionPositionSensorData = MotionPositionSensorData()
            motionPositionSensorData?.init(this.activity, gpsLocation!!)
        }
        if (permissionRequest.size > 0) {
                var message = "Die Anwendung Fahrradwege Monitoring App benötigt Zugriff auf: " + permissionRequest[0]
                for (i in 1 until permissionRequest.size)
                    message = message + ", " + permissionRequest[i]

            showRequestMessage(message,
                     DialogInterface.OnClickListener { _ , _ ->
                          requestPermissions(permissionRequest.toTypedArray(),
                                    REQUEST_MULTIPLE_PERMISSIONS)
                        })
                return
            }
    }

    /**
     * Fragt den Benutzer um Erlaubnis auf bestimmte Dienste des Smartphones zuzugreifen
     */
    private fun showRequestMessage(message: String, okListener: DialogInterface.OnClickListener) {
        AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_MULTIPLE_PERMISSIONS) {
            if (permissions.size == 3) {
                if ((ContextCompat.checkSelfPermission(activity, permissions[0]) == grantResults[0]) &&
                        (ContextCompat.checkSelfPermission(activity, permissions[1]) == grantResults[1]) &&
                        (ContextCompat.checkSelfPermission(activity, permissions[2]) == grantResults[2])) {
                    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
                    gpsLocation = GPSLocation(activity)
                    gpsLocation?.init()
                    motionPositionSensorData = MotionPositionSensorData()
                    motionPositionSensorData?.init(this.activity, gpsLocation!!)
                    return
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /**
     * Hier werden die Exemplarvariablen initialisiert die von der Kamera API verwendet werden
     *
     * @param width  Breite der Kamera Preview
     * @param height Höhe der Kamera Preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int) {
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                // In dieser Anwendung wird nicht die vordere Kamera verwendet
                val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (cameraDirection != null &&
                        cameraDirection == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }
                fpsRange = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
             //   val exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                val map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
                val largest = max(
                        Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
                        CompareSizesByArea())
                val displaySize = Point()
                activity.windowManager.defaultDisplay.getSize(displaySize)
                var maxPreviewWidth = displaySize.x
                var maxPreviewHeight = displaySize.y
                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) maxPreviewWidth = MAX_PREVIEW_WIDTH
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) maxPreviewHeight = MAX_PREVIEW_HEIGHT
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                imageReader = ImageReader.newInstance(captureHeight, captureWidth,
                        ImageFormat.JPEG, 20).apply {
                    setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
                }
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
                        width, height,
                        maxPreviewWidth, maxPreviewHeight,
                        largest)
                    textureView.setAspectRatio(previewSize.height, previewSize.width)
                this.cameraId = cameraId
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: NullPointerException) {
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(childFragmentManager, FRAGMENT_DIALOG)
        }
    }

    /**
     * Öffnet die Kamera [CameraFragment.cameraId].
     */
    private fun openCamera(width: Int, height: Int) {
        val permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
        requestPermissions()
        if (permission != PackageManager.PERMISSION_GRANTED) {
            return
        }
        setUpCameraOutputs(width, height)
        configureTransform(width, height)
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Logger.writeToLogger(Exception().stackTrace[0],"cameraOpenCloseLock")
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            if(ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                manager.openCamera(cameraId, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Logger.writeToLogger(Exception().stackTrace[0],"CameraAccessException")
            Log.e(TAG, e.toString())
        } catch (e: InterruptedException) {
            Logger.writeToLogger(Exception().stackTrace[0],"InterruptedException")
            throw RuntimeException("Interrupted bei der Öffnung der Kamera", e)
        }

    }

    override fun onStop() {
        super.onStop()
        Logger.writeToLogger(Exception().stackTrace[0],"")
        closeCamera()
        gpsLocation?.onStop()
        motionPositionSensorData?.onStop()
    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: InterruptedException) {
            Logger.writeToLogger(Exception().stackTrace[0],"InterruptedException")
            throw RuntimeException("Interrupt während versucht wurde die Kamera zu schließen", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    /**
     * Startet den Hintergrundthread [Handler].
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /**
     * Stopt den Hintergrundthread [Handler].
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Logger.writeToLogger(Exception().stackTrace[0],"InterruptedException")
            Log.e(TAG, e.toString())
        }

    }

    /**
     * Wenn die Kamera geöffnet wurde wird eine [CameraCaptureSession] gestartet. Dabei wird für
     * die Preview kontinuierlich Bilder aufgenommen.
     */
    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture

            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            val surface = Surface(texture)
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW
            )
            previewRequestBuilder.addTarget(surface)
            cameraDevice?.createCaptureSession(Arrays.asList(surface, imageReader?.surface),
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            if (cameraDevice == null) return
                            captureSession = cameraCaptureSession
                            try {
                               previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_OFF)
                                previewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.32f)
                                previewRequest = previewRequestBuilder.build()
                                captureSession?.setRepeatingRequest(previewRequest,
                                        captureCallback, backgroundHandler)
                            } catch (e: CameraAccessException) {
                                Logger.writeToLogger(Exception().stackTrace[0],"CameraAccessException")
                                Log.e(TAG, e.toString())
                            }
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Toast.makeText(activity, "onConfigure Fehler", Toast.LENGTH_SHORT).show()
                            Logger.writeToLogger(Exception().stackTrace[0],"onConfigureFailed")
                        }
                    }, null)
        } catch (e: CameraAccessException) {
            Logger.writeToLogger(Exception().stackTrace[0],"CameraAccessException")
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `textureView` is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        activity ?: return
        val rotation = activity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale = Math.max(
                    viewHeight.toFloat() / previewSize.height,
                    viewWidth.toFloat() / previewSize.width)
            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    /**
     * Nimmt ein vorläufiges Foto auf, um dann ein qualitatives Bild mit guter Belichtung aufzunehmen.
     * Dafür wird die automatisierte Belichtungsberechnung getriggert.
     */
    private fun runPrecaptureSequence() {
        try {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            state = STATE_WAITING_PRECAPTURE
            captureSession?.capture(previewRequestBuilder.build(), captureCallback,
                    backgroundHandler)
        } catch (e: CameraAccessException) {
            Logger.writeToLogger(Exception().stackTrace[0],"CameraAccessException")
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Nimmt ein einzelnes JPG Bild auf.
     *
     */
    private fun captureStillPicture() {
        try {
            if (activity == null || cameraDevice == null) {
                Logger.writeToLogger(Exception().stackTrace[0],"activity oder cameraDevice gleich null")
                return
            }
            var frameNumberStart : Long = 0
            val rotation = activity.windowManager.defaultDisplay.rotation
            val captureBuilder = cameraDevice?.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE)?.apply {// Block an Anweisungen
                addTarget(imageReader?.surface) // Zum abspeichern wird das Bild an den ImageReader Handler geschickt
                // Die Kamera Ausrichtung beträgt 90 Grad auf den meisten Geräten. Bei einigen beträgt diese allerdings 270 Grad
                set(CaptureRequest.JPEG_ORIENTATION,
                        (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360)
                set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF)
                set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.32f)
                // Szenenmodus für bewgende Objekte
                set(CaptureRequest.CONTROL_SCENE_MODE,
                        CaptureRequest.CONTROL_SCENE_MODE_ACTION)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange?.get(fpsRange?.size!! - 1))
                if (gpsLocation != null ) // Wenn ungleich null werden GPS Daten zu den Bild Metadaten hinzugefügt
                    set(CaptureRequest.JPEG_GPS_LOCATION, gpsLocation?.getLocation()
                    )
            }
            val captureCallback = object : CameraCaptureSession.CaptureCallback() {

                override fun onCaptureStarted(session: CameraCaptureSession?, request: CaptureRequest?, timestamp: Long, frameNumber: Long) {
                    frameNumberStart = frameNumber
                    exposureTimeStart = timestamp
                    if(gpsLocation != null ) {
                        location = gpsLocation?.getLocation()
                    }
                }

                override fun onCaptureFailed(session: CameraCaptureSession?, request: CaptureRequest?, failure: CaptureFailure?) {
                    Logger.writeToLogger(Exception().stackTrace[0],"$failure")
                }

                override fun onCaptureCompleted(session: CameraCaptureSession,
                                                request: CaptureRequest,
                                                result: TotalCaptureResult) {
                    if(result.frameNumber == frameNumberStart) { // Nur wenn die Frame Nummern übereinstimmen ist dies das einzelne angefragte Bild
                        exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                        // Nach der Aufnahme wird der Kamera Zustand auf preview gesetzt
                        setRepeatingPreviewRequest()
                    }
                }
            }
            // In diesem Block wird die Kontinuierliche Aufnahme von Bildern abgebrochen
            captureSession?.apply {
                stopRepeating()
                abortCaptures()
            }
            captureSession?.capture(captureBuilder?.build(), captureCallback, null)
        } catch (e: CameraAccessException) {
            Logger.writeToLogger(Exception().stackTrace[0],"CameraAccessException")
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Die Methode wird aufgerufen, nachdem ein einzelnes Foto aufgenommen wurde in der Methode captureStillPicture.
     * Prec.:
     * Postc.: Es werden wieder kontinierlich Bilder für die Preview aufgenommen
     */
    private fun setRepeatingPreviewRequest() {
        try {
            if (state != STATE_TAKE_PICTURE) {
                state = STATE_PREVIEW
                captureSession?.setRepeatingRequest(previewRequest, captureCallback,
                        backgroundHandler)
            }
            } catch (e: CameraAccessException) {
                Logger.writeToLogger(Exception().stackTrace[0], e.toString())
                Log.e(TAG, e.toString())
            }
    }

    private fun takePictures() : Boolean {
        val result : Boolean
        if (!buttonCaptureRequestActive && !activeImageCapturing) {
            buttonCaptureRequestActive = true
            activeImageCapturing = true
            Toast.makeText(activity, "Daten werden erfasst", Toast.LENGTH_SHORT).show()
            result = true
            state = STATE_TAKE_PICTURE
        } else {
            Toast.makeText(activity, "Daten erfassung gestoppt", Toast.LENGTH_SHORT).show()
            result = false
        }
        return result
    }

    /**
     * Die Aufnahme von Bildern wird gestopt.
     */
    private fun stopPictures () {
        buttonCaptureRequestActive = false
    }

    override fun onClick(v: View?) {
    }

    companion object {

        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        private val ORIENTATIONS = SparseIntArray()
        private const val FRAGMENT_DIALOG = "dialog"

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        /**
         * Tag für den [Log]
         */
        private const val TAG = "Camera2 FMA "

        /**
         * Die Kamera zeigt die Preview an
         */
        private const val STATE_PREVIEW = 0

        /**
         * In diesem Zustand wird eine Anfrage gestellt für eine Bildaufnahme
         */
        private const val STATE_TAKE_PICTURE = 1

        /**
         * In diesem Zustand wird ein vorläufige Aufnahme gemacht um die Qualität der automatischen Belichtung
         * zu verbessern
         */
        private const val STATE_WAITING_PRECAPTURE = 2

        /**
         * Wartet darauf das der Belichtungszustand sich nicht mehr in Precapture befindet
         */
        private const val STATE_WAITING_NON_PRECAPTURE = 3

        /**
         * Ein Bild wurde aufgenommen
         */
        private const val STATE_PICTURE_TAKEN = 4

        /**
         * Die Maximale mögliche Preview Höhe welche von der Camera2 API sichergestellt wird
         */
        private const val MAX_PREVIEW_WIDTH = 640

        /**
         * Die Maximale mögliche Preview Breite welche von der Camera2 API sichergestellt wird
         */
        private const val MAX_PREVIEW_HEIGHT = 480

        /**
         * Given `choices` of `Size`s supported by a camera, choose the smallest one that
         * is at least as large as the respective texture view size, and that is at most as large as
         * the respective max size, and whose aspect ratio matches with the specified value. If such
         * size doesn't exist, choose the largest one that is at most as large as the respective max
         * size, and whose aspect ratio matches with the specified value.
         *
         * @param choices           The list of sizes that the camera supports for the intended
         *                          output class
         * @param textureViewWidth  The width of the texture view relative to sensor coordinate
         * @param textureViewHeight The height of the texture view relative to sensor coordinate
         * @param maxWidth          The maximum width that can be chosen
         * @param maxHeight         The maximum height that can be chosen
         * @param aspectRatio       The aspect ratio
         * @return The optimal `Size`, or an arbitrary one if none were big enough
         */
        @JvmStatic private fun chooseOptimalSize(
                choices: Array<Size>,
                textureViewWidth: Int,
                textureViewHeight: Int,
                maxWidth: Int,
                maxHeight: Int,
                aspectRatio: Size
        ): Size {

            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough = ArrayList<Size>()
            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough = ArrayList<Size>()
            val w = aspectRatio.width
            val h = aspectRatio.height
            choices
                    .filter { it.width <= maxWidth && it.height <= maxHeight && it.height == it.width * h / w }
                    .forEach {
                        if (it.width >= textureViewWidth && it.height >= textureViewHeight) {
                            bigEnough.add(it)
                        } else {
                            notBigEnough.add(it)
                        }
                    }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            return when {
                bigEnough.size > 0 -> Collections.min(bigEnough, CompareSizesByArea())
                notBigEnough.size > 0 -> max(notBigEnough, CompareSizesByArea())
                else -> {
                    Logger.writeToLogger(Exception().stackTrace[0],"keine passende Preview-Groeße gefunden")
                    Log.e(TAG, "Es konnte keine passende Preview-Groeße gefunden werden")
                    choices[0]
                }
            }
        }
        @JvmStatic fun newInstance(): CameraFragment = CameraFragment()
    }
}

