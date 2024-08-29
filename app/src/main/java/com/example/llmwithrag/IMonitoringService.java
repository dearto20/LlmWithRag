package com.example.llmwithrag;

import android.os.Looper;

import com.example.llmwithrag.kg.KnowledgeManager;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;
import com.example.llmwithrag.llm.EmbeddingManager;

import java.util.List;

public interface IMonitoringService {
    IMonitoringService addKnowledge(String name, IKnowledgeComponent component);

    void delete(String name);

    void deleteAll();

    List<String> findSimilarOnes(String query, String response);

    String getSchema();

    IMonitoringService setEmbeddingManager(EmbeddingManager embeddingManager);

    IMonitoringService setKnowledgeManager(KnowledgeManager knowledgeManager);
    IMonitoringService setLooper(Looper looper);

    IMonitoringService setViewModel(ServiceViewModel viewModel);

    void startMonitoring();

    void stopMonitoring();

    void update(String name, int type, boolean enabled, ServiceViewModel.IResultListener listener);
}
