package com.example.llmwithrag.knowledge;

import com.example.llmwithrag.MonitoringService;
import com.example.llmwithrag.datasource.IDataSourceTracker;

import java.util.Map;

public abstract class KnowledgeGenerator {
    public static final String TRACKER_CALENDAR = "calendar";
    public static final String TRACKER_CONNECTIVITY = "connectivity";
    public static final String TRACKER_EMAIL = "email";
    public static final String TRACKER_LOCATION = "location";
    public static final String TRACKER_MESSAGES = "messages";
    public static final String TRACKER_MOVEMENT = "movement";

    public KnowledgeGenerator(Map<String, IDataSourceTracker> trackers) {
    }

    private KnowledgeGenerator() {
    }

    public abstract void deleteAll();

    public abstract void startMonitoring();

    public abstract void stopMonitoring();

    public abstract void update(int type, MonitoringService.EmbeddingResultListener listener);
}
