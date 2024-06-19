package com.example.llmwithrag;

import static com.example.llmwithrag.llm.EmbeddingManager.CATEGORY_DAY_LOCATION;
import static com.example.llmwithrag.llm.EmbeddingManager.CATEGORY_ENTERPRISE_WIFI_TIME;
import static com.example.llmwithrag.llm.EmbeddingManager.CATEGORY_NIGHT_LOCATION;
import static com.example.llmwithrag.llm.EmbeddingManager.CATEGORY_PERSONAL_WIFI_TIME;
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
import android.location.Address;
import android.location.Geocoder;
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
import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeGraphManager;
import com.example.llmwithrag.knowledge.apps.CalendarAppManager;
import com.example.llmwithrag.knowledge.apps.PhotoAppManager;
import com.example.llmwithrag.knowledge.apps.SmsAppManager;
import com.example.llmwithrag.knowledge.connectivity.WifiConnectionTimeManager;
import com.example.llmwithrag.knowledge.connectivity.WifiConnectionTimeRepository;
import com.example.llmwithrag.knowledge.location.PersistentLocationManager;
import com.example.llmwithrag.knowledge.location.PersistentLocationRepository;
import com.example.llmwithrag.knowledge.status.StationaryTimeManager;
import com.example.llmwithrag.knowledge.status.StationaryTimeRepository;
import com.example.llmwithrag.llm.EmbeddingManager;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MonitoringService extends Service implements IMonitoringService {
    private static final String TAG = MonitoringService.class.getSimpleName();
    private static final String NAME_SHARED_PREFS = "default_storage";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String KEY_DAY_LOCATION = "key_day_location";
    private static final String KEY_NIGHT_LOCATION = "key_night_location";
    private static final String KEY_WEEKEND_LOCATION = "key_weekend_location";
    private static final String KEY_STATIONARY_TIME = "stationary_time";
    private static final String KEY_ENTERPRISE_WIFI_TIME = "enterprise_wifi_time";
    private static final String KEY_PERSONAL_WIFI_TIME = "personal_wifi_time";
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
    private final MutableLiveData<String> mTheMostFrequentEnterpriseWifiConnectionTime = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostFrequentPersonalWifiConnectionTime = new MutableLiveData<>();
    private KnowledgeGraphManager mKgManager;
    private EmbeddingManager mKgEmbeddingManager;
    private CalendarAppManager mCalendarAppManager;
    private PhotoAppManager mPhotoAppManager;
    private SmsAppManager mSmsAppManager;
    private PersistentLocationManager mPersistentLocationManager;
    private WifiConnectionTimeManager mWifiConnectionTimeManager;
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

        if (BuildConfig.IS_SCHEMA_ENABLED) {
            mKgManager = new KnowledgeGraphManager(getApplicationContext());
            mKgEmbeddingManager = new EmbeddingManager(getApplicationContext());
            mCalendarAppManager = new CalendarAppManager(context, mKgManager, mKgEmbeddingManager);
            mPhotoAppManager = new PhotoAppManager(context, mKgManager, mKgEmbeddingManager);
            mSmsAppManager = new SmsAppManager(context, mKgManager, mKgEmbeddingManager);
        }
        mPersistentLocationManager = new PersistentLocationManager(context,
                new PersistentLocationRepository(context), new LocationTracker(context, looper));
        mWifiConnectionTimeManager = new WifiConnectionTimeManager(context,
                new WifiConnectionTimeRepository(context), new ConnectivityTracker(context, looper));
        mStationaryTimeManager = new StationaryTimeManager(context,
                new StationaryTimeRepository(context), new MovementTracker(context, looper));
        mEmbeddingManager = new EmbeddingManager(getApplicationContext());
        mStarted = false;
    }

    private void initKnowledge() {
        mTheMostFrequentlyVisitedPlaceDuringTheDay.postValue(getExplanatoryDayLocation(
                getTheMostFrequentlyVisitedPlaceDuringTheDayInternal(false), true));
        mTheMostFrequentlyVisitedPlaceDuringTheNight.postValue(getExplanatoryNightLocation(
                getTheMostFrequentlyVisitedPlaceDuringTheNightInternal(false), true));
        mTheMostFrequentlyVisitedPlaceDuringTheWeekend.postValue(getExplanatoryWeekendLocation(
                getTheMostFrequentlyVisitedPlaceDuringTheWeekendInternal(false), true));
        mTheMostFrequentStationaryTime.postValue(getExplanatoryStationaryTime(
                getTheMostFrequentStationaryTimeInternal(false), true));
        mTheMostFrequentEnterpriseWifiConnectionTime.postValue(getExplanatoryEnterpriseWifiConnectionTime(
                getTheMostFrequentEnterpriseWifiConnectionTimeInternal(false), true));
        mTheMostFrequentPersonalWifiConnectionTime.postValue(getExplanatoryPersonalWifiConnectionTime(
                getTheMostFrequentPersonalWifiConnectionTimeInternal(false), true));
    }

    private void updateKnowledge() {
        updateKnowledge(false);
    }

    private void updateKnowledge(boolean forceUpdate) {
        updateDayLocation(isDayLocationEnabled(), forceUpdate);
        updateNightLocation(isNightLocationEnabled(), forceUpdate);
        updateWeekendLocation(isWeekendLocationEnabled(), forceUpdate);
        updateStationaryTime(isStationaryTimeEnabled(), forceUpdate);
        updateEnterpriseWifiTime(isEnterpriseWifiTimeEnabled(), forceUpdate);
        updatePersonalWifiTime(isPersonalWifiTimeEnabled(), forceUpdate);
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
        if (BuildConfig.IS_SCHEMA_ENABLED) {
            mKgManager.deleteAll();
            mKgEmbeddingManager.deleteAll();
            mCalendarAppManager.deleteAll();
            mPhotoAppManager.deleteAll();
            mSmsAppManager.deleteAll();
        }
        mPersistentLocationManager.deleteAll();
        mWifiConnectionTimeManager.deleteAll();
        mStationaryTimeManager.deleteAll();
        mEmbeddingManager.deleteAll();
        updateKnowledge(true);
    }

    @Override
    public List<String> findSimilarOnes(String query) {
        return mEmbeddingManager.findSimilarOnes(query);
    }

    @Override
    public List<String> findSimilarOnes(String query, String response) {
        Log.i(TAG, "find similar ones : " + query + ", " + response);
        Map<String, Entity> entities =
                mKgManager.parseEntitiesFromResponse(response);
        Log.i(TAG, "entities : " + entities);

        String flattened = new Gson().toJson(entities);
        Log.i(TAG, "flattened : " + flattened);
        List<String> result = mEmbeddingManager.findSimilarOnes(flattened, 0);
        Log.i(TAG, "result : " + Arrays.toString(result.toArray()));
        return result;
    }

    @Override
    public String getSchema() {
        return KnowledgeGraphManager.SCHEMA;
    }

    @Override
    public LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheDay() {
        return mTheMostFrequentlyVisitedPlaceDuringTheDay;
    }

    private String getTheMostFrequentlyVisitedPlaceDuringTheDayInternal() {
        return getTheMostFrequentlyVisitedPlaceDuringTheDayInternal(true);
    }

    private String getTheMostFrequentlyVisitedPlaceDuringTheDayInternal(boolean update) {
        List<String> results = update ?
                mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheDay(1) : null;
        return (results != null && !results.isEmpty()) ? results.get(0) : "";
    }

    @Override
    public LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheNight() {
        return mTheMostFrequentlyVisitedPlaceDuringTheNight;
    }

    private String getTheMostFrequentlyVisitedPlaceDuringTheNightInternal() {
        return getTheMostFrequentlyVisitedPlaceDuringTheNightInternal(true);
    }

    private String getTheMostFrequentlyVisitedPlaceDuringTheNightInternal(boolean update) {
        List<String> results = update ?
                mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheNight(1) : null;
        return (results != null && !results.isEmpty()) ? results.get(0) : "";
    }

    @Override
    public LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheWeekend() {
        return mTheMostFrequentlyVisitedPlaceDuringTheWeekend;
    }

    private String getTheMostFrequentlyVisitedPlaceDuringTheWeekendInternal() {
        return getTheMostFrequentlyVisitedPlaceDuringTheWeekendInternal(true);
    }

    private String getTheMostFrequentlyVisitedPlaceDuringTheWeekendInternal(boolean update) {
        List<String> results = update ?
                mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheWeekend(1) : null;
        return (results != null && !results.isEmpty()) ? results.get(0) : "";
    }

    @Override
    public LiveData<String> getTheMostFrequentStationaryTime() {
        return mTheMostFrequentStationaryTime;
    }

    private String getTheMostFrequentStationaryTimeInternal() {
        return getTheMostFrequentStationaryTimeInternal(true);
    }

    private String getTheMostFrequentStationaryTimeInternal(boolean update) {
        List<String> results = update ?
                mStationaryTimeManager.getMostFrequentStationaryTimes(1) : null;
        return (results != null && !results.isEmpty()) ? results.get(0) : "";
    }

    @Override
    public LiveData<String> getTheMostFrequentEnterpriseWifiConnectionTime() {
        return mTheMostFrequentEnterpriseWifiConnectionTime;
    }

    private String getTheMostFrequentEnterpriseWifiConnectionTimeInternal() {
        return getTheMostFrequentEnterpriseWifiConnectionTimeInternal(true);
    }

    private String getTheMostFrequentEnterpriseWifiConnectionTimeInternal(boolean update) {
        List<String> results = update ?
                mWifiConnectionTimeManager.getMostFrequentEnterpriseWifiConnectionTimes(1) : null;
        return (results != null && !results.isEmpty()) ? results.get(0) : "";
    }

    @Override
    public LiveData<String> getTheMostFrequentPersonalWifiConnectionTime() {
        return mTheMostFrequentPersonalWifiConnectionTime;
    }

    private String getTheMostFrequentPersonalWifiConnectionTimeInternal() {
        return getTheMostFrequentPersonalWifiConnectionTimeInternal(true);
    }

    private String getTheMostFrequentPersonalWifiConnectionTimeInternal(boolean update) {
        List<String> results = update ?
                mWifiConnectionTimeManager.getMostFrequentPersonalWifiConnectionTimes(1) : null;
        return (results != null && !results.isEmpty()) ? results.get(0) : "";
    }

    @Override
    public boolean isServiceEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, false);
    }

    @Override
    public boolean isDayLocationEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_DAY_LOCATION, true);
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
    public boolean isEnterpriseWifiTimeEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_ENTERPRISE_WIFI_TIME, true);
    }

    @Override
    public boolean isPersonalWifiTimeEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_PERSONAL_WIFI_TIME, true);
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
    public boolean setEnterpriseWifiTimeEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_ENTERPRISE_WIFI_TIME, enabled)) {
            updateEnterpriseWifiTime(enabled, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean setPersonalWifiTimeEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_PERSONAL_WIFI_TIME, enabled)) {
            updatePersonalWifiTime(enabled, true);
            return true;
        }
        return false;
    }

    private void updateDayLocation(boolean isChecked, boolean forceUpdate) {
        EmbeddingResultListener listener = new EmbeddingResultListener() {
            @Override
            public void onSuccess() {
                String location = mEmbeddingManager.getTheMostFrequentlyVisitedPlaceDuringTheDay();
                if (TextUtils.isEmpty(location)) {
                    location = getExplanatoryDayLocation("", true);
                }
                mTheMostFrequentlyVisitedPlaceDuringTheDay.postValue(location);
                if (DEBUG) Log.i(TAG, "day location is updated to " + location);
            }
        };

        String oldValue = mEmbeddingManager.getTheMostFrequentlyVisitedPlaceDuringTheDay();
        if (isChecked) {
            String rawValue = getTheMostFrequentlyVisitedPlaceDuringTheDayInternal();
            String newValue = getExplanatoryDayLocation(rawValue, true);
            if (forceUpdate || !TextUtils.equals(oldValue, newValue)) {
                String description = getExplanatoryDayLocation(rawValue, false);
                mEmbeddingManager.addEmbeddings(newValue, description, CATEGORY_DAY_LOCATION, listener);
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
                if (TextUtils.isEmpty(location)) {
                    location = getExplanatoryNightLocation("", true);
                }
                mTheMostFrequentlyVisitedPlaceDuringTheNight.postValue(location);
                if (DEBUG) Log.i(TAG, "night location is updated to " + location);
            }
        };

        String oldValue = mEmbeddingManager.getTheMostFrequentlyVisitedPlaceDuringTheNight();
        if (isChecked) {
            String rawValue = getTheMostFrequentlyVisitedPlaceDuringTheNightInternal();
            String newValue = getExplanatoryNightLocation(rawValue, true);
            if (forceUpdate || !TextUtils.equals(oldValue, newValue)) {
                String description = getExplanatoryNightLocation(rawValue, false);
                mEmbeddingManager.addEmbeddings(newValue, description, CATEGORY_NIGHT_LOCATION, listener);
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
                String location = mEmbeddingManager.getTheMostFrequentlyVisitedPlaceDuringTheWeekend();
                if (TextUtils.isEmpty(location)) {
                    location = getExplanatoryWeekendLocation("", true);
                }
                mTheMostFrequentlyVisitedPlaceDuringTheWeekend.postValue(location);
                if (DEBUG) Log.i(TAG, "weekend location is updated to " + location);
            }
        };

        String oldValue = mEmbeddingManager.getTheMostFrequentlyVisitedPlaceDuringTheWeekend();
        if (isChecked) {
            String rawValue = getTheMostFrequentlyVisitedPlaceDuringTheWeekendInternal();
            String newValue = getExplanatoryWeekendLocation(rawValue, true);
            if (forceUpdate || !TextUtils.equals(oldValue, newValue)) {
                String description = getExplanatoryWeekendLocation(rawValue, false);
                mEmbeddingManager.addEmbeddings(newValue, description, CATEGORY_WEEKEND_LOCATION, listener);
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
                String time = mEmbeddingManager.getTheMostFrequentStationaryTime();
                if (TextUtils.isEmpty(time)) {
                    time = getExplanatoryStationaryTime("", true);
                }
                mTheMostFrequentStationaryTime.postValue(time);
                if (DEBUG) Log.i(TAG, "stationary time is updated to " + time);
            }
        };

        String oldValue = mEmbeddingManager.getTheMostFrequentStationaryTime();
        if (isChecked) {
            String rawValue = getTheMostFrequentStationaryTimeInternal();
            String newValue = getExplanatoryStationaryTime(rawValue, true);
            if (forceUpdate || !TextUtils.equals(oldValue, newValue)) {
                String description = getExplanatoryStationaryTime(rawValue, false);
                mEmbeddingManager.addEmbeddings(newValue, description, CATEGORY_STATIONARY_TIME, listener);
            }
        } else {
            mEmbeddingManager.removeEmbeddings(CATEGORY_STATIONARY_TIME, listener);
            TextUtils.isEmpty(oldValue);
        }
    }

    private void updateEnterpriseWifiTime(boolean isChecked, boolean forceUpdate) {
        EmbeddingResultListener listener = new EmbeddingResultListener() {
            @Override
            public void onSuccess() {
                String time = mEmbeddingManager.getTheMostFrequentEnterpriseWifiConnectionTime();
                if (TextUtils.isEmpty(time)) {
                    time = getExplanatoryEnterpriseWifiConnectionTime("", true);
                }
                mTheMostFrequentEnterpriseWifiConnectionTime.postValue(time);
                if (DEBUG) Log.i(TAG, "enterprise wifi time is updated to " + time);
            }
        };

        String oldValue = mEmbeddingManager.getTheMostFrequentEnterpriseWifiConnectionTime();
        if (isChecked) {
            String rawValue = getTheMostFrequentEnterpriseWifiConnectionTimeInternal();
            String newValue = getExplanatoryEnterpriseWifiConnectionTime(rawValue, true);
            if (forceUpdate || !TextUtils.equals(oldValue, newValue)) {
                String description = getExplanatoryEnterpriseWifiConnectionTime(rawValue, false);
                mEmbeddingManager.addEmbeddings(newValue, description, CATEGORY_ENTERPRISE_WIFI_TIME, listener);
            }
        } else {
            mEmbeddingManager.removeEmbeddings(CATEGORY_ENTERPRISE_WIFI_TIME, listener);
            TextUtils.isEmpty(oldValue);
        }
    }

    private void updatePersonalWifiTime(boolean isChecked, boolean forceUpdate) {
        EmbeddingResultListener listener = new EmbeddingResultListener() {
            @Override
            public void onSuccess() {
                String time = mEmbeddingManager.getTheMostFrequentPersonalWifiConnectionTime();
                if (TextUtils.isEmpty(time)) {
                    time = getExplanatoryPersonalWifiConnectionTime("", true);
                }
                mTheMostFrequentPersonalWifiConnectionTime.postValue(time);
                if (DEBUG) Log.i(TAG, "personal wifi time is updated to " + time);
            }
        };

        String oldValue = mEmbeddingManager.getTheMostFrequentPersonalWifiConnectionTime();
        if (isChecked) {
            String rawValue = getTheMostFrequentPersonalWifiConnectionTimeInternal();
            String newValue = getExplanatoryPersonalWifiConnectionTime(rawValue, true);
            if (forceUpdate || !TextUtils.equals(oldValue, newValue)) {
                String description = getExplanatoryPersonalWifiConnectionTime(rawValue, false);
                mEmbeddingManager.addEmbeddings(newValue, description, CATEGORY_PERSONAL_WIFI_TIME, listener);
            }
        } else {
            mEmbeddingManager.removeEmbeddings(CATEGORY_PERSONAL_WIFI_TIME, listener);
            TextUtils.isEmpty(oldValue);
        }
    }

    private String getExplanatoryDayLocation(String text, boolean readable) {
        if (!TextUtils.isEmpty(text)) {
            return getApplicationContext().getString(R.string.day_location) + " is\n" + (readable ? getReadableAddress(text) : text);
        } else {
            return getApplicationContext().getString(R.string.day_location_unavailable);
        }
    }

    private String getExplanatoryNightLocation(String text, boolean readable) {
        if (!TextUtils.isEmpty(text)) {
            return getApplicationContext().getString(R.string.night_location) + " is\n" + (readable ? getReadableAddress(text) : text);
        } else {
            return getApplicationContext().getString(R.string.night_location_unavailable);
        }
    }

    private String getExplanatoryWeekendLocation(String text, boolean readable) {
        if (!TextUtils.isEmpty(text)) {
            return getApplicationContext().getString(R.string.weekend_location) + " is\n" + (readable ? getReadableAddress(text) : text);
        } else {
            return getApplicationContext().getString(R.string.weekend_location_unavailable);
        }
    }

    private String getExplanatoryStationaryTime(String text, boolean readable) {
        if (!TextUtils.isEmpty(text)) {
            return getApplicationContext().getString(R.string.stationary_time) + " is " + text;
        } else {
            return getApplicationContext().getString(R.string.stationary_time_unavailable);
        }
    }

    private String getExplanatoryEnterpriseWifiConnectionTime(String text, boolean readable) {
        if (!TextUtils.isEmpty(text)) {
            return getApplicationContext().getString(R.string.enterprise_wifi_time) + " is " + text;
        } else {
            return getApplicationContext().getString(R.string.enterprise_wifi_time_unavailable);
        }
    }

    private String getExplanatoryPersonalWifiConnectionTime(String text, boolean readable) {
        if (!TextUtils.isEmpty(text)) {
            return getApplicationContext().getString(R.string.personal_wifi_time) + " is " + text;
        } else {
            return getApplicationContext().getString(R.string.personal_wifi_time_unavailable);
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
        if (BuildConfig.IS_SCHEMA_ENABLED) {
            mCalendarAppManager.startMonitoring();
            mPhotoAppManager.startMonitoring();
            mSmsAppManager.startMonitoring();
        }
        mPersistentLocationManager.startMonitoring();
        mWifiConnectionTimeManager.startMonitoring();
        mStationaryTimeManager.startMonitoring();
        mHandler.removeCallbacksAndMessages(null);
        mUpdateCallback.run();
        mStarted = true;
        initKnowledge();
        updateKnowledge(true);
    }

    private void stopMonitoring() {
        Log.i(TAG, "stopMonitoring " + mStarted);
        if (!mStarted) return;
        Toast.makeText(getApplicationContext(), "Service Stopped", Toast.LENGTH_SHORT).show();
        mStationaryTimeManager.stopMonitoring();
        mWifiConnectionTimeManager.stopMonitoring();
        mPersistentLocationManager.stopMonitoring();
        if (BuildConfig.IS_SCHEMA_ENABLED) {
            mSmsAppManager.stopMonitoring();
            mPhotoAppManager.stopMonitoring();
            mCalendarAppManager.stopMonitoring();
        }
        mHandler.removeCallbacksAndMessages(null);
        mStarted = false;
    }

    private String getReadableAddress(String location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        String result = "";

        try {
            String[] values = location.split(",");
            double latitude = Double.parseDouble(values[0].trim());
            double longitude = Double.parseDouble(values[1].trim());

            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String countryName = address.getCountryName();
                result = address.getAddressLine(0);
                if (!TextUtils.isEmpty(countryName)) {
                    result = result.replace(countryName, "")
                            .replaceAll(",\\s*,", ",")
                            .replaceAll("^,\\s*", "")
                            .replaceAll(",\\s*$", "");
                }
                return result;
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return location;
    }
}
