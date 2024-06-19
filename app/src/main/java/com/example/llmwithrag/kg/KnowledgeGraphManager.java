package com.example.llmwithrag.kg;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.example.llmwithrag.MonitoringService;
import com.example.llmwithrag.llm.Embedding;
import com.example.llmwithrag.llm.EmbeddingManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KnowledgeGraphManager {
    private static final String TAG = KnowledgeGraphManager.class.getSimpleName();
    public static final String ENTITY_TYPE_EVENT = "Event";
    public static final String ENTITY_TYPE_PHOTO = "Photo";
    public static final String ENTITY_TYPE_USER = "User";
    public static final String ENTITY_TYPE_MESSAGE = "Message";
    private final Map<String, Entity> mEntities;

    public KnowledgeGraphManager(Context context) {
        mEntities = new HashMap<>();
    }

    public static final String SCHEMA = "{\n" +
            "    \"entities\": [\n" +
            "        {\n" +
            "            \"type\": \"Photo\",\n" +
            "            \"attributes\": [\n" +
            "                \"filePath\", \"date\", \"time\", \"location\", \"sentBy\",\n" +
            "            ],\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"Event\",\n" +
            "            \"attributes\": [\n" +
            "                \"eventId\", \"title\", \"date\", \"time\", \"location\", \"startDate\", \"endDate\",\n" +
            "            ],\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"Message\",\n" +
            "            \"attributes\": [\n" +
            "                \"sentBy\", \"body\", \"address\", \"date\", \"time\",\n" +
            "            ],\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"User\",\n" +
            "            \"attributes\": [\n" +
            "                \"name\",\n" +
            "            ],\n" +
            "        },\n" +
            "    ],\n" +
            "}";

    public Entity getEntity(Entity entity) {
        if (ENTITY_TYPE_EVENT.equals(entity.getType())) {
            for (Entity _entity : mEntities.values()) {
                if (TextUtils.equals(_entity.getType(), entity.getType()) &&
                        TextUtils.equals(_entity.getAttributes().get("eventId"),
                                entity.getAttributes().get("eventId"))) {
                    return _entity;
                }
            }
        } else {
            for (Entity _entity : mEntities.values()) {
                if (TextUtils.equals(_entity.getType(), entity.getType()) &&
                        TextUtils.equals(_entity.getDescription().toString(),
                                entity.getDescription().toString())) {
                    return _entity;
                }
            }
        }

        return null;
    }

    public boolean equals(Entity src, Entity tar) {
        return src != null && tar != null &&
                TextUtils.equals(src.getDescription().toString(), tar.getDescription().toString());
    }

    public void addEntity(Entity entity) {
        if (getEntity(entity) != null) return;
        mEntities.put(entity.getId(), entity);
        Log.i(TAG, "entity added : " + entity);
    }

    public void removeEntity(Entity entity) {
        mEntities.remove(entity.getId());
    }

    public void deleteAll() {
        mEntities.clear();
    }

    private boolean hasEmbedding(EmbeddingManager embeddingManager, Entity entity) {
        for (Embedding embedding : embeddingManager.getAll()) {
            if (embedding.category.equals(entity.getId())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Entity> parseEntitiesFromResponse(String response) {
        Map<String, Entity> entities = new HashMap<>();
        try {
            Gson gson = new Gson();
            Type responseType = new TypeToken<Map<String, Object>>() {
            }.getType();

            Map<String, Object> responseMap = gson.fromJson(response, responseType);
            List<Map<String, Object>> entitiesList = (List<Map<String, Object>>) responseMap.get("entities");
            if (entitiesList != null) {
                for (Map<String, Object> entityMap : entitiesList) {
                    String id = UUID.randomUUID().toString();
                    String type = (String) entityMap.get("type");
                    Map<String, String> attributes = (Map<String, String>) entityMap.get("attributes");
                    if (attributes != null) {
                        Entity entity = new Entity(id, type, "");
                        for (Map.Entry<String, String> entry : attributes.entrySet()) {
                            entity.addAttribute(entry.getKey(), entry.getValue());
                        }
                        entities.put(type, entity);
                    }
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return entities;
    }

    public void addEmbedding(EmbeddingManager embeddingManager, Entity entity) {
        if (hasEmbedding(embeddingManager, entity)) return;
        String flattened = new Gson().toJson(entity);
        MonitoringService.EmbeddingResultListener listener =
                new MonitoringService.EmbeddingResultListener() {
                    @Override
                    public void onSuccess() {
                        Log.i(TAG, "embedding is added : " + flattened);
                    }
                };
        embeddingManager.addEmbeddings(flattened,
                entity.getType() + ", " + entity.getName(), entity.getContentId(), listener);
    }

    public void removeEmbedding(EmbeddingManager embeddingManager, Entity entity) {
        embeddingManager.removeEmbeddings(entity.getContentId());
    }
}
