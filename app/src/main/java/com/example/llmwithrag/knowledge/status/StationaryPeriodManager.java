package com.example.llmwithrag.knowledge.status;

import android.util.Log;

import com.example.llmwithrag.datasource.movement.MovementData;
import com.example.llmwithrag.datasource.movement.MovementTracker;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class StationaryPeriodManager implements IKnowledgeComponent {
    private static final String TAG = StationaryPeriodManager.class.getSimpleName();
    private static final float GRAVITY = 9.8f;
    private static final float THRESHOLD = 0.1f;
    private static final long MIN_DURATION = 5000; // TODO: 3600000;
    private final MovementTracker mMovementTracker;
    private boolean mIsStationary;
    private long mStartTime;
    private long mCheckTime;

    public StationaryPeriodManager(MovementTracker movementTracker) {
        mMovementTracker = movementTracker;
    }

    private void initialize() {
        mIsStationary = false;
        mStartTime = System.currentTimeMillis();
        mCheckTime = 0;
    }

    private String timeOf(long startTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(startTime));
    }

    public List<String> getMostFrequentStationaryTimes(int topN) {
        List<MovementData> movements = mMovementTracker.getAllData();
        List<String> periods = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (MovementData movement : movements) {
            if (movement.timestamp < mCheckTime) continue;
            double magnitude = Math.sqrt(movement.x * movement.x + movement.y * movement.y +
                    movement.z * movement.z);
            boolean isNewStationary = Math.abs(magnitude - GRAVITY) < THRESHOLD;
            long timestamp = movement.timestamp;

            if (mStartTime == 0 || mIsStationary != isNewStationary) {
                Log.i(TAG, "stationary status change to " + isNewStationary);
                if (isNewStationary) {
                    mStartTime = timestamp;
                    mIsStationary = true;
                } else {
                    long duration = timestamp - mStartTime;
                    Log.i(TAG, "duration : " + duration + ", min duration : " + MIN_DURATION);
                    if (duration >= MIN_DURATION) {
                        periods.add(periodOf(mStartTime, timestamp) + " for " + durationOf(duration));
                        mStartTime = timestamp;
                        Log.i(TAG, "period of duration " + duration + " is added");
                    }
                    mIsStationary = false;
                }
            }
        }

        mCheckTime = currentTime;

        return periods.stream()
                .sorted((a, b) -> Long.compare(
                        Long.parseLong(b.split(" for ")[1].split(" ")[0]),
                        Long.parseLong(a.split(" for ")[1].split(" ")[0])
                ))
                .limit(topN)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAll() {
        mMovementTracker.deleteAllData();
    }

    private String periodOf(long startTime, long endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(startTime)) + " to " + sdf.format(new Date(endTime));
    }

    private String durationOf(long duration) {
        return (duration / 3600000) + " hours";
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
