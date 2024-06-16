package com.example.llmwithrag.kg;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public class Entity {
    private final Map<String, String> mAttributes;
    private final String mId;
    private final String mType;
    private final String mName;

    public Entity(String id, String type, String name) {
        mId = id;
        mType = type;
        mName = name;
        mAttributes = new HashMap<>();
    }

    public String getId() {
        return mId;
    }

    public String getContentId() {
        return String.valueOf(hash(getDescription().toString()));
    }

    public String getType() {
        return mType;
    }

    public String getName() {
        return mName;
    }

    public void addAttribute(String key, String value) {
        mAttributes.put(key, value);
    }

    public Map<String, String> getAttributes() {
        return mAttributes;
    }

    public Map<String, String> getDescription() {
        Map<String, String> description = new HashMap<>(mAttributes);
        description.put("type", mType);
        description.put("name", mName);
        return description;
    }

    private long hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.wrap(hash);
            return buffer.getLong();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return -1;
    }

    @NonNull
    @Override
    public String toString() {
        Map<String, String> description = new HashMap<>(getDescription());
        description.put("id", mId);
        return description.toString();
    }
}
