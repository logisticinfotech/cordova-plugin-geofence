package com.cowbell.cordova.geofence;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.util.Log;
import android.Manifest;

import com.cowbell.cordova.geofence.geofencebackgroundsrvice.GeoFencingObserverService;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class GeofencePlugin extends CordovaPlugin {
    public static final String TAG = "GeofencePlugin";

    public static final boolean inBackground = false;
    public static final String ERROR_UNKNOWN = "UNKNOWN";
    public static final String ERROR_PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String ERROR_GEOFENCE_NOT_AVAILABLE = "GEOFENCE_NOT_AVAILABLE";
    public static final String ERROR_GEOFENCE_LIMIT_EXCEEDED = "GEOFENCE_LIMIT_EXCEEDED";
    private static final int REQUEST_CHECK_GPS_ENABLE = 1256;

    private GeoNotificationManager geoNotificationManager;
    private Context context;
    public static CordovaWebView webView = null;
    private boolean gpsIsOn = false;
    private Activity activity;

    public static boolean isLogin = true;
    public static boolean isGeoFenceAdded = false;

    private class Action {
        public String action;
        public JSONArray args;
        public CallbackContext callbackContext;

        public Action(String action, JSONArray args, CallbackContext callbackContext) {
            this.action = action;
            this.args = args;
            this.callbackContext = callbackContext;
        }
    }

    //FIXME: what about many executedActions at once
    private Action executedAction;

    /**
     * @param cordova The context of the main Activity.
     * @param webView The associated CordovaWebView.
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        GeofencePlugin.webView = webView;
        context = this.cordova.getActivity().getApplicationContext();
        activity = this.cordova.getActivity();
        Logger.setLogger(new Logger(TAG, context, false));
        geoNotificationManager = new GeoNotificationManager(context);
    }

    @Override
    public boolean execute(final String action, final JSONArray args,
                           final CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, "GeofencePlugin execute action: " + action + " args: " + args.toString());
        executedAction = new Action(action, args, callbackContext);

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                if (action.equals("addOrUpdate")) {
                    List<GeoNotification> geoNotifications = new ArrayList<GeoNotification>();
                    for (int i = 0; i < args.length(); i++) {
                        GeoNotification not = parseFromJSONObject(args.optJSONObject(i));
                        if (not != null) {
                            geoNotifications.add(not);
                        }
                    }
                    geoNotificationManager.addGeoNotifications(geoNotifications, callbackContext);
                } else if (action.equals("remove")) {
                    List<String> ids = new ArrayList<String>();
                    for (int i = 0; i < args.length(); i++) {
                        ids.add(args.optString(i));
                    }
                    geoNotificationManager.removeGeoNotifications(ids, callbackContext);
                } else if (action.equals("removeAll")) {
                    geoNotificationManager.removeAllGeoNotifications(callbackContext);
                } else if (action.equals("getWatched")) {
                    List<GeoNotification> geoNotifications = geoNotificationManager.getWatched();
                    callbackContext.success(Gson.get().toJson(geoNotifications));
                } else if (action.equals("initialize")) {
                    initialize(callbackContext);
                } else if (action.equals("deviceReady")) {
                    deviceReady();
                } else if (action.equals("startServiceInBackground")) {

                } else if (action.equals("startServiceInForeground")) {

                }
            }
        });

        return true;
    }

    public boolean execute(Action action) throws JSONException {
        return execute(action.action, action.args, action.callbackContext);
    }

    private GeoNotification parseFromJSONObject(JSONObject object) {
        GeoNotification geo = GeoNotification.fromJson(object.toString());
        return geo;
    }

    public static void onTransitionReceived(List<GeoNotification> notifications) {
        Log.d(TAG, "Transition Event Received!");
        String js = "setTimeout(() => { geofence.onTransitionReceived(" + Gson.get().toJson(notifications) + ") }, 0)";
        if (webView == null) {
            Log.d(TAG, "Webview is null");
        } else {
            webView.sendJavascript(js);
        }
    }

    private void deviceReady() {
        Intent intent = cordova.getActivity().getIntent();
        String data = intent.getStringExtra("geofence.notification.data");
        String js = "setTimeout(() => { geofence.onNotificationClicked(" + data + ") }, 0)";

        if (data == null) {
            Log.d(TAG, "No notifications clicked.");
        } else {
            webView.sendJavascript(js);
        }
    }

    private void initialize(CallbackContext callbackContext) {
        String[] permissions = {
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        };

        if (!hasPermissions(permissions)) {
            PermissionHelper.requestPermissions(this, 0, permissions);
        } else {
            callbackContext.success();
            handleGPSTracker();
        }
    }

    private void handleGPSTracker() {
        Log.d(TAG, "handleGPSTracker: ");
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(2000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setSmallestDisplacement(100);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);


        LocationSettingsRequest.Builder builder1 = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        SettingsClient client = LocationServices.getSettingsClient(context);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder1.build());

        task.addOnSuccessListener(command -> {
            gpsIsOn = true;
            Log.d(TAG, "handleGPSTracker: success");
//            GeoFencingObserverService.stopService(this);
//            GeoFencingObserverService.startService(this, false);
        });


        task.addOnCompleteListener(
                task1 -> {
                    try {
                        LocationSettingsResponse response = task1.getResult(ApiException.class);
                        // All location settings are satisfied. The client can initialize location
                        // requests here.
                        gpsIsOn = true;
                        Log.d(TAG, "handleGPSTracker: success");


                    } catch (ApiException exception) {
                        switch (exception.getStatusCode()) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                // Location settings are not satisfied. But could be fixed by showing the
                                // user a dialog.
                                try {
                                    // Cast to a resolvable exception.
                                    ResolvableApiException resolvable = (ResolvableApiException) exception;
                                    // Show the dialog by calling startResolutionForResult(),
                                    // and check the result in onActivityResult().
                                    resolvable.startResolutionForResult(
                                            activity,
                                            REQUEST_CHECK_GPS_ENABLE);
                                } catch (IntentSender.SendIntentException e) {
                                    // Ignore the error.
                                    Log.e(TAG, "handleGPSTracker: ", e);
                                } catch (ClassCastException e) {
                                    // Ignore, should be an impossible error.
                                    Log.e(TAG, "handleGPSTracker: ", e);
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                // Location settings are not satisfied. However, we have no way to fix the
                                // settings so we won't show the dialog.
                                Log.d(TAG, "handleGPSTracker:  settings so we won't show the dialog");
                                break;
                        }
                    }
                });

    }

    private boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (!PermissionHelper.hasPermission(this, permission)) return false;
        }

        return true;
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        PluginResult result;

        if (executedAction != null) {
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_DENIED) {
                    Log.d(TAG, "Permission Denied!");
                    result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
                    executedAction.callbackContext.sendPluginResult(result);
                    executedAction = null;
                    return;
                }
            }
            Log.d(TAG, "Permission Granted!");
            execute(executedAction);
            executedAction = null;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: ");
        if (isLogin && isGeoFenceAdded){
            GeoFencingObserverService.stopService(context);
            GeoFencingObserverService.startService(context, false);
        }

    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: ");
        if (isGeoFenceAdded && isLogin) {
            GeoFencingObserverService.stopService(context);
            GeoFencingObserverService.startService(context,true);
        }
    }
}
