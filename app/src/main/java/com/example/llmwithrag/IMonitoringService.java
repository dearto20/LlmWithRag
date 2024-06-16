package com.example.llmwithrag;

import androidx.lifecycle.LiveData;

import java.util.List;

public interface IMonitoringService {
    void deleteAll();

    List<String> findSimilarOnes(String query);

    List<String> findSimilarOnes(String query, String response);

    String getSchema();

    LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheDay();

    LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheNight();

    LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheWeekend();

    LiveData<String> getTheMostFrequentStationaryTime();

    LiveData<String> getTheMostFrequentEnterpriseWifiConnectionTime();

    LiveData<String> getTheMostFrequentPersonalWifiConnectionTime();

    boolean isServiceEnabled();

    boolean isDayLocationEnabled();

    boolean isNightLocationEnabled();

    boolean isWeekendLocationEnabled();

    boolean isStationaryTimeEnabled();

    boolean isEnterpriseWifiTimeEnabled();

    boolean isPersonalWifiTimeEnabled();

    boolean setServiceEnabled(boolean enabled);

    boolean setDayLocationEnabled(boolean enabled);

    boolean setNightLocationEnabled(boolean enabled);

    boolean setWeekendLocationEnabled(boolean enabled);

    boolean setStationaryTimeEnabled(boolean enabled);

    boolean setEnterpriseWifiTimeEnabled(boolean enabled);

    boolean setPersonalWifiTimeEnabled(boolean enabled);
}
