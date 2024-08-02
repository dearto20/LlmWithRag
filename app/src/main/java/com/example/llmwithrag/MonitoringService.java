package com.example.llmwithrag;

import static com.example.llmwithrag.BuildConfig.IS_SENTENCE_BASED;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_EVENT_IN_THE_CALENDAR_APP;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_LOCATION_DURING_THE_DAY;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_LOCATION_DURING_THE_NIGHT;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_LOCATION_DURING_THE_WEEKEND;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_MESSAGE_IN_THE_EMAIL_APP;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_MESSAGE_IN_THE_MESSAGES_APP;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_PERIOD_ENTERPRISE_WIFI_CONNECTION;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_PERIOD_PERSONAL_WIFI_CONNECTION;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_PERIOD_STATIONARY;

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
import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeManager;
import com.example.llmwithrag.knowledge.apps.CalendarAppManager;
import com.example.llmwithrag.knowledge.apps.EmailAppManager;
import com.example.llmwithrag.knowledge.apps.MessagesAppManager;
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
    private static final String KEY_CALENDAR_APP_EVENT = "calendar_app_event";
    private static final String KEY_EMAIL_APP_MESSAGE = "email_app_message";
    private static final String KEY_MESSAGES_APP_MESSAGE = "messages_app_message";
    private static final long MIN_DELAY_PERIODIC_UPDATE = 5000L;
    private static final long MAX_DELAY_PERIODIC_UPDATE = 600000L;
    private static final boolean DEBUG = true;
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
    private final MutableLiveData<String> mTheMostRecentCalendarAppEvent = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostRecentEmailAppMessage = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostRecentMessagesAppMessage = new MutableLiveData<>();
    private KnowledgeManager mKnowledgeManager;
    private EmbeddingManager mEmbeddingManager;
    private CalendarAppManager mCalendarAppManager;
    private EmailAppManager mEmailAppManager;
    private MessagesAppManager mMessagesAppManager;
    private PersistentLocationManager mPersistentLocationManager;
    private WifiConnectionTimeManager mWifiConnectionTimeManager;
    private StationaryTimeManager mStationaryTimeManager;
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

        mEmbeddingManager = new EmbeddingManager(context);
        mKnowledgeManager = new KnowledgeManager(context);
        mEmbeddingManager = new EmbeddingManager(context);
        mCalendarAppManager = new CalendarAppManager(context,
                mKnowledgeManager, mEmbeddingManager,
                () -> updateCalendarAppEvent(isCalendarAppEventEnabled()));
        mEmailAppManager = new EmailAppManager(context,
                mKnowledgeManager, mEmbeddingManager,
                () -> updateEmailAppMessage(isEmailAppMessageEnabled()));
        mMessagesAppManager = new MessagesAppManager(context,
                mKnowledgeManager, mEmbeddingManager,
                () -> updateMessagesAppMessage(isMessagesAppMessageEnabled()));
        mPersistentLocationManager = new PersistentLocationManager(context,
                mKnowledgeManager, mEmbeddingManager,
                new PersistentLocationRepository(context), new LocationTracker(context, looper));
        mWifiConnectionTimeManager = new WifiConnectionTimeManager(context,
                mKnowledgeManager, mEmbeddingManager,
                new WifiConnectionTimeRepository(context), new ConnectivityTracker(context, looper));
        mStationaryTimeManager = new StationaryTimeManager(context,
                mKnowledgeManager, mEmbeddingManager,
                new StationaryTimeRepository(context), new MovementTracker(context, looper));
        mStarted = false;
    }

    private void updateKnowledge() {
        updateDayLocation(isDayLocationEnabled());
        updateNightLocation(isNightLocationEnabled());
        updateWeekendLocation(isWeekendLocationEnabled());
        updateStationaryTime(isStationaryTimeEnabled());
        updateEnterpriseWifiTime(isEnterpriseWifiTimeEnabled());
        updatePersonalWifiTime(isPersonalWifiTimeEnabled());
        updateCalendarAppEvent(isCalendarAppEventEnabled());
        updateEmailAppMessage(isEmailAppMessageEnabled());
        updateMessagesAppMessage(isMessagesAppMessageEnabled());
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
        mKnowledgeManager.deleteAll();
        mEmbeddingManager.deleteAll();
        mCalendarAppManager.deleteAll();
        mEmailAppManager.deleteAll();
        mMessagesAppManager.deleteAll();
        mPersistentLocationManager.deleteAll();
        mWifiConnectionTimeManager.deleteAll();
        mStationaryTimeManager.deleteAll();
        mEmbeddingManager.deleteAll();
        updateKnowledge();
    }

    @Override
    public List<String> findSimilarOnes(String query, String response) {
        Log.i(TAG, "find similar ones : " + query + ", " + response);

        String flattened = null;
        if (IS_SENTENCE_BASED) {
            flattened = response;
        } else {
            Map<String, Entity> entities =
                    mKnowledgeManager.parseEntitiesFromResponse(response);
            Log.i(TAG, "entities : " + entities);
            flattened = new Gson().toJson(entities);
        }

        Log.i(TAG, "flattened : " + flattened);
        List<String> result = mEmbeddingManager.findSimilarOnes(flattened, 0);
        Log.i(TAG, "result : " + Arrays.toString(result.toArray()));
        return result;
    }

    @Override
    public String getSchema() {
        return KnowledgeManager.SCHEMA;
    }

    @Override
    public LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheDay() {
        return mTheMostFrequentlyVisitedPlaceDuringTheDay;
    }

    @Override
    public LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheNight() {
        return mTheMostFrequentlyVisitedPlaceDuringTheNight;
    }

    @Override
    public LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheWeekend() {
        return mTheMostFrequentlyVisitedPlaceDuringTheWeekend;
    }

    @Override
    public LiveData<String> getTheMostFrequentStationaryTime() {
        return mTheMostFrequentStationaryTime;
    }

    @Override
    public LiveData<String> getTheMostFrequentEnterpriseWifiConnectionTime() {
        return mTheMostFrequentEnterpriseWifiConnectionTime;
    }

    @Override
    public LiveData<String> getTheMostFrequentPersonalWifiConnectionTime() {
        return mTheMostFrequentPersonalWifiConnectionTime;
    }

    @Override
    public LiveData<String> getTheMostRecentCalendarAppEvent() {
        return mTheMostRecentCalendarAppEvent;
    }

    @Override
    public LiveData<String> getTheMostRecentEmailAppMessage() {
        return mTheMostRecentEmailAppMessage;
    }

    @Override
    public LiveData<String> getTheMostRecentMessagesAppMessage() {
        return mTheMostRecentMessagesAppMessage;
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
    public boolean isCalendarAppEventEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_CALENDAR_APP_EVENT, true);
    }

    @Override
    public boolean isEmailAppMessageEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_EMAIL_APP_MESSAGE, true);
    }

    @Override
    public boolean isMessagesAppMessageEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_MESSAGES_APP_MESSAGE, true);
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
            return true;
        }
        return false;
    }

    @Override
    public boolean setNightLocationEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_NIGHT_LOCATION, enabled)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean setWeekendLocationEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_WEEKEND_LOCATION, enabled)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean setStationaryTimeEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_STATIONARY_TIME, enabled)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean setEnterpriseWifiTimeEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_ENTERPRISE_WIFI_TIME, enabled)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean setPersonalWifiTimeEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_PERSONAL_WIFI_TIME, enabled)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean setCalendarAppEventEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_CALENDAR_APP_EVENT, enabled)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean setEmailAppMessageEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_EMAIL_APP_MESSAGE, enabled)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean setMessagesAppMessageEnabled(boolean enabled) {
        if (setSharedPreferences(KEY_MESSAGES_APP_MESSAGE, enabled)) {
            return true;
        }
        return false;
    }

    private void updateDayLocation(boolean isChecked) {
        if (isChecked) {
            EmbeddingResultListener listener = new EmbeddingResultListener() {
                @Override
                public void onSuccess() {
                    String location = mEmbeddingManager.getEmbeddingByName(ENTITY_NAME_LOCATION_DURING_THE_DAY);
                    if (TextUtils.isEmpty(location)) {
                        location = getApplicationContext().getString(R.string.day_location_unavailable);
                    }
                    mTheMostFrequentlyVisitedPlaceDuringTheDay.postValue(location);
                    if (DEBUG) Log.i(TAG, "day location is updated to " + location);
                }
            };
            mPersistentLocationManager.update(0, listener);
        } else {
            String location = getApplicationContext().getString(R.string.day_location_unavailable);
            mTheMostFrequentlyVisitedPlaceDuringTheDay.postValue(location);
            if (DEBUG) Log.i(TAG, "day location is updated to " + location);
        }
    }

    private void updateNightLocation(boolean isChecked) {
        if (isChecked) {
            EmbeddingResultListener listener = new EmbeddingResultListener() {
                @Override
                public void onSuccess() {
                    String location = mEmbeddingManager.getEmbeddingByName(ENTITY_NAME_LOCATION_DURING_THE_NIGHT);
                    if (TextUtils.isEmpty(location)) {
                        location = getApplicationContext().getString(R.string.night_location_unavailable);
                    }
                    mTheMostFrequentlyVisitedPlaceDuringTheNight.postValue(location);
                    if (DEBUG) Log.i(TAG, "night location is updated to " + location);
                }
            };
            mPersistentLocationManager.update(1, listener);
        } else {
            String location = getApplicationContext().getString(R.string.night_location_unavailable);
            mTheMostFrequentlyVisitedPlaceDuringTheNight.postValue(location);
            if (DEBUG) Log.i(TAG, "night location is updated to " + location);
        }
    }

    private void updateWeekendLocation(boolean isChecked) {
        if (isChecked) {
            EmbeddingResultListener listener = new EmbeddingResultListener() {
                @Override
                public void onSuccess() {
                    String location = mEmbeddingManager.getEmbeddingByName(ENTITY_NAME_LOCATION_DURING_THE_WEEKEND);
                    if (TextUtils.isEmpty(location)) {
                        location = getApplicationContext().getString(R.string.weekend_location_unavailable);
                    }
                    mTheMostFrequentlyVisitedPlaceDuringTheWeekend.postValue(location);
                    if (DEBUG) Log.i(TAG, "weekend location is updated to " + location);
                }
            };
            mPersistentLocationManager.update(2, listener);
        } else {
            String location = getApplicationContext().getString(R.string.weekend_location_unavailable);
            mTheMostFrequentlyVisitedPlaceDuringTheWeekend.postValue(location);
            if (DEBUG) Log.i(TAG, "weekend location is updated to " + location);
        }
    }

    private void updateStationaryTime(boolean isChecked) {
        if (isChecked) {
            EmbeddingResultListener listener = new EmbeddingResultListener() {
                @Override
                public void onSuccess() {
                    String time = mEmbeddingManager.getEmbeddingByName(ENTITY_NAME_PERIOD_STATIONARY);
                    if (TextUtils.isEmpty(time)) {
                        time = getApplicationContext().getString(R.string.stationary_time_unavailable);
                    }
                    mTheMostFrequentStationaryTime.postValue(time);
                    if (DEBUG) Log.i(TAG, "stationary time is updated to " + time);
                }
            };
            mStationaryTimeManager.update(0, listener);
        } else {
            String time = getApplicationContext().getString(R.string.stationary_time_unavailable);
            mTheMostFrequentStationaryTime.postValue(time);
            if (DEBUG) Log.i(TAG, "stationary time is updated to " + time);
        }
    }

    private void updateEnterpriseWifiTime(boolean isChecked) {
        if (isChecked) {
            EmbeddingResultListener listener = new EmbeddingResultListener() {
                @Override
                public void onSuccess() {
                    String time = mEmbeddingManager.getEmbeddingByName(ENTITY_NAME_PERIOD_ENTERPRISE_WIFI_CONNECTION);
                    if (TextUtils.isEmpty(time)) {
                        time = getApplicationContext().getString(R.string.enterprise_wifi_time_unavailable);
                    }
                    mTheMostFrequentEnterpriseWifiConnectionTime.postValue(time);
                    if (DEBUG) Log.i(TAG, "enterprise wifi time is updated to " + time);
                }
            };
            mWifiConnectionTimeManager.update(0, listener);
        } else {
            String time = getApplicationContext().getString(R.string.enterprise_wifi_time_unavailable);
            mTheMostFrequentEnterpriseWifiConnectionTime.postValue(time);
            if (DEBUG) Log.i(TAG, "enterprise wifi time is updated to " + time);
        }
    }

    private void updatePersonalWifiTime(boolean isChecked) {
        if (isChecked) {
            EmbeddingResultListener listener = new EmbeddingResultListener() {
                @Override
                public void onSuccess() {
                    String time = mEmbeddingManager.getEmbeddingByName(ENTITY_NAME_PERIOD_PERSONAL_WIFI_CONNECTION);
                    if (TextUtils.isEmpty(time)) {
                        time = getApplicationContext().getString(R.string.personal_wifi_time_unavailable);
                    }
                    mTheMostFrequentPersonalWifiConnectionTime.postValue(time);
                    if (DEBUG) Log.i(TAG, "personal wifi time is updated to " + time);
                }
            };
            mWifiConnectionTimeManager.update(1, listener);
        } else {
            String time = getApplicationContext().getString(R.string.personal_wifi_time_unavailable);
            mTheMostFrequentPersonalWifiConnectionTime.postValue(time);
            if (DEBUG) Log.i(TAG, "personal wifi time is updated to " + time);
        }
    }

    private void updateCalendarAppEvent(boolean isChecked) {
        if (isChecked) {
            EmbeddingResultListener listener = new EmbeddingResultListener() {
                @Override
                public void onSuccess() {
                    String event = mEmbeddingManager.getEmbeddingByName(ENTITY_NAME_EVENT_IN_THE_CALENDAR_APP);
                    if (TextUtils.isEmpty(event)) {
                        event = getApplicationContext().getString(R.string.calendar_app_event_unavailable);
                    }
                    mTheMostRecentCalendarAppEvent.postValue(event);
                    if (DEBUG) Log.i(TAG, "calendar app event is updated to " + event);
                }
            };
            mCalendarAppManager.update(0, listener);
        } else {
            String event = getApplicationContext().getString(R.string.calendar_app_event_unavailable);
            mTheMostRecentCalendarAppEvent.postValue(event);
            if (DEBUG) Log.i(TAG, "calendar app event is updated to " + event);
        }
    }

    private void updateEmailAppMessage(boolean isChecked) {
        if (isChecked) {
            EmbeddingResultListener listener = new EmbeddingResultListener() {
                @Override
                public void onSuccess() {
                    String message = mEmbeddingManager.getEmbeddingByName(ENTITY_NAME_MESSAGE_IN_THE_EMAIL_APP);
                    if (TextUtils.isEmpty(message)) {
                        message = getApplicationContext().getString(R.string.email_app_message_unavailable);
                    }
                    mTheMostRecentEmailAppMessage.postValue(message);
                    if (DEBUG) Log.i(TAG, "email app message is updated to " + message);
                }
            };
            mEmailAppManager.update(1, listener);
        } else {
            String message = getApplicationContext().getString(R.string.email_app_message_unavailable);
            mTheMostRecentEmailAppMessage.postValue(message);
            if (DEBUG) Log.i(TAG, "email app event is updated to " + message);
        }
    }

    private void updateMessagesAppMessage(boolean isChecked) {
        if (isChecked) {
            EmbeddingResultListener listener = new EmbeddingResultListener() {
                @Override
                public void onSuccess() {
                    String message = mEmbeddingManager.getEmbeddingByName(ENTITY_NAME_MESSAGE_IN_THE_MESSAGES_APP);
                    if (TextUtils.isEmpty(message)) {
                        message = getApplicationContext().getString(R.string.messages_app_message_unavailable);
                    }
                    mTheMostRecentMessagesAppMessage.postValue(message);
                    if (DEBUG) Log.i(TAG, "messages app message is updated to " + message);
                }
            };
            mMessagesAppManager.update(1, listener);
        } else {
            String message = getApplicationContext().getString(R.string.messages_app_message_unavailable);
            mTheMostRecentMessagesAppMessage.postValue(message);
            if (DEBUG) Log.i(TAG, "messages app message is updated to " + message);
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
        mCalendarAppManager.startMonitoring();
        mEmailAppManager.startMonitoring();
        mMessagesAppManager.startMonitoring();
        mPersistentLocationManager.startMonitoring();
        mWifiConnectionTimeManager.startMonitoring();
        mStationaryTimeManager.startMonitoring();
        mHandler.removeCallbacksAndMessages(null);
        mUpdateCallback.run();
        mStarted = true;
        updateKnowledge();
    }

    private void stopMonitoring() {
        Log.i(TAG, "stopMonitoring " + mStarted);
        if (!mStarted) return;
        Toast.makeText(getApplicationContext(), "Service Stopped", Toast.LENGTH_SHORT).show();
        mStationaryTimeManager.stopMonitoring();
        mWifiConnectionTimeManager.stopMonitoring();
        mPersistentLocationManager.stopMonitoring();
        mMessagesAppManager.stopMonitoring();
        mEmailAppManager.stopMonitoring();
        mCalendarAppManager.stopMonitoring();
        mHandler.removeCallbacksAndMessages(null);
        mStarted = false;
    }
}
