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

        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        1);

        }
        // Acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                 locationExemplar = location;
            }
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
            public void onProviderEnabled(String provider) {
            }
            public void onProviderDisabled(String provider) {
            }
            };

        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,locationListener);
    }

    public Location getLocation() {
        return locationExemplar;
    }
}
