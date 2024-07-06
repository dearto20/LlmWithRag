package com.example.llmwithrag.kg;

import static java.util.Objects.hash;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

public class Relationship implements IElement {
    private final Entity mFromEntity;
    private final String mType;
    private final Entity mToEntity;

    public Relationship(Entity fromEntity, String type, Entity toEntity) {
        mFromEntity = fromEntity;
        mType = type;
        mToEntity = toEntity;
    }

    @Override
    public String getId() {
        return mFromEntity.getId() + ":" + mToEntity.getId();
    }

    @Override
    public String getContentId() {
        return String.valueOf(hash(getDescription().toString()));
    }

    public String getFromEntityId() {
        return mFromEntity.getId();
    }

    @Override
    public String getType() {
        return mType;
    }

    @Override
    public String getName() {
        return mType;
    }

    public String getToEntityId() {
        return mToEntity.getId();
    }

    @Override
    public Map<String, String> getDescription() {
        Map<String, String> description = new HashMap<>();
        description.put("from", mFromEntity.getName());
        description.put("type", getType());
        description.put("to", mToEntity.getName());
        return description;
    }

    @NonNull
    @Override
    public String toString() {
        Map<String, String> description = new HashMap<>(getDescription());
        description.put("id", getId());
        return description.toString();
    }
}
