package com.example.llmwithrag;

import static com.example.llmwithrag.llm.EmbeddingManager.CATEGORY_DAY_LOCATION;
import static com.example.llmwithrag.llm.EmbeddingManager.CATEGORY_NIGHT_LOCATION;
import static com.example.llmwithrag.llm.EmbeddingManager.CATEGORY_PUBLIC_WIFI_TIME;
import static com.example.llmwithrag.llm.EmbeddingManager.CATEGORY_STATIONARY_TIME;
import static com.example.llmwithrag.llm.EmbeddingManager.CATEGORY_WEEKEND_LOCATION;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.llmwithrag.datasource.connectivity.ConnectivityTracker;
import com.example.llmwithrag.datasource.location.LocationTracker;
import com.example.llmwithrag.datasource.movement.MovementTracker;
import com.example.llmwithrag.knowledge.connectivity.PublicWifiUsageManager;
import com.example.llmwithrag.knowledge.connectivity.PublicWifiUsageRepository;
import com.example.llmwithrag.knowledge.location.PersistentLocationManager;
import com.example.llmwithrag.knowledge.location.PersistentLocationRepository;
import com.example.llmwithrag.knowledge.status.StationaryTimeManager;
import com.example.llmwithrag.knowledge.status.StationaryTimeRepository;
import com.example.llmwithrag.llm.EmbeddingManager;

import java.util.List;

public class MonitoringService extends Service implements IMonitoringService {
    private static final String TAG = MonitoringService.class.getSimpleName();
    private static final String NAME_SHARED_PREFS = "default_storage";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String KEY_DAY_LOCATION = "key_day_location";
    private static final String KEY_NIGHT_LOCATION = "key_night_location";
    private static final String KEY_WEEKEND_LOCATION = "key_weekend_location";
    private static final String KEY_STATIONARY_TIME = "stationary_time";
    private static final String KEY_PUBLIC_WIFI_TIME = "public_wifi_time";
    private static final long MIN_DELAY_PERIODIC_UPDATE = 5000L;
    private static final long MAX_DELAY_PERIODIC_UPDATE = 600000L;
    private static final boolean DEBUG = false;
    private static final int ID_NOTIFICATION = 1;
    private static final String ID_MAIN_CHANNEL = "001";
    private final IBinder mBinder = new LocalBinder();
    private Handler mHandler;
    private Runnable mUpdateCallback;
    private long mCurrentDelay;
    private final MutableLiveData<String> mTheMostFrequentlyVisitedPlaceDuringTheDay = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostFrequentlyVisitedPlaceDuringTheNight = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostFrequentlyVisitedPlaceDuringTheWeekend = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostFrequentStationaryTime = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostFrequentPublicWifiConnectionTime = new MutableLiveData<>();
    private PersistentLocationManager mPersistentLocationManager;
    private PublicWifiUsageManager mPublicWifiUsageManager;
    private StationaryTimeManager mStationaryTimeManager;
    private EmbeddingManager mEmbeddingManager;
    private boolean mStarted;

    public class LocalBinder extends Binder {
        IMonitoringService getService() {
            return MonitoringService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service Created");
        if (DEBUG) {
            Toast.makeText(getApplicationContext(), "Service Created", Toast.LENGTH_SHORT).show();
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_NOTIFICATION, getNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(ID_NOTIFICATION, getNotification());
        }

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        Context context = getApplicationContext();
        mHandler = new Handler(looper);
        mCurrentDelay = MIN_DELAY_PERIODIC_UPDATE;
        mUpdateCallback = () -> {
            mHandler.postDelayed(mUpdateCallback, mCurrentDelay);
            mCurrentDelay = Math.min(mCurrentDelay + MIN_DELAY_PERIODIC_UPDATE,
                    MAX_DELAY_PERIODIC_UPDATE);
            Log.i(TAG, "run periodic update");
            updateKnowledge();
        };
        mPersistentLocationManager = new PersistentLocationManager(context,
                new PersistentLocationRepository(context), new LocationTracker(context, looper));
        mPublicWifiUsageManager = new PublicWifiUsageManager(context,
                new PublicWifiUsageRepository(context), new ConnectivityTracker(context, looper));
        mStationaryTimeManager = new StationaryTimeManager(context,
                new StationaryTimeRepository(context), new MovementTracker(context, looper));
        mEmbeddingManager = new EmbeddingManager(getApplicationContext());
        mStarted = false;
    }

