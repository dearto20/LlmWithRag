package com.example.llmwithrag.knowledge.location;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.llmwithrag.knowledge.KnowledgeRepository;

import java.util.List;
import java.util.Map;

public class PersistentLocationRepository extends KnowledgeRepository<Integer> {
    private static final String TAG = PersistentLocationRepository.class.getSimpleName();
    private static final String NAME_SHARED_PREFS = "persistent_location";
    private final Context mContext;

    public PersistentLocationRepository(Context context) {
        mContext = context;
    }

    @Override
    public void updateCandidateList(Map<String, Integer> frequencyMap, String locationKey,
                                     String countKey) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                NAME_SHARED_PREFS, Context.MODE_PRIVATE);
        String oldKey = sharedPreferences.getString(locationKey, null);
        int oldValue = sharedPreferences.getInt(countKey, 0);
        Integer newValue = frequencyMap.get(oldKey);
        if (oldKey != null && oldValue > 0) {
            if (!frequencyMap.containsKey(oldKey) || (newValue == null || newValue < oldValue)) {
                frequencyMap.put(oldKey, oldValue);
                Log.i(TAG, "add last key " + oldKey + " with value " + oldValue);
            }
        }
    }

    @Override
    public void updateLastResult(Map<String, Integer> frequencyMap, String locationKey,
                                  String countKey, List<String> result) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                NAME_SHARED_PREFS, Context.MODE_PRIVATE);
        if (!result.isEmpty()) {
            String key = result.get(0);
            Integer value = frequencyMap.get(key);
            if (value != null) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(locationKey, key);
                editor.putInt(countKey, value);
                editor.apply();
                Log.i(TAG, "update last key " + key + " with value " + value);
            }
        }
    }

    @Override
    public void deleteLastResult() {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                NAME_SHARED_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        Log.i(TAG, "remove last data");
    }
}
