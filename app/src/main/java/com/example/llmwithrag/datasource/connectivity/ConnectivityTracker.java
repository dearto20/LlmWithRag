package com.example.llmwithrag.datasource.connectivity;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.llmwithrag.datasource.IDataSource;

import java.util.List;

public class ConnectivityTracker implements IDataSource {
    private static final String TAG = ConnectivityTracker.class.getSimpleName();
    private final Context mContext;
    private final ConnectivityRepository mRepository;
    private final WifiManager mWifiManager;
    private BroadcastReceiver mWifiScanReceiver;

    public ConnectivityTracker(Context context) {
        mContext = context;
        mRepository = new ConnectivityRepository(context);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mWifiScanReceiver = null;
    }

    @Override
    public void startMonitoring() {
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                == PERMISSION_GRANTED) {
            registerScanReceiver();
            mWifiManager.startScan();
        } else {
            Log.e(TAG, "permission ACCESS_FINE_LOCATION not granted");
        }
    }

    @Override
    public void stopMonitoring() {
        if (mWifiScanReceiver != null) {
            mContext.unregisterReceiver(mWifiScanReceiver);
            mWifiScanReceiver = null;
        }
    }

    @SuppressLint("MissingPermission")
    private void registerScanReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mWifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) {
                    WifiInfo connectedWifiInfo = mWifiManager.getConnectionInfo();
                    List<ScanResult> results = mWifiManager.getScanResults();
                    for (ScanResult result : results) {
                        if (result.BSSID.equals(connectedWifiInfo.getBSSID())) {
                            Log.i(TAG, "wifi update : (" +
                                    result.SSID + ", " + result.BSSID + ")");
                            mRepository.insertData(new ConnectivityData(result.SSID, result.BSSID,
                                    result.capabilities, System.currentTimeMillis()));
                        }
                    }
                } else {
                    Log.e(TAG, "failed to update results");
                }
            }
        };
        mContext.registerReceiver(mWifiScanReceiver, intentFilter);
    }

    public List<ConnectivityData> getAllData() {
        return mRepository.getAllData();
    }

    public void deleteAllData() {
        mRepository.deleteAllData();
    }
}
