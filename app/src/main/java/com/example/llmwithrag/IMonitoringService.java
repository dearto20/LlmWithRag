package com.example.llmwithrag;

public interface IMonitoringService {
    void deleteAll();

    String getMostFrequentlyVisitedPlaceDuringTheDay();

    String getMostFrequentlyVisitedPlaceDuringTheNight();

    String getMostFrequentlyVisitedPlaceDuringTheWeekend();

    String getMostFrequentStationaryTime();

    String getMostFrequentPublicWifiConnectionTime();

    void startMonitoring();

    void stopMonitoring();

    boolean isStarted();
}
