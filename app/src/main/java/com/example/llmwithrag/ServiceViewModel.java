package com.example.llmwithrag;

import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_EVENT_IN_THE_CALENDAR_APP;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_LOCATION_DURING_THE_DAY;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_LOCATION_DURING_THE_NIGHT;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_LOCATION_DURING_THE_WEEKEND;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_MESSAGE_IN_THE_EMAIL_APP;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_MESSAGE_IN_THE_MESSAGES_APP;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_PERIOD_ENTERPRISE_WIFI_CONNECTION;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_PERIOD_PERSONAL_WIFI_CONNECTION;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_PERIOD_STATIONARY;
import static com.example.llmwithrag.knowledge.KnowledgeGenerator.TRACKER_CALENDAR;
import static com.example.llmwithrag.knowledge.KnowledgeGenerator.TRACKER_CONNECTIVITY;
import static com.example.llmwithrag.knowledge.KnowledgeGenerator.TRACKER_EMAIL;
import static com.example.llmwithrag.knowledge.KnowledgeGenerator.TRACKER_LOCATION;
import static com.example.llmwithrag.knowledge.KnowledgeGenerator.TRACKER_MESSAGES;
import static com.example.llmwithrag.knowledge.KnowledgeGenerator.TRACKER_MOVEMENT;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.llmwithrag.datasource.IDataSourceTracker;
import com.example.llmwithrag.datasource.apps.CalendarTracker;
import com.example.llmwithrag.datasource.apps.EmailTracker;
import com.example.llmwithrag.datasource.apps.MessagesTracker;
import com.example.llmwithrag.datasource.connectivity.ConnectivityTracker;
import com.example.llmwithrag.datasource.location.LocationTracker;
import com.example.llmwithrag.datasource.movement.MovementTracker;
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

import java.util.HashMap;
import java.util.Map;

public class ServiceViewModel extends AndroidViewModel {
    private static final String TAG = ServiceViewModel.class.getSimpleName();
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
    private final MutableLiveData<IMonitoringService> mService = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostFrequentlyVisitedPlaceDuringTheDay = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostFrequentlyVisitedPlaceDuringTheNight = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostFrequentlyVisitedPlaceDuringTheWeekend = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostFrequentStationaryTime = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostFrequentEnterpriseWifiConnectionTime = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostFrequentPersonalWifiConnectionTime = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostRecentCalendarAppEvent = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostRecentEmailAppMessage = new MutableLiveData<>();
    private final MutableLiveData<String> mTheMostRecentMessagesAppMessage = new MutableLiveData<>();
    private final Context mContext;
    private String mLastTheMostFrequentlyVisitedPlaceDuringTheDay;
    private String mLastTheMostFrequentlyVisitedPlaceDuringTheNight;
    private String mLastTheMostFrequentlyVisitedPlaceDuringTheWeekend;
    private String mLastTheMostFrequentStationaryTime;
    private String mLastTheMostFrequentEnterpriseWifiConnectionTime;
    private String mLastTheMostFrequentPersonalWifiConnectionTime;
    private String mLastTheMostRecentCalendarAppEvent;
    private String mLastTheMostRecentEmailAppMessage;
    private String mLastTheMostRecentMessagesAppMessage;

    public ServiceViewModel(Application application) {
        super(application);
        mContext = application.getApplicationContext();
        mLastTheMostFrequentlyVisitedPlaceDuringTheDay = null;
        mLastTheMostFrequentlyVisitedPlaceDuringTheNight = null;
        mLastTheMostFrequentlyVisitedPlaceDuringTheWeekend = null;
        mLastTheMostFrequentStationaryTime = null;
        mLastTheMostFrequentEnterpriseWifiConnectionTime = null;
        mLastTheMostFrequentPersonalWifiConnectionTime = null;
        mLastTheMostRecentCalendarAppEvent = null;
        mLastTheMostRecentEmailAppMessage = null;
        mLastTheMostRecentMessagesAppMessage = null;
    }

    public LiveData<IMonitoringService> getService() {
        return mService;
    }

