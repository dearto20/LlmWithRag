package com.example.llmwithrag.datasource.location;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.llmwithrag.datasource.IDataSource;

import java.util.List;

public class LocationTracker implements IDataSource {
    private static final String TAG = LocationTracker.class.getSimpleName();
    private static final long INTERVAL = 1000 * 10;//60 * 10;
    private final Context mContext;
    private final LocationManager mLocationManager;
    private final LocationDataRepository mRepository;

    public LocationTracker(Context context) {
        mContext = context;
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mRepository = new LocationDataRepository(context);
    }

    @Override
    public void startMonitoring() {
        if (ContextCompat.checkSelfPermission(mContext, ACCESS_COARSE_LOCATION)
                == PERMISSION_GRANTED) {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, INTERVAL, 0, mLocationListener);
        } else {
            Log.e(TAG, "permission ACCESS_COARSE_LOCATION not granted");
        }

        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                == PERMISSION_GRANTED) {
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, INTERVAL, 0, mLocationListener);
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
            Log.i(TAG, "location update : (" +
                    location.getLatitude() + ", " + location.getLongitude() + ")");
            mRepository.insertData(new LocationData(location.getLatitude(),
                    location.getLongitude(), System.currentTimeMillis()));
        }
    };
}
