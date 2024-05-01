package com.example.llmwithrag;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CHANGE_WIFI_STATE;
import static android.Manifest.permission.RECORD_AUDIO;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
    private static final String KEY_DURING_THE_DAY = "during_the_day";
    private static final String KEY_DURING_THE_NIGHT = "during_the_night";
    private static final String KEY_DURING_THE_WEEKENDS = "during_the_weekends";
    private static final String KEY_STATIONARY_TIME = "stationary_time";
    private static final String KEY_PUBLIC_WIFI_TIME = "public_wifi_time";
    private static final long DELAY_PERIODIC_CHECK = 10000L;

    private EmbeddingViewModel viewModel;
    private TextView embeddingsInDatabaseView;
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private IMonitoringService mService;
    private Handler mHandler;
    private Runnable mCheckRunnable;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            mService = ((MonitoringService.LocalBinder) binder).getService();
            mService.startMonitoring();

            FragmentActivity activity = getActivity();
            if (activity == null) return;

            Switch enableServiceSwitch = activity.findViewById(R.id.enableServiceSwitch);
            enableServiceSwitch.setChecked(true);

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
            Context context = getContext();
            if (context == null) return;
            SharedPreferences sharedPreferences = context
                    .getSharedPreferences(NAME_SHARED_PREFS, Context.MODE_PRIVATE);
            if (sharedPreferences == null) return;
            if (!sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, false)) {
                Log.i(TAG, "update knowledge : service is disabled");
                return;
            }

            FragmentActivity activity = getActivity();
            if (activity == null) return;

            boolean isDuringTheDayEnabled = sharedPreferences.getBoolean(KEY_DURING_THE_DAY, true);
            boolean isDuringTheNightEnabled = sharedPreferences.getBoolean(KEY_DURING_THE_NIGHT, true);
            boolean isDuringTheWeekendEnabled = sharedPreferences.getBoolean(KEY_DURING_THE_WEEKENDS, true);
            boolean isStationaryTimeEnabled = sharedPreferences.getBoolean(KEY_STATIONARY_TIME, true);
            boolean isPublicWifiTimeEnabled = sharedPreferences.getBoolean(KEY_PUBLIC_WIFI_TIME, true);

            Log.i(TAG, "update knowledge, " + isDuringTheDayEnabled + ", " +
                    isDuringTheNightEnabled + ", " + isDuringTheWeekendEnabled + ", " +
                    isStationaryTimeEnabled + ", " + isPublicWifiTimeEnabled);

            if (updateSwitch) {
                Switch duringTheDaySwitch = activity.findViewById(R.id.duringTheDaySwitch);
                duringTheDaySwitch.setChecked(isDuringTheDayEnabled);
                Switch duringTheNightSwitch = activity.findViewById(R.id.duringTheNightSwitch);
                duringTheNightSwitch.setChecked(isDuringTheNightEnabled);
                Switch duringTheWeekendSwitch = activity.findViewById(R.id.duringTheWeekendSwitch);
                duringTheWeekendSwitch.setChecked(isDuringTheWeekendEnabled);
                Switch stationaryTimeSwitch = activity.findViewById(R.id.stationaryTimeSwitch);
                stationaryTimeSwitch.setChecked(isStationaryTimeEnabled);
                Switch publicWifiTimeSwitch = activity.findViewById(R.id.publicWifiTimeSwitch);
                publicWifiTimeSwitch.setChecked(isPublicWifiTimeEnabled);
            }

            updateDuringTheDay(isDuringTheDayEnabled);
            updateDuringTheNight(isDuringTheNightEnabled);
            updateDuringTheWeekend(isDuringTheWeekendEnabled);
            updateStationaryTime(isStationaryTimeEnabled);
            updatePublicWifiTime(isPublicWifiTimeEnabled);
        }
    };

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
        requestPermissionLauncher = registerForActivityResult(
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup
            container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_store_embeddings, container, false);
        embeddingsInDatabaseView = view.findViewById(R.id.embeddingsInDatabaseView);
        viewModel = new ViewModelProvider(this).get(EmbeddingViewModel.class);

        Switch enableServiceSwitch = view.findViewById(R.id.enableServiceSwitch);
        TextView configureKnowledgeView = view.findViewById(R.id.configureDatabaseView);
        LinearLayout configureKnowledgeSwitch = view.findViewById(R.id.configureKnowledgeLayout);
        TextView configureDatabaseView = view.findViewById(R.id.configureKnowledgeView);
        LinearLayout configureDatabaseSwitch = view.findViewById(R.id.configureDatabaseLayout);
        TextView showKnowledgeView = view.findViewById(R.id.showKnowledgeView);
        LinearLayout showKnowledgeSwitch = view.findViewById(R.id.showKnowledgeLayout);

        Switch duringTheDaySwitch = view.findViewById(R.id.duringTheDaySwitch);
        Switch duringTheNightSwitch = view.findViewById(R.id.duringTheNightSwitch);
        Switch duringTheWeekendSwitch = view.findViewById(R.id.duringTheWeekendSwitch);
        Switch stationaryTimeSwitch = view.findViewById(R.id.stationaryTimeSwitch);
        Switch publicWifiTimeSwitch = view.findViewById(R.id.publicWifiTimeSwitch);
        Button resetButton = view.findViewById(R.id.resetDatabaseButton);

        String[] permissions = new String[]{
                ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, CHANGE_WIFI_STATE, RECORD_AUDIO
        };

        enableServiceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                setSharedPreferences(KEY_SERVICE_ENABLED, isChecked);
                if (isChecked) {
                    configureKnowledgeView.setVisibility(View.VISIBLE);
                    configureKnowledgeSwitch.setVisibility(View.VISIBLE);
                    configureDatabaseView.setVisibility(View.VISIBLE);
                    configureDatabaseSwitch.setVisibility(View.VISIBLE);
                    showKnowledgeView.setVisibility(View.VISIBLE);
                    showKnowledgeSwitch.setVisibility(View.VISIBLE);

                    if (isPermissionGranted(permissions)) {
                        bindToMonitoringService();
                    } else {
                        requestPermissionLauncher.launch(permissions);
                    }
                } else {
                    configureKnowledgeView.setVisibility(View.GONE);
                    configureKnowledgeSwitch.setVisibility(View.GONE);
                    configureDatabaseView.setVisibility(View.GONE);
                    configureDatabaseSwitch.setVisibility(View.GONE);
                    showKnowledgeView.setVisibility(View.GONE);
                    showKnowledgeSwitch.setVisibility(View.GONE);

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

        duringTheDaySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                setSharedPreferences(KEY_DURING_THE_DAY, isChecked);
                updateDuringTheDay(isChecked);
            }
        });

        duringTheNightSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                setSharedPreferences(KEY_DURING_THE_NIGHT, isChecked);
                updateDuringTheNight(isChecked);
            }
        });

        duringTheWeekendSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                setSharedPreferences(KEY_DURING_THE_WEEKENDS, isChecked);
                updateDuringTheWeekend(isChecked);
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

        updateEmbeddingsList();
        enableServiceSwitch.setChecked(false);
        return view;
    }

    private void updateDuringTheDay(boolean isChecked) {
        if (mService == null) return;
        if (isChecked) {
            String result = "Not Available Yet";
            List<String> results = mService.getMostFrequentlyVisitedPlacesDuringTheDay(1);
            if (!results.isEmpty()) {
                result = results.get(0);
            }
            String text = getString(R.string.during_the_day) + " is " + result;
            addEmbeddings(text, "location_during_the_day");
        } else {
            removeEmbeddings("location_during_the_day");
        }
    }

    private void updateDuringTheNight(boolean isChecked) {
        if (mService == null) return;
        if (isChecked) {
            String result = "Not Available Yet";
            List<String> results = mService.getMostFrequentlyVisitedPlacesDuringTheNight(1);
            if (!results.isEmpty()) {
                result = results.get(0);
            }
            String text = getString(R.string.during_the_night) + " is " + result;
            addEmbeddings(text, "location_during_the_night");
        } else {
            removeEmbeddings("location_during_the_night");
        }
    }

    private void updateDuringTheWeekend(boolean isChecked) {
        if (mService == null) return;
        if (isChecked) {
            String result = "Not Available Yet";
            List<String> results = mService.getMostFrequentlyVisitedPlacesDuringTheWeekend(1);
            if (!results.isEmpty()) {
                result = results.get(0);
            }
            String text = getString(R.string.weekends_location) + " is " + result;
            addEmbeddings(text, "weekends_location");
        } else {
            removeEmbeddings("weekends_location");
        }
    }

    private void updateStationaryTime(boolean isChecked) {
        if (mService == null) return;
        if (isChecked) {
            String result = "Not Available Yet";
            List<String> results = mService.getMostFrequentStationaryTimes(1);
            if (!results.isEmpty()) {
                result = results.get(0);
            }
            String text = getString(R.string.stationary_time) + " is " + result;
            addEmbeddings(text, "stationary_time");
        } else {
            removeEmbeddings("stationary_time");
        }
    }

    private void updatePublicWifiTime(boolean isChecked) {
        if (mService == null) return;
        if (isChecked) {
            String result = "Not Found Yet";
            List<String> results = mService.getMostFrequentPublicWifiConnectionTimes(1);
            if (!results.isEmpty()) {
                result = results.get(0);
            }
            String text = getString(R.string.public_wifi) + " is " + result;
            addEmbeddings(text, "wifi_connected");
        } else {
            removeEmbeddings("wifi_connected");
        }
    }

    private void updateEmbeddingsList() {
        List<Embedding> embeddings = viewModel.getAll();
        if (embeddings.isEmpty()) {
            embeddingsInDatabaseView.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Embedding embedding : embeddings) {
            sb.append("* ").append(embedding.text).append("\n");
        }
        embeddingsInDatabaseView.setText(sb.toString());
    }

    private boolean hasText(String text) {
        List<Embedding> embeddings = viewModel.getAll();
        if (embeddings.isEmpty()) return false;
        for (Embedding embedding : embeddings) {
            if (TextUtils.equals(text, embedding.text)) {
                return true;
            }
        }
        return false;
    }

    private void addEmbeddings(String text, String category) {
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
                    viewModel.insert(new Embedding(text, category, embedding));
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
        List<Embedding> embeddings = viewModel.getAll();
        if (embeddings.isEmpty()) return;
        for (Embedding embedding : embeddings) {
            if (TextUtils.equals(category, embedding.category)) {
                viewModel.delete(embedding);
                Log.i(TAG, "embeddings deleted for " + embedding.text);
            }
        }
        updateEmbeddingsList();
    }

    private void removeAllEmbeddings() {
        Log.i(TAG, "embeddings deleted for all");
        viewModel.deleteAll();
        updateEmbeddingsList();
    }
}
