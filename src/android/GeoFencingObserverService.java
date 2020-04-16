package com.cowbell.cordova.geofence;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.apptivatellc.iSS.MainActivity;
import com.apptivatellc.iSS.R;
import com.cowbell.cordova.geofence.ReceiveTransitionsIntentService;
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

import java.util.ArrayList;
import java.util.List;

public class GeoFencingObserverService extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final int REQUEST_CHECK_GPS_ENABLE = 1254;
    private GoogleApiClient googleApiClient;
    private GeofencingClient geofencingClient;

    private static final String TAG = "GeoFencingObserverServi";
    private static final String START_SERVICE = "start_service";
    private static final String STOP_SERVICE = "stop_service";
    private static final String FLAG = "flag_extra";
    private static final String FLAG_BACKGROUND = "background";


    private String channel_id_sound = "this is a channel id";
    private PendingIntent pendingIntent;
    private boolean gpsIsOn = false;
    public static List<Geofence> allGeoFence = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: ");
        setupGeoFencing();

    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        Log.d(TAG, "onStartCommand: ");

        try{
            if (intent.hasExtra(FLAG_BACKGROUND))
                if (intent.getBooleanExtra(FLAG_BACKGROUND, false)) {
                    startForeGroundService();

                }    
        }catch (Exception e){
            Log.d(TAG, "onStartCommand: ");
        }

        return START_STICKY;
    }

    private void setupGeoFencing() {
        Log.d(TAG, "setupGeoFencing: ");
        geofencingClient = LocationServices.getGeofencingClient(this);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        googleApiClient.connect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: ");

        googleApiClient.disconnect();

//        geofencingClient.removeGeofences(getGeofencePendingIntent());
        super.onDestroy();
    }

    private void startForeGroundService() {
        Log.d(TAG, "startForeGroundService: ");
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);



        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.app_name));
        builder.setContentTitle(getString(R.string.app_name))
                .setContentText("Run in Background")
                .setSmallIcon(R.mipmap.ic_launcher)
//                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher))
                .setAutoCancel(true)
                .setWhen(0)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)

                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

                .setContentIntent(pendingIntent);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }


        startForeground((int) System.currentTimeMillis(), builder.build());


    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "onTaskRemoved: ");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    getString(R.string.app_name),
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            serviceChannel.setShowBadge(false);
            serviceChannel.enableLights(false);
            serviceChannel.setSound(null, null);


            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel(Context context, NotificationManager mNotificationManager, boolean playSound) {

        CharSequence name = context.getString(R.string.app_name);

        // The user-visible description of the channel.

        String description = context.getString(R.string.app_name);
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel mChannel = new NotificationChannel(channel_id_sound, name, importance);

        // Configure the notification channel.
        mChannel.setDescription(description);
        mChannel.enableLights(true);

        // Sets the notification light color for notifications posted to this
        // channel, if the device supports this feature.
        mChannel.setLightColor(Color.RED);
        mChannel.enableVibration(true);
        mChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
        if (!playSound)
            mChannel.setSound(null, null);
        mNotificationManager.createNotificationChannel(mChannel);
    }


    public static void startService(Context context, boolean isInBackground) {
        Intent intent = new Intent(context, GeoFencingObserverService.class);
        intent.putExtra(FLAG, START_SERVICE);
        intent.putExtra(FLAG_BACKGROUND, isInBackground);
        context.startService(intent);
    }

    public static void stopService(Context context) {
        Intent intent = new Intent(context, GeoFencingObserverService.class);
        intent.putExtra(FLAG, STOP_SERVICE);
        context.stopService(intent);
    }


    private void startGeofencing() {
        Log.d(TAG, "Start geofencing monitoring call");

        if (!googleApiClient.isConnected() || (allGeoFence.size() <= 0)) {
            Log.d(TAG, "Google API client not connected");
        } else {
            try {
                geofencingClient.addGeofences(getGeofencingRequest(), getGeofencePendingIntent())
                        .addOnSuccessListener( new OnSuccessListener<Void>() {
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
                        Log.e(TAG, "onFailure: "+e);
                        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                            Log.e(TAG, "Provider is not avaible");
                        }
                        if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
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
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(allGeoFence);
        return builder.build();
    }



    private PendingIntent getGeofencePendingIntent() {
        if (pendingIntent != null) {
            return pendingIntent;
        }
        Intent intent = new Intent(this, ReceiveTransitionsIntentService.class);
        return PendingIntent.getService(this, 0, intent, PendingIntent.
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
                .setInterval(2000)
                .setFastestInterval(1000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        try {
//            LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(locationRequest)
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    Log.d(TAG, "Location Change Lat Lng " + location.getLatitude() + " " + location.getLongitude());
                    Log.d(TAG, "onLocationChanged: Total GeoFence = "+allGeoFence.size());
                }
            });
        } catch (SecurityException e) {
            Log.d(TAG, e.getMessage());
        }

    }



}