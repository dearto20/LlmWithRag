package com.example.llmwithrag.knowledge.status;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.llmwithrag.datasource.movement.MovementData;
import com.example.llmwithrag.datasource.movement.MovementTracker;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class StationaryDurationManager implements IKnowledgeComponent {
    private static final String TAG = StationaryDurationManager.class.getSimpleName();
    private static final String NAME_SHARED_PREFS = "stationary_duration";
    private static final String KEY_STATIONARY_TIME = "stationary_time";
    private static final String KEY_STATIONARY_DURATION = "stationary_duration";
    private static final float GRAVITY = 9.8f;
    private static final float THRESHOLD = 0.2f;
    private static final long MIN_DURATION = 3600000;
    private final MovementTracker mMovementTracker;
    private final Context mContext;
    private boolean mIsStationary;
    private long mStartTime;
    private long mCheckTime;

    public StationaryDurationManager(Context context, MovementTracker movementTracker) {
        mMovementTracker = movementTracker;
        mContext = context;
    }

    private void initialize() {
        mIsStationary = false;
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
                Log.i(TAG, "stationary status change to " + isNewStationary);
                if (isNewStationary) {
                    mStartTime = timestamp;
                    mIsStationary = true;
                } else {
                    long duration = timestamp - mStartTime;
                    Log.i(TAG, "duration : " + duration + ", min duration : " + MIN_DURATION);
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
        updateCandidateList(durationMap, KEY_STATIONARY_TIME, KEY_STATIONARY_DURATION);

        List<String> result = durationMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());

        // Update last top value
        updateLastResult(durationMap, KEY_STATIONARY_TIME, KEY_STATIONARY_DURATION, result);
        Log.i(TAG, "get most frequent stationary time : " + result);
        return result;
    }

    @Override
    public void deleteAll() {
        deleteLastResult();
        mMovementTracker.deleteAllData();
    }

    private String periodOf(long startTime, long endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(startTime)) + " to " + sdf.format(new Date(endTime));
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
}