    public void setService(IMonitoringService service) {
        mService.setValue(service);
        if (service != null) {
            EmbeddingManager embeddingManager = new EmbeddingManager(mContext);
            KnowledgeManager knowledgeManager = new KnowledgeManager(mContext);
            HandlerThread handlerThread = new HandlerThread(TAG);
            handlerThread.start();
            Looper looper = handlerThread.getLooper();
            service.setViewModel(this)
                    .setEmbeddingManager(embeddingManager)
                    .setKnowledgeManager(knowledgeManager)
                    .setLooper(looper);
            PersistentLocationRepository persistentLocationRepository =
                    new PersistentLocationRepository(mContext);
            WifiConnectionTimeRepository wifiConnectionTimeRepository =
                    new WifiConnectionTimeRepository(mContext);
            StationaryTimeRepository stationaryTimeRepository =
                    new StationaryTimeRepository(mContext);
            LocationTracker locationTracker = new LocationTracker(mContext, looper);
            ConnectivityTracker connectivityTracker = new ConnectivityTracker(mContext, looper);
            MovementTracker movementTracker = new MovementTracker(mContext, looper);

            Map<String, IDataSourceTracker> trackers = new HashMap<>();
            trackers.put(TRACKER_CALENDAR, new CalendarTracker(mContext));
            trackers.put(TRACKER_EMAIL, new EmailTracker(mContext));
            trackers.put(TRACKER_MESSAGES, new MessagesTracker(mContext));
            trackers.put(TRACKER_LOCATION, new LocationTracker(mContext, looper));
            trackers.put(TRACKER_CONNECTIVITY, new ConnectivityTracker(mContext, looper));
            trackers.put(TRACKER_MOVEMENT, new MovementTracker(mContext, looper));

            service.addKnowledge(ENTITY_NAME_EVENT_IN_THE_CALENDAR_APP,
                    new CalendarAppManager(trackers, mContext,
                            knowledgeManager, embeddingManager,
                            () -> updateCalendarAppEvent(isCalendarAppEventEnabled())));
            service.addKnowledge(ENTITY_NAME_MESSAGE_IN_THE_EMAIL_APP,
                    new EmailAppManager(trackers, mContext,
                            knowledgeManager, embeddingManager,
                            () -> updateEmailAppMessage(isEmailAppMessageEnabled())));
            service.addKnowledge(ENTITY_NAME_MESSAGE_IN_THE_MESSAGES_APP,
                    new MessagesAppManager(trackers, mContext,
                            knowledgeManager, embeddingManager,
                            () -> updateMessagesAppMessage(isMessagesAppMessageEnabled())));
            service.addKnowledge(ENTITY_NAME_LOCATION_DURING_THE_DAY,
                    new PersistentLocationManager(trackers, mContext,
                            knowledgeManager, embeddingManager,
                            persistentLocationRepository));
            service.addKnowledge(ENTITY_NAME_LOCATION_DURING_THE_NIGHT,
                    new PersistentLocationManager(trackers, mContext,
                            knowledgeManager, embeddingManager,
                            persistentLocationRepository));
            service.addKnowledge(ENTITY_NAME_LOCATION_DURING_THE_WEEKEND,
                    new PersistentLocationManager(trackers, mContext,
                            knowledgeManager, embeddingManager,
                            persistentLocationRepository));
            service.addKnowledge(ENTITY_NAME_PERIOD_ENTERPRISE_WIFI_CONNECTION,
                    new WifiConnectionTimeManager(trackers, mContext,
                            knowledgeManager, embeddingManager,
                            wifiConnectionTimeRepository));
            service.addKnowledge(ENTITY_NAME_PERIOD_PERSONAL_WIFI_CONNECTION,
                    new WifiConnectionTimeManager(trackers, mContext,
                            knowledgeManager, embeddingManager,
                            wifiConnectionTimeRepository));
            service.addKnowledge(ENTITY_NAME_PERIOD_STATIONARY,
                    new StationaryTimeManager(trackers, mContext,
                            knowledgeManager, embeddingManager,
                            stationaryTimeRepository, movementTracker));

            setServiceEnabled(isServiceEnabled());
        }
    }

