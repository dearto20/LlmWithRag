package com.example.llmwithrag.knowledge.location;

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
    private final LocationTracker mLocationTracker;

    public PersistentLocationManager(LocationTracker locationTracker) {
        mLocationTracker = locationTracker;
    }

    private void initialize() {
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

        return frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());
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

        return frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());
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

        return frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAll() {
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
