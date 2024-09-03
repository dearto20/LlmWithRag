package com.example.llmwithrag.datasource;

import java.util.List;

public interface IDataSourceTracker {
    String KEY_ADDRESS = "address";
    String KEY_BODY = "body";
    String KEY_DATE = "date";
    String KEY_END_DATE = "endDate";
    String KEY_FILE_PATH = "filePath";
    String KEY_ID = "id";
    String KEY_LOCATION = "location";
    String KEY_NAME = "name";
    String KEY_SENDER = "sender";
    String KEY_START_DATE = "startDate";
    String KEY_SUBJECT = "subject";
    String KEY_TIME = "time";
    String KEY_TITLE = "title";

    void deleteAllData();

    List<Object> getAllData();

    void startMonitoring();

    void stopMonitoring();

    void registerListener(IDataSourceListener listener);

    void unregisterListener(IDataSourceListener listener);
}
