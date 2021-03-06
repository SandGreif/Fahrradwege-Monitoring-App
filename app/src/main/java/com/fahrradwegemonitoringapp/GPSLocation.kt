package com.fahrradwegemonitoringapp
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.widget.Toast


/**
 * Diese Klasse dient dazu eine GPS Lokation anzubieten. Der Zustand der Lokation wird mit Hilfe
 * des locationListener stetig aktualisiert.
 * Created by morro on 26.01.2018.
 */

class GPSLocation(private val activity: Activity)  {

    private var location: Location? = null

    private lateinit var locationManager : LocationManager

    private lateinit var locationListener : LocationListener

    /**
     * Wird benötigt um abzufragen ob  beim letzten Aufruf der callback Methode
     * onStatusChanged der Status 2 war
     */
    private var providerLastStatus = 0

    /**
     * Initialisiert die Klasse
      */
    fun init() {
        locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val duration = Toast.LENGTH_LONG

        // Definiert ein listener der auf Lokations Update reagiert
        locationListener = object : LocationListener {
            override fun onLocationChanged(locationListener: Location) {
                // Wird aufgerufen wenn eine neue Lokation gefunden wurde von den Lokations Provider
                location = locationListener
            }

            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
                val statusTxt: String
                when (status) {
                    0 -> {
                        statusTxt = "OUT_OF_SERVICE"
                        providerLastStatus = 0
                    }
                    1 -> {
                        statusTxt = "TEMPORARILY_UNAVAILABLE"
                        providerLastStatus = 1
                    }
                    2 -> statusTxt = "AVAILABLE"
                    else -> {
                        statusTxt = "no valid state found"
                        providerLastStatus = -1
                    }
                }
                if (providerLastStatus != 2) {
                    Toast.makeText(activity, "LocationListener: Methode: onStatusChanged: Status: " + statusTxt, duration).show()
                    Logger.writeToLogger(Exception().stackTrace[0],"status: " + status)
                }
                if (status == 2) {
                    providerLastStatus = 2
                }
            }

            override fun onProviderEnabled(provider: String) {
                Toast.makeText(activity, "LocationListener: Methode: onProviderEnabled: provider: " + provider, duration).show()
                Logger.writeToLogger(Exception().stackTrace[0],"provider: " + provider)
            }

            override fun onProviderDisabled(provider: String) {
                Toast.makeText(activity, "LocationListener: Methode: onProviderDisabled: provider: " + provider, duration).show()
                Logger.writeToLogger(Exception().stackTrace[0],"provider: " + provider)
            }
        }

        if (ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // Registriert den listener mit den Location Manager um Lokations Updates zu erhalten
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, locationListener)
        }

    }


    fun getLocation(): Location? {
        return location
    }

    /**
     * Meldet die Listener ab. Sollte aufgerufen wenn die App geschlossen wird.
     */
    fun onStop() {
        locationManager.removeUpdates(locationListener)
    }

}
