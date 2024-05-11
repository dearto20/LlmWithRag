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
    private final IBinder mBinder = new LocalBinder();
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
    public String getMostFrequentlyVisitedPlaceDuringTheDay() {
        String result = "";
        List<String> results = mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheDay(1);
        if (results != null && !results.isEmpty()) result = results.get(0);
        return result;
    }

    @Override
    public String getMostFrequentlyVisitedPlaceDuringTheNight() {
        String result = "";
        List<String> results = mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheNight(1);
        if (results != null && !results.isEmpty()) result = results.get(0);
        return result;
    }

    @Override
    public String getMostFrequentlyVisitedPlaceDuringTheWeekend() {
        String result = "";
        List<String> results = mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheWeekend(1);
        if (results != null && !results.isEmpty()) result = results.get(0);
        return result;
    }

    @Override
    public String getMostFrequentStationaryTime() {
        String result = "";
        List<String> results = mStationaryTimeManager.getMostFrequentStationaryTimes(1);
        if (results != null && !results.isEmpty()) result = results.get(0);
        return result;
    }

    @Override
    public String getMostFrequentPublicWifiConnectionTime() {
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
        mStarted = false;
    }

    @Override
    public boolean isStarted() {
        return mStarted;
    }
}
