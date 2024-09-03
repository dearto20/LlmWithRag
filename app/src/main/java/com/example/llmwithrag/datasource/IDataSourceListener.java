package com.example.llmwithrag.datasource;

import java.util.Map;

public interface IDataSourceListener {
    void onUpdate(Map<String, Object> data);
}
