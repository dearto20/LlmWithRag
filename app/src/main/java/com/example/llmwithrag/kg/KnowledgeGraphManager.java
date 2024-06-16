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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KnowledgeGraphManager {
    private static final String TAG = KnowledgeGraphManager.class.getSimpleName();
    public static final String ENTITY_TYPE_EVENT = "Event";
    public static final String ENTITY_TYPE_PHOTO = "Photo";
    public static final String ENTITY_TYPE_LOCATION = "Location";
    public static final String ENTITY_TYPE_DATE = "Date";
    public static final String ENTITY_TYPE_TIME = "Time";
    public static final String ENTITY_TYPE_USER = "User";
    public static final String ENTITY_TYPE_MESSAGE = "Message";
    private final Map<String, Entity> mEntities;
    private final List<Relationship> mRelationships;

    public KnowledgeGraphManager(Context context) {
        mEntities = new HashMap<>();
        mRelationships = new ArrayList<>();
    }

    public static final String SCHEMA = "{\n" +
            "    \"entities\": [\n" +
            "        {\n" +
            "            \"type\": \"Photo\",\n" +
            "            \"attributes\": [\n" +
            "                \"filePath\", \"dateTaken\", \"location\"\n" +
            "            ]\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"Event\",\n" +
            "            \"attributes\": [\n" +
            "                \"eventId\", \"title\", \"startDate\",  \"endDate\", \"location\"\n" +
            "            ]\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"Message\",\n" +
            "            \"attributes\": [\n" +
            "                \"body\", \"address\", \"date\"\n" +
            "            ]\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"User\",\n" +
            "            \"attributes\": [\n" +
            "                \"name\"\n" +
            "            ]\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"Location\",\n" +
            "            \"attributes\": [\n" +
            "                \"location\"\n" +
            "            ]\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"Date\",\n" +
            "            \"attributes\": [\n" +
            "                \"date\"\n" +
            "            ]\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"Time\",\n" +
            "            \"attributes\": [\n" +
            "                \"time\"\n" +
            "            ]\n" +
            "        }\n" +
            "    ],\n" +
            "    \"relationships\": [\n" +
            "        {\n" +
            "            \"type\": \"taken at location\",\n" +
            "            \"from\": \"Photo\",\n" +
            "            \"to\": \"Location\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"taken on date\",\n" +
            "            \"from\": \"Photo\",\n" +
            "            \"to\": \"Date\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"taken at time\",\n" +
            "            \"from\": \"Photo\",\n" +
            "            \"to\": \"Time\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"takes place at location\",\n" +
            "            \"from\": \"Event\",\n" +
            "            \"to\": \"Location\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"takes place on date\",\n" +
            "            \"from\": \"Event\",\n" +
            "            \"to\": \"Date\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"takes place at time\",\n" +
            "            \"from\": \"Event\",\n" +
            "            \"to\": \"Time\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"sent by\",\n" +
            "            \"from\": \"Message\",\n" +
            "            \"to\": \"User\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"sent on date\",\n" +
            "            \"from\": \"Message\",\n" +
            "            \"to\": \"Date\"\n" +
            "        },\n" +
            "        {\n" +
            "            \"type\": \"sent at time\",\n" +
            "            \"from\": \"Message\",\n" +
            "            \"to\": \"Time\"\n" +
            "        }\n" +
            "    ]\n" +
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
        }

        for (Entity _entity : mEntities.values()) {
            if (TextUtils.equals(_entity.getType(), entity.getType()) &&
                    TextUtils.equals(_entity.getDescription().toString(),
                            entity.getDescription().toString())) {
                return _entity;
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

    public void addRelationship(Relationship relationship) {
        mRelationships.add(relationship);
    }

    public Relationship getRelationship(String fromEntityId, String toEntityId, String type) {
        for (Relationship relationship : mRelationships) {
            if (relationship.getFromEntityId().equals(fromEntityId) &&
                    relationship.getType().equals(type) &&
                    relationship.getToEntityId().equals(toEntityId)) {
                return relationship;
            }
        }
        return null;
    }

    public List<Relationship> getRelationships(String entityId) {
        List<Relationship> result = new ArrayList<>();
        for (Relationship relationship : mRelationships) {
            if (relationship.getFromEntityId().equals(entityId) ||
                    relationship.getToEntityId().equals(entityId)) {
                result.add(relationship);
            }
        }
        return result;
    }

    public Map<String, Entity> getRelatedEntities(String entityId) {
        Map<String, Entity> result = new HashMap<>();
        for (Relationship relationship : mRelationships) {
            if (relationship.getFromEntityId().equals(entityId)) {
                Entity entity = mEntities.get(relationship.getToEntityId());
                if (entity != null) result.put(entity.getId(), entity);
            } else if (relationship.getToEntityId().equals(entityId)) {
                Entity entity = mEntities.get(relationship.getFromEntityId());
                if (entity != null) result.put(entity.getId(), entity);
            }
        }
        return result;
    }

    public void deleteAll() {
        mEntities.clear();
        mRelationships.clear();
    }

    private boolean hasEmbedding(EmbeddingManager embeddingManager, Entity entity) {
        for (Embedding embedding : embeddingManager.getAll()) {
            if (embedding.category.equals(entity.getId())) {
                return true;
            }
        }
        return false;
    }

    public String flattenEntities(Map<String, Entity> entities, List<Relationship> relationships) {
        StringBuilder sb = new StringBuilder();
        for (Entity entity : entities.values()) {
            sb.append(flattenEntity(entity, entities, relationships));
        }
        return sb.toString();
    }

    public String flattenEntity(Entity entity) {
        return flattenEntity(entity, getRelatedEntities(entity.getId()),
                getRelationships(entity.getId()));
    }

    private String flattenEntity(Entity entity, Map<String, Entity> entities,
                                 List<Relationship> relationships) {
        StringBuilder sb = new StringBuilder();
        sb.append("entity id: ").append(entity.getId()).append("; ");
        sb.append("type: ").append(entity.getType()).append("; ");
        for (Map.Entry<String, String> entry : entity.getAttributes().entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("; ");
        }

        for (Relationship relationship : relationships) {
            if (relationship.getFromEntityId().equals(entity.getId())) {
                String relatedEntityId = relationship.getToEntityId();
                Entity relatedEntity = entities.get(relatedEntityId);
                if (relatedEntity != null) {
                    sb.append("relationship: ").append(relationship.getType())
                            .append(" entity id: ").append(relatedEntityId)
                            .append(" (type: ").append(relatedEntity.getType()).append("; ");
                    for (Map.Entry<String, String> entry : relatedEntity.getAttributes().entrySet()) {
                        sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("; ");
                    }
                    sb.append("); ");
                }
            }
        }
        return sb.toString();
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

    @SuppressWarnings("unchecked")
    public List<Relationship> parseRelationshipsFromResponse(String response,
                                                             Map<String, Entity> entities) {
        List<Relationship> relationships = new ArrayList<>();
        Gson gson = new Gson();
        Type responseType = new TypeToken<Map<String, Object>>() {
        }.getType();
        Map<String, Object> responseMap = gson.fromJson(response, responseType);

        List<Map<String, String>> relationshipsList =
                (List<Map<String, String>>) responseMap.get("relationships");
        if (relationshipsList != null) {
            for (Map<String, String> relationshipsMap : relationshipsList) {
                String type = relationshipsMap.get("type");
                String from = relationshipsMap.get("from");
                String to = relationshipsMap.get("to");
                Entity fromEntity = entities.get(from);
                Entity toEntity = entities.get(to);
                if (fromEntity != null && toEntity != null) {
                    relationships.add(new Relationship(fromEntity.getId(), toEntity.getId(), type));
                }
            }
        }
        return relationships;
    }

    public void addEmbedding(EmbeddingManager embeddingManager, Entity entity) {
        if (hasEmbedding(embeddingManager, entity)) return;
        String flattened = flattenEntity(entity);
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
