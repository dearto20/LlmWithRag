package com.example.llmwithrag;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

@SuppressLint("UseSwitchCompatOrMaterialCode")
public class StoreEmbeddingsFragment extends Fragment {
    private static final String TAG = StoreEmbeddingsFragment.class.getSimpleName();

    private ServiceViewModel mViewModel;
    private IMonitoringService mService;

    private boolean mStarted;

    private void updateViews() {
        try {
            FragmentActivity activity = getActivity();
            if (activity == null) return;
            TextView enableServiceView = activity.findViewById(R.id.enableServiceView);
            LinearLayout enableServiceLayout = activity.findViewById(R.id.enableServiceLayout);
            Switch enableServiceSwitch = activity.findViewById(R.id.enableServiceSwitch);

            TextView configureKnowledgeView = activity.findViewById(R.id.configureKnowledgeView);
            LinearLayout configureKnowledgeLayout = activity.findViewById(R.id.configureKnowledgeLayout);
            TextView showKnowledgeView = activity.findViewById(R.id.showKnowledgeView);

            LinearLayout showKnowledgeLayout = activity.findViewById(R.id.showKnowledgeLayout);
            Button resetDatabaseButton = activity.findViewById(R.id.resetDatabaseButton);

            int visibility = mService != null ? View.VISIBLE : View.GONE;
            enableServiceView.setVisibility(visibility);
            enableServiceLayout.setVisibility(visibility);

            if (isServiceEnabled()) {
                enableServiceSwitch.setChecked(true);
                configureKnowledgeView.setVisibility(View.VISIBLE);
                configureKnowledgeLayout.setVisibility(View.VISIBLE);
                showKnowledgeView.setVisibility(View.VISIBLE);
                showKnowledgeLayout.setVisibility(View.VISIBLE);
                resetDatabaseButton.setVisibility(View.VISIBLE);

                Switch dayLocationSwitch = activity.findViewById(R.id.dayLocationSwitch);
                dayLocationSwitch.setChecked(isDayLocationEnabled());

                Switch nightLocationSwitch = activity.findViewById(R.id.nightLocationSwitch);
                nightLocationSwitch.setChecked(isNightLocationEnabled());

                Switch weekendLocationSwitch = activity.findViewById(R.id.weekendLocationSwitch);
                weekendLocationSwitch.setChecked(isWeekendLocationEnabled());

                Switch stationaryTimeSwitch = activity.findViewById(R.id.stationaryTimeSwitch);
                stationaryTimeSwitch.setChecked(isStationaryTimeEnabled());

                Switch enterpriseWifiTimeSwitch = activity.findViewById(R.id.enterpriseWifiTimeSwitch);
                enterpriseWifiTimeSwitch.setChecked(isEnterpriseWifiTimeEnabled());

                Switch personalWifiTimeSwitch = activity.findViewById(R.id.personalWifiTimeSwitch);
                personalWifiTimeSwitch.setChecked(isPersonalWifiTimeEnabled());

                Switch calendarAppEventSwitch = activity.findViewById(R.id.calendarAppEventSwitch);
                calendarAppEventSwitch.setChecked(isCalendarAppEventEnabled());

                Switch emailAppMessageSwitch = activity.findViewById(R.id.emailAppMessageSwitch);
                emailAppMessageSwitch.setChecked(isEmailAppMessageEnabled());

                Switch messagesAppMessageSwitch = activity.findViewById(R.id.messagesAppMessageSwitch);
                messagesAppMessageSwitch.setChecked(isMessagesAppMessageEnabled());

                updateEmbeddingsList();
            } else {
                enableServiceSwitch.setChecked(false);
                configureKnowledgeView.setVisibility(View.GONE);
                configureKnowledgeLayout.setVisibility(View.GONE);
                showKnowledgeView.setVisibility(View.GONE);
                showKnowledgeLayout.setVisibility(View.GONE);
                resetDatabaseButton.setVisibility(View.GONE);
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    private boolean isStarted() {
        return mStarted;
    }

    private void setStarted(boolean started) {
        Log.i(TAG, started ? "started" : "stopped");
        mStarted = started;
    }

    @Override
    public void onStart() {
        super.onStart();
        setStarted(true);
        updateViews();
    }

    @Override
    public void onStop() {
        super.onStop();
        setStarted(false);
        updateViews();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mStarted = false;
    }

    @RequiresApi(api = 34)
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup
            container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_store_embeddings, container, false);
        mViewModel = new ViewModelProvider(requireActivity()).get(ServiceViewModel.class);
        mViewModel.getService().observe(getViewLifecycleOwner(), service -> {
            if (service != null) {
                Log.i(TAG, "connected to the service");
                mService = service;
                updateViewModel();

                mService.getTheMostFrequentlyVisitedPlaceDuringTheDay().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheDay(result);
                            updateEmbeddingsList();
                        });

                mService.getTheMostFrequentlyVisitedPlaceDuringTheNight().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheNight(result);
                            updateEmbeddingsList();
                        });

                mService.getTheMostFrequentlyVisitedPlaceDuringTheWeekend().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheWeekend(result);
                            updateEmbeddingsList();
                        });

                mService.getTheMostFrequentStationaryTime().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostFrequentStationaryTime(result);
                            updateEmbeddingsList();
                        });

                mService.getTheMostFrequentEnterpriseWifiConnectionTime().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostFrequentEnterpriseWifiConnectionTime(result);
                            updateEmbeddingsList();
                        });

                mService.getTheMostFrequentPersonalWifiConnectionTime().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostFrequentPersonalWifiConnectionTime(result);
                            updateEmbeddingsList();
                        });

                mService.getTheMostRecentCalendarAppEvent().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostRecentCalendarAppEvent(result);
                            updateEmbeddingsList();
                        });

                mService.getTheMostRecentEmailAppMessage().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostRecentEmailAppMessage(result);
                            updateEmbeddingsList();
                        });

                mService.getTheMostRecentMessagesAppMessage().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostRecentMessagesAppMessage(result);
                            updateEmbeddingsList();
                        });

                updateViews();
            } else {
                Log.i(TAG, "disconnected from the service");
                updateViews();
            }
        });

        Switch enableServiceSwitch = view.findViewById(R.id.enableServiceSwitch);
        Switch dayLocationSwitch = view.findViewById(R.id.dayLocationSwitch);
        Switch nightLocationSwitch = view.findViewById(R.id.nightLocationSwitch);
        Switch weekendLocationSwitch = view.findViewById(R.id.weekendLocationSwitch);
        Switch stationaryTimeSwitch = view.findViewById(R.id.stationaryTimeSwitch);
        Switch enterpriseWifiTimeSwitch = view.findViewById(R.id.enterpriseWifiTimeSwitch);
        Switch personalWifiTimeSwitch = view.findViewById(R.id.personalWifiTimeSwitch);
        Switch calendarAppEventSwitch = view.findViewById(R.id.calendarAppEventSwitch);
        Switch emailAppMessageSwitch = view.findViewById(R.id.emailAppMessageSwitch);
        Switch messagesAppMessageSwitch = view.findViewById(R.id.messagesAppMessageSwitch);
        Button resetButton = view.findViewById(R.id.resetDatabaseButton);

        enableServiceSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setServiceEnabled(isChecked)) {
                updateViews();
            } else {
                Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        dayLocationSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setDayLocationEnabled(isChecked)) {
                updateViews();
            } else {
                Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        nightLocationSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setNightLocationEnabled(isChecked)) {
                updateViews();
            } else {
                Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        weekendLocationSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setWeekendLocationEnabled(isChecked)) {
                updateViews();
            } else {
                Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        stationaryTimeSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setStationaryTimeEnabled(isChecked)) {
                updateViews();
            } else {
                Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        enterpriseWifiTimeSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setEnterpriseWifiTimeEnabled(isChecked)) {
                updateViews();
            } else {
                Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        personalWifiTimeSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setPersonalWifiTimeEnabled(isChecked)) {
                updateViews();
            } else {
                Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        calendarAppEventSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setCalendarAppEventEnabled(isChecked)) {
                updateViews();
            } else {
                Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        emailAppMessageSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setEmailAppMessageEnabled(isChecked)) {
                updateViews();
            } else {
                Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        messagesAppMessageSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setMessagesAppMessageEnabled(isChecked)) {
                updateViews();
            } else {
                Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        resetButton.setOnClickListener(view1 -> {
            if (mService != null) mService.deleteAll();
            updateViews();
        });

        enableServiceSwitch.setChecked(false);
        return view;
    }

    private void updateViewModel() {
        if (mService == null) return;
        mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheDay(
                mService.getTheMostFrequentlyVisitedPlaceDuringTheDay().getValue());
        mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheWeekend(
                mService.getTheMostFrequentlyVisitedPlaceDuringTheNight().getValue());
        mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheWeekend(
                mService.getTheMostFrequentlyVisitedPlaceDuringTheWeekend().getValue());
        mViewModel.setLastTheMostFrequentStationaryTime(
                mService.getTheMostFrequentStationaryTime().getValue());
        mViewModel.setLastTheMostFrequentEnterpriseWifiConnectionTime(
                mService.getTheMostFrequentEnterpriseWifiConnectionTime().getValue());
        mViewModel.setLastTheMostFrequentPersonalWifiConnectionTime(
                mService.getTheMostFrequentPersonalWifiConnectionTime().getValue());
        mViewModel.setLastTheMostRecentCalendarAppEvent(
                mService.getTheMostRecentCalendarAppEvent().getValue());
        mViewModel.setLastTheMostRecentEmailAppMessage(
                mService.getTheMostRecentEmailAppMessage().getValue());
        mViewModel.setLastTheMostRecentMessagesAppMessage(
                mService.getTheMostRecentMessagesAppMessage().getValue());
    }

    private void updateEmbeddingsList() {
        try {
            FragmentActivity activity = getActivity();
            if (activity == null) return;
            if (!isStarted() || !isServiceEnabled()) return;
            TextView dayLocationView = activity.findViewById(R.id.dayLocationView);
            TextView nightLocationView = activity.findViewById(R.id.nightLocationView);
            TextView weekendLocationView = activity.findViewById(R.id.weekendLocationView);
            TextView stationaryTimeView = activity.findViewById(R.id.stationaryTimeView);
            TextView enterpriseWifiTimeView = activity.findViewById(R.id.enterpriseWifiTimeView);
            TextView personalWifiTimeView = activity.findViewById(R.id.personalWifiTimeView);
            TextView calendarAppEventView = activity.findViewById(R.id.calendarAppEventView);
            TextView emailAppMessageView = activity.findViewById(R.id.emailAppMessageView);
            TextView messagesAppMessageView = activity.findViewById(R.id.messagesAppMessageView);

            updateViewModel();

            dayLocationView.setText(
                    mViewModel.getLastTheMostFrequentlyVisitedPlaceDuringTheDay());
            nightLocationView.setText(
                    mViewModel.getLastTheMostFrequentlyVisitedPlaceDuringTheNight());
            weekendLocationView.setText(
                    mViewModel.getLastTheMostFrequentlyVisitedPlaceDuringTheWeekend());
            stationaryTimeView.setText(
                    mViewModel.getLastTheMostFrequentStationaryTime());
            enterpriseWifiTimeView.setText(
                    mViewModel.getLastTheMostFrequentEnterpriseWifiConnectionTime());
            personalWifiTimeView.setText(
                    mViewModel.getLastTheMostFrequentPersonalWifiConnectionTime());
            calendarAppEventView.setText(
                    mViewModel.getLastTheMostRecentCalendarAppEvent());
            emailAppMessageView.setText(
                    mViewModel.getLastTheMostRecentEmailAppMessage());
            messagesAppMessageView.setText(
                    mViewModel.getLastTheMostRecentMessagesAppMessage());
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    private boolean isServiceEnabled() {
        return mService != null && mService.isServiceEnabled();
    }

    private boolean isDayLocationEnabled() {
        return mService != null && mService.isDayLocationEnabled();
    }

    private boolean isNightLocationEnabled() {
        return mService != null && mService.isNightLocationEnabled();
    }

    private boolean isWeekendLocationEnabled() {
        return mService != null && mService.isWeekendLocationEnabled();
    }

    private boolean isStationaryTimeEnabled() {
        return mService != null && mService.isStationaryTimeEnabled();
    }

    private boolean isEnterpriseWifiTimeEnabled() {
        return mService != null && mService.isEnterpriseWifiTimeEnabled();
    }

    private boolean isPersonalWifiTimeEnabled() {
        return mService != null && mService.isPersonalWifiTimeEnabled();
    }

    private boolean isCalendarAppEventEnabled() {
        return mService != null && mService.isCalendarAppEventEnabled();
    }

    private boolean isEmailAppMessageEnabled() {
        return mService != null && mService.isEmailAppMessageEnabled();
    }

    private boolean isMessagesAppMessageEnabled() {
        return mService != null && mService.isMessagesAppMessageEnabled();
    }

    private boolean setServiceEnabled(boolean enabled) {
        return mService != null && mService.setServiceEnabled(enabled);
    }

    private boolean setDayLocationEnabled(boolean enabled) {
        return mService != null && mService.setDayLocationEnabled(enabled);
    }

    private boolean setNightLocationEnabled(boolean enabled) {
        return mService != null && mService.setNightLocationEnabled(enabled);
    }

    private boolean setWeekendLocationEnabled(boolean enabled) {
        return mService != null && mService.setWeekendLocationEnabled(enabled);
    }

    private boolean setStationaryTimeEnabled(boolean enabled) {
        return mService != null && mService.setStationaryTimeEnabled(enabled);
    }

    private boolean setEnterpriseWifiTimeEnabled(boolean enabled) {
        return mService != null && mService.setEnterpriseWifiTimeEnabled(enabled);
    }

    private boolean setPersonalWifiTimeEnabled(boolean enabled) {
        return mService != null && mService.setPersonalWifiTimeEnabled(enabled);
    }

    private boolean setCalendarAppEventEnabled(boolean enabled) {
        return mService != null && mService.setCalendarAppEventEnabled(enabled);
    }

    private boolean setEmailAppMessageEnabled(boolean enabled) {
        return mService != null && mService.setEmailAppMessageEnabled(enabled);
    }

    private boolean setMessagesAppMessageEnabled(boolean enabled) {
        return mService != null && mService.setMessagesAppMessageEnabled(enabled);
    }
}
