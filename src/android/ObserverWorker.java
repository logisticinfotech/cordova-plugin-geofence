package com.cowbell.cordova.geofence;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ConcurrentModificationException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ObserverWorker extends Worker implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "UploadWorker";

    Context context;
    WorkerParameters parameters;
    private static final int REQUEST_CHECK_GPS_ENABLE = 1254;
    private GoogleApiClient googleApiClient;
    private GeofencingClient geofencingClient;

    private String channel_id_sound = "this is a channel id";
    private PendingIntent pendingIntent;
    private boolean gpsIsOn = false;
    public static List<Geofence> allGeoFence = new ArrayList<>();


    public ObserverWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);

        this.context = context;
        parameters = params;

    }

    @Override
    public Result doWork() {


        Log.d(TAG, "doWork: ");

        if (allGeoFence.size() ==0){
            Gson googleGson = new Gson();
            Type listType = new TypeToken<List<GeoNotification>>() {
            }.getType();
            List<GeoNotification> myModelList = null;
            try {
                myModelList = googleGson.fromJson(Prefs.with(context).getString("key",""), listType);
                allGeoFence.clear();
                for (GeoNotification geo : myModelList) {
                    allGeoFence.add(geo.toGeofence());
                }
            } catch (Exception e) {
                Log.e(TAG, "doWork: ",e);
            }
        }

        setupGeoFencing();
        // Indicate whether the task finished successfully with the Result
            return Result.Retry.retry();

    }

    @Override
    public void onStopped() {
        super.onStopped();
        Log.d(TAG, "onStopped: ");
    }


    private void setupGeoFencing() {
        Log.d(TAG, "setupGeoFencing: ");
        geofencingClient = LocationServices.getGeofencingClient(context);

        googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        googleApiClient.connect();
    }

    private void startGeofencing() {
        Log.d(TAG, "Start geofencing monitoring call");

        if (!googleApiClient.isConnected() || (allGeoFence.size() <= 0)) {
            Log.d(TAG, "Google API client not connected");
        } else {
            try {
                geofencingClient.addGeofences(getGeofencingRequest(), getGeofencePendingIntent())
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                // Geofences added
                                // ...
                                Log.d(TAG, "onSuccess: ");
                            }

                        }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Failed to add geofences
                        // ...
                        Log.e(TAG, "onFailure: " + e);
                        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            Log.e(TAG, "Provider is not avaible");
                        }
                        if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                            Log.e(TAG, "Provider is not avaible");
                        }
                    }
                });
            } catch (SecurityException e) {
                Log.d(TAG, e.getMessage());
            }
        }
    }

    private GeofencingRequest getGeofencingRequest() {
        try{
            GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
            builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER | GeofencingRequest.INITIAL_TRIGGER_DWELL | GeofencingRequest.INITIAL_TRIGGER_EXIT);
            builder.addGeofences(allGeoFence);
            return builder.build();    
        }catch (ConcurrentModificationException ce){
            return null;
        }catch (Exception e){
            return null;
        } 
    }


    private PendingIntent getGeofencePendingIntent() {
        if (pendingIntent != null) {
            return pendingIntent;
        }
        Intent intent = new Intent(context, ReceiveTransitionsIntentService.class);
        return PendingIntent.getService(context, 0, intent, PendingIntent.
                FLAG_UPDATE_CURRENT);
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected: ");
        startLocationMonitor();
        startGeofencing();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended: ");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed: ");
    }

    private void startLocationMonitor() {
        Log.d(TAG, "start location monitor");
        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(10000)
                .setFastestInterval(5000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    Log.d(TAG, "Location Change Lat Lng " + location.getLatitude() + " " + location.getLongitude());
                    Log.d(TAG, "onLocationChanged: Total GeoFence = " + allGeoFence.size());
                }
            });
        } catch (SecurityException e) {
            Log.d(TAG, e.getMessage());
        }

    }
}
