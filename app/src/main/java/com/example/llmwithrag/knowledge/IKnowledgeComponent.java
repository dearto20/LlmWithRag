package com.example.llmwithrag.knowledge;

import com.example.llmwithrag.MonitoringService;

public interface IKnowledgeComponent {
    void deleteAll();
    void startMonitoring();
    void stopMonitoring();
    void update(int type, MonitoringService.EmbeddingResultListener listener);
}
