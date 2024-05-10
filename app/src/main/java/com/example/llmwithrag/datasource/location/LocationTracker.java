package com.example.llmwithrag.datasource.location;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.llmwithrag.datasource.IDataSourceComponent;

import java.util.List;

public class LocationTracker implements IDataSourceComponent {
    private static final String TAG = LocationTracker.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final long INTERVAL = 1000 * 10; // TODO: 1000 * 60 * 10;
    private final Context mContext;
    private final Handler mHandler;
    private final LocationManager mLocationManager;
    private final LocationRepository mRepository;

    public LocationTracker(Context context, Looper looper) {
        mContext = context;
        mHandler = new Handler(looper);
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mRepository = new LocationRepository(context);
    }

    @Override
    public void startMonitoring() {
        if (ContextCompat.checkSelfPermission(mContext, ACCESS_COARSE_LOCATION)
                == PERMISSION_GRANTED) {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, INTERVAL, 0, mLocationListener,
                    mHandler.getLooper());
        } else {
            Log.e(TAG, "permission ACCESS_COARSE_LOCATION not granted");
        }

        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                == PERMISSION_GRANTED) {
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, INTERVAL, 0, mLocationListener,
                    mHandler.getLooper());
        } else {
            Log.e(TAG, "permission ACCESS_FINE_LOCATION not granted");
        }
    }

    @Override
    public void stopMonitoring() {
        if (ContextCompat.checkSelfPermission(mContext, ACCESS_COARSE_LOCATION)
                == PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(mContext, ACCESS_FINE_LOCATION)
                        == PERMISSION_GRANTED) {
            mLocationManager.removeUpdates(mLocationListener);
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    public List<LocationData> getAllData() {
        return mRepository.getAllData();
    }

    public void deleteAllData() {
        mRepository.deleteAllData();
    }

    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            double scale = Math.pow(10, 4);
            double latitude = Math.floor(location.getLatitude() * scale) / scale;
            double longitude = Math.floor(location.getLongitude() * scale) / scale;
            if (DEBUG) Log.d(TAG, "location update : (" + latitude + ", " + longitude + ")");
            mRepository.insertData(new LocationData(latitude, longitude,
                    System.currentTimeMillis()));
        }
    };
}
