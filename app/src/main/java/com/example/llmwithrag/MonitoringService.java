package com.example.llmwithrag;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
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

import java.util.List;

public class MonitoringService extends Service implements IMonitoringService {
    private static final String TAG = MonitoringService.class.getSimpleName();
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
    private boolean mStarted;

    public class LocalBinder extends Binder {
        IMonitoringService getService() {
            return MonitoringService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
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
            mTheMostFrequentlyVisitedPlaceDuringTheDay.postValue(
                    getTheMostFrequentlyVisitedPlaceDuringTheDayInternal());
            mTheMostFrequentlyVisitedPlaceDuringTheNight.postValue(
                    getTheMostFrequentlyVisitedPlaceDuringTheNightInternal());
            mTheMostFrequentlyVisitedPlaceDuringTheWeekend.postValue(
                    getTheMostFrequentlyVisitedPlaceDuringTheWeekendInternal());
            mTheMostFrequentStationaryTime.postValue(
                    getTheMostFrequentStationaryTimeInternal());
            mTheMostFrequentPublicWifiConnectionTime.postValue(
                    getTheMostFrequentPublicWifiConnectionTimeInternal());
        };
        mPersistentLocationManager = new PersistentLocationManager(context,
                new PersistentLocationRepository(context), new LocationTracker(context, looper));
        mPublicWifiUsageManager = new PublicWifiUsageManager(context,
                new PublicWifiUsageRepository(context), new ConnectivityTracker(context, looper));
        mStationaryTimeManager = new StationaryTimeManager(context,
                new StationaryTimeRepository(context), new MovementTracker(context, looper));
        mStarted = false;
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

    private String getTheMostFrequentPublicWifiConnectionTimeInternal() {
        String result = "";
        List<String> results = mPublicWifiUsageManager.getMostFrequentPublicWifiConnectionTimes(1);
        if (results != null && !results.isEmpty()) result = results.get(0);
        return result;
    }

    @Override
    public void startMonitoring() {
        Log.i(TAG, "startMonitoring " + mStarted);
        if (mStarted) return;
        Toast.makeText(getApplicationContext(), "Service Started", Toast.LENGTH_SHORT).show();
        mPersistentLocationManager.startMonitoring();
        mPublicWifiUsageManager.startMonitoring();
        mStationaryTimeManager.startMonitoring();
        mHandler.removeCallbacksAndMessages(null);
        mUpdateCallback.run();
        mStarted = true;
    }

    @Override
    public void stopMonitoring() {
        Log.i(TAG, "stopMonitoring " + mStarted);
        if (!mStarted) return;
        Toast.makeText(getApplicationContext(), "Service Stopped", Toast.LENGTH_SHORT).show();
        mStationaryTimeManager.stopMonitoring();
        mPublicWifiUsageManager.stopMonitoring();
        mPersistentLocationManager.stopMonitoring();
        mHandler.removeCallbacksAndMessages(null);
        mStarted = false;
    }

    @Override
    public boolean isStarted() {
        return mStarted;
    }
}
