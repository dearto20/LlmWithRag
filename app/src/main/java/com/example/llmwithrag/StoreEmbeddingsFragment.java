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
    private boolean DEBUG = false;

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

                mViewModel.getTheMostFrequentlyVisitedPlaceDuringTheDay().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheDay(result);
                            updateEmbeddingsList();
                        });

                mViewModel.getTheMostFrequentlyVisitedPlaceDuringTheNight().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheNight(result);
                            updateEmbeddingsList();
                        });

                mViewModel.getTheMostFrequentlyVisitedPlaceDuringTheWeekend().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheWeekend(result);
                            updateEmbeddingsList();
                        });

                mViewModel.getTheMostFrequentStationaryTime().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostFrequentStationaryTime(result);
                            updateEmbeddingsList();
                        });

                mViewModel.getTheMostFrequentEnterpriseWifiConnectionTime().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostFrequentEnterpriseWifiConnectionTime(result);
                            updateEmbeddingsList();
                        });

                mViewModel.getTheMostFrequentPersonalWifiConnectionTime().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostFrequentPersonalWifiConnectionTime(result);
                            updateEmbeddingsList();
                        });

                mViewModel.getTheMostRecentCalendarAppEvent().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostRecentCalendarAppEvent(result);
                            updateEmbeddingsList();
                        });

                mViewModel.getTheMostRecentEmailAppMessage().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostRecentEmailAppMessage(result);
                            updateEmbeddingsList();
                        });

                mViewModel.getTheMostRecentMessagesAppMessage().observe(
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
                if (DEBUG) Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        dayLocationSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setDayLocationEnabled(isChecked)) {
                updateViews();
            } else {
                if (DEBUG) Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        nightLocationSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setNightLocationEnabled(isChecked)) {
                updateViews();
            } else {
                if (DEBUG) Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        weekendLocationSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setWeekendLocationEnabled(isChecked)) {
                updateViews();
            } else {
                if (DEBUG) Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        stationaryTimeSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setStationaryTimeEnabled(isChecked)) {
                updateViews();
            } else {
                if (DEBUG) Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        enterpriseWifiTimeSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setEnterpriseWifiTimeEnabled(isChecked)) {
                updateViews();
            } else {
                if (DEBUG) Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        personalWifiTimeSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setPersonalWifiTimeEnabled(isChecked)) {
                updateViews();
            } else {
                if (DEBUG) Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        calendarAppEventSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setCalendarAppEventEnabled(isChecked)) {
                updateViews();
            } else {
                if (DEBUG) Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        emailAppMessageSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setEmailAppMessageEnabled(isChecked)) {
                updateViews();
            } else {
                if (DEBUG) Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
            }
        });

        messagesAppMessageSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setMessagesAppMessageEnabled(isChecked)) {
                updateViews();
            } else {
                if (DEBUG) Toast.makeText(getContext(), "Try Again", Toast.LENGTH_SHORT).show();
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
        if (mViewModel == null) return;
        mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheDay(
                mViewModel.getTheMostFrequentlyVisitedPlaceDuringTheDay().getValue());
        mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheWeekend(
                mViewModel.getTheMostFrequentlyVisitedPlaceDuringTheNight().getValue());
        mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheWeekend(
                mViewModel.getTheMostFrequentlyVisitedPlaceDuringTheWeekend().getValue());
        mViewModel.setLastTheMostFrequentStationaryTime(
                mViewModel.getTheMostFrequentStationaryTime().getValue());
        mViewModel.setLastTheMostFrequentEnterpriseWifiConnectionTime(
                mViewModel.getTheMostFrequentEnterpriseWifiConnectionTime().getValue());
        mViewModel.setLastTheMostFrequentPersonalWifiConnectionTime(
                mViewModel.getTheMostFrequentPersonalWifiConnectionTime().getValue());
        mViewModel.setLastTheMostRecentCalendarAppEvent(
                mViewModel.getTheMostRecentCalendarAppEvent().getValue());
        mViewModel.setLastTheMostRecentEmailAppMessage(
                mViewModel.getTheMostRecentEmailAppMessage().getValue());
        mViewModel.setLastTheMostRecentMessagesAppMessage(
                mViewModel.getTheMostRecentMessagesAppMessage().getValue());
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
        return mViewModel != null && mViewModel.isServiceEnabled();
    }

    private boolean isDayLocationEnabled() {
        return mViewModel != null && mViewModel.isDayLocationEnabled();
    }

    private boolean isNightLocationEnabled() {
        return mViewModel != null && mViewModel.isNightLocationEnabled();
    }

    private boolean isWeekendLocationEnabled() {
        return mViewModel != null && mViewModel.isWeekendLocationEnabled();
    }

    private boolean isStationaryTimeEnabled() {
        return mViewModel != null && mViewModel.isStationaryTimeEnabled();
    }

    private boolean isEnterpriseWifiTimeEnabled() {
        return mViewModel != null && mViewModel.isEnterpriseWifiTimeEnabled();
    }

    private boolean isPersonalWifiTimeEnabled() {
        return mViewModel != null && mViewModel.isPersonalWifiTimeEnabled();
    }

    private boolean isCalendarAppEventEnabled() {
        return mViewModel != null && mViewModel.isCalendarAppEventEnabled();
    }

    private boolean isEmailAppMessageEnabled() {
        return mViewModel != null && mViewModel.isEmailAppMessageEnabled();
    }

    private boolean isMessagesAppMessageEnabled() {
        return mViewModel != null && mViewModel.isMessagesAppMessageEnabled();
    }

    private boolean setServiceEnabled(boolean enabled) {
        return mViewModel != null && mViewModel.setServiceEnabled(enabled);
    }

    private boolean setDayLocationEnabled(boolean enabled) {
        return mViewModel != null && mViewModel.setDayLocationEnabled(enabled);
    }

    private boolean setNightLocationEnabled(boolean enabled) {
        return mViewModel != null && mViewModel.setNightLocationEnabled(enabled);
    }

    private boolean setWeekendLocationEnabled(boolean enabled) {
        return mViewModel != null && mViewModel.setWeekendLocationEnabled(enabled);
    }

    private boolean setStationaryTimeEnabled(boolean enabled) {
        return mViewModel != null && mViewModel.setStationaryTimeEnabled(enabled);
    }

    private boolean setEnterpriseWifiTimeEnabled(boolean enabled) {
        return mViewModel != null && mViewModel.setEnterpriseWifiTimeEnabled(enabled);
    }

    private boolean setPersonalWifiTimeEnabled(boolean enabled) {
        return mViewModel != null && mViewModel.setPersonalWifiTimeEnabled(enabled);
    }

    private boolean setCalendarAppEventEnabled(boolean enabled) {
        return mViewModel != null && mViewModel.setCalendarAppEventEnabled(enabled);
    }

    private boolean setEmailAppMessageEnabled(boolean enabled) {
        return mViewModel != null && mViewModel.setEmailAppMessageEnabled(enabled);
    }

    private boolean setMessagesAppMessageEnabled(boolean enabled) {
        return mViewModel != null && mViewModel.setMessagesAppMessageEnabled(enabled);
    }
}
