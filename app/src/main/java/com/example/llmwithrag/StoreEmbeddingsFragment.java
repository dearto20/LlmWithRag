package com.example.llmwithrag;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StoreEmbeddingsFragment extends Fragment {
    private static final String TAG = StoreEmbeddingsFragment.class.getSimpleName();
    private EmbeddingViewModel viewModel;
    private TextView embeddingsInDatabaseView;
    private IMonitoringService mService;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            mService = ((MonitoringService.LocalBinder) binder).getService();
            mService.startMonitoring();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            if (mService != null) mService.stopMonitoring();
            mService = null;
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(getActivity(), MonitoringService.class);
        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_store_embeddings, container, false);
        embeddingsInDatabaseView = view.findViewById(R.id.embeddingsInDatabaseView);
        viewModel = new ViewModelProvider(this).get(EmbeddingViewModel.class);

        Button startMonitoringDataSourceButton = view.findViewById(R.id.startMonitoringDataSourceButton);
        startMonitoringDataSourceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.startMonitoring();
            }
        });

        Button stopMonitoringDataSourceButton = view.findViewById(R.id.stopMonitoringDataSourceButton);
        stopMonitoringDataSourceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.stopMonitoring();
            }
        });

        Button addLocationDuringTheDayButton = view.findViewById(R.id.addLocationDuringTheDayButton);
        addLocationDuringTheDayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String result = getString(R.string.during_the_day) + " is " +
                        mService.getMostFrequentlyVisitedPlacesDuringTheDay(1).get(0);
                removeEmbeddings("location_during_the_day");
                addEmbeddings(result, "location_during_the_day");
            }
        });

        Button removeLocationDuringTheDayButton = view.findViewById(R.id.removeLocationDuringTheDayButton);
        removeLocationDuringTheDayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeEmbeddings("location_during_the_day");
            }
        });

        Button addLocationDuringTheNightButton = view.findViewById(R.id.addLocationDuringTheNightButton);
        addLocationDuringTheNightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String result = getString(R.string.during_the_night) + " is " +
                        mService.getMostFrequentlyVisitedPlacesDuringTheNight(1).get(0);
                removeEmbeddings("location_during_the_night");
                addEmbeddings(result, "location_during_the_night");
            }
        });

        Button removeLocationDuringTheNightButton = view.findViewById(R.id.removeLocationDuringTheNightButton);
        removeLocationDuringTheNightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeEmbeddings("location_during_the_night");
            }
        });

        Button addWeekendsLocationButton = view.findViewById(R.id.addWeekendsLocationButton);
        addWeekendsLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String result = getString(R.string.weekends_location) + " is " +
                        mService.getMostFrequentlyVisitedPlacesDuringTheWeekend(1).get(0);
                removeEmbeddings("weekends_location");
                addEmbeddings(result, "weekends_location");
            }
        });

        Button removeWeekendsLocationButton = view.findViewById(R.id.removeWeekendsLocationButton);
        removeWeekendsLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeEmbeddings("weekends_location");
            }
        });

        Button addStationaryTimeButton = view.findViewById(R.id.addStationaryTimeButton);
        addStationaryTimeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String result = getString(R.string.stationary_time) + " is " +
                        mService.getMostFrequentStationaryTimes(1).get(0);
                removeEmbeddings("stationary_time");
                addEmbeddings(result, "stationary_time");
            }
        });

        Button removeStationaryTimeButton = view.findViewById(R.id.removeStationaryTimeButton);
        removeStationaryTimeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeEmbeddings("stationary_time");
            }
        });

        Button addPublicWiFiButton = view.findViewById(R.id.addPublicWiFiButton);
        addPublicWiFiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String result = getString(R.string.public_wifi) + " is " +
                        mService.getMostFrequentPublicWifiConnectionTimes(1).get(0);
                removeEmbeddings("wifi_connected");
                addEmbeddings(result, "wifi_connected");
            }
        });

        Button removePublicWiFiButton = view.findViewById(R.id.removePublicWiFiButton);
        removePublicWiFiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeEmbeddings("wifi_connected");
            }
        });

        Button resetButton = view.findViewById(R.id.resetDatabaseButton);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.deleteAll();
                removeAllEmbeddings();
            }
        });

        updateEmbeddingsList();
        return view;
    }

    private void updateEmbeddingsList() {
        List<Embedding> embeddings = viewModel.getAll();
        if (embeddings.isEmpty()) {
            embeddingsInDatabaseView.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Embedding embedding : embeddings) {
            sb.append("- ").append(embedding.text).append("\n");
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
