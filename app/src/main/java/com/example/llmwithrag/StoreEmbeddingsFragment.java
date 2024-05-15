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
            Switch enableServiceSwitch = activity.findViewById(R.id.enableServiceSwitch);
            TextView configureKnowledgeView = activity.findViewById(R.id.configureKnowledgeView);
            LinearLayout configureKnowledgeLayout = activity.findViewById(R.id.configureKnowledgeLayout);
            TextView showKnowledgeView = activity.findViewById(R.id.showKnowledgeView);
            LinearLayout showKnowledgeLayout = activity.findViewById(R.id.showKnowledgeLayout);
            Button resetDatabaseButton = activity.findViewById(R.id.resetDatabaseButton);

            enableServiceSwitch.setVisibility(mService != null ? View.VISIBLE : View.GONE);
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

                Switch publicWifiTimeSwitch = activity.findViewById(R.id.publicWifiTimeSwitch);
                publicWifiTimeSwitch.setChecked(isPublicWifiTimeEnabled());

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
                mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheDay(
                        mService.getTheMostFrequentlyVisitedPlaceDuringTheDay().getValue());
                mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheWeekend(
                        mService.getTheMostFrequentlyVisitedPlaceDuringTheNight().getValue());
                mViewModel.setLastTheMostFrequentlyVisitedPlaceDuringTheWeekend(
                        mService.getTheMostFrequentlyVisitedPlaceDuringTheWeekend().getValue());
                mViewModel.setLastTheMostFrequentStationaryTime(
                        mService.getTheMostFrequentStationaryTime().getValue());
                mViewModel.setLastTheMostFrequentPublicWifiConnectionTime(
                        mService.getTheMostFrequentPublicWifiConnectionTime().getValue());

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

                mService.getTheMostFrequentPublicWifiConnectionTime().observe(
                        getViewLifecycleOwner(),
                        result -> {
                            mViewModel.setLastTheMostFrequentPublicWifiConnectionTime(result);
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
        Switch publicWifiTimeSwitch = view.findViewById(R.id.publicWifiTimeSwitch);
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

        publicWifiTimeSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            if (setPublicWifiTimeEnabled(isChecked)) {
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

    private void updateEmbeddingsList() {
        try {
            FragmentActivity activity = getActivity();
            if (activity == null) return;
            if (!isStarted() || !isServiceEnabled()) return;
            TextView dayLocationView = activity.findViewById(R.id.dayLocationView);
            TextView nightLocationView = activity.findViewById(R.id.nightLocationView);
            TextView weekendLocationView = activity.findViewById(R.id.weekendLocationView);
            TextView stationaryTimeView = activity.findViewById(R.id.stationaryTimeView);
            TextView publicWifiTimeView = activity.findViewById(R.id.publicWifiTimeView);

            dayLocationView.setText(
                    mViewModel.getLastTheMostFrequentlyVisitedPlaceDuringTheDay());
            nightLocationView.setText(
                    mViewModel.getLastTheMostFrequentlyVisitedPlaceDuringTheNight());
            weekendLocationView.setText(
                    mViewModel.getLastTheMostFrequentlyVisitedPlaceDuringTheWeekend());
            stationaryTimeView.setText(
                    mViewModel.getLastTheMostFrequentStationaryTime());
            publicWifiTimeView.setText(
                    mViewModel.getLastTheMostFrequentPublicWifiConnectionTime());
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

    private boolean isPublicWifiTimeEnabled() {
        return mService != null && mService.isPublicWifiTimeEnabled();
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

    private boolean setPublicWifiTimeEnabled(boolean enabled) {
        return mService != null && mService.setPublicWifiTimeEnabled(enabled);
    }
}
