package com.bx.carDVR.bylym.model.tools;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import com.bx.carDVR.DvrApplication;
import com.bx.carDVR.bylym.model.NotifyMessageManager;

/**
 * @author Altair
 * @date :2020.03.31 下午 06:01
 * @description:
 */
public class LocationManagerTool {
    private LocationManager mLocationManager;
    private Location mLocation;


    @SuppressLint("MissingPermission")
    public Location getLocation() {
        mLocationManager =
                (LocationManager) DvrApplication.getInstance().getSystemService(Context.LOCATION_SERVICE);
        if (mLocationManager == null) {
            return null;
        }
        mLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (mLocation == null) {
            mLocation = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0,
                mLocationListener);
        return mLocation;
    }

    private LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location == null) return;
            String strResult = "getAccuracy:" + location.getAccuracy() + "\r\n"
                    + "getAltitude:" + location.getAltitude() + "\r\n"
                    + "getBearing:" + location.getBearing() + "\r\n"
                    + "getElapsedRealtimeNanos:" + String.valueOf(location.getElapsedRealtimeNanos()) + "\r\n"
                    + "getLatitude:" + location.getLatitude() + "\r\n"
                    + "getLongitude:" + location.getLongitude() + "\r\n"
                    + "getProvider:" + location.getProvider() + "\r\n"
                    + "getSpeed:" + location.getSpeed() + "\r\n"
                    + "getTime:" + location.getTime() + "\r\n";
            Log.d("Show", strResult);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

    //停止定位
    public void stopLocation() {
        if (mLocationManager != null && mLocationListener != null) {
            mLocationManager.removeUpdates(mLocationListener);
        }
    }

    private void sendToActivity() {
        NotifyMessageManager.getInstance().gpsSpeedChange();
    }

}
