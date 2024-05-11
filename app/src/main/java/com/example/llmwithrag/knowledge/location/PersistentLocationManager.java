package com.example.llmwithrag.knowledge.location;

import android.content.Context;
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
    private static final boolean DEBUG = false;
    private static final String KEY_LOCATION_0_COORDINATES = "location_0_coordinates";
    private static final String KEY_LOCATION_0_COUNT = "location_0_count";
    private static final String KEY_LOCATION_1_COORDINATES = "location_1_coordinates";
    private static final String KEY_LOCATION_1_COUNT = "location_1_count";
    private static final String KEY_LOCATION_2_COORDINATES = "location_2_coordinates";
    private static final String KEY_LOCATION_2_COUNT = "location_2_count";
    private final PersistentLocationRepository mRepository;
    private final LocationTracker mLocationTracker;

    public PersistentLocationManager(Context context,
                                     PersistentLocationRepository persistentLocationRepository,
                                     LocationTracker locationTracker) {
        mRepository = persistentLocationRepository;
        mLocationTracker = locationTracker;
    }

    private void initialize() {
    }

    public List<String> getMostFrequentlyVisitedPlacesDuringTheDay(int topN) {
        List<LocationData> locations = mLocationTracker.getAllData();
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (LocationData location : locations) {
            String when = timeOf(location.timestamp);
            if (DEBUG) Log.d(TAG, "location 0: " + location.latitude + ", " +
                    location.longitude + " at " + when);
            if (!when.contains("day")) continue;

            String key = location.latitude + "," + location.longitude;
            frequencyMap.put(key, frequencyMap.getOrDefault(key, 0) + 1);
        }

        // Add last top value to the candidate list.
        mRepository.updateCandidateList(frequencyMap, KEY_LOCATION_0_COORDINATES, KEY_LOCATION_0_COUNT);

        List<String> result = frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());

        // Update last top value
        mRepository.updateLastResult(frequencyMap, KEY_LOCATION_0_COORDINATES, KEY_LOCATION_0_COUNT, result);
        Log.i(TAG, "get most frequently visited places during the day : " + result);
        return result;
    }

    public List<String> getMostFrequentlyVisitedPlacesDuringTheNight(int topN) {
        List<LocationData> locations = mLocationTracker.getAllData();
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (LocationData location : locations) {
            String when = timeOf(location.timestamp);
            if (DEBUG) Log.d(TAG, "location 1: " + location.latitude + ", " +
                    location.longitude + " at " + when);
            if (!when.contains("night")) continue;

            String key = location.latitude + "," + location.longitude;
            frequencyMap.put(key, frequencyMap.getOrDefault(key, 0) + 1);
        }

        // Add last top value to the candidate list.
        mRepository.updateCandidateList(frequencyMap, KEY_LOCATION_1_COORDINATES, KEY_LOCATION_1_COUNT);

        List<String> result = frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());

        // Update last top value
        mRepository.updateLastResult(frequencyMap, KEY_LOCATION_1_COORDINATES, KEY_LOCATION_1_COUNT, result);
        Log.i(TAG, "get most frequently visited places during the night : " + result);
        return result;
    }

    public List<String> getMostFrequentlyVisitedPlacesDuringTheWeekend(int topN) {
        List<LocationData> locations = mLocationTracker.getAllData();
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (LocationData location : locations) {
            if (DEBUG) Log.d(TAG, "location 2: " + location.latitude + ", " + location.longitude);
            if (!duringTheWeekend(location.timestamp)) continue;

            String key = location.latitude + "," + location.longitude;
            frequencyMap.put(key, frequencyMap.getOrDefault(key, 0) + 1);
        }

        // Add last top value to the candidate list.
        mRepository.updateCandidateList(frequencyMap, KEY_LOCATION_2_COORDINATES, KEY_LOCATION_2_COUNT);

        List<String> result = frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());

        // Update last top value
        mRepository.updateLastResult(frequencyMap, KEY_LOCATION_2_COORDINATES, KEY_LOCATION_2_COUNT, result);
        Log.i(TAG, "get most frequently visited places during weekend : " + result);
        return result;
    }

    @Override
    public void deleteAll() {
        mRepository.deleteLastResult();
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
