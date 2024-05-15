package com.example.llmwithrag;

import androidx.lifecycle.LiveData;

public interface IMonitoringService {
    void deleteAll();

    LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheDay();

    LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheNight();

    LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheWeekend();

    LiveData<String> getTheMostFrequentStationaryTime();

    LiveData<String> getTheMostFrequentPublicWifiConnectionTime();

    void startMonitoring();

    void stopMonitoring();

    boolean isStarted();
}
