package com.example.llmwithrag.knowledge.location;

import static com.example.llmwithrag.Utils.getReadableAddressFromCoordinates;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_LOCATION_DURING_THE_DAY;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_LOCATION_DURING_THE_NIGHT;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_LOCATION_DURING_THE_WEEKEND;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_LOCATION;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.example.llmwithrag.MonitoringService;
import com.example.llmwithrag.datasource.location.LocationData;
import com.example.llmwithrag.datasource.location.LocationTracker;
import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeManager;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;
import com.example.llmwithrag.llm.EmbeddingManager;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final Context mContext;
    private final KnowledgeManager mKnowledgeManager;
    private final EmbeddingManager mEmbeddingManager;
    private final PersistentLocationRepository mRepository;
    private final LocationTracker mLocationTracker;

    public PersistentLocationManager(Context context,
                                     KnowledgeManager knowledgeManager,
                                     EmbeddingManager embeddingManager,
                                     PersistentLocationRepository persistentLocationRepository,
                                     LocationTracker locationTracker) {
        mContext = context;
        mKnowledgeManager = knowledgeManager;
        mEmbeddingManager = embeddingManager;
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
        mKnowledgeManager.removeEntity(mEmbeddingManager, ENTITY_TYPE_LOCATION);
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

    @Override
    public void update(int type, MonitoringService.EmbeddingResultListener listener) {
        String latest = getLatestLocation(type);
        if (TextUtils.isEmpty(latest)) {
            listener.onSuccess();
            return;
        }

        Entity locationEntity = new Entity(UUID.randomUUID().toString(),
                ENTITY_TYPE_LOCATION, getName(type));
        locationEntity.addAttribute("coordinate", latest);
        locationEntity.addAttribute("location", getReadableAddressFromCoordinates(mContext, latest));
        mKnowledgeManager.addEntity(mEmbeddingManager, locationEntity, listener);
    }

    private String getLatestLocation(int type) {
        List<String> results = null;
        switch (type) {
            case 0: {
                results = getMostFrequentlyVisitedPlacesDuringTheDay(1);
                break;
            }
            case 1: {
                results = getMostFrequentlyVisitedPlacesDuringTheNight(1);
                break;
            }
            case 2: {
                results = getMostFrequentlyVisitedPlacesDuringTheWeekend(1);
                break;
            }
            default: {
                break;
            }
        }
        return (results != null && !results.isEmpty()) ? results.get(0) : "";
    }

    private String getName(int type) {
        switch (type) {
            case 0:
                return ENTITY_NAME_LOCATION_DURING_THE_DAY;
            case 1:
                return ENTITY_NAME_LOCATION_DURING_THE_NIGHT;
            case 2:
                return ENTITY_NAME_LOCATION_DURING_THE_WEEKEND;
            default:
                return "";
        }
    }
}
