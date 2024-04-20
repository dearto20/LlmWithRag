package com.example.llmwithrag.llm;

import androidx.room.TypeConverter;

public class TypeConverters {
    @TypeConverter
    public static String fromFloatArray(float[] array) {
        if (array == null) return null;
        StringBuilder sb = new StringBuilder();
        for (float v : array) sb.append(v).append(",");
        return sb.toString();
    }

    @TypeConverter
    public static float[] toFloatArray(String array) {
        if (array == null) return null;
        String[] strings = array.split(",");
        float[] result = new float[strings.length];
        for (int i = 0; i < strings.length; i++) result[i] = Float.parseFloat(strings[i]);
        return result;
    }
}
