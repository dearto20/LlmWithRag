package com.example.llmwithrag.knowledge.status;

import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_PERIOD_STATIONARY;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_PERIOD;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.example.llmwithrag.MonitoringService;
import com.example.llmwithrag.datasource.movement.MovementData;
import com.example.llmwithrag.datasource.movement.MovementTracker;
import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeManager;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;
import com.example.llmwithrag.llm.EmbeddingManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class StationaryTimeManager implements IKnowledgeComponent {
    private static final String TAG = StationaryTimeManager.class.getSimpleName();
    private final boolean DEBUG = true;
    private static final String KEY_STATIONARY_TIME = "stationary_time";
    private static final String KEY_STATIONARY_DURATION = "stationary_duration";
    private static final float GRAVITY = 9.8f;
    private static final float THRESHOLD = 0.2f;
    private static final long MIN_DURATION = 600000L;
    private final Context mContext;
    private final KnowledgeManager mKnowledgeManager;
    private final EmbeddingManager mEmbeddingManager;
    private final StationaryTimeRepository mRepository;
    private final MovementTracker mMovementTracker;
    private boolean mIsStationary;
    private long mStartTime;
    private long mCheckTime;

    public StationaryTimeManager(Context context,
                                 KnowledgeManager knowledgeManager,
                                 EmbeddingManager embeddingManager,
                                 StationaryTimeRepository stationaryTimeRepository,
                                 MovementTracker movementTracker) {
        mContext = context;
        mKnowledgeManager = knowledgeManager;
        mEmbeddingManager = embeddingManager;
        mRepository = stationaryTimeRepository;
        mMovementTracker = movementTracker;
    }

    private void initialize() {
        mIsStationary = false;
        mStartTime = System.currentTimeMillis();
        mCheckTime = 0;
    }

    public List<String> getMostFrequentStationaryTimes(int topN) {
        List<MovementData> movements = mMovementTracker.getAllData();
        Map<String, Long> durationMap = new HashMap<>();
        long currentTime = System.currentTimeMillis();

        for (MovementData movement : movements) {
            if (movement.timestamp < mCheckTime) continue;
            double magnitude = Math.sqrt(movement.x * movement.x + movement.y * movement.y +
                    movement.z * movement.z);
            boolean isNewStationary = Math.abs(magnitude - GRAVITY) < THRESHOLD;
            long timestamp = movement.timestamp;

            if (mIsStationary != isNewStationary) {
                if (DEBUG) Log.d(TAG, "stationary status change to " + isNewStationary);
                if (isNewStationary) {
                    mStartTime = timestamp;
                    mIsStationary = true;
                } else {
                    long duration = timestamp - mStartTime;
                    if (DEBUG) Log.d(TAG, "duration : " + duration + ", min duration : " +
                            MIN_DURATION);
                    if (duration >= MIN_DURATION) {
                        durationMap.put(periodOf(mStartTime, timestamp), duration);
                        mStartTime = timestamp;
                        Log.i(TAG, "period of duration " + duration + " is added");
                    }
                    mIsStationary = false;
                }
            }
        }

        mCheckTime = currentTime;

        // Add last top value to the candidate list.
        mRepository.updateCandidateList(durationMap, KEY_STATIONARY_TIME, KEY_STATIONARY_DURATION);

        List<String> result = durationMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());

        // Update last top value
        mRepository.updateLastResult(durationMap, KEY_STATIONARY_TIME, KEY_STATIONARY_DURATION, result);
        Log.i(TAG, "get most frequent stationary time : " + result);
        return result;
    }

    @Override
    public void deleteAll() {
        mRepository.deleteLastResult();
        mMovementTracker.deleteAllData();
    }

    private String periodOf(long startTime, long endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return "from " + sdf.format(new Date(startTime)) + " to " + sdf.format(new Date(endTime));
    }

    @Override
    public void startMonitoring() {
        initialize();
        mMovementTracker.startMonitoring();
    }

    @Override
    public void stopMonitoring() {
        mMovementTracker.stopMonitoring();
    }

    @Override
    public void update(int type, MonitoringService.EmbeddingResultListener listener) {
        String latest = getLatestPeriod(type);
        if (TextUtils.isEmpty(latest)) {
            listener.onSuccess();
            return;
        }

        Entity periodEntity = new Entity(UUID.randomUUID().toString(),
                ENTITY_TYPE_PERIOD, getName(type));
        periodEntity.addAttribute("period", latest);
        mKnowledgeManager.addEntity(mEmbeddingManager, periodEntity, listener);
    }

    private String getLatestPeriod(int type) {
        List<String> results = null;
        switch (type) {
            case 0: {
                results = getMostFrequentStationaryTimes(1);
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
                return ENTITY_NAME_PERIOD_STATIONARY;
            default:
                return "";
        }
    }
}