    public String getLastTheMostFrequentlyVisitedPlaceDuringTheDay() {
        return mLastTheMostFrequentlyVisitedPlaceDuringTheDay;
    }

    public void setLastTheMostFrequentlyVisitedPlaceDuringTheDay(String text) {
        mLastTheMostFrequentlyVisitedPlaceDuringTheDay = text;
    }

    public String getLastTheMostFrequentlyVisitedPlaceDuringTheNight() {
        return mLastTheMostFrequentlyVisitedPlaceDuringTheNight;
    }

    public void setLastTheMostFrequentlyVisitedPlaceDuringTheNight(String text) {
        mLastTheMostFrequentlyVisitedPlaceDuringTheNight = text;
    }

    public String getLastTheMostFrequentlyVisitedPlaceDuringTheWeekend() {
        return mLastTheMostFrequentlyVisitedPlaceDuringTheWeekend;
    }

    public void setLastTheMostFrequentlyVisitedPlaceDuringTheWeekend(String text) {
        mLastTheMostFrequentlyVisitedPlaceDuringTheWeekend = text;
    }

    public String getLastTheMostFrequentStationaryTime() {
        return mLastTheMostFrequentStationaryTime;
    }

    public void setLastTheMostFrequentStationaryTime(String text) {
        mLastTheMostFrequentStationaryTime = text;
    }

    public String getLastTheMostFrequentEnterpriseWifiConnectionTime() {
        return mLastTheMostFrequentEnterpriseWifiConnectionTime;
    }

    public void setLastTheMostFrequentEnterpriseWifiConnectionTime(String text) {
        mLastTheMostFrequentEnterpriseWifiConnectionTime = text;
    }

    public String getLastTheMostFrequentPersonalWifiConnectionTime() {
        return mLastTheMostFrequentPersonalWifiConnectionTime;
    }

    public void setLastTheMostFrequentPersonalWifiConnectionTime(String text) {
        mLastTheMostFrequentPersonalWifiConnectionTime = text;
    }


    public String getLastTheMostRecentCalendarAppEvent() {
        return mLastTheMostRecentCalendarAppEvent;
    }

    public void setLastTheMostRecentCalendarAppEvent(String text) {
        mLastTheMostRecentCalendarAppEvent = text;
    }

    public String getLastTheMostRecentEmailAppMessage() {
        return mLastTheMostRecentEmailAppMessage;
    }

    public void setLastTheMostRecentEmailAppMessage(String text) {
        mLastTheMostRecentEmailAppMessage = text;
    }

    public String getLastTheMostRecentMessagesAppMessage() {
        return mLastTheMostRecentMessagesAppMessage;
    }

    public void setLastTheMostRecentMessagesAppMessage(String text) {
        mLastTheMostRecentMessagesAppMessage = text;
    }

    public LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheDay() {
        return mTheMostFrequentlyVisitedPlaceDuringTheDay;
    }

    public LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheNight() {
        return mTheMostFrequentlyVisitedPlaceDuringTheNight;
    }

    public LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheWeekend() {
        return mTheMostFrequentlyVisitedPlaceDuringTheWeekend;
    }

    public LiveData<String> getTheMostFrequentStationaryTime() {
        return mTheMostFrequentStationaryTime;
    }

    public LiveData<String> getTheMostFrequentEnterpriseWifiConnectionTime() {
        return mTheMostFrequentEnterpriseWifiConnectionTime;
    }

    public LiveData<String> getTheMostFrequentPersonalWifiConnectionTime() {
        return mTheMostFrequentPersonalWifiConnectionTime;
    }

    public LiveData<String> getTheMostRecentCalendarAppEvent() {
        return mTheMostRecentCalendarAppEvent;
    }

    public LiveData<String> getTheMostRecentEmailAppMessage() {
        return mTheMostRecentEmailAppMessage;
    }

    public LiveData<String> getTheMostRecentMessagesAppMessage() {
        return mTheMostRecentMessagesAppMessage;
    }

