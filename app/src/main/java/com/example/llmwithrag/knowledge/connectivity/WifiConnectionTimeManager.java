package com.example.llmwithrag.knowledge.connectivity;

import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_PERIOD_ENTERPRISE_WIFI_CONNECTION;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_PERIOD_PERSONAL_WIFI_CONNECTION;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_PERIOD;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.example.llmwithrag.MonitoringService;
import com.example.llmwithrag.datasource.connectivity.ConnectivityData;
import com.example.llmwithrag.datasource.connectivity.ConnectivityTracker;
import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeManager;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;
import com.example.llmwithrag.llm.EmbeddingManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WifiConnectionTimeManager implements IKnowledgeComponent {
    private static final String TAG = WifiConnectionTimeManager.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final int NUMBER_OF_TYPES = 2;
    private static final String[] KEY_CONNECTION_TIME = {"connection_0_time", "connection_1_time"};
    private static final String[] KEY_CONNECTION_DURATION = {"connection_0_duration", "connection_1_duration"};
    private static final long MIN_DURATION = 600000L;
    private final Context mContext;
    private final KnowledgeManager mKnowledgeManager;
    private final EmbeddingManager mEmbeddingManager;
    private final WifiConnectionTimeRepository mRepository;

    private final ConnectivityTracker mConnectivityTracker;
    private boolean[] mIsConnected;
    private long[] mStartTime;
    private long[] mCheckTime;

    public WifiConnectionTimeManager(Context context,
                                     KnowledgeManager knowledgeManager,
                                     EmbeddingManager embeddingManager,
                                     WifiConnectionTimeRepository wifiConnectionTimeRepository,
                                     ConnectivityTracker connectivityTracker) {
        mContext = context;
        mKnowledgeManager = knowledgeManager;
        mEmbeddingManager = embeddingManager;
        mRepository = wifiConnectionTimeRepository;
        mConnectivityTracker = connectivityTracker;
    }

    private void initialize() {
        long startTime = System.currentTimeMillis();
        mIsConnected = new boolean[2];
        mStartTime = new long[2];
        mCheckTime = new long[2];
        for (int i = 0; i < NUMBER_OF_TYPES; i++) {
            mIsConnected[i] = false;
            mStartTime[i] = startTime;
            mCheckTime[i] = 0;
        }
    }

    public List<String> getMostFrequentEnterpriseWifiConnectionTimes(int topN) {
        Predicate<ConnectivityData> isEnterprise = connectivityData -> connectivityData.enterprise;
        return getMostFrequentWifiConnectionTimes(0, isEnterprise, topN);
    }

    public List<String> getMostFrequentPersonalWifiConnectionTimes(int topN) {
        Predicate<ConnectivityData> isPersonal = connectivityData -> !connectivityData.enterprise;
        return getMostFrequentWifiConnectionTimes(1, isPersonal, topN);
    }

    private List<String> getMostFrequentWifiConnectionTimes(int type, Predicate<ConnectivityData> condition, int topN) {
        List<ConnectivityData> allData = mConnectivityTracker.getAllData();
        Map<String, Long> durationMap = new HashMap<>();
        long currentTime = System.currentTimeMillis();

        for (ConnectivityData data : allData) {
            if (data.timestamp < mCheckTime[type] || !condition.test(data)) continue;
            boolean isNewConnected = data.connected;
            long timestamp = data.timestamp;

            if (mIsConnected[type] != isNewConnected) {
                if (DEBUG) Log.d(TAG, "connection status change to " + isNewConnected);
                if (isNewConnected) {
                    mStartTime[type] = timestamp;
                    mIsConnected[type] = true;
                } else {
                    long duration = timestamp - mStartTime[type];
                    if (DEBUG) Log.d(TAG, "duration : " + duration + ", min duration : " +
                            MIN_DURATION);
                    if (duration >= MIN_DURATION) {
                        durationMap.put(periodOf(mStartTime[type], timestamp), duration);
                        mStartTime[type] = timestamp;
                        Log.i(TAG, "period of duration " + duration + " is added");
                    }
                    mIsConnected[type] = false;
                }
            }
        }

        mCheckTime[type] = currentTime;

        // Add last top value to the candidate list.
        mRepository.updateCandidateList(durationMap,
                KEY_CONNECTION_TIME[type], KEY_CONNECTION_DURATION[type]);

        List<String> result = durationMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());

        // Update last top value
        mRepository.updateLastResult(durationMap,
                KEY_CONNECTION_TIME[type], KEY_CONNECTION_DURATION[type], result);
        Log.i(TAG, "get most frequent connection time of type" + type + " : " + result);
        return result;
    }

    @Override
    public void deleteAll() {
        mKnowledgeManager.removeEntity(mEmbeddingManager, ENTITY_TYPE_PERIOD);
        mRepository.deleteLastResult();
        mConnectivityTracker.deleteAllData();
    }

    private String periodOf(long startTime, long endTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return "from " + sdf.format(new Date(startTime)) + " to " + sdf.format(new Date(endTime));
    }

    @Override
    public void startMonitoring() {
        initialize();
        mConnectivityTracker.startMonitoring();
    }

    @Override
    public void stopMonitoring() {
        mConnectivityTracker.stopMonitoring();
    }

    @Override
    public void update(int type, MonitoringService.EmbeddingResultListener listener) {
        String latest = getLatestPeriod(type);
        if (TextUtils.isEmpty(latest)) {
            listener.onSuccess();
            return;
        }

        Entity periodEntity = new Entity(UUID.randomUUID().toString(),
                ENTITY_TYPE_PERIOD, getName(type));
        periodEntity.addAttribute("period", latest);
        mKnowledgeManager.addEntity(mEmbeddingManager, periodEntity, listener);
    }

    private String getLatestPeriod(int type) {
        List<String> results = null;
        switch (type) {
            case 0: {
                results = getMostFrequentEnterpriseWifiConnectionTimes(1);
                break;
            }
            case 1: {
                results = getMostFrequentPersonalWifiConnectionTimes(1);
                break;
            }
            default: {
                break;
            }
        }
        return (results != null && !results.isEmpty()) ? results.get(0) : "";
    }

    private String getName(int type) {
        switch (type) {
            case 0:
                return ENTITY_NAME_PERIOD_ENTERPRISE_WIFI_CONNECTION;
            case 1:
                return ENTITY_NAME_PERIOD_PERSONAL_WIFI_CONNECTION;
            default:
                return "";
        }
    }
}
