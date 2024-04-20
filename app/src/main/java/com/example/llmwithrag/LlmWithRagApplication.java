package com.example.llmwithrag;

import android.app.Application;

public class LlmWithRagApplication extends Application {
    private static LlmWithRagApplication sInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
    }

    public static LlmWithRagApplication getInstance() {
        return sInstance;
    }
}
