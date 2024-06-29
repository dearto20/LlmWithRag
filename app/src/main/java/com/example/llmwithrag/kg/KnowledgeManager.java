package com.example.llmwithrag.kg;

import static com.example.llmwithrag.BuildConfig.IS_SENTENCE_BASED;
import static com.example.llmwithrag.Utils.generateDescriptiveQuery;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.example.llmwithrag.MonitoringService;
import com.example.llmwithrag.Utils;
import com.example.llmwithrag.llm.Embedding;
import com.example.llmwithrag.llm.EmbeddingManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KnowledgeManager {
    private static final String TAG = KnowledgeManager.class.getSimpleName();
    public static final String ENTITY_TYPE_EVENT = "Event";
    public static final String ENTITY_TYPE_PHOTO = "Photo";
    public static final String ENTITY_TYPE_MESSAGE = "Message";
    public static final String ENTITY_TYPE_EMAIL = "Email";
    public static final String ENTITY_TYPE_USER = "User";
    public static final String ENTITY_TYPE_LOCATION = "Location";
    public static final String ENTITY_TYPE_PERIOD = "Period";
    public static final String TAG_LOCATION_DURING_THE_DAY = "낮에 가장 많이 머무는 장소";
    public static final String TAG_LOCATION_DURING_THE_NIGHT = "밤에 가장 많이 머무는 장소";
    public static final String TAG_LOCATION_DURING_THE_WEEKEND = "주말에 가장 많이 머무는 장소";
    public static final String TAG_PERIOD_ENTERPRISE_WIFI_CONNECTION = "기업 와이파이를 가장 오래 사용하는 시간대";
    public static final String TAG_PERIOD_PERSONAL_WIFI_CONNECTION = "개인 와이파이를 가장 오래 사용하는 시간대";
    public static final String TAG_PERIOD_STATIONARY = "휴대폰 사용이 가장 적은 시간대";
    private final Map<String, Entity> mEntities;

    public KnowledgeManager(Context context) {
        mEntities = new HashMap<>();
    }

    public static final String SCHEMA = "{\n" +
            "    \"entities\":[\n" +
            "        {\n" +
            "            \"type\":\"Photo\",\n" +
            "            \"attributes\":{\n" +
            "                \"sender\":\"\",\n" +
            "                \"body:\"\",\n" +
            "                \"filePath\":\"\",\n" +
            "                \"date\":\"\",\n" +
            "                \"time\":\"\",\n" +
            "                \"location\":\"\",\n" +
            "            },\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\":\"Event\",\n" +
            "            \"attributes\":{\n" +
            "                \"eventId\":\"\",\n" +
            "                \"title\":\"\",\n" +
            "                \"date\":\"\",\n" +
            "                \"time\":\"\",\n" +
            "                \"location\":\"\",\n" +
            "                \"startDate\":\"\",\n" +
            "                \"endDate\":\"\",\n" +
            "            },\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\":\"Message\",\n" +
            "            \"attributes\":{\n" +
            "                \"address\",\n" +
            "                \"sender\":\"\",\n" +
            "                \"body\":\"\",\n" +
            "                \"date\":\"\",\n" +
            "                \"time\":\"\",\n" +
            "            },\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\":\"Email\",\n" +
            "            \"attributes\":{\n" +
            "                \"address\":\"\",\n" +
            "                \"sender\":\"\",\n" +
            "                \"subject\":\"\",\n" +
            "                \"body\":\"\",\n" +
            "                \"date\":\"\",\n" +
            "                \"time\":\"\",\n" +
            "            },\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\":\"User\",\n" +
            "            \"attributes\":{\n" +
            "                \"name\":\"\",\n" +
            "            },\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\":\"Location\",\n" +
            "            \"attributes\":{\n" +
            "                \"tag\":\"\",\n" +
            "                \"coordinate\":\"\",\n" +
            "                \"location\":\"\",\n" +
            "            }\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\":\"Period\",\n" +
            "            \"attributes\":{\n" +
            "                \"tag\":\"\",\n" +
            "                \"period\",\n" +
            "            }\n" +
            "        },\n" +
            "    ],\n" +
            "}";

    public Entity getEntity(Entity entity) {
        if (ENTITY_TYPE_LOCATION.equals(entity.getType()) ||
                ENTITY_TYPE_PERIOD.equals(entity.getType())) {
            for (Entity _entity : mEntities.values()) {
                if (TextUtils.equals(_entity.getType(), entity.getType()) &&
                        TextUtils.equals(_entity.getName(), entity.getName())) {
                    return _entity;
                }
            }
        } else if (ENTITY_TYPE_EVENT.equals(entity.getType())) {
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

    public void addEmbedding(EmbeddingManager embeddingManager, Entity entity, long date) {
        MonitoringService.EmbeddingResultListener listener =
                new MonitoringService.EmbeddingResultListener() {
                    @Override
                    public void onSuccess() {
                        Log.i(TAG, "embedding is added");
                    }
                };
        addEmbedding(embeddingManager, entity, date, listener);
    }

    public void addEmbedding(EmbeddingManager embeddingManager, Entity entity, long date,
                             MonitoringService.EmbeddingResultListener listener) {
        if (hasEmbedding(embeddingManager, entity)) return;

        if (IS_SENTENCE_BASED) {
            Log.i(TAG, "in => " + entity.getDescription());
            String query = generateDescriptiveQuery(entity, date);
            Utils.performQuery(query, new Utils.QueryResponseListener() {
                @Override
                public void onSuccess(String result) {
                    embeddingManager.addEmbeddings(result,
                            entity.getType() + ", " + entity.getName(), entity.getContentId(), listener);
                }

                @Override
                public void onError() {
                    Log.i(TAG, "error occurred");
                }

                @Override
                public void onFailure() {
                    Log.i(TAG, "failure occurred");
                }
            });
        } else {
            String flattened = new Gson().toJson(entity);
            embeddingManager.addEmbeddings(flattened,
                    entity.getType() + ", " + entity.getName(), entity.getContentId(), listener);
        }
    }

    public void removeEmbedding(EmbeddingManager embeddingManager, Entity entity) {
        embeddingManager.removeEmbeddings(entity.getContentId());
    }
}
