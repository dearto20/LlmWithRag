package com.example.llmwithrag;

import androidx.lifecycle.LiveData;

import com.example.llmwithrag.llm.Embedding;

import java.util.List;

public interface IMonitoringService {
    void deleteAll();

    List<String> findSimilarOnes(String query);

    LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheDay();

    LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheNight();

    LiveData<String> getTheMostFrequentlyVisitedPlaceDuringTheWeekend();

    LiveData<String> getTheMostFrequentStationaryTime();

    LiveData<String> getTheMostFrequentPublicWifiConnectionTime();

    boolean isServiceEnabled();

    boolean isDayLocationEnabled();

    boolean isNightLocationEnabled();

    boolean isWeekendLocationEnabled();

    boolean isStationaryTimeEnabled();

    boolean isPublicWifiTimeEnabled();

    boolean setServiceEnabled(boolean enabled);

    boolean setDayLocationEnabled(boolean enabled);

    boolean setNightLocationEnabled(boolean enabled);

    boolean setWeekendLocationEnabled(boolean enabled);

    boolean setStationaryTimeEnabled(boolean enabled);

    boolean setPublicWifiTimeEnabled(boolean enabled);
}
