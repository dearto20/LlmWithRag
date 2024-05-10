package com.example.llmwithrag;

public interface IMonitoringService {
    void deleteAll();

    String getMostFrequentlyVisitedPlaceDuringTheDay();

    String getMostFrequentlyVisitedPlaceDuringTheNight();

    String getMostFrequentlyVisitedPlaceDuringTheWeekend();

    String getMostFrequentStationaryTime();

    String getMostFrequentPublicWifiConnectionTime();

    void setDayLocationEnabled(boolean enabled);

    void setNightLocationEnabled(boolean enabled);

    void setWeekendLocationEnabled(boolean enabled);

    void setStationaryTimeEnabled(boolean enabled);

    void setPublicWifiTimeEnabled(boolean enabled);

    void startMonitoring();

    void stopMonitoring();

    boolean isStarted();
}
