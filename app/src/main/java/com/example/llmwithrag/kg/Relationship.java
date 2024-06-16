package com.example.llmwithrag.kg;

public class Relationship {
    private final String mFromEntityId;
    private final String mToEntityId;
    private final String mType;

    public Relationship(String fromEntityId, String toEntityId, String type) {
        mFromEntityId = fromEntityId;
        mToEntityId = toEntityId;
        mType = type;
    }

    public String getFromEntityId() {
        return mFromEntityId;
    }

    public String getToEntityId() {
        return mToEntityId;
    }

    public String getType() {
        return mType;
    }
}
