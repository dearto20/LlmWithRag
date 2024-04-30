package com.example.llmwithrag;

import java.util.List;

public interface IMonitoringService {
    void deleteAll();

    List<String> getMostFrequentPublicWifiConnectionTimes(int topN);

    List<String> getMostFrequentStationaryTimes(int topN);

    List<String> getMostFrequentlyVisitedPlacesDuringTheDay(int topN);

    List<String> getMostFrequentlyVisitedPlacesDuringTheNight(int topN);

    List<String> getMostFrequentlyVisitedPlacesDuringTheWeekend(int topN);

    void startMonitoring();

    void stopMonitoring();
}
