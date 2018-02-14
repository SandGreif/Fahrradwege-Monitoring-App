package com.example.morro.fahrradwegemonitoringapp;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import java.io.File;

/**
 * Diese Klasse dient dazu eine GPS Lokation anzubieten. Der Zustand der Lokation wird mit Hilfe
 * des locationListener stetig aktualisiert.
 * Created by morro on 26.01.2018.
 */

public class GPSLocation {

    private Location locationExemplar;

    private Activity activity;

    /**
     * Wird benötigt um abzufragen ob  beim letzten Aufruf der callback Methode
     * onStatusChanged der Status 2 war
     */
    private int providerLastStatus = 0;

    public GPSLocation(Activity activity) {
        this.activity = activity;
    }

    public void init() {
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        1);
        }

        LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        final int duration = Toast.LENGTH_LONG;

        // Definiert ein listener der auf Lokations Update reagiert
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Wird aufgerufen wenn eine neue Lokation gefunden wurde von den Lokations Provider
                 locationExemplar = location;
            }
            public void onStatusChanged(String provider, int status, Bundle extras) {
                String statusTxt = "";
                switch(status) {
                    case 0:
                        statusTxt = "OUT_OF_SERVICE";
                        providerLastStatus = 0;
                        break;
                    case 1:
                        statusTxt = "TEMPORARILY_UNAVAILABLE";
                        providerLastStatus = 1;
                        break;
                    case 2:
                        statusTxt = "AVAILABLE";
                        break;
                    default:
                        statusTxt = "no valid state found";
                        providerLastStatus = -1;
                }
                if (providerLastStatus != 2) {
                    Toast.makeText(activity, "LocationListener: Methode: onStatusChanged: Status: " + statusTxt, duration).show();
                    Logger.INSTANCE.writeToLogger("LocationListener: methode: onStatusChanged: status: " + status);
                }
                if(status == 2) {
                    providerLastStatus = 2;
                }
            }
            public void onProviderEnabled(String provider) {
                Toast.makeText(activity, "LocationListener: Methode: onProviderEnabled: provider: " + provider, duration).show();
                Logger.INSTANCE.writeToLogger("LocationListener: methode: onProviderEnabled: provider: " + provider);
            }
            public void onProviderDisabled(String provider) {
                Toast.makeText(activity, "LocationListener: Methode: onProviderDisabled: provider: " + provider, duration).show();
                Logger.INSTANCE.writeToLogger("LocationListener: methode: onProviderEnabled: provider: " + provider);
            }
            };

        // Registriert den listener mit den Location Manager um Lokations Updates zu erhalten
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,200,1,locationListener);
    }

    public Location getLocation() {
        return locationExemplar;
    }
}
