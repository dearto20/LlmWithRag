package com.example.llmwithrag;

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
    private String mLastTheMostFrequentEnterpriseWifiConnectionTime;
    private String mLastTheMostFrequentPersonalWifiConnectionTime;

    public ServiceViewModel() {
        mLastTheMostFrequentlyVisitedPlaceDuringTheDay = null;
        mLastTheMostFrequentlyVisitedPlaceDuringTheNight = null;
        mLastTheMostFrequentlyVisitedPlaceDuringTheWeekend = null;
        mLastTheMostFrequentStationaryTime = null;
        mLastTheMostFrequentEnterpriseWifiConnectionTime = null;
        mLastTheMostFrequentPersonalWifiConnectionTime = null;
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

    public String getLastTheMostFrequentEnterpriseWifiConnectionTime() {
        return mLastTheMostFrequentEnterpriseWifiConnectionTime;
    }

    public void setLastTheMostFrequentEnterpriseWifiConnectionTime(String text) {
        mLastTheMostFrequentEnterpriseWifiConnectionTime = text;
    }

    public String getLastTheMostFrequentPersonalWifiConnectionTime() {
        return mLastTheMostFrequentPersonalWifiConnectionTime;
    }

    public void setLastTheMostFrequentPersonalWifiConnectionTime(String text) {
        mLastTheMostFrequentPersonalWifiConnectionTime = text;
    }
}
