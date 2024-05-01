package com.example.llmwithrag;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.llmwithrag.datasource.connectivity.ConnectivityTracker;
import com.example.llmwithrag.datasource.location.LocationTracker;
import com.example.llmwithrag.datasource.movement.MovementTracker;
import com.example.llmwithrag.knowledge.connectivity.PublicWifiUsageManager;
import com.example.llmwithrag.knowledge.location.PersistentLocationManager;
import com.example.llmwithrag.knowledge.status.StationaryPeriodManager;

import java.util.List;
import java.util.Random;

public class MonitoringService extends Service implements IMonitoringService {
    private static final String TAG = MonitoringService.class.getSimpleName();
    private static final int ID_NOTIFICATION = 1;
    private static final String ID_MAIN_CHANNEL = "001";
    private final IBinder binder = new LocalBinder();
    private PersistentLocationManager mPersistentLocationManager;
    private PublicWifiUsageManager mPublicWifiUsageManager;
    private StationaryPeriodManager mStationaryPeriodManager;
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
        startForeground(ID_NOTIFICATION, getNotification());

        Context context = getApplicationContext();
        mPersistentLocationManager = new PersistentLocationManager(new LocationTracker(context));
        mPublicWifiUsageManager = new PublicWifiUsageManager(new ConnectivityTracker(context));
        mStationaryPeriodManager = new StationaryPeriodManager(new MovementTracker(context));
        mStarted = false;
    }

    private Notification getNotification() {
        createNotificationChannel(ID_MAIN_CHANNEL, "main", NotificationManager.IMPORTANCE_DEFAULT);
        return createNotification(ID_MAIN_CHANNEL, "Galaxy AutoNav", "Running...");
    }

    private void postNotification(String channelId, String title, String content) {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        Notification notification = createNotification(channelId, title, content);
        notificationManager.notify(new Random().nextInt(), notification);
    }

    private void createNotificationChannel(String channelId, String channelName, int importance) {
        NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification createNotification(String channelId, String title, String content) {
        return new Notification.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void startMonitoring() {
        if (mStarted) return;
        Toast.makeText(getApplicationContext(), "Service Started", Toast.LENGTH_SHORT).show();
        mPersistentLocationManager.startMonitoring();
        mPublicWifiUsageManager.startMonitoring();
        mStationaryPeriodManager.startMonitoring();
        mStarted = true;
    }

    @Override
    public void stopMonitoring() {
        if (!mStarted) return;
        Toast.makeText(getApplicationContext(), "Service Stopped", Toast.LENGTH_SHORT).show();
        mStationaryPeriodManager.stopMonitoring();
        mPublicWifiUsageManager.stopMonitoring();
        mPersistentLocationManager.stopMonitoring();
        mStarted = false;
    }

    @Override
    public List<String> getMostFrequentPublicWifiConnectionTimes(int topN) {
        return mPublicWifiUsageManager.getMostFrequentPublicWifiConnectionTimes(topN);
    }

    @Override
    public List<String> getMostFrequentStationaryTimes(int topN) {
        return mStationaryPeriodManager.getMostFrequentStationaryTimes(topN);
    }

    @Override
    public List<String> getMostFrequentlyVisitedPlacesDuringTheDay(int topN) {
        return mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheDay(topN);
    }

    @Override
    public List<String> getMostFrequentlyVisitedPlacesDuringTheNight(int topN) {
        return mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheNight(topN);
    }

    @Override
    public List<String> getMostFrequentlyVisitedPlacesDuringTheWeekend(int topN) {
        return mPersistentLocationManager.getMostFrequentlyVisitedPlacesDuringTheWeekend(topN);
    }

    @Override
    public void deleteAll() {
        mPersistentLocationManager.deleteAll();
        mPublicWifiUsageManager.deleteAll();
        mStationaryPeriodManager.deleteAll();
    }
}
