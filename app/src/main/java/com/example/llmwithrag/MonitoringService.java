package com.example.llmwithrag;

import static com.example.llmwithrag.BuildConfig.IS_SENTENCE_BASED;

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
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeManager;
import com.example.llmwithrag.knowledge.KnowledgeGenerator;
import com.example.llmwithrag.llm.EmbeddingManager;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private KnowledgeManager mKnowledgeManager;
    private EmbeddingManager mEmbeddingManager;
    private ServiceViewModel mViewModel;
    private final Map<String, KnowledgeGenerator> mKnowledgeGenerators = new HashMap<>();
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

        mHandler = null;
        mCurrentDelay = -1;
        mUpdateCallback = null;
        mEmbeddingManager = null;
        mKnowledgeManager = null;
        mViewModel = null;
        mStarted = false;
    }

    private void updateKnowledge() {
        mViewModel.update();
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
    public IMonitoringService setKnowledgeManager(KnowledgeManager knowledgeManager) {
        mKnowledgeManager = knowledgeManager;
        return this;
    }

    @Override
    public IMonitoringService setLooper(Looper looper) {
        mHandler = new Handler(looper);
        mCurrentDelay = MIN_DELAY_PERIODIC_UPDATE;
        mUpdateCallback = () -> {
            mHandler.postDelayed(mUpdateCallback, mCurrentDelay);
            mCurrentDelay = Math.min(mCurrentDelay + MIN_DELAY_PERIODIC_UPDATE,
                    MAX_DELAY_PERIODIC_UPDATE);
            Log.i(TAG, "run periodic update");
            updateKnowledge();
        };
        return this;
    }

    @Override
    public IMonitoringService setEmbeddingManager(EmbeddingManager embeddingManager) {
        mEmbeddingManager = embeddingManager;
        return this;
    }

    @Override
    public IMonitoringService addKnowledge(String name, KnowledgeGenerator component) {
        mKnowledgeGenerators.put(name, component);
        return this;
    }

    @Override
    public void delete(String name) {
        Objects.requireNonNull(mKnowledgeGenerators.get(name)).deleteAll();
    }

    @Override
    public void deleteAll() {
        mKnowledgeManager.deleteAll();
        mEmbeddingManager.deleteAll();
        for (Map.Entry<String, KnowledgeGenerator> entry : mKnowledgeGenerators.entrySet()) {
            entry.getValue().deleteAll();
        }

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
    public IMonitoringService setViewModel(ServiceViewModel viewModel) {
        mViewModel = viewModel;
        return this;
    }

    @Override
    public void update(String name, int type, boolean enabled,
                       ServiceViewModel.IResultListener resultListener) {
        if (enabled) {
            EmbeddingResultListener listener = new EmbeddingResultListener() {
                @Override
                public void onSuccess() {
                    resultListener.postValue(mEmbeddingManager.getEmbeddingByName(name));
                }
            };
            Objects.requireNonNull(
                    mKnowledgeGenerators.get(name)).update(type, listener);
        } else {
            resultListener.postValue("");
        }
    }

    @Override
    public void startMonitoring() {
        Log.i(TAG, "startMonitoring " + mStarted);
        if (mStarted) {
            stopMonitoring();
        }
        if (DEBUG) {
            Toast.makeText(getApplicationContext(), "Service Started", Toast.LENGTH_SHORT).show();
        }

        for (Map.Entry<String, KnowledgeGenerator> entry : mKnowledgeGenerators.entrySet()) {
            entry.getValue().startMonitoring();
        }

        mHandler.removeCallbacksAndMessages(null);
        mUpdateCallback.run();
        mStarted = true;
        updateKnowledge();
    }

    @Override
    public void stopMonitoring() {
        Log.i(TAG, "stopMonitoring " + mStarted);
        if (!mStarted) return;
        if (DEBUG) {
            Toast.makeText(getApplicationContext(), "Service Stopped", Toast.LENGTH_SHORT).show();
        }

        for (Map.Entry<String, KnowledgeGenerator> entry : mKnowledgeGenerators.entrySet()) {
            entry.getValue().stopMonitoring();
        }

        mHandler.removeCallbacksAndMessages(null);
        mStarted = false;
    }
}
