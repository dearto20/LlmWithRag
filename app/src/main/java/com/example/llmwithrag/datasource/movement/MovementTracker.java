package com.example.llmwithrag.datasource.movement;

import static android.hardware.Sensor.TYPE_ACCELEROMETER;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.llmwithrag.datasource.IDataSourceListener;
import com.example.llmwithrag.datasource.IDataSourceTracker;

import java.util.ArrayList;
import java.util.List;

public class MovementTracker implements SensorEventListener, IDataSourceTracker {
    private static final String TAG = MovementTracker.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final long INTERVAL = 1000L;
    private final Handler mHandler;
    private final SensorManager mSensorManager;
    private final Sensor mAccelerometerSensor;
    private final MovementRepository mRepository;
    private long lastTime;
    private boolean mStarted;

    public MovementTracker(Context context, Looper looper) {
        mHandler = new Handler(looper);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometerSensor = mSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        mRepository = new MovementRepository(context);
        lastTime = 0;
        mStarted = false;
    }

    @Override
    public void startMonitoring() {
        if (mStarted) return;
        Log.i(TAG, "started");
        mSensorManager.registerListener(this, mAccelerometerSensor,
                mAccelerometerSensor.getMaxDelay(), mHandler);
        mStarted = true;
    }

    @Override
    public void stopMonitoring() {
        if (!mStarted) return;
        Log.i(TAG, "stopped");
        mSensorManager.unregisterListener(this);
        mHandler.removeCallbacksAndMessages(null);
        mStarted = false;
    }

    @Override
    public void registerListener(IDataSourceListener listener) {
    }

    @Override
    public void unregisterListener(IDataSourceListener listener) {
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTime >= INTERVAL) {
            lastTime = currentTime;
            if (sensorEvent.sensor.getType() == TYPE_ACCELEROMETER) {
                if (DEBUG) Log.d(TAG, "movement update : (" + sensorEvent.values[0] + ", " +
                        sensorEvent.values[1] + ", " + sensorEvent.values[2] + " at " +
                        currentTime + ")");
                mRepository.insertData(new MovementData(sensorEvent.values[0],
                        sensorEvent.values[1], sensorEvent.values[2], currentTime));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public List<Object> getAllData() {
        return new ArrayList<>(mRepository.getAllData());
    }

    @Override
    public void deleteAllData() {
        mRepository.deleteAllData();
    }
}
