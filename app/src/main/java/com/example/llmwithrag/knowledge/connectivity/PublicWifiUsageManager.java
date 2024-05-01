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
    private static final String KEY_CONNECTION_TIMESTAMP = "connection_timestamp";
    private static final String KEY_CONNECTION_COUNT = "connection_count";

    private final ConnectivityTracker mConnectivityTracker;
    private final Context mContext;

    public PublicWifiUsageManager(Context context, ConnectivityTracker connectivityTracker) {
        mContext = context;
        mConnectivityTracker = connectivityTracker;
    }

    private void initialize() {
    }

    private String timeOf(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    private void updateCandidateList(Map<String, Integer> frequencyMap, String timestampKey,
                                     String countKey) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                NAME_SHARED_PREFS, Context.MODE_PRIVATE);
        String oldKey = sharedPreferences.getString(timestampKey, null);
        int oldValue = sharedPreferences.getInt(countKey, 0);
        Integer newValue = frequencyMap.get(oldKey);
        if (oldKey != null && oldValue > 0) {
            if (!frequencyMap.containsKey(oldKey) || (newValue == null || newValue < oldValue)) {
                frequencyMap.put(oldKey, oldValue);
                Log.i(TAG, "add last key " + oldKey + " with value " + oldValue);
            }
        }
    }

    private void updateLastResult(Map<String, Integer> frequencyMap, String timestampKey,
                                  String countKey, List<String> result) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                NAME_SHARED_PREFS, Context.MODE_PRIVATE);
        if (!result.isEmpty()) {
            String key = result.get(0);
            Integer value = frequencyMap.get(key);
            if (value != null) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(timestampKey, key);
                editor.putInt(countKey, value);
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
        List<ConnectivityData> events = mConnectivityTracker.getAllData();
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (ConnectivityData event : events) {
            if (!event.capabilities.contains("EAP")) {
                String key = timeOf(event.timestamp);
                frequencyMap.put(key, frequencyMap.getOrDefault(key, 0) + 1);
            }
        }

        // Add last top value to the candidate list.
        updateCandidateList(frequencyMap, KEY_CONNECTION_TIMESTAMP, KEY_CONNECTION_COUNT);

        List<String> result = frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());

        // Update last top value
        updateLastResult(frequencyMap, KEY_CONNECTION_TIMESTAMP, KEY_CONNECTION_COUNT, result);
        Log.i(TAG, "get most frequent public wifi connection time : " + result);
        return result;
    }

    @Override
    public void deleteAll() {
        deleteLastResult();
        mConnectivityTracker.deleteAllData();
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
