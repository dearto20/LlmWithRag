package com.example.llmwithrag.knowledge.connectivity;

import android.content.Context;
import android.content.SharedPreferences;
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

public class PublicWifiUsageManager implements IKnowledgeComponent {
    private static final String TAG = PublicWifiUsageManager.class.getSimpleName();
    private static final String NAME_SHARED_PREFS = "public_wifi_usage";
    private static final String KEY_CONNECTION_TIME = "connection_time";
    private static final String KEY_CONNECTION_DURATION = "connection_duration";
    private static final long MIN_DURATION = 900000;
    ;
    private final ConnectivityTracker mConnectivityTracker;
    private final Context mContext;
    private boolean mIsConnected;
    private long mStartTime;
    private long mCheckTime;

    public PublicWifiUsageManager(Context context, ConnectivityTracker connectivityTracker) {
        mContext = context;
        mConnectivityTracker = connectivityTracker;
    }

    private void initialize() {
        mIsConnected = false;
        mStartTime = System.currentTimeMillis();
        mCheckTime = 0;
    }

    private void updateCandidateList(Map<String, Long> durationMap, String timeKey,
                                     String durationKey) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                NAME_SHARED_PREFS, Context.MODE_PRIVATE);
        String oldKey = sharedPreferences.getString(timeKey, null);
        long oldValue = sharedPreferences.getLong(durationKey, 0);
        Long newValue = durationMap.get(oldKey);
        if (oldKey != null && oldValue > 0) {
            if (!durationMap.containsKey(oldKey) || (newValue == null || newValue < oldValue)) {
                durationMap.put(oldKey, oldValue);
                Log.i(TAG, "add last key " + oldKey + " with value " + oldValue);
            }
        }
    }

    private void updateLastResult(Map<String, Long> durationMap, String timeKey,
                                  String durationKey, List<String> result) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                NAME_SHARED_PREFS, Context.MODE_PRIVATE);
        if (!result.isEmpty()) {
            String key = result.get(0);
            Long value = durationMap.get(key);
            if (value != null) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(timeKey, key);
                editor.putLong(durationKey, value);
                editor.apply();
                Log.i(TAG, "update last key " + key + " with value " + value);
            }
        }
    }

    private void deleteLastResult() {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                NAME_SHARED_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        Log.i(TAG, "remove last data");
    }

    public List<String> getMostFrequentPublicWifiConnectionTimes(int topN) {
        List<ConnectivityData> allData = mConnectivityTracker.getAllData();
        Map<String, Long> durationMap = new HashMap<>();
        long currentTime = System.currentTimeMillis();

        for (ConnectivityData data : allData) {
            if (data.timestamp < mCheckTime) continue;
            boolean isNewConnected = data.connected;
            long timestamp = data.timestamp;

            if (mIsConnected != isNewConnected) {
                Log.i(TAG, "connection status change to " + isNewConnected);
                if (isNewConnected) {
                    mStartTime = timestamp;
                    mIsConnected = true;
                } else {
                    long duration = timestamp - mStartTime;
                    Log.i(TAG, "duration : " + duration + ", min duration : " + MIN_DURATION);
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
        updateCandidateList(durationMap, KEY_CONNECTION_TIME, KEY_CONNECTION_DURATION);

        List<String> result = durationMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());

        // Update last top value
        updateLastResult(durationMap, KEY_CONNECTION_TIME, KEY_CONNECTION_DURATION, result);
        Log.i(TAG, "get most frequent connection time : " + result);
        return result;
    }

    @Override
    public void deleteAll() {
        deleteLastResult();
        mConnectivityTracker.deleteAllData();
    }

    private String periodOf(long startTime, long endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(startTime)) + " to " + sdf.format(new Date(endTime));
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
