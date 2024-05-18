package com.example.llmwithrag.knowledge.connectivity;

import android.content.Context;
import android.util.Log;

import com.example.llmwithrag.datasource.connectivity.ConnectivityData;
import com.example.llmwithrag.datasource.connectivity.ConnectivityTracker;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class EnterpriseWifiUsageManager implements IKnowledgeComponent {
    private static final String TAG = EnterpriseWifiUsageManager.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final String KEY_CONNECTION_TIME = "connection_time";
    private static final String KEY_CONNECTION_DURATION = "connection_duration";
    private static final long MIN_DURATION = 600000L;
    private final EnterpriseWifiUsageRepository mRepository;

    private final ConnectivityTracker mConnectivityTracker;
    private boolean mIsConnected;
    private long mStartTime;
    private long mCheckTime;

    public EnterpriseWifiUsageManager(Context context,
                                      EnterpriseWifiUsageRepository enterpriseWifiUsageRepository,
                                    ConnectivityTracker connectivityTracker) {
        mRepository = enterpriseWifiUsageRepository;
        mConnectivityTracker = connectivityTracker;
    }

    private void initialize() {
        mIsConnected = false;
        mStartTime = System.currentTimeMillis();
        mCheckTime = 0;
    }

    public List<String> getMostFrequentEnterpriseWifiConnectionTimes(int topN) {
        List<ConnectivityData> allData = mConnectivityTracker.getAllData();
        Map<String, Long> durationMap = new HashMap<>();
        long currentTime = System.currentTimeMillis();

        for (ConnectivityData data : allData) {
            if (data.timestamp < mCheckTime || !data.enterprise) continue;
            boolean isNewConnected = data.connected;
            long timestamp = data.timestamp;

            if (mIsConnected != isNewConnected) {
                if (DEBUG) Log.d(TAG, "connection status change to " + isNewConnected);
                if (isNewConnected) {
                    mStartTime = timestamp;
                    mIsConnected = true;
                } else {
                    long duration = timestamp - mStartTime;
                    if (DEBUG) Log.d(TAG, "duration : " + duration + ", min duration : " +
                            MIN_DURATION);
                    if (duration >= MIN_DURATION) {
                        durationMap.put(periodOf(mStartTime, timestamp), duration);
                        mStartTime = timestamp;
                        Log.i(TAG, "period of duration " + duration + " is added");
                    }
                    mIsConnected = false;
                }
            }
        }

        mCheckTime = currentTime;

        // Add last top value to the candidate list.
        mRepository.updateCandidateList(durationMap, KEY_CONNECTION_TIME, KEY_CONNECTION_DURATION);

        List<String> result = durationMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());

        // Update last top value
        mRepository.updateLastResult(durationMap, KEY_CONNECTION_TIME, KEY_CONNECTION_DURATION, result);
        Log.i(TAG, "get most frequent connection time : " + result);
        return result;
    }

    @Override
    public void deleteAll() {
        mRepository.deleteLastResult();
        mConnectivityTracker.deleteAllData();
    }

    private String periodOf(long startTime, long endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return "from " + sdf.format(new Date(startTime)) + " to " + sdf.format(new Date(endTime));
    }

    @Override
    public void startMonitoring() {
        initialize();
        mConnectivityTracker.startMonitoring();
    }

    @Override
    public void stopMonitoring() {
        mConnectivityTracker.stopMonitoring();
    }
}