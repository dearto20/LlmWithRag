package com.example.llmwithrag.datasource.connectivity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;
import android.widget.Toast;

import com.example.llmwithrag.datasource.IDataSource;

import java.util.List;

public class ConnectivityTracker implements IDataSource {
    private static final String TAG = ConnectivityTracker.class.getSimpleName();
    private final Context mContext;
    private final ConnectivityRepository mRepository;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private ConnectivityManager mConnectivityManager;

    public ConnectivityTracker(Context context) {
        mContext = context;
        mRepository = new ConnectivityRepository(context);
        mConnectivityManager = (ConnectivityManager)
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public void startMonitoring() {
        registerNetworkCallback();
    }

    @Override
    public void stopMonitoring() {
        unregisterNetworkCallback();
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
                Log.i(TAG, "connected to " +
                        ((isEnterpriseNetwork) ? "an enterprise" : "a non-enterprise") + " network");
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                boolean isEnterpriseNetwork = isEnterpriseNetwork(network);
                mRepository.insertData(new ConnectivityData(false,
                        isEnterpriseNetwork(network), System.currentTimeMillis()));
                Log.i(TAG, "disconnected from " +
                        ((isEnterpriseNetwork) ? "an enterprise" : "a non-enterprise") + " network");
            }
        };
        mConnectivityManager.registerNetworkCallback(request, mNetworkCallback);
    }

    public void unregisterNetworkCallback() {
        if (mNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
            mNetworkCallback = null;
        }
    }
}
