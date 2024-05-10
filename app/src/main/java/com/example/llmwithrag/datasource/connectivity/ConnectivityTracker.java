package com.example.llmwithrag.datasource.connectivity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.llmwithrag.datasource.IDataSourceComponent;

import java.util.List;

public class ConnectivityTracker implements IDataSourceComponent {
    private static final String TAG = ConnectivityTracker.class.getSimpleName();
    private static final boolean DEBUG = false;
    private final ConnectivityManager mConnectivityManager;
    private final ConnectivityRepository mRepository;
    private final Handler mHandler;
    private ConnectivityManager.NetworkCallback mNetworkCallback;

    public ConnectivityTracker(Context context, Looper looper) {
        mHandler = new Handler(looper);
        mRepository = new ConnectivityRepository(context);
        mConnectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
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
        boolean result = false;
        NetworkCapabilities capabilities = mConnectivityManager.getNetworkCapabilities(network);
        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            result = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE);
        }
        return result;
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
                        isEnterpriseNetwork(network), System.currentTimeMillis()));
                if (DEBUG) Log.i(TAG, "connected to " +
                        ((isEnterpriseNetwork) ? "an enterprise" : "a non-enterprise") + " network");
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                boolean isEnterpriseNetwork = isEnterpriseNetwork(network);
                mRepository.insertData(new ConnectivityData(false,
                        isEnterpriseNetwork(network), System.currentTimeMillis()));
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