    public boolean isServiceEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, false);
    }

    public boolean isDayLocationEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_DAY_LOCATION, true);
    }

    public boolean isNightLocationEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_NIGHT_LOCATION, true);
    }

    public boolean isWeekendLocationEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_WEEKEND_LOCATION, true);
    }

    public boolean isStationaryTimeEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_STATIONARY_TIME, true);
    }

    public boolean isEnterpriseWifiTimeEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_ENTERPRISE_WIFI_TIME, true);
    }

    public boolean isPersonalWifiTimeEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_PERSONAL_WIFI_TIME, true);
    }

    public boolean isCalendarAppEventEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_CALENDAR_APP_EVENT, true);
    }

    public boolean isEmailAppMessageEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_EMAIL_APP_MESSAGE, true);
    }

    public boolean isMessagesAppMessageEnabled() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_MESSAGES_APP_MESSAGE, true);
    }

    public boolean setServiceEnabled(boolean enabled) {
        Log.i(TAG, "service is " + ((enabled) ? "enabled" : "disabled"));
        if (setSharedPreferences(KEY_SERVICE_ENABLED, enabled)) {
            IMonitoringService service = mService.getValue();
            if (service == null) {
                Log.e(TAG, "service is null");
                return false;
            }
            if (enabled) service.startMonitoring();
            else service.stopMonitoring();
            return true;
        }
        return false;
    }

    public boolean setDayLocationEnabled(boolean enabled) {
        Log.i(TAG, "set day location enabled to " + enabled);
        if (setSharedPreferences(KEY_DAY_LOCATION, enabled)) {
            IMonitoringService service = mService.getValue();
            if (service == null) {
                Log.e(TAG, "service is null");
                return false;
            }
            if (!enabled) service.delete(ENTITY_NAME_LOCATION_DURING_THE_DAY);
            updateDayLocation(enabled);
            return true;
        }
        return false;
    }

    public boolean setNightLocationEnabled(boolean enabled) {
        Log.i(TAG, "set night location enabled to " + enabled);
        if (setSharedPreferences(KEY_NIGHT_LOCATION, enabled)) {
            IMonitoringService service = mService.getValue();
            if (service == null) {
                Log.e(TAG, "service is null");
                return false;
            }
            if (!enabled) service.delete(ENTITY_NAME_LOCATION_DURING_THE_NIGHT);
            updateNightLocation(enabled);
            return true;
        }
        return false;
    }

    public boolean setWeekendLocationEnabled(boolean enabled) {
        Log.i(TAG, "set weekend location enabled to " + enabled);
        if (setSharedPreferences(KEY_WEEKEND_LOCATION, enabled)) {
            IMonitoringService service = mService.getValue();
            if (service == null) {
                Log.e(TAG, "service is null");
                return false;
            }
            if (!enabled) service.delete(ENTITY_NAME_LOCATION_DURING_THE_WEEKEND);
            updateWeekendLocation(enabled);
            return true;
        }
        return false;
    }

    public boolean setStationaryTimeEnabled(boolean enabled) {
        Log.i(TAG, "set stationary time enabled to " + enabled);
        if (setSharedPreferences(KEY_STATIONARY_TIME, enabled)) {
            IMonitoringService service = mService.getValue();
            if (service == null) {
                Log.e(TAG, "service is null");
                return false;
            }
            if (!enabled) service.delete(ENTITY_NAME_PERIOD_STATIONARY);
            updateStationaryTime(isStationaryTimeEnabled());
            return true;
        }
        return false;
    }

    public boolean setEnterpriseWifiTimeEnabled(boolean enabled) {
        Log.i(TAG, "set enterprise wifi time enabled to " + enabled);
        if (setSharedPreferences(KEY_ENTERPRISE_WIFI_TIME, enabled)) {
            IMonitoringService service = mService.getValue();
            if (service == null) {
                Log.e(TAG, "service is null");
                return false;
            }
            if (!enabled) service.delete(ENTITY_NAME_PERIOD_ENTERPRISE_WIFI_CONNECTION);
            updateEnterpriseWifiTime(isEnterpriseWifiTimeEnabled());
            return true;
        }
        return false;
    }

    public boolean setPersonalWifiTimeEnabled(boolean enabled) {
        Log.i(TAG, "set personal wifi time enabled to " + enabled);
        if (setSharedPreferences(KEY_PERSONAL_WIFI_TIME, enabled)) {
            IMonitoringService service = mService.getValue();
            if (service == null) {
                Log.e(TAG, "service is null");
                return false;
            }
            if (!enabled) service.delete(ENTITY_NAME_PERIOD_PERSONAL_WIFI_CONNECTION);
            updatePersonalWifiTime(isPersonalWifiTimeEnabled());
            return true;
        }
        return false;
    }

    public boolean setCalendarAppEventEnabled(boolean enabled) {
        Log.i(TAG, "set calendar app event enabled to " + enabled);
        if (setSharedPreferences(KEY_CALENDAR_APP_EVENT, enabled)) {
            IMonitoringService service = mService.getValue();
            if (service == null) {
                Log.e(TAG, "service is null");
                return false;
            }
            if (!enabled) service.delete(ENTITY_NAME_EVENT_IN_THE_CALENDAR_APP);
            updateCalendarAppEvent(isCalendarAppEventEnabled());
            return true;
        }
        return false;
    }

    public boolean setEmailAppMessageEnabled(boolean enabled) {
        Log.i(TAG, "set email app message enabled to " + enabled);
        if (setSharedPreferences(KEY_EMAIL_APP_MESSAGE, enabled)) {
            IMonitoringService service = mService.getValue();
            if (service == null) {
                Log.e(TAG, "service is null");
                return false;
            }
            if (!enabled) service.delete(ENTITY_NAME_MESSAGE_IN_THE_EMAIL_APP);
            updateEmailAppMessage(isEmailAppMessageEnabled());
            return true;
        }
        return false;
    }

    public boolean setMessagesAppMessageEnabled(boolean enabled) {
        Log.i(TAG, "set messages app message enabled to " + enabled);
        if (setSharedPreferences(KEY_MESSAGES_APP_MESSAGE, enabled)) {
            IMonitoringService service = mService.getValue();
            if (service == null) {
                Log.e(TAG, "service is null");
                return false;
            }
            if (!enabled) service.delete(ENTITY_NAME_MESSAGE_IN_THE_MESSAGES_APP);
            updateMessagesAppMessage(isMessagesAppMessageEnabled());
            return true;
        }
        return false;
    }

    private void updateDayLocation(boolean enabled) {
        IMonitoringService service = mService.getValue();
        if (service == null) {
            Log.e(TAG, "service is null");
            return;
        }
        service.update(ENTITY_NAME_LOCATION_DURING_THE_DAY, 0, enabled,
                value -> {
                    if (TextUtils.isEmpty(value)) {
                        value = mContext.getString(R.string.day_location_unavailable);
                    }
                    mTheMostFrequentlyVisitedPlaceDuringTheDay.postValue(value);
                    Log.i(TAG, "day location is updated to " + value);
                });
    }

    private void updateNightLocation(boolean enabled) {
        IMonitoringService service = mService.getValue();
        if (service == null) {
            Log.e(TAG, "service is null");
            return;
        }
        service.update(ENTITY_NAME_LOCATION_DURING_THE_NIGHT, 1, enabled,
                value -> {
                    if (TextUtils.isEmpty(value)) {
                        value = mContext.getString(R.string.night_location_unavailable);
                    }
                    mTheMostFrequentlyVisitedPlaceDuringTheNight.postValue(value);
                    Log.i(TAG, "night location is updated to " + value);
                });
    }

    private void updateWeekendLocation(boolean enabled) {
        IMonitoringService service = mService.getValue();
        if (service == null) {
            Log.e(TAG, "service is null");
            return;
        }
        service.update(ENTITY_NAME_LOCATION_DURING_THE_WEEKEND, 2, enabled,
                value -> {
                    if (TextUtils.isEmpty(value)) {
                        value = mContext.getString(R.string.weekend_location_unavailable);
                    }
                    mTheMostFrequentlyVisitedPlaceDuringTheWeekend.postValue(value);
                    Log.i(TAG, "weekend location is updated to " + value);
                });
    }

    private void updateStationaryTime(boolean enabled) {
        IMonitoringService service = mService.getValue();
        if (service == null) {
            Log.e(TAG, "service is null");
            return;
        }
        service.update(ENTITY_NAME_PERIOD_STATIONARY, 0, enabled,
                value -> {
                    if (TextUtils.isEmpty(value)) {
                        value = mContext.getString(R.string.stationary_time_unavailable);
                    }
                    mTheMostFrequentStationaryTime.postValue(value);
                    Log.i(TAG, "stationary time is updated to " + value);
                });
    }

    private void updateEnterpriseWifiTime(boolean enabled) {
        IMonitoringService service = mService.getValue();
        if (service == null) {
            Log.e(TAG, "service is null");
            return;
        }
        service.update(ENTITY_NAME_PERIOD_ENTERPRISE_WIFI_CONNECTION, 0, enabled,
                value -> {
                    if (TextUtils.isEmpty(value)) {
                        value = mContext.getString(R.string.enterprise_wifi_time_unavailable);
                    }
                    mTheMostFrequentEnterpriseWifiConnectionTime.postValue(value);
                    Log.i(TAG, "enterprise wifi time is updated to " + value);
                });
    }

    private void updatePersonalWifiTime(boolean enabled) {
        IMonitoringService service = mService.getValue();
        if (service == null) {
            Log.e(TAG, "service is null");
            return;
        }
        service.update(ENTITY_NAME_PERIOD_PERSONAL_WIFI_CONNECTION, 0, enabled,
                value -> {
                    if (TextUtils.isEmpty(value)) {
                        value = mContext.getString(R.string.personal_wifi_time_unavailable);
                    }
                    mTheMostFrequentPersonalWifiConnectionTime.postValue(value);
                    Log.i(TAG, "personal wifi time is updated to " + value);
                });
    }

    private void updateCalendarAppEvent(boolean enabled) {
        IMonitoringService service = mService.getValue();
        if (service == null) {
            Log.e(TAG, "service is null");
            return;
        }
        service.update(ENTITY_NAME_EVENT_IN_THE_CALENDAR_APP, 0, enabled,
                value -> {
                    if (TextUtils.isEmpty(value)) {
                        value = mContext.getString(R.string.calendar_app_event_unavailable);
                    }
                    mTheMostRecentCalendarAppEvent.postValue(value);
                    Log.i(TAG, "calendar app event is updated to " + value);
                });
    }

    private void updateEmailAppMessage(boolean enabled) {
        IMonitoringService service = mService.getValue();
        if (service == null) {
            Log.e(TAG, "service is null");
            return;
        }
        service.update(ENTITY_NAME_MESSAGE_IN_THE_EMAIL_APP, 0, enabled,
                value -> {
                    if (TextUtils.isEmpty(value)) {
                        value = mContext.getString(R.string.email_app_message_unavailable);
                    }
                    mTheMostRecentEmailAppMessage.postValue(value);
                    Log.i(TAG, "email app message is updated to " + value);
                });
    }

    private void updateMessagesAppMessage(boolean enabled) {
        IMonitoringService service = mService.getValue();
        if (service == null) {
            Log.e(TAG, "service is null");
            return;
        }
        service.update(ENTITY_NAME_MESSAGE_IN_THE_MESSAGES_APP, 0, enabled,
                value -> {
                    if (TextUtils.isEmpty(value)) {
                        value = mContext.getString(R.string.messages_app_message_unavailable);
                    }
                    mTheMostRecentMessagesAppMessage.postValue(value);
                    Log.i(TAG, "messages app message is updated to " + value);
                });
    }

    public void update() {
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

    private SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(ServiceViewModel.NAME_SHARED_PREFS, Context.MODE_PRIVATE);
    }

    private boolean setSharedPreferences(String key, boolean value) {
        SharedPreferences sharedPreferences = getSharedPreferences();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
        return true;
    }

    public interface IResultListener {
        void postValue(String value);
    }
}
