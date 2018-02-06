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
 * Created by morro on 26.01.2018.
 */

public class GPSLocation {

    private Location locationExemplar;

    private Activity activity;

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
                Toast.makeText(activity, "LocationListener: methode: onStatusChanged: status: " + status, duration).show();
                Logger.INSTANCE.writeToLogger("LocationListener: methode: onStatusChanged: status: " + status);
              //  init();
            }
            public void onProviderEnabled(String provider) {
                Toast.makeText(activity, "LocationListener: methode: onProviderEnabled: provider: " + provider, duration).show();
                Logger.INSTANCE.writeToLogger("LocationListener: methode: onProviderEnabled: provider: " + provider);
            }
            public void onProviderDisabled(String provider) {
                Toast.makeText(activity, "LocationListener: methode: onProviderDisabled: provider: " + provider, duration).show();
                Logger.INSTANCE.writeToLogger("LocationListener: methode: onProviderEnabled: provider: " + provider);
                init();
            }
            };

        // Registriert den listener mit den Location Manager um Lokations Updates zu erhalten
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,400,0,locationListener);
    }

    public Location getLocation() {
        return locationExemplar;
    }
}
