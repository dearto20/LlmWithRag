package com.example.llmwithrag;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ServiceViewModel extends ViewModel {
    private static final String TAG = ServiceViewModel.class.getSimpleName();
    private final MutableLiveData<IMonitoringService> mService = new MutableLiveData<>();
    private String mLastTheMostFrequentlyVisitedPlaceDuringTheDay;
    private String mLastTheMostFrequentlyVisitedPlaceDuringTheNight;
    private String mLastTheMostFrequentlyVisitedPlaceDuringTheWeekend;
    private String mLastTheMostFrequentStationaryTime;
    private String mLastTheMostFrequentPublicWifiConnectionTime;

    public ServiceViewModel() {
        mLastTheMostFrequentlyVisitedPlaceDuringTheDay = null;
        mLastTheMostFrequentlyVisitedPlaceDuringTheNight = null;
        mLastTheMostFrequentlyVisitedPlaceDuringTheWeekend = null;
        mLastTheMostFrequentStationaryTime = null;
        mLastTheMostFrequentPublicWifiConnectionTime = null;
    }

    public LiveData<IMonitoringService> getService() {
        return mService;
    }

    public void setService(IMonitoringService service) {
        mService.setValue(service);
    }

    public String getLastTheMostFrequentlyVisitedPlaceDuringTheDay() {
        return mLastTheMostFrequentlyVisitedPlaceDuringTheDay;
    }

    public void setLastTheMostFrequentlyVisitedPlaceDuringTheDay(String text) {
        mLastTheMostFrequentlyVisitedPlaceDuringTheDay = text;
    }

    public String getLastTheMostFrequentlyVisitedPlaceDuringTheNight() {
        return mLastTheMostFrequentlyVisitedPlaceDuringTheNight;
    }

    public void setLastTheMostFrequentlyVisitedPlaceDuringTheNight(String text) {
        mLastTheMostFrequentlyVisitedPlaceDuringTheNight = text;
    }

    public String getLastTheMostFrequentlyVisitedPlaceDuringTheWeekend() {
        return mLastTheMostFrequentlyVisitedPlaceDuringTheWeekend;
    }

    public void setLastTheMostFrequentlyVisitedPlaceDuringTheWeekend(String text) {
        mLastTheMostFrequentlyVisitedPlaceDuringTheWeekend = text;
    }

    public String getLastTheMostFrequentStationaryTime() {
        return mLastTheMostFrequentStationaryTime;
    }

    public void setLastTheMostFrequentStationaryTime(String text) {
        mLastTheMostFrequentStationaryTime = text;
    }

    public String getLastTheMostFrequentPublicWifiConnectionTime() {
        return mLastTheMostFrequentPublicWifiConnectionTime;
    }

    public void setLastTheMostFrequentPublicWifiConnectionTime(String text) {
        mLastTheMostFrequentPublicWifiConnectionTime = text;
    }
}
