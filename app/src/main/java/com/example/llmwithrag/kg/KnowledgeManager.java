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
import java.util.ArrayList;
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
    public static final String ENTITY_TYPE_DATE = "date";
    public static final String ENTITY_TYPE_LOCATION = "Location";
    public static final String ENTITY_TYPE_PERIOD = "Period";
    public static final String ENTITY_NAME_LOCATION_DURING_THE_DAY = "낮에 가장 오래 머무는 장소";
    public static final String ENTITY_NAME_LOCATION_DURING_THE_NIGHT = "밤에 가장 오래 머무는 장소";
    public static final String ENTITY_NAME_LOCATION_DURING_THE_WEEKEND = "주말에 가장 오래 머무는 장소";
    public static final String ENTITY_NAME_PERIOD_ENTERPRISE_WIFI_CONNECTION = "기업 와이파이를 가장 오래 사용하는 시간대";
    public static final String ENTITY_NAME_PERIOD_PERSONAL_WIFI_CONNECTION = "개인 와이파이를 가장 오래 사용하는 시간대";
    public static final String ENTITY_NAME_PERIOD_STATIONARY = "휴대폰 사용이 가장 적은 시간대";
    public static final String ENTITY_NAME_EVENT_IN_THE_CALENDAR_APP = "캘린더 앱에 등록된 이벤트";
    public static final String ENTITY_NAME_MESSAGE_IN_THE_EMAIL_APP = "이메일 앱에서 받은 메시지";
    public static final String ENTITY_NAME_MESSAGE_IN_THE_MESSAGES_APP = "메시지 앱에서 받은 메시지";
    public static final String RELATIONSHIP_SENT_BY_USER = "was sent by user";
    public static final String RELATIONSHIP_SENT_ON_DATE = "was sent on date";
    public static final String RELATIONSHIP_TAKEN_ON_DATE = "was taken on date";
    public static final String RELATIONSHIP_TAKEN_AT_LOCATION = "was taken at location";
    public static final String RELATIONSHIP_ATTACHED_IN = "was attached in";
    public static final String RELATIONSHIP_HELD_ON_DATE = "held on date";
    public static final String RELATIONSHIP_HELD_AT_LOCATION = "held at location";
    private final Map<String, Entity> mEntities;
    private final List<Relationship> mRelationships;

    public KnowledgeManager(Context context) {
        mEntities = new HashMap<>();
        mRelationships = new ArrayList<>();
    }

    public static final String SCHEMA = "{\n" +
            "    \"entities\": [\n" +
            "        {\n" +
            "            \"type\": \"Photo\",\n" +
            "            \"attributes\": {\n" +
            "                \"filePath\": \"\",\n" +
            "                \"date\": \"\",\n" +
            "                \"time\": \"\"\n" +
            "            },\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"Event\",\n" +
            "            \"attributes\": {\n" +
            "                \"eventId\": \"\",\n" +
            "                \"title\": \"\",\n" +
            "                \"date\": \"\",\n" +
            "                \"time\": \"\",\n" +
            "                \"location\": \"\",\n" +
            "                \"startDate\": \"\",\n" +
            "                \"endDate\": \"\"\n" +
            "            },\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"Message\",\n" +
            "            \"attributes\": {\n" +
            "                \"address\": \"\",\n" +
            "                \"sender\": \"\",\n" +
            "                \"body\": \"\",\n" +
            "                \"date\": \"\",\n" +
            "                \"time\": \"\"\n" +
            "            },\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"Email\",\n" +
            "            \"attributes\": {\n" +
            "                \"address\": \"\",\n" +
            "                \"sender\": \"\",\n" +
            "                \"subject\": \"\",\n" +
            "                \"body\": \"\",\n" +
            "                \"date\": \"\",\n" +
            "                \"time\": \"\"\n" +
            "            },\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"User\",\n" +
            "            \"attributes\": {\n" +
            "                \"name\": \"\"\n" +
            "            },\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"Location\",\n" +
            "            \"attributes\": {\n" +
            "                \"coordinate\": \"\",\n" +
            "                \"location\": \"\"\n" +
            "            },\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"Date\",\n" +
            "            \"attributes\": {\n" +
            "                \"date\": \"\"\n" +
            "            },\n" +
            "        },\n" +
            "    ],\n" +
            "    \"relationships\": [\n" +
            "        {\n" +
            "            \"from\": \"Message\",\n" +
            "            \"type\": \"was sent by user\",\n" +
            "            \"to\": \"User\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"from\": \"Message\",\n" +
            "            \"type\": \"was sent on date\",\n" +
            "            \"to\": \"Date\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"from\": \"Email\",\n" +
            "            \"type\": \"was sent by user\",\n" +
            "            \"to\": \"User\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"from\": \"Email\",\n" +
            "            \"type\": \"was sent on date\",\n" +
            "            \"to\": \"Date\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"was taken on date\",\n" +
            "            \"from\": \"Photo\",\n" +
            "            \"to\": \"Date\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"was taken at location\",\n" +
            "            \"from\": \"Photo\",\n" +
            "            \"to\": \"Location\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"was attached in\",\n" +
            "            \"from\": \"Photo\",\n" +
            "            \"to\": \"Message\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"held on date\",\n" +
            "            \"from\": \"Event\",\n" +
            "            \"to\": \"Date\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"held at location\",\n" +
            "            \"from\": \"Event\",\n" +
            "            \"to\": \"Location\"\n" +
            "        }\n" +
            "    ]\n" +
            "}\n";

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
                if (TextUtils.equals(_entity.getDescription().toString(),
                        entity.getDescription().toString())) {
                    return _entity;
                }
            }
        }
        return null;
    }

    public boolean equals(Entity src, Entity tar) {
        boolean result = src != null && tar != null &&
                TextUtils.equals(src.getDescription().toString(), tar.getDescription().toString());
        if ((src != null && ENTITY_TYPE_EVENT.equals(src.getType())) &&
                (tar != null && ENTITY_TYPE_EVENT.equals(tar.getType()))) {
            result &= TextUtils.equals(
                    src.getAttributes().get("eventId"), tar.getAttributes().get("eventId"));
        }
        return result;
    }

    public boolean addEntity(EmbeddingManager embeddingManager, Entity newEntity) {
        return addEntity(embeddingManager, newEntity, null);
    }

    public boolean addEntity(EmbeddingManager embeddingManager, Entity newEntity,
                             MonitoringService.EmbeddingResultListener listener) {
        Entity oldEntity = getEntity(newEntity);
        if (equals(oldEntity, newEntity)) return false;

        if (oldEntity != null) {
            removeEntity(embeddingManager, oldEntity);
            removeEmbedding(embeddingManager, oldEntity);
        }
        addEntity(newEntity);
        removeEmbedding(embeddingManager, newEntity);
        addEmbedding(embeddingManager, newEntity, listener);
        Log.i(TAG, "added " + newEntity);
        return true;
    }

    private void addEntity(Entity entity) {
        if (getEntity(entity) != null) return;
        mEntities.put(entity.getId(), entity);
    }

    public void removeEntity(EmbeddingManager embeddingManager, Entity entity) {
        mEntities.remove(entity.getId());
        for (Relationship relationship : mRelationships) {
            if (relationship.getFromEntityId().equals(entity.getId()) ||
                    relationship.getToEntityId().equals(entity.getId())) {
                mRelationships.remove(relationship);
                removeEmbedding(embeddingManager, relationship);
            }
        }
    }

    public void addRelationship(EmbeddingManager embeddingManager, Entity fromEntity, String type, Entity toEntity) {
        if (getRelationship(fromEntity.getId(), RELATIONSHIP_SENT_BY_USER, toEntity.getId()) == null) {
            Relationship relationship = new Relationship(fromEntity, type, toEntity);
            mRelationships.add(relationship);
            removeEmbedding(embeddingManager, relationship);
            addEmbedding(embeddingManager, relationship, null);
            Log.i(TAG, "added " + relationship);
        }
    }

    private Relationship getRelationship(String fromEntityId, String type, String toEntityId) {
        for (Relationship relationship : mRelationships) {
            if (relationship.getFromEntityId().equals(fromEntityId) &&
                    relationship.getType().equals(type) &&
                    relationship.getToEntityId().equals(toEntityId)) {
                return relationship;
            }
        }
        return null;
    }

    public void deleteAll() {
        mEntities.clear();
        mRelationships.clear();
    }

    private boolean hasEmbedding(EmbeddingManager embeddingManager, IElement element) {
        for (Embedding embedding : embeddingManager.getAll()) {
            if (embedding.category.equals(element.getContentId())) {
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

    public void addEmbedding(EmbeddingManager embeddingManager, IElement element,
                             MonitoringService.EmbeddingResultListener listener) {
        if (hasEmbedding(embeddingManager, element)) return;

        MonitoringService.EmbeddingResultListener finalListener =
                new MonitoringService.EmbeddingResultListener() {
                    @Override
                    public void onSuccess() {
                        if (listener != null) listener.onSuccess();
                        Log.i(TAG, "embedding has been added");
                    }

                    @Override
                    public void onError() {
                        if (listener != null) listener.onError();
                        Log.i(TAG, "embedding has not been added due to error");
                    }

                    @Override
                    public void onFailure() {
                        if (listener != null) listener.onFailure();
                        Log.e(TAG, "embedding has not added due to failure");
                    }
                };

        if (IS_SENTENCE_BASED) {
            Log.i(TAG, "in => " + element.getDescription());
            String query = generateDescriptiveQuery(element);
            Utils.performQuery(query, new Utils.QueryResponseListener() {
                @Override
                public void onSuccess(String result) {
                    embeddingManager.addEmbeddings(result,
                            element.getName() + "[" + element.getId() + "]", element.getContentId(), finalListener);
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
            String flattened = new Gson().toJson(element);
            embeddingManager.addEmbeddings(flattened,
                    element.getId(), element.getContentId(), finalListener);
        }
    }

    private void removeEmbedding(EmbeddingManager embeddingManager, Entity entity) {
        embeddingManager.removeEmbeddings(entity.getContentId());
    }

    private void removeEmbedding(EmbeddingManager embeddingManager, Relationship relationship) {
        embeddingManager.removeEmbeddings(relationship.getContentId());
    }
}
