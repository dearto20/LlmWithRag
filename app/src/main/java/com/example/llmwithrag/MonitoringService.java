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
    private static final boolean DEBUG = false;
    private static final int ID_NOTIFICATION = 1;
    private static final String ID_MAIN_CHANNEL = "001";
    private static final long DELAY_PERIODIC_CHECK = 30000L;
    private final IBinder mBinder = new LocalBinder();
    private PersistentLocationManager mPersistentLocationManager;
    private PublicWifiUsageManager mPublicWifiUsageManager;
    private StationaryTimeManager mStationaryTimeManager;
    private String mDayLocation;
    private String mNightLocation;
    private String mWeekendLocation;
    private String mStationaryTime;
    private String mPublicWifiTime;
    private Handler mHandler;
    private Runnable mCheckRunnable;
    private boolean mDayLocationEnabled;
    private boolean mNightLocationEnabled;
    private boolean mWeekendLocationEnabled;
    private boolean mStationaryTimeEnabled;
    private boolean mPublicWifiTimeEnabled;
    private boolean mStarted;

    public class LocalBinder extends Binder {
        IMonitoringService getService() {
            return MonitoringService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Toast.makeText(getApplicationContext(), "Service Created", Toast.LENGTH_SHORT).show();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_NOTIFICATION, getNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(ID_NOTIFICATION, getNotification());
        }

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        mHandler = new Handler(looper);
        mCheckRunnable = () -> {
            mHandler.postDelayed(mCheckRunnable, DELAY_PERIODIC_CHECK);
            updateKnowledge();
        };

        Context context = getApplicationContext();
        mPersistentLocationManager = new PersistentLocationManager(context,
                new PersistentLocationRepository(context), new LocationTracker(context, looper));
        mPublicWifiUsageManager = new PublicWifiUsageManager(context,
                new PublicWifiUsageRepository(context), new ConnectivityTracker(context, looper));
        mStationaryTimeManager = new StationaryTimeManager(context,
                new StationaryTimeRepository(context), new MovementTracker(context, looper));
        mDayLocation = "";
        mNightLocation = "";
        mWeekendLocation = "";
        mStationaryTime = "";
        mPublicWifiTime = "";
        mDayLocationEnabled = false;
        mNightLocationEnabled = false;
        mWeekendLocationEnabled = false;
        mStationaryTimeEnabled = false;
        mPublicWifiTimeEnabled = false;
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
    public String getMostFrequentlyVisitedPlaceDuringTheDay() {
        return mDayLocation;
    }

    @Override
    public String getMostFrequentlyVisitedPlaceDuringTheNight() {
        return mNightLocation;
    }

    @Override
    public String getMostFrequentlyVisitedPlaceDuringTheWeekend() {
        return mWeekendLocation;
    }

    @Override
    public String getMostFrequentStationaryTime() {
        return mStationaryTime;
    }

    @Override
    public String getMostFrequentPublicWifiConnectionTime() {
        return mPublicWifiTime;
    }

    @Override
    public void setDayLocationEnabled(boolean enabled) {
        Log.i(TAG, "set day location to " + enabled);
        mDayLocationEnabled = enabled;
    }

    @Override
    public void setNightLocationEnabled(boolean enabled) {
        Log.i(TAG, "set night location to " + enabled);
        mNightLocationEnabled = enabled;
    }

    @Override
    public void setWeekendLocationEnabled(boolean enabled) {
        Log.i(TAG, "set weekend location to " + enabled);
        mWeekendLocationEnabled = enabled;
    }

    @Override
    public void setStationaryTimeEnabled(boolean enabled) {
        Log.i(TAG, "set stationary time to " + enabled);
        mStationaryTimeEnabled = enabled;
    }

    @Override
    public void setPublicWifiTimeEnabled(boolean enabled) {
        Log.i(TAG, "set public wifi time enabled to " + enabled);
        mPublicWifiTimeEnabled = enabled;
    }

    @Override
    public void startMonitoring() {
        if (mStarted) return;
        Toast.makeText(getApplicationContext(), "Service Started", Toast.LENGTH_SHORT).show();
        mPersistentLocationManager.startMonitoring();
        mPublicWifiUsageManager.startMonitoring();
        mStationaryTimeManager.startMonitoring();
        mHandler.postDelayed(mCheckRunnable, DELAY_PERIODIC_CHECK);
        mStarted = true;
    }

    @Override
    public void stopMonitoring() {
        if (!mStarted) return;
        Toast.makeText(getApplicationContext(), "Service Stopped", Toast.LENGTH_SHORT).show();
        mStationaryTimeManager.stopMonitoring();
        mPublicWifiUsageManager.stopMonitoring();
        mPersistentLocationManager.stopMonitoring();
        mHandler.removeCallbacksAndMessages(null);
        mDayLocation = "";
        mNightLocation = "";
        mWeekendLocation = "";
        mStationaryTime = "";
        mPublicWifiTime = "";
        mStarted = false;
    }

    @Override
    public boolean isStarted() {
        return mStarted;
    }

    private void updateKnowledge() {
        try {
            Log.i(TAG, "update knowledge");
            updateDayLocation();
            updateNightLocation();
            updateWeekendLocation();
            updateStationaryTime();
            updatePublicWifiTime();
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    private void updateDayLocation() {
        mDayLocation = "";
        if (mDayLocationEnabled) {
            List<String> results = getMostFrequentlyVisitedPlacesDuringTheDay(1);
            if (!results.isEmpty()) mDayLocation = results.get(0);
        }
    }

    private void updateNightLocation() {
        mNightLocation = "";
        if (mNightLocationEnabled) {
            List<String> results = getMostFrequentlyVisitedPlacesDuringTheNight(1);
            if (!results.isEmpty()) mNightLocation = results.get(0);
        }
    }

    private void updateWeekendLocation() {
        mWeekendLocation = "";
        if (mWeekendLocationEnabled) {
            List<String> results = getMostFrequentlyVisitedPlacesDuringTheWeekend(1);
            if (!results.isEmpty()) mWeekendLocation = results.get(0);
        }
    }

    private void updateStationaryTime() {
        mStationaryTime = "";
        if (mStationaryTimeEnabled) {
            List<String> results = getMostFrequentStationaryTimes(1);
            if (!results.isEmpty()) mStationaryTime = results.get(0);
        }
    }

    private void updatePublicWifiTime() {
        mPublicWifiTime = "";
        if (mPublicWifiTimeEnabled) {
            List<String> results = getMostFrequentPublicWifiConnectionTimes(1);
            if (!results.isEmpty()) mPublicWifiTime = results.get(0);
        }
    }

    private List<String> getMostFrequentlyVisitedPlacesDuringTheDay(int topN) {
        return mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheDay(topN);
    }

    private List<String> getMostFrequentlyVisitedPlacesDuringTheNight(int topN) {
        return mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheNight(topN);
    }

    private List<String> getMostFrequentlyVisitedPlacesDuringTheWeekend(int topN) {
        return mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheWeekend(topN);
    }

    private List<String> getMostFrequentStationaryTimes(int topN) {
        return mStationaryTimeManager.getMostFrequentStationaryTimes(topN);
    }

    private List<String> getMostFrequentPublicWifiConnectionTimes(int topN) {
        return mPublicWifiUsageManager.getMostFrequentPublicWifiConnectionTimes(topN);
    }
}
