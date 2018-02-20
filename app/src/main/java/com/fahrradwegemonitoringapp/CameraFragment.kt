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
 * Quelle: Basic Camera2 example https://github.com/googlesamples/android-Camera2Basic/tree/master/kotlinApp
 */

package com.fahrradwegemonitoringapp

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.content.res.Configuration
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

class CameraFragment : Fragment(), View.OnClickListener,
        ActivityCompat.OnRequestPermissionsResultCallback {

    /**
     * ID der Kamera [CameraDevice].
     */
    private lateinit var cameraId: String

    @Volatile private var buttonCaptureRequestActive : Boolean = false

    private var  activeImageCapturing : Boolean = false

    /**
     * Um die aktuelle Geschwindigkeit für den Nutzer sichtbar zu machen
     */
    private lateinit var speedTxt : TextView

    /**
     * Anzahl der gespeicherten Bilder
     */
    private var imageCounter : Int = 0

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

    private var realtimeNanos: Long = 0
    private var realtimeElapsed: Long = 0
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
    private var exposureTime: Long = 0

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
     * Der aktuelle Zustand um Bilder aufzunehmen.
     * Der Anfangszustandand ist Preview. In welchem für die Preview Bilder aufgenommen werden
     *
     * @see .captureCallback
     */
    private var state = STATE_PREVIEW

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

        private fun process(result: CaptureResult) {
            when (state) {
                STATE_PREVIEW -> Unit
                STATE_WAITING_LOCK -> capturePicture(result)
                STATE_WAITING_PRECAPTURE -> {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        state = STATE_WAITING_NON_PRECAPTURE
                        Logger.writeToLogger("CameraFragment: captureCallback() STATE_WAITING_NON_PRECAPTURE \n")
                    } else {
                        runPrecaptureSequence()
                    }
                }
                STATE_WAITING_NON_PRECAPTURE -> {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        state = STATE_PICTURE_TAKEN
                        Logger.writeToLogger("CameraFragment: captureCallback() STATE_PICTURE_TAKEN \n")
                        captureStillPicture()
                    }
                }
            }
        }

        private fun capturePicture(result: CaptureResult) {
            val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
            if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
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
            Logger.writeToLogger("CameraFragment: stateCallback Display onError \n")
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@CameraFragment.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            Logger.writeToLogger("CameraFragment: stateCallback onError \n")
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
     * zum abspeichern. Zu den Bild werden Features gesichert. Es wird für den Nutzer auch eine Text Ausgabe erstellt,
     * um diesen die aktuelle Geschwindigkeit und die Anzahl der bereits aufgenommen Bilder mitzuteilen.
     */
    private val onImageAvailableListener = OnImageAvailableListener {
        val image = it.acquireLatestImage()
        if (image != null) {
            if (location != null) {
                speed = (location?.speed!! * 60 * 60) / 1000 // Umrechnung von m/s in km/h
              //  if (((speed - 4.0f) > 0.0001)) {  // bei vorhandener Geschwindigkeit
                if (imageCounter % (2000 * directoriesCounter) == 0) {
                    newFolder()
                }
                saveFeatures(image.timestamp)
                saveImage(image)
                if (imageCounter % 10 == 0) {
                    activity.runOnUiThread({
                        run {
                            speedTxt.text = "%s %s %d %s".format(df.format(speed), "km/h /", imageCounter, "Bilder")
                        }
                    })
                }
              //  } else {
               //    image.close()
               //  }
            } else {
                Toast.makeText(activity, "Lokation war null", Toast.LENGTH_SHORT).show()
                Logger.writeToLogger("Lokation war null \n")
                image.close()
            }
        }
        requestNextImage()
    }

    /**
     * Prec.: Image != null
     * Post.: Bild ist abgespeichert
     */
    private fun saveImage(image: Image) {
        ImageSaver().saveImage(image, File(actualDirectory, ( "${image.timestamp}" + ".jpg")))
        imageCounter++
    }

    /**
     * Speichert Features ab: Zeit in ms/ GPS /
     * Prec.: Diese Methode muss vor der Methode saveImage aufgerufen werden. timestamp > 0
     * Postc.: Features wurden in eine Datei geschrieben
     */
    private fun saveFeatures(timestamp : Long) {
        val latitude = location?.latitude?.toFloat()
        val longitude = location?.longitude?.toFloat()
        val accelerometerString = motionPositionSensorData?.getData()
        fileLocation.appendText("$timestamp,$latitude,$longitude," +
                "$speed,$accelerometerString,${motionPositionSensorData?.getFirstTimestamp()}," +
                "$exposureTime,${motionPositionSensorData?.getLastTimestamp()},$realtimeNanos,$realtimeElapsed \n")
    }

    /**
     * Prec.: - Alle Bilder vom ImageReader sind abgearbeitet
     * Postc.: Wenn actualTakingPictures wahr ist wurde die Methode actualTakingPictures aufgerufen
     */
    private fun requestNextImage() {
        if(buttonCaptureRequestActive) {
            lockFocus()
        } else {
            activeImageCapturing = false
        }
    }

    /**
     * Mit dieser Methode kann ein neuer Ordner erstellt werden für Feature Daten.
     * Prec.:
     * Postc.: Ein neuer Ordner für die Features wurde erstellt
     */
    private fun newFolder() {
        if(imageCounter>1) {
            directoriesCounter++
        }
        actualDirectory = File(letDirectory, "$directoriesCounter")
        actualDirectory.mkdir()
        fileLocation = File(actualDirectory, ("features.csv"))
        fileLocation.appendText("Zeitstempel,Breitengrad,Laengengrad,Geschwindigkeit," +
                "MittelWX,VarianzX,StandardAX,MittelWY,VarianzY,StandardAY,MittelWZ,VarianzZ,StandardAZ," +
                "Azimuth,MittelWPitch,VarianzPitch,StandardAPitch,MittelWRoll,VarianzRoll,StandardRoll,StartBewegungsD,Belichtungszeit,StopBewegungsD \n")
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera2_fma, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = view.findViewById(R.id.texture)
        speedTxt = view.findViewById(R.id.speedTxt) as TextView
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
        super.onResume()
        startBackgroundThread()
        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            Logger.writeToLogger("CameraFragment: onPause() wurde aufgerufen \n")
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
     * @param width  Die breite der Kamera Preview
     * @param height Höhe der Kamera Preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int) {
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                // In dieser Anwendung wird nicht die vorder Kamera verwendet
                val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (cameraDirection != null &&
                        cameraDirection == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }
                val map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue

                // For still image captures, we use the largest available size.
                val largest = max(
                        Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
                        CompareSizesByArea())
                imageReader = ImageReader.newInstance(captureHeight, captureWidth,
                        ImageFormat.JPEG, /*maxImages*/ 20).apply {
                    setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
                }

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                val displayRotation = activity.windowManager.defaultDisplay.rotation

                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                val swappedDimensions = areDimensionsSwapped(displayRotation)

               val displaySize = Point()
                activity.windowManager.defaultDisplay.getSize(displaySize)
                val rotatedPreviewWidth = if (swappedDimensions) height else width
                val rotatedPreviewHeight = if (swappedDimensions) width else height
                var maxPreviewWidth = if (swappedDimensions) displaySize.y else displaySize.x
                var maxPreviewHeight = if (swappedDimensions) displaySize.x else displaySize.y

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) maxPreviewWidth = MAX_PREVIEW_WIDTH
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) maxPreviewHeight = MAX_PREVIEW_HEIGHT

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
                        rotatedPreviewWidth, rotatedPreviewHeight,
                        maxPreviewWidth, maxPreviewHeight,
                        largest)

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(previewSize.width, previewSize.height)
                } else {
                    textureView.setAspectRatio(previewSize.height, previewSize.width)
                }
                this.cameraId = cameraId
                return
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(childFragmentManager, FRAGMENT_DIALOG)
        }

    }

    /**
     * Determines if the dimensions are swapped given the phone's current rotation.
     *
     * @param displayRotation The current rotation of the display
     *
     * @return true if the dimensions are swapped, false otherwise.
     */
    private fun areDimensionsSwapped(displayRotation: Int): Boolean {
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    swappedDimensions = true
                }
            }
            else -> {
                Logger.writeToLogger("CameraFragment: areDimensionsSwapped()  Display rotation ist ungültig \n")
                Log.e(TAG, "Display rotation ist ungültig: $displayRotation")
            }
        }
        return swappedDimensions
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
                Logger.writeToLogger("CameraFragment: openCamera()  cameraOpenCloseLock \n")
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            if(ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                manager.openCamera(cameraId, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Logger.writeToLogger("CameraFragment: openCamera()  keine CameraAccessException \n")
            Log.e(TAG, e.toString())
        } catch (e: InterruptedException) {
            Logger.writeToLogger("CameraFragment: openCamera()  InterruptedException \n")
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }

    }

    override fun onStop() {
        super.onStop()
        Logger.writeToLogger("CameraFragment: onStop() wurde aufgerufen \n")
        closeCamera()
        motionPositionSensorData?.onStop()
        gpsLocation?.onStop()
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
            Logger.writeToLogger("CameraFragment: closeCamera() InterruptedException\n")
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Logger.writeToLogger("CameraFragment: stopBackgroundThread() InterruptedException\n")
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
                                Logger.writeToLogger("CameraFragment: createCameraPreviewSession() CameraAccessException\n")
                                Log.e(TAG, e.toString())
                            }
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Toast.makeText(activity, "onConfigure Fehler", Toast.LENGTH_SHORT).show()
                            Logger.writeToLogger("CameraFragment: createCameraPreviewSession() onConfigureFailed\n")
                        }
                    }, null)
        } catch (e: CameraAccessException) {
            Logger.writeToLogger("CameraFragment: createCameraPreviewSession() CameraAccessException\n")
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
     * Erster Schritt um ein Bild aufzunehmen
     */
    private fun lockFocus() {
        try {
            state = STATE_WAITING_LOCK
            captureSession?.capture(previewRequestBuilder.build(), captureCallback,
                    backgroundHandler)
        } catch (e: CameraAccessException) {
            Logger.writeToLogger("CameraFragment: lockFocus() CameraAccessException \n")
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Nimmt ein vorläufiges Foto auf, um dann ein qualitatives Bild aufzunehmen.
     * Dafür wird die automatisierte Belichtungsberechnung getriggert
     */
    private fun runPrecaptureSequence() {
        try {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            state = STATE_WAITING_PRECAPTURE
            captureSession?.capture(previewRequestBuilder.build(), captureCallback,
                    backgroundHandler)
        } catch (e: CameraAccessException) {
            Logger.writeToLogger("CameraFragment: runPrecaptureSequence() CameraAccessException \n")
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Nimmt ein einzelnes JPG Bild auf.
     */
    private fun captureStillPicture() {
        try {
            if (activity == null || cameraDevice == null) {
                Logger.writeToLogger("CameraFragment: captureStillPicture() activity == null || cameraDevice == null \n")
                return
            }
            motionPositionSensorData?.clearData() // Vor der Aufnahme werden die letzten erfassten Sensordaten(Beschl./Gier-Roll-Nick) entfernt
            var frameNumberStart : Long = 0
            val rotation = activity.windowManager.defaultDisplay.rotation
            val captureBuilder = cameraDevice?.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE)?.apply {
                addTarget(imageReader?.surface) // Zum abspeichern wird das Bild an den ImageReader Handler geschickt

                // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
                // We have to take that into account and rotate JPEG properly.
                // For devices with orientation of 90, we return our mapping from ORIENTATIONS.
                // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
                set(CaptureRequest.JPEG_ORIENTATION,
                        (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360)
                set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF)
                set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.32f)
                if (gpsLocation != null ) // Wenn ungleich null werden GPS Daten zu den Bild Metadaten hinzugefügt
                    set(CaptureRequest.JPEG_GPS_LOCATION, gpsLocation?.getLocation()
                )
            }
            val captureCallback = object : CameraCaptureSession.CaptureCallback() {

                override fun onCaptureStarted(session: CameraCaptureSession?, request: CaptureRequest?, timestamp: Long, frameNumber: Long) {
                    frameNumberStart = frameNumber
                    if(gpsLocation != null ) {
                        location = gpsLocation?.getLocation()
                    }
                    Logger.writeToLogger("CameraFragment: captureStillPicture() onCaptureStarted timestamp: $timestamp \n")
                }

                override fun onCaptureFailed(session: CameraCaptureSession?, request: CaptureRequest?, failure: CaptureFailure?) {
                    Logger.writeToLogger("CameraFragment: onCaptureFailed():  $failure \n")
                }


                override fun onCaptureCompleted(session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult) {
                    if(result.frameNumber == frameNumberStart) { // Nur wenn die Frame Nummern übereinstimmen ist dies das einzelne angefragte Bild
                        motionPositionSensorData?.stopDataCollection()
                        exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                        realtimeElapsed = SystemClock.elapsedRealtimeNanos()
                        realtimeNanos = System.nanoTime()
                        Logger.writeToLogger("CameraFragment: captureStillPicture() onCaptureCompleted: FOCUS DISTANCE:  " +
                                "${result.get(CaptureResult.LENS_FOCUS_DISTANCE)} /  ISO: ${result.get(CaptureResult.SENSOR_SENSITIVITY)} " +
                                " / Belichtungszeit: ${result.get(CaptureResult.SENSOR_EXPOSURE_TIME)} " +
                                "/ Frame Rate: ${result.get(CaptureResult.SENSOR_FRAME_DURATION)} / Zeitstempel: ${result.get(CaptureResult.SENSOR_TIMESTAMP)} \n")
                        // Nach der Aufnahme wird der Kamera Zustand auf preview gesetzt
                        unlockFocus()
                    }
                }
            }
            // In diesem Block wird die Konitinuierlich Aufnahme von Bildern abgebrochen und eine Anfrage
            // gestellt für eine einzelne Aufnahme
            captureSession?.apply {
                stopRepeating()
                abortCaptures()
                motionPositionSensorData?.startDataCollection()
                capture(captureBuilder?.build(), captureCallback, null)
            }

        } catch (e: CameraAccessException) {
            Logger.writeToLogger("CameraFragment: captureStillPicture() CameraAccessException\n")
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Die Methode wird aufgerufen, nachdem ein einzelnes Foto aufgenommen wurde in der Methode captureStillPicture.
     */
    private fun unlockFocus() {
        try {
            state = STATE_PREVIEW
            captureSession?.setRepeatingRequest(previewRequest, captureCallback,
                    backgroundHandler)
        } catch (e: CameraAccessException) {
            Logger.writeToLogger("CameraFragment: unlockFocus() CameraAccessException \n")
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
            lockFocus()
        } else {
            Toast.makeText(activity, "Daten erfassung gestoppt", Toast.LENGTH_SHORT).show()
            result = false
        }
        return result
    }

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
         * Tag for the [Log].
         */
        private const val TAG = "Camera2 FMA "

        /**
         * Camera state: Showing camera preview.
         */
        private const val STATE_PREVIEW = 0

        /**
         * Camera state: Waiting for the focus to be locked.
         */
        private const val STATE_WAITING_LOCK = 1

        /**
         * Camera state: Waiting for the exposure to be precapture state.
         */
        private const val STATE_WAITING_PRECAPTURE = 2

        /**
         * Camera state: Waiting for the exposure state to be something other than precapture.
         */
        private const val STATE_WAITING_NON_PRECAPTURE = 3

        /**
         * Camera state: Picture was taken.
         */
        private const val STATE_PICTURE_TAKEN = 4

        /**
         * Max preview width that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_WIDTH = 640

        /**
         * Max preview height that is guaranteed by Camera2 API
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
                    Logger.writeToLogger("CameraFragment: chooseOptimalSize()  keine passende preview groeße \n")
                    Log.e(TAG, "Es konnte keine passende preview groeße gefunden werden")
                    choices[0]
                }
            }
        }

        @JvmStatic fun newInstance(): CameraFragment = CameraFragment()
    }
}
