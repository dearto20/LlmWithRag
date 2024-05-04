package com.example.llmwithrag;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CHANGE_WIFI_STATE;
import static android.Manifest.permission.FOREGROUND_SERVICE_LOCATION;
import static android.Manifest.permission.RECORD_AUDIO;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.llmwithrag.llm.Embedding;
import com.example.llmwithrag.llm.EmbeddingRequest;
import com.example.llmwithrag.llm.EmbeddingResponse;
import com.example.llmwithrag.llm.OpenAiService;
import com.example.llmwithrag.llm.RetrofitClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@SuppressLint("UseSwitchCompatOrMaterialCode")
public class StoreEmbeddingsFragment extends Fragment {
    private static final String TAG = StoreEmbeddingsFragment.class.getSimpleName();
    private static final String NAME_SHARED_PREFS = "default_storage";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String KEY_DAY_LOCATION = "key_day_location";
    private static final String KEY_NIGHT_LOCATION = "key_night_location";
    private static final String KEY_WEEKEND_LOCATION = "key_weekend_location";
    private static final String KEY_STATIONARY_TIME = "stationary_time";
    private static final String KEY_PUBLIC_WIFI_TIME = "public_wifi_time";
    private static final long DELAY_PERIODIC_CHECK = 10000L;

    private EmbeddingViewModel mViewModel;
    private TextView mEmbeddingsInDatabaseView;
    private ActivityResultLauncher<String[]> mRequestPermissionLauncher;
    private IMonitoringService mService;
    private Handler mHandler;
    private Runnable mCheckRunnable;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            Log.i(TAG, "connected to the service");
            mService = ((MonitoringService.LocalBinder) binder).getService();
            mService.startMonitoring();

