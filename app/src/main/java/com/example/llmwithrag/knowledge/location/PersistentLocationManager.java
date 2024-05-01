package com.example.llmwithrag.knowledge.location;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.llmwithrag.datasource.location.LocationData;
import com.example.llmwithrag.datasource.location.LocationTracker;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PersistentLocationManager implements IKnowledgeComponent {
    private static final String TAG = PersistentLocationManager.class.getSimpleName();
    private static final String NAME_SHARED_PREFS = "persistent_location";
    private static final String KEY_LOCATION_0_COORDINATES = "location_0_coordinates";
    private static final String KEY_LOCATION_0_COUNT = "location_0_count";
    private static final String KEY_LOCATION_1_COORDINATES = "location_1_coordinates";
    private static final String KEY_LOCATION_1_COUNT = "location_1_count";
    private static final String KEY_LOCATION_2_COORDINATES = "location_2_coordinates";
    private static final String KEY_LOCATION_2_COUNT = "location_2_count";
    private final LocationTracker mLocationTracker;
    private final Context mContext;

    public PersistentLocationManager(Context context, LocationTracker locationTracker) {
        mLocationTracker = locationTracker;
        mContext = context;
    }

    private void initialize() {
    }

    private void updateCandidateList(Map<String, Integer> frequencyMap, String locationKey,
                                     String countKey) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                NAME_SHARED_PREFS, Context.MODE_PRIVATE);
        String oldKey = sharedPreferences.getString(locationKey, null);
        int oldValue = sharedPreferences.getInt(countKey, 0);
        Integer newValue = frequencyMap.get(oldKey);
        if (oldKey != null && oldValue > 0) {
            if (!frequencyMap.containsKey(oldKey) || (newValue == null || newValue < oldValue)) {
                frequencyMap.put(oldKey, oldValue);
                Log.i(TAG, "add last key " + oldKey + " with value " + oldValue);
            }
        }
    }

    private void updateLastResult(Map<String, Integer> frequencyMap, String locationKey,
                                  String countKey, List<String> result) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                NAME_SHARED_PREFS, Context.MODE_PRIVATE);
        if (!result.isEmpty()) {
            String key = result.get(0);
            Integer value = frequencyMap.get(key);
            if (value != null) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(locationKey, key);
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

    public List<String> getMostFrequentlyVisitedPlacesDuringTheDay(int topN) {
        List<LocationData> locations = mLocationTracker.getAllData();
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (LocationData location : locations) {
            String when = timeOf(location.timestamp);
            Log.i(TAG, "location 0: " + location.latitude + ", " + location.longitude + " at " + when);
            if (!when.contains("day")) continue;

            String key = location.latitude + "," + location.longitude;
            frequencyMap.put(key, frequencyMap.getOrDefault(key, 0) + 1);
        }

        // Add last top value to the candidate list.
        updateCandidateList(frequencyMap, KEY_LOCATION_0_COORDINATES, KEY_LOCATION_0_COUNT);

        List<String> result = frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());

        // Update last top value
        updateLastResult(frequencyMap, KEY_LOCATION_0_COORDINATES, KEY_LOCATION_0_COUNT, result);
        Log.i(TAG, "get most frequently visited places during the day : " + result);
        return result;
    }

    public List<String> getMostFrequentlyVisitedPlacesDuringTheNight(int topN) {
        List<LocationData> locations = mLocationTracker.getAllData();
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (LocationData location : locations) {
            String when = timeOf(location.timestamp);
            Log.i(TAG, "location 1: " + location.latitude + ", " + location.longitude + " at " + when);
            if (!when.contains("night")) continue;

            String key = location.latitude + "," + location.longitude;
            frequencyMap.put(key, frequencyMap.getOrDefault(key, 0) + 1);
        }

        // Add last top value to the candidate list.
        updateCandidateList(frequencyMap, KEY_LOCATION_1_COORDINATES, KEY_LOCATION_1_COUNT);

        List<String> result = frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());

        // Update last top value
        updateLastResult(frequencyMap, KEY_LOCATION_1_COORDINATES, KEY_LOCATION_1_COUNT, result);
        Log.i(TAG, "get most frequently visited places during the night : " + result);
        return result;
    }

    public List<String> getMostFrequentlyVisitedPlacesDuringTheWeekend(int topN) {
        List<LocationData> locations = mLocationTracker.getAllData();
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (LocationData location : locations) {
            Log.i(TAG, "location 2: " + location.latitude + ", " + location.longitude);
            if (!duringTheWeekend(location.timestamp)) continue;

            String key = location.latitude + "," + location.longitude;
            frequencyMap.put(key, frequencyMap.getOrDefault(key, 0) + 1);
        }

        // Add last top value to the candidate list.
        updateCandidateList(frequencyMap, KEY_LOCATION_2_COORDINATES, KEY_LOCATION_2_COUNT);

        List<String> result = frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());

        // Update last top value
        updateLastResult(frequencyMap, KEY_LOCATION_2_COORDINATES, KEY_LOCATION_2_COUNT, result);
        Log.i(TAG, "get most frequently visited places during weekend : " + result);
        return result;
    }

    @Override
    public void deleteAll() {
        deleteLastResult();
        mLocationTracker.deleteAllData();
    }

    private String timeOf(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        if (8 <= hour && hour < 20) {
            return "day";
        } else {
            return "night";
        }
    }

    private boolean duringTheWeekend(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY;
    }

    @Override
    public void startMonitoring() {
        initialize();
        mLocationTracker.startMonitoring();
    }

    @Override
    public void stopMonitoring() {
        mLocationTracker.stopMonitoring();
    }
}
