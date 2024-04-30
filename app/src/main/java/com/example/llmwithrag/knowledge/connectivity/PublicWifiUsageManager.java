package com.example.llmwithrag.knowledge.connectivity;

import com.example.llmwithrag.datasource.connectivity.ConnectivityData;
import com.example.llmwithrag.datasource.connectivity.ConnectivityTracker;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class PublicWifiUsageManager implements IKnowledgeComponent {
    private final ConnectivityTracker mConnectivityTracker;

    public PublicWifiUsageManager(ConnectivityTracker connectivityTracker) {
        mConnectivityTracker = connectivityTracker;
    }

    private void initialize() {
    }

    private String timeOf(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public List<String> getMostFrequentPublicWifiConnectionTimes(int topN) {
        List<ConnectivityData> events = mConnectivityTracker.getAllData();
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (ConnectivityData event : events) {
            if (!event.capabilities.contains("EAP")) {
                String key = timeOf(event.timestamp);
                frequencyMap.put(key, frequencyMap.getOrDefault(key, 0) + 1);
            }
        }

        return frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> String.format("%s", entry.getKey()))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAll() {
        mConnectivityTracker.deleteAllData();
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
}