            FragmentActivity activity = getActivity();
            if (activity == null) return;
            SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
            if (sharedPreferences != null) {
                boolean enabled = sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, false);
                Switch enableServiceSwitch = activity.findViewById(R.id.enableServiceSwitch);
                if (enabled != enableServiceSwitch.isChecked()) {
                    enableServiceSwitch.setChecked(enabled);
                }
            }

            mHandler = new Handler(Looper.getMainLooper());
            mCheckRunnable = new Runnable() {
                @Override
                public void run() {
                    updateKnowledge();
                    mHandler.postDelayed(mCheckRunnable, DELAY_PERIODIC_CHECK);
                }
            };
            mHandler.postDelayed(mCheckRunnable, DELAY_PERIODIC_CHECK);
            updateKnowledge(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG, "disconnected from the service");
            mCheckRunnable = null;
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
            if (mService != null) mService.stopMonitoring();
            mService = null;
        }

        private void updateKnowledge() {
            updateKnowledge(false);
        }

        private void updateKnowledge(boolean updateSwitch) {
            updateViews();

            SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
            if (sharedPreferences == null) {
                return;
            }

            updateDayLocation(sharedPreferences.getBoolean(KEY_DAY_LOCATION, true));
            updateNightLocation(sharedPreferences.getBoolean(KEY_NIGHT_LOCATION, true));
            updateWeekendLocation(sharedPreferences.getBoolean(KEY_WEEKEND_LOCATION, true));
            updateStationaryTime(sharedPreferences.getBoolean(KEY_STATIONARY_TIME, true));
            updatePublicWifiTime(sharedPreferences.getBoolean(KEY_PUBLIC_WIFI_TIME, true));
        }
    };

    private void updateViews() {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        if (sharedPreferences == null) {
            return;
        }

        TextView configureKnowledgeView = activity.findViewById(R.id.configureKnowledgeView);
        LinearLayout configureKnowledgeLayout = activity.findViewById(R.id.configureKnowledgeLayout);
        TextView showKnowledgeView = activity.findViewById(R.id.showKnowledgeView);
        LinearLayout showKnowledgeLayout = activity.findViewById(R.id.showKnowledgeLayout);
        Button resetDatabaseButton = activity.findViewById(R.id.resetDatabaseButton);

        boolean enabled = sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, false);
        if (enabled) {
            configureKnowledgeView.setVisibility(View.VISIBLE);
            configureKnowledgeLayout.setVisibility(View.VISIBLE);
            showKnowledgeView.setVisibility(View.VISIBLE);
            showKnowledgeLayout.setVisibility(View.VISIBLE);
            resetDatabaseButton.setVisibility(View.VISIBLE);

            boolean isDayLocationEnabled = sharedPreferences.getBoolean(KEY_DAY_LOCATION, true);
            boolean isNightLocationEnabled = sharedPreferences.getBoolean(KEY_NIGHT_LOCATION, true);
            boolean isWeekendLocationEnabled = sharedPreferences.getBoolean(KEY_WEEKEND_LOCATION, true);
            boolean isStationaryTimeEnabled = sharedPreferences.getBoolean(KEY_STATIONARY_TIME, true);
            boolean isPublicWifiTimeEnabled = sharedPreferences.getBoolean(KEY_PUBLIC_WIFI_TIME, true);

            Switch dayLocationSwitch = activity.findViewById(R.id.dayLocationSwitch);
            if (isDayLocationEnabled != dayLocationSwitch.isChecked()) {
                dayLocationSwitch.setChecked(isDayLocationEnabled);
            }

            Switch nightLocationSwitch = activity.findViewById(R.id.nightLocationSwitch);
            if (isNightLocationEnabled != nightLocationSwitch.isChecked()) {
                nightLocationSwitch.setChecked(isNightLocationEnabled);
            }

            Switch weekendLocationSwitch = activity.findViewById(R.id.weekendLocationSwitch);
            if (isWeekendLocationEnabled != weekendLocationSwitch.isChecked()) {
                weekendLocationSwitch.setChecked(isWeekendLocationEnabled);
            }

            Switch stationaryTimeSwitch = activity.findViewById(R.id.stationaryTimeSwitch);
            if (isStationaryTimeEnabled != stationaryTimeSwitch.isChecked()) {
                stationaryTimeSwitch.setChecked(isStationaryTimeEnabled);
            }

            Switch publicWifiTimeSwitch = activity.findViewById(R.id.publicWifiTimeSwitch);
            if (isPublicWifiTimeEnabled != publicWifiTimeSwitch.isChecked()) {
                publicWifiTimeSwitch.setChecked(isPublicWifiTimeEnabled);
            }
        } else {
            configureKnowledgeView.setVisibility(View.GONE);
            configureKnowledgeLayout.setVisibility(View.GONE);
            showKnowledgeView.setVisibility(View.GONE);
            showKnowledgeLayout.setVisibility(View.GONE);
            resetDatabaseButton.setVisibility(View.GONE);
        }

        updateEmbeddingsList();
    }

    private SharedPreferences getSharedPreferences(String name) {
        Context context = getContext();
        if (context == null) return null;
        return context.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    private boolean setSharedPreferences(String key, boolean value) {
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        if (sharedPreferences == null) return false;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
        return true;
    }

    private boolean isPermissionGranted(String[] permissions) {
        if (permissions == null || permissions.length == 0) return false;
        Context context = getContext();
        if (context == null) return false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void bindToMonitoringService() {
        Intent serviceIntent = new Intent(getContext(), MonitoringService.class);
        ContextCompat.startForegroundService(requireContext(), serviceIntent);

        Intent intent = new Intent(getActivity(), MonitoringService.class);
        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        FragmentActivity activity = getActivity();
        if (activity == null) return;
        SharedPreferences sharedPreferences = getSharedPreferences(NAME_SHARED_PREFS);
        if (sharedPreferences != null) {
            boolean enabled = sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, false);
            Switch enableServiceSwitch = getActivity().findViewById(R.id.enableServiceSwitch);
            enableServiceSwitch.setChecked(enabled);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mService != null) {
            FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.unbindService(mConnection);
            }
            mService = null;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRequestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    boolean allPermissionsGranted = true;
                    for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                        if (!entry.getValue()) {
                            allPermissionsGranted = false;
                            break;
                        }
                    }

                    if (allPermissionsGranted) {
                        bindToMonitoringService();
                    } else {
                        Toast.makeText(getContext(),
                                "Permission Denied", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateViews();
    }

    @RequiresApi(api = 34)
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup
            container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_store_embeddings, container, false);
        mViewModel = new ViewModelProvider(this).get(EmbeddingViewModel.class);

        Switch enableServiceSwitch = view.findViewById(R.id.enableServiceSwitch);
        Switch dayLocationSwitch = view.findViewById(R.id.dayLocationSwitch);
        Switch nightLocationSwitch = view.findViewById(R.id.nightLocationSwitch);
        Switch weekendLocationSwitch = view.findViewById(R.id.weekendLocationSwitch);
        Switch stationaryTimeSwitch = view.findViewById(R.id.stationaryTimeSwitch);
        Switch publicWifiTimeSwitch = view.findViewById(R.id.publicWifiTimeSwitch);
        Button resetButton = view.findViewById(R.id.resetDatabaseButton);

        String[] permissions;

        if (Build.VERSION.SDK_INT >= 34) {
            permissions = new String[]{
                    ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, CHANGE_WIFI_STATE, RECORD_AUDIO,
                    FOREGROUND_SERVICE_LOCATION
            };
        } else {
            permissions = new String[]{
                    ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, CHANGE_WIFI_STATE, RECORD_AUDIO
            };
        }

        enableServiceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                Log.i(TAG, "onCheckedChanged");
                setSharedPreferences(KEY_SERVICE_ENABLED, isChecked);
                updateViews();

                if (isChecked) {
                    if (isPermissionGranted(permissions)) {
                        bindToMonitoringService();
                    } else {
                        mRequestPermissionLauncher.launch(permissions);
                    }
                } else {
                    if (mService != null) {
                        mService.stopMonitoring();
                        Context context = getContext();
                        if (context == null) return;
                        context.unbindService(mConnection);
                        mService = null;
                    }
                }
            }
        });

        dayLocationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                setSharedPreferences(KEY_DAY_LOCATION, isChecked);
                updateDayLocation(isChecked);
            }
        });

        nightLocationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                setSharedPreferences(KEY_NIGHT_LOCATION, isChecked);
                updateNightLocation(isChecked);
            }
        });

        weekendLocationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                setSharedPreferences(KEY_WEEKEND_LOCATION, isChecked);
                updateWeekendLocation(isChecked);
            }
        });

        stationaryTimeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                setSharedPreferences(KEY_STATIONARY_TIME, isChecked);
                updateStationaryTime(isChecked);
            }
        });

        publicWifiTimeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                setSharedPreferences(KEY_PUBLIC_WIFI_TIME, isChecked);
                updatePublicWifiTime(isChecked);
            }
        });

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.deleteAll();
                removeAllEmbeddings();
            }
        });

        enableServiceSwitch.setChecked(false);
        return view;
    }

    private void updateDayLocation(boolean isChecked) {
        if (mService == null) return;
        if (isChecked) {
            String result = "Unavailable";
            List<String> results = mService.getMostFrequentlyVisitedPlacesDuringTheDay(1);
            if (!results.isEmpty()) {
                result = results.get(0);
            }
            String text = getString(R.string.day_location) + " is " + result;
            addEmbeddings(text, text, KEY_DAY_LOCATION);
        } else {
            removeEmbeddings(KEY_DAY_LOCATION);
        }
    }

    private void updateNightLocation(boolean isChecked) {
        if (mService == null) return;
        if (isChecked) {
            String result = "Unavailable";
            List<String> results = mService.getMostFrequentlyVisitedPlacesDuringTheNight(1);
            if (!results.isEmpty()) {
                result = results.get(0);
            }
            String text = getString(R.string.night_location) + " is " + result;
            addEmbeddings(text, text, KEY_NIGHT_LOCATION);
        } else {
            removeEmbeddings(KEY_NIGHT_LOCATION);
        }
    }

    private void updateWeekendLocation(boolean isChecked) {
        if (mService == null) return;
        if (isChecked) {
            String result = "Unavailable";
            List<String> results = mService.getMostFrequentlyVisitedPlacesDuringTheWeekend(1);
            if (!results.isEmpty()) {
                result = results.get(0);
            }
            String text = getString(R.string.weekend_location) + " is " + result;
            addEmbeddings(text, text, KEY_WEEKEND_LOCATION);
        } else {
            removeEmbeddings(KEY_WEEKEND_LOCATION);
        }
    }

    private void updateStationaryTime(boolean isChecked) {
        if (mService == null) return;
        if (isChecked) {
            String result = "Unavailable";
            List<String> results = mService.getMostFrequentStationaryTimes(1);
            if (!results.isEmpty()) {
                result = results.get(0);
            }
            String text = getString(R.string.stationary_time) + " is " + result;
            addEmbeddings(text, text, KEY_STATIONARY_TIME);
        } else {
            removeEmbeddings(KEY_STATIONARY_TIME);
        }
    }

    private void updatePublicWifiTime(boolean isChecked) {
        if (mService == null) return;
        if (isChecked) {
            String result = "Unavailable";
            List<String> results = mService.getMostFrequentPublicWifiConnectionTimes(1);
            if (!results.isEmpty()) {
                result = results.get(0);
            }
            String text = getString(R.string.public_wifi_time) + " is " + result;
            addEmbeddings(text, text, KEY_PUBLIC_WIFI_TIME);
        } else {
            removeEmbeddings(KEY_PUBLIC_WIFI_TIME);
        }
    }

    private void updateEmbeddingsList() {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        Switch enableServiceSwitch = activity.findViewById(R.id.enableServiceSwitch);
        if (enableServiceSwitch == null || !enableServiceSwitch.isChecked()) return;
        TextView dayLocationView = activity.findViewById(R.id.dayLocationView);
        TextView nightLocationView = activity.findViewById(R.id.nightLocationView);
        TextView weekendLocationView = activity.findViewById(R.id.weekendLocationView);
        TextView stationaryTimeView = activity.findViewById(R.id.stationaryTimeView);
        TextView publicWifiTimeView = activity.findViewById(R.id.publicWifiTimeView);
        List<Embedding> embeddings = mViewModel.getAll();
        if (embeddings.isEmpty()) {
            dayLocationView.setText(R.string.day_location_unavailable);
            nightLocationView.setText(R.string.night_location_unavailable);
            weekendLocationView.setText(R.string.weekend_location_unavailable);
            stationaryTimeView.setText(R.string.stationary_time_unavailable);
            publicWifiTimeView.setText(R.string.public_wifi_time_unavailable);
        } else {
            for (Embedding embedding : embeddings) {
                if (TextUtils.equals(KEY_DAY_LOCATION, embedding.category)) {
                    dayLocationView.setText(embedding.description);
                } else if (TextUtils.equals(KEY_NIGHT_LOCATION, embedding.category)) {
                    nightLocationView.setText(embedding.description);
                } else if (TextUtils.equals(KEY_WEEKEND_LOCATION, embedding.category)) {
                    weekendLocationView.setText(embedding.description);
                } else if (TextUtils.equals(KEY_STATIONARY_TIME, embedding.category)) {
                    stationaryTimeView.setText(embedding.description);
                } else if (TextUtils.equals(KEY_PUBLIC_WIFI_TIME, embedding.category)) {
                    publicWifiTimeView.setText(embedding.description);
                }
            }
        }
    }

    private boolean hasText(String text) {
        List<Embedding> embeddings = mViewModel.getAll();
        if (embeddings.isEmpty()) return false;
        for (Embedding embedding : embeddings) {
            if (TextUtils.equals(text, embedding.text)) {
                return true;
            }
        }
        return false;
    }

    private void addEmbeddings(String text, String description, String category) {
        if (hasText(text)) return;
        EmbeddingRequest request = new EmbeddingRequest(text, "text-embedding-3-small", "float");
        OpenAiService service = RetrofitClient.getInstance().create(OpenAiService.class);
        Call<EmbeddingResponse> call = service.getEmbedding(request);
        call.enqueue(new Callback<EmbeddingResponse>() {
            @Override
            public void onResponse(Call<EmbeddingResponse> call, Response<EmbeddingResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    float[] embedding = response.body().data.get(0).embedding;
                    Log.i(TAG, "response: " + Arrays.toString(embedding));
                    removeEmbeddings(category);
                    mViewModel.insert(new Embedding(text, description, category, embedding));
                    Log.i(TAG, "embeddings added for " + text);
                    updateEmbeddingsList();
                }
            }

            @Override
            public void onFailure(Call<EmbeddingResponse> call, Throwable t) {
                Log.e(TAG, "failed in fetching embeddings: " + t.getMessage());
            }
        });
    }

    private void removeEmbeddings(String category) {
        List<Embedding> embeddings = mViewModel.getAll();
        if (embeddings.isEmpty()) return;
        for (Embedding embedding : embeddings) {
            if (TextUtils.equals(category, embedding.category)) {
                mViewModel.delete(embedding);
                Log.i(TAG, "embeddings deleted for " + embedding.text);
            }
        }
        updateEmbeddingsList();
    }

    private void removeAllEmbeddings() {
        Log.i(TAG, "embeddings deleted for all");
        mViewModel.deleteAll();
        updateEmbeddingsList();
    }
}
