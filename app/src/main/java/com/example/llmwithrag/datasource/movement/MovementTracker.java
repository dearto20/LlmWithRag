package com.example.llmwithrag.datasource.movement;

import static android.hardware.Sensor.TYPE_ACCELEROMETER;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.example.llmwithrag.datasource.IDataSource;

import java.util.List;

public class MovementTracker implements SensorEventListener, IDataSource {
    private static final String TAG = MovementTracker.class.getSimpleName();
    private final SensorManager mSensorManager;
    private final Sensor mAccelerometerSensor;
    private final MovementRepository mRepository;
    private final Context mContext;
    private long lastTime;

    public MovementTracker(Context context) {
        mContext = context;
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometerSensor = mSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        mRepository = new MovementRepository(context);
        lastTime = 0;
    }

    @Override
    public void startMonitoring() {
        int maxDelay = mAccelerometerSensor.getMaxDelay();
        int delay = Math.min(maxDelay, 60000000);
        mSensorManager.registerListener(this, mAccelerometerSensor, delay);
    }

    @Override
    public void stopMonitoring() {
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTime >= 1000 * 10) {
            lastTime = currentTime;
            if (sensorEvent.sensor.getType() == TYPE_ACCELEROMETER) {
                Log.i(TAG, "movement update : (" + sensorEvent.values[0] + ", " +
                        sensorEvent.values[1] + ", " + sensorEvent.values[2] + ")");
                mRepository.insertData(new MovementData(sensorEvent.values[0],
                        sensorEvent.values[1], sensorEvent.values[2], System.currentTimeMillis()));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public List<MovementData> getAllData() {
        return mRepository.getAllData();
    }

    public void deleteAllData() {
        mRepository.deleteAllData();
    }
}