    private void updateKnowledge() {
        updateKnowledge(false);
    }

    private void updateKnowledge(boolean forceUpdate) {
        updateDayLocation(isDayLocationEnabled(), forceUpdate);

        updateNightLocation(isNightLocationEnabled(), forceUpdate);

        updateWeekendLocation(isWeekendLocationEnabled(), forceUpdate);

        updateStationaryTime(isStationaryTimeEnabled(), forceUpdate);

        updatePublicWifiTime(isPublicWifiTimeEnabled(), forceUpdate);
    }

    public static class EmbeddingResultListener {
        public void onSuccess() {
        }

        public void onError() {
        }

        public void onFailure() {
        }
    }

    private Notification getNotification() {
        createNotificationChannel(ID_MAIN_CHANNEL, "main", NotificationManager.IMPORTANCE_DEFAULT);
        return createNotification(ID_MAIN_CHANNEL, "Galaxy AutoNav", "Tap Here to Open");
    }

    private void createNotificationChannel(String channelId, String channelName, int importance) {
        NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification createNotification(String channelId, String title, String content) {
        Context context = getApplicationContext();
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_notification)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void deleteAll() {
        mPersistentLocationManager.deleteAll();
        mPublicWifiUsageManager.deleteAll();
        mStationaryTimeManager.deleteAll();
        mEmbeddingManager.deleteAll();
        updateKnowledge(true);
    }

    @Override
    public List<String> findSimilarOnes(String query) {
        return mEmbeddingManager.findSimilarOnes(query);
    }

    @Override
    public LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheDay() {
        return mTheMostFrequentlyVisitedPlaceDuringTheDay;
    }

    private String getTheMostFrequentlyVisitedPlaceDuringTheDayInternal() {
        String result = "";
        List<String> results = mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheDay(1);
        if (results != null && !results.isEmpty()) result = results.get(0);
        return result;
    }

    @Override
    public LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheNight() {
        return mTheMostFrequentlyVisitedPlaceDuringTheNight;
    }

    private String getTheMostFrequentlyVisitedPlaceDuringTheNightInternal() {
        String result = "";
        List<String> results = mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheNight(1);
        if (results != null && !results.isEmpty()) result = results.get(0);
        return result;
    }

    @Override
    public LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheWeekend() {
        return mTheMostFrequentlyVisitedPlaceDuringTheWeekend;
    }

    private String getTheMostFrequentlyVisitedPlaceDuringTheWeekendInternal() {
        String result = "";
        List<String> results = mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheWeekend(1);
        if (results != null && !results.isEmpty()) result = results.get(0);
        return result;
    }

    @Override
    public LiveData<String> getTheMostFrequentStationaryTime() {
        return mTheMostFrequentStationaryTime;
    }

    private String getTheMostFrequentStationaryTimeInternal() {
        String result = "";
        List<String> results = mStationaryTimeManager.getMostFrequentStationaryTimes(1);
        if (results != null && !results.isEmpty()) result = results.get(0);
        return result;
    }

    @Override
    public LiveData<String> getTheMostFrequentPublicWifiConnectionTime() {
        return mTheMostFrequentPublicWifiConnectionTime;
    }

    @Override
    public boolean isServiceEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, false);
    }

