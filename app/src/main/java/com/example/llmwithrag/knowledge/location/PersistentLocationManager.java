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
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PersistentLocationManager implements IKnowledgeComponent {
    private static final String TAG = PersistentLocationManager.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final int NUMBER_OF_TYPE = 3;
    private static final String[] KEY_LOCATION_COORDINATES =
            {"location_0_coordinates", "location_1_coordinates", "location_2_coordinates"};
    private static final String[] KEY_LOCATION_COUNT =
            {"location_0_count", "location_1_count", "location_2_count"};
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
        Predicate<LocationData> isDuringTheDay = location -> {
            String when = timeOf(location.timestamp);
            return when.contains("day");
        };
        return getMostFrequentlyVisitedPlaces(0, isDuringTheDay, topN);
    }

    public List<String> getMostFrequentlyVisitedPlacesDuringTheNight(int topN) {
        Predicate<LocationData> isDuringTheDay = location -> {
            String when = timeOf(location.timestamp);
            return when.contains("night");
        };
        return getMostFrequentlyVisitedPlaces(1, isDuringTheDay, topN);
    }

    public List<String> getMostFrequentlyVisitedPlacesDuringTheWeekend(int topN) {
        Predicate<LocationData> isDuringTheWeekend = location -> duringTheWeekend(location.timestamp);
        return getMostFrequentlyVisitedPlaces(2, isDuringTheWeekend, topN);
    }

    private List<String> getMostFrequentlyVisitedPlaces(int type, Predicate<LocationData> condition, int topN) {
        List<LocationData> locations = mLocationTracker.getAllData();
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (LocationData location : locations) {
            if (DEBUG) Log.d(TAG, "location " + type + " : " + location.latitude + ", " +
                    location.longitude + " at " + timeOf(location.timestamp));
            if (!condition.test(location)) continue;

            String key = location.latitude + "," + location.longitude;
            Integer value = frequencyMap.get(key);
            frequencyMap.put(key, (value != null ? value : 0) + 1);
        }

        // Add last top value to the candidate list.
        mRepository.updateCandidateList(frequencyMap,
                KEY_LOCATION_COORDINATES[type], KEY_LOCATION_COUNT[type]);

        List<String> result = frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());

        // Update last top value
        mRepository.updateLastResult(frequencyMap,
                KEY_LOCATION_COORDINATES[type], KEY_LOCATION_COUNT[type], result);
        Log.i(TAG, "get most frequently visited places for type " + type + " : " + result);
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
