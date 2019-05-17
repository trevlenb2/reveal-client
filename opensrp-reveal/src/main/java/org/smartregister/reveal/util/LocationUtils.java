package org.smartregister.reveal.util;

import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import io.ona.kujaku.interfaces.ILocationClient;
import io.ona.kujaku.listeners.BaseLocationListener;
import io.ona.kujaku.location.clients.GoogleLocationClient;
import io.ona.kujaku.utils.Constants;
import io.ona.kujaku.utils.LocationSettingsHelper;
import io.ona.kujaku.utils.LogUtil;

/**
 * Created by samuelgithengi on 3/20/19.
 */
public class LocationUtils {

    private static final String TAG = "LocationUtils";
    private ILocationClient locationClient;

    public LocationUtils(Context context) {
        locationClient = new GoogleLocationClient(context);
    }

    public void requestLocationUpdates(BaseLocationListener locationListener) {
        locationClient.requestLocationUpdates(locationListener);
    }

    public Location getLastLocation() {
        return locationClient.getLastLocation();
    }

    public void stopLocationClient() {
        locationClient.close();
        locationClient = null;
    }


    public void checkLocationSettingsAndStartLocationServices(Context context, BaseLocationListener locationListener) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;

            LocationSettingsHelper.checkLocationEnabled(activity, new ResultCallback<LocationSettingsResult>() {
                @Override
                public void onResult(LocationSettingsResult result) {
                    final Status status = result.getStatus();

                    switch (status.getStatusCode()) {
                        case LocationSettingsStatusCodes.SUCCESS:
                            Log.i(TAG, "All location settings are satisfied.");
                            requestLocationUpdates(locationListener);
                            break;
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to upgrade location settings");

                            try {
                                status.startResolutionForResult(activity, Constants.RequestCode.LOCATION_SETTINGS);
                            } catch (IntentSender.SendIntentException e) {
                                Log.i(TAG, "PendingIntent unable to execute request.");
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            Log.e(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog cannot be created.");
                            break;

                        default:
                            Log.e(TAG, "Unknown status code returned after checking location settings");
                            break;
                    }
                }
            });
        } else {
            LogUtil.e(TAG, "KujakuMapView is not started in an Activity and can therefore not start location services");
        }
    }


}