    @Override
    public boolean isDayLocationEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        boolean result = sharedPreferences != null && sharedPreferences.getBoolean(KEY_DAY_LOCATION, true);
        return result;
    }

    @Override
    public boolean isNightLocationEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_NIGHT_LOCATION, true);
    }

    @Override
    public boolean isWeekendLocationEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_WEEKEND_LOCATION, true);
    }

    @Override
    public boolean isStationaryTimeEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_STATIONARY_TIME, true);
    }

    @Override
    public boolean isPublicWifiTimeEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_PUBLIC_WIFI_TIME, true);
    }

    @Override
    public boolean setServiceEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_SERVICE_ENABLED, enabled)) {
            if (enabled) startMonitoring();
            else stopMonitoring();
            return true;
        }
        return false;
    }

    @Override
    public boolean setDayLocationEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_DAY_LOCATION, enabled)) {
            updateDayLocation(enabled, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean setNightLocationEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_NIGHT_LOCATION, enabled)) {
            updateNightLocation(enabled, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean setWeekendLocationEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_WEEKEND_LOCATION, enabled)) {
            updateWeekendLocation(enabled, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean setStationaryTimeEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_STATIONARY_TIME, enabled)) {
            updateStationaryTime(enabled, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean setPublicWifiTimeEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_PUBLIC_WIFI_TIME, enabled)) {
            updatePublicWifiTime(enabled, true);
            return true;
        }
        return false;
    }

    private String getTheMostFrequentPublicWifiConnectionTimeInternal() {
        String result = "";
        List<String> results = mPublicWifiUsageManager.getMostFrequentPublicWifiConnectionTimes(1);
        if (results != null && !results.isEmpty()) result = results.get(0);
        return result;
    }

    private void updateDayLocation(boolean isChecked, boolean forceUpdate) {
        EmbeddingResultListener listener = new EmbeddingResultListener() {
            @Override
            public void onSuccess() {
                mTheMostFrequentlyVisitedPlaceDuringTheDay.postValue(
                        mEmbeddingManager.getTheMostFrequentlyVisitedPlaceDuringTheDay());
                Log.i(TAG, "day location is updated");
            }
        };

        String oldValue = mEmbeddingManager.getTheMostFrequentlyVisitedPlaceDuringTheDay();
        if (isChecked) {
            String newValue = getTheMostFrequentlyVisitedPlaceDuringTheDayInternal();
            if (forceUpdate || !TextUtils.equals(oldValue, newValue)) {
                mEmbeddingManager.addEmbeddings(newValue, newValue, CATEGORY_DAY_LOCATION, listener);
            }
        } else {
            mEmbeddingManager.removeEmbeddings(CATEGORY_DAY_LOCATION, listener);
            TextUtils.isEmpty(oldValue);
        }
    }

    private void updateNightLocation(boolean isChecked, boolean forceUpdate) {
        EmbeddingResultListener listener = new EmbeddingResultListener() {
            @Override
            public void onSuccess() {
                String location = mEmbeddingManager.getTheMostFrequentlyVisitedPlaceDuringTheNight();
                mTheMostFrequentlyVisitedPlaceDuringTheNight.postValue(location
                );
                Log.i(TAG, "night location is updated");
            }
        };

        String oldValue = mEmbeddingManager.getTheMostFrequentlyVisitedPlaceDuringTheNight();
        if (isChecked) {
            String newValue = getTheMostFrequentlyVisitedPlaceDuringTheNightInternal();
            if (forceUpdate || !TextUtils.equals(oldValue, newValue)) {
                mEmbeddingManager.addEmbeddings(newValue, newValue, CATEGORY_NIGHT_LOCATION, listener);
            }
        } else {
            mEmbeddingManager.removeEmbeddings(CATEGORY_NIGHT_LOCATION, listener);
            TextUtils.isEmpty(oldValue);
        }
    }

    private void updateWeekendLocation(boolean isChecked, boolean forceUpdate) {
        EmbeddingResultListener listener = new EmbeddingResultListener() {
            @Override
            public void onSuccess() {
                mTheMostFrequentlyVisitedPlaceDuringTheWeekend.postValue(
                        mEmbeddingManager.getTheMostFrequentlyVisitedPlaceDuringTheWeekend());
                Log.i(TAG, "weekend location is updated");
            }
        };

        String oldValue = mEmbeddingManager.getTheMostFrequentlyVisitedPlaceDuringTheWeekend();
        if (isChecked) {
            String newValue = getTheMostFrequentlyVisitedPlaceDuringTheWeekendInternal();
            if (forceUpdate || !TextUtils.equals(oldValue, newValue)) {
                mEmbeddingManager.addEmbeddings(newValue, newValue, CATEGORY_WEEKEND_LOCATION, listener);
            }
        } else {
            mEmbeddingManager.removeEmbeddings(CATEGORY_WEEKEND_LOCATION, listener);
            TextUtils.isEmpty(oldValue);
        }
    }

    private void updateStationaryTime(boolean isChecked, boolean forceUpdate) {
        EmbeddingResultListener listener = new EmbeddingResultListener() {
            @Override
            public void onSuccess() {
                mTheMostFrequentStationaryTime.postValue(
                        mEmbeddingManager.getTheMostFrequentStationaryTime());
                Log.i(TAG, "stationary time is updated");
            }
        };

        String oldValue = mEmbeddingManager.getTheMostFrequentStationaryTime();
        if (isChecked) {
            String newValue = getTheMostFrequentStationaryTimeInternal();
            if (forceUpdate || !TextUtils.equals(oldValue, newValue)) {
                mEmbeddingManager.addEmbeddings(newValue, newValue, CATEGORY_STATIONARY_TIME, listener);
            }
        } else {
            mEmbeddingManager.removeEmbeddings(CATEGORY_STATIONARY_TIME, listener);
            TextUtils.isEmpty(oldValue);
        }
    }

    private void updatePublicWifiTime(boolean isChecked, boolean forceUpdate) {
        EmbeddingResultListener listener = new EmbeddingResultListener() {
            @Override
            public void onSuccess() {
                mTheMostFrequentPublicWifiConnectionTime.postValue(
                        mEmbeddingManager.getTheMostFrequentPublicWifiConnectionTime());
                Log.i(TAG, "public wifi time is updated");
            }
        };

        String oldValue = mEmbeddingManager.getTheMostFrequentPublicWifiConnectionTime();
        if (isChecked) {
            String newValue = getTheMostFrequentPublicWifiConnectionTimeInternal();
            if (forceUpdate || !TextUtils.equals(oldValue, newValue)) {
                mEmbeddingManager.addEmbeddings(newValue, newValue, CATEGORY_PUBLIC_WIFI_TIME, listener);
            }
        } else {
            mEmbeddingManager.removeEmbeddings(CATEGORY_PUBLIC_WIFI_TIME, listener);
            TextUtils.isEmpty(oldValue);
        }
    }

    private SharedPreferences getSharedPreferences(String name) {
        return getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    private boolean setSharedPreferences(String key, boolean value) {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        if (sharedPreferences == null) return false;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
        return true;
    }

    private void startMonitoring() {
        Log.i(TAG, "startMonitoring " + mStarted);
        if (mStarted) return;
        Toast.makeText(getApplicationContext(), "Service Started", Toast.LENGTH_SHORT).show();
        mPersistentLocationManager.startMonitoring();
        mPublicWifiUsageManager.startMonitoring();
        mStationaryTimeManager.startMonitoring();
        mHandler.removeCallbacksAndMessages(null);
        mUpdateCallback.run();
        mStarted = true;
        updateKnowledge(true);
    }

    private void stopMonitoring() {
        Log.i(TAG, "stopMonitoring " + mStarted);
        if (!mStarted) return;
        Toast.makeText(getApplicationContext(), "Service Stopped", Toast.LENGTH_SHORT).show();
        mStationaryTimeManager.stopMonitoring();
        mPublicWifiUsageManager.stopMonitoring();
        mPersistentLocationManager.stopMonitoring();
        mHandler.removeCallbacksAndMessages(null);
        mStarted = false;
    }
}
