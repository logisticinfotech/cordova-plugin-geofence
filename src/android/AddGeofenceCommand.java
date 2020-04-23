package com.cowbell.cordova.geofence;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.location.Geofence;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.cordova.LOG;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AddGeofenceCommand extends AbstractGoogleServiceCommand {
    private List<Geofence> geofencesToAdd;
    private PendingIntent pendingIntent;
    private Context context;
    private Activity activity;
    List<GeoNotification> geoNotifications;
    private static final String TAG = "AddGeofenceCommand";

    public AddGeofenceCommand(Context context, Activity activity, PendingIntent pendingIntent,
                              List<Geofence> geofencesToAdd, List<GeoNotification> geoNotifications) {
        super(context);
        this.context = context;
        this.geofencesToAdd = geofencesToAdd;
        this.pendingIntent = pendingIntent;
        this.activity = activity;
        this.geoNotifications = geoNotifications;
    }

    @Override
    public void ExecuteCustomCode() {
        logger.log(Log.DEBUG, "Adding new geofences...");
        if (geofencesToAdd != null && geofencesToAdd.size() > 0) try {

            GeoFencingObserverService.allGeoFence = geofencesToAdd;
            ObserverWorker.allGeoFence = geofencesToAdd;

            GeofencePlugin.isGeoFenceAdded = true;

            GeoFencingObserverService.stopService(context);
            GeoFencingObserverService.startService(context, false);

            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();


            Gson gson = new Gson();
            String json = gson.toJson(geoNotifications);

            Log.d(TAG, "ExecuteCustomCode: "+json);
            Prefs.with(context).save("key",json);


            OneTimeWorkRequest uploadWorkRequest = new OneTimeWorkRequest.Builder(ObserverWorker.class)
                    .setBackoffCriteria(
                            BackoffPolicy.LINEAR,
                            1,TimeUnit.SECONDS)

                    .addTag("geofenceWorker")
                    .setConstraints(constraints)
                    .build();

//            WorkManager.getInstance().beginUniqueWork("uploadWorkRequest", ExistingWorkPolicy.KEEP, uploadWorkRequest).enqueue();
            WorkManager.getInstance().enqueue(uploadWorkRequest);

//
//
//            PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(
//                    ObserverWorker.class, 15, TimeUnit.MINUTES)
//
//                    .setConstraints(constraints)
//                    .build();
//            WorkManager.getInstance().enqueue(periodicWorkRequest);
//
//
            logger.log(Log.DEBUG, "Geofences successfully added");
            CommandExecuted();

//            LocationServices.GeofencingApi
//                .addGeofences(mGoogleApiClient, geofencesToAdd, pendingIntent)
//                .setResultCallback(new ResultCallback<Status>() {
//                    @Override
//                    public void onResult(Status status) {
//                        if (status.isSuccess()) {
//                            logger.log(Log.DEBUG, "Geofences successfully added");
//                            CommandExecuted();
//                        } else try {
//                            Map<Integer, String> errorCodeMap = new HashMap<Integer, String>();
//                            errorCodeMap.put(GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE, GeofencePlugin.ERROR_GEOFENCE_NOT_AVAILABLE);
//                            errorCodeMap.put(GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES, GeofencePlugin.ERROR_GEOFENCE_LIMIT_EXCEEDED);
//
//                            Integer statusCode = status.getStatusCode();
//                            String message = "Adding geofences failed - SystemCode: " + statusCode;
//                            JSONObject error = new JSONObject();
//                            error.put("message", message);
//
//                            if (statusCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
//                                error.put("code", GeofencePlugin.ERROR_GEOFENCE_NOT_AVAILABLE);
//                            } else if (statusCode == GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES) {
//                                error.put("code", GeofencePlugin.ERROR_GEOFENCE_LIMIT_EXCEEDED);
//                            } else {
//                                error.put("code", GeofencePlugin.ERROR_UNKNOWN);
//                            }
//
//                            logger.log(Log.ERROR, message);
//                            CommandExecuted(error);
//                        } catch (JSONException exception) {
//                            CommandExecuted(exception);
//                        }
//                    }
//                });
        } catch (Exception exception) {
            logger.log(LOG.ERROR, "Exception while adding geofences");
            exception.printStackTrace();
            CommandExecuted(exception);
        }
    }


}
