package com.example.llmwithrag.knowledge;

import java.util.List;
import java.util.Map;

public abstract class KnowledgeRepository<T> {
    public abstract void updateCandidateList(Map<String, T> resultMap, String keyId, String valueId);

    public abstract void updateLastResult(Map<String, T> resultMap, String keyId, String valueId, List<String> result);

    public abstract void deleteLastResult();
}
