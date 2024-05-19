package com.example.llmwithrag.datasource.connectivity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.example.llmwithrag.datasource.IDataSourceComponent;

import java.util.List;

public class ConnectivityTracker implements IDataSourceComponent {
    private static final String TAG = ConnectivityTracker.class.getSimpleName();
    private static final boolean DEBUG = true; // yong4531
    private final ConnectivityManager mConnectivityManager;
    private final ConnectivityRepository mRepository;
    private final Context mContext;
    private final Handler mHandler;
    private final WifiManager mWifiManager;
    private ConnectivityManager.NetworkCallback mNetworkCallback;

    public ConnectivityTracker(Context context, Looper looper) {
        mContext = context;
        mHandler = new Handler(looper);
        mRepository = new ConnectivityRepository(context);
        mConnectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager)
                context.getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public void startMonitoring() {
        registerNetworkCallback();
    }

    @Override
    public void stopMonitoring() {
        unregisterNetworkCallback();
        mHandler.removeCallbacksAndMessages(null);
    }

    public List<ConnectivityData> getAllData() {
        return mRepository.getAllData();
    }

    public void deleteAllData() {
        mRepository.deleteAllData();
    }

    private boolean isEnterpriseNetwork(Network network) {
        boolean hasEnterpriseCapability = hasEnterpriseCapability(network);
        boolean hasEnterpriseWifiConfig = hasEnterpriseWifiConfig(mContext);
        int result = (hasEnterpriseCapability ? 1 : 0) + (hasEnterpriseWifiConfig ? 2 : 0);
        if (DEBUG) {
            Log.i(TAG, "isEnterpriseNetwork : " + result);
            Toast.makeText(mContext, "isEnterpriseNetwork : " + result, Toast.LENGTH_SHORT).show();
        }
        return (result > 0);
    }

    private boolean hasEnterpriseCapability(Network network) {
        boolean result = false;
        NetworkCapabilities capabilities = mConnectivityManager.getNetworkCapabilities(network);
        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            result = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean hasEnterpriseWifiConfig(Context context) {
        try {
            List<WifiConfiguration> configuredNetworks = (List<WifiConfiguration>)
                    mWifiManager.getClass().getMethod("getPrivilegedConfiguredNetworks")
                            .invoke(mWifiManager);
            if (configuredNetworks != null) {
                for (WifiConfiguration config : configuredNetworks) {
                    if (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP) ||
                            config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X)) {
                        return true;
                    }
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return false;
    }

    public void registerNetworkCallback() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                boolean isEnterpriseNetwork = isEnterpriseNetwork(network);
                mRepository.insertData(new ConnectivityData(true,
                        isEnterpriseNetwork, System.currentTimeMillis()));
                if (DEBUG) Log.i(TAG, "connected to " +
                        ((isEnterpriseNetwork) ? "an enterprise" : "a non-enterprise") + " network");
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                boolean isEnterpriseNetwork = isEnterpriseNetwork(network);
                mRepository.insertData(new ConnectivityData(false,
                        isEnterpriseNetwork, System.currentTimeMillis()));
                if (DEBUG) Log.i(TAG, "disconnected from " +
                        ((isEnterpriseNetwork) ? "an enterprise" : "a non-enterprise") + " network");
            }
        };
        mConnectivityManager.registerNetworkCallback(request, mNetworkCallback, mHandler);
    }

    public void unregisterNetworkCallback() {
        if (mNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
            mNetworkCallback = null;
        }
    }
}
