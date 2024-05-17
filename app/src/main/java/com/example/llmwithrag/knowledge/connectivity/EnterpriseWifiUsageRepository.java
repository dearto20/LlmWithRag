package com.example.llmwithrag.knowledge.connectivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.llmwithrag.knowledge.KnowledgeRepository;

import java.util.List;
import java.util.Map;

public class EnterpriseWifiUsageRepository extends KnowledgeRepository<Long> {
    private static final String TAG = EnterpriseWifiUsageRepository.class.getSimpleName();
    private static final String NAME_SHARED_PREFS = "enterprise_wifi_usage";
    private final Context mContext;

    public EnterpriseWifiUsageRepository(Context context) {
        mContext = context;
    }

    @Override
    public void updateCandidateList(Map<String, Long> durationMap, String timeKey,
                                    String durationKey) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                NAME_SHARED_PREFS, Context.MODE_PRIVATE);
        String oldKey = sharedPreferences.getString(timeKey, null);
        long oldValue = sharedPreferences.getLong(durationKey, 0);
        Long newValue = durationMap.get(oldKey);
        if (oldKey != null && oldValue > 0) {
            if (!durationMap.containsKey(oldKey) || (newValue == null || newValue < oldValue)) {
                durationMap.put(oldKey, oldValue);
                Log.i(TAG, "add last key " + oldKey + " with value " + oldValue);
            }
        }
    }

    @Override
    public void updateLastResult(Map<String, Long> durationMap, String timeKey,
                                 String durationKey, List<String> result) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(
                NAME_SHARED_PREFS, Context.MODE_PRIVATE);
        if (!result.isEmpty()) {
            String key = result.get(0);
            Long value = durationMap.get(key);
            if (value != null) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(timeKey, key);
                editor.putLong(durationKey, value);
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
