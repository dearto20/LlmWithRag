package com.example.llmwithrag;

import android.os.Bundle;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_store_embeddings, container, false);
        embeddingsInDatabaseView = view.findViewById(R.id.embeddingsInDatabaseView);
        viewModel = new ViewModelProvider(this).get(EmbeddingViewModel.class);
        String location = " is \"Seongnam-si\"";
        String duration = " is \"Overnight\"";
        String time = " is \"from 8 PM to 11 PM\"";
        String app = " is \"GalaxyNav\"";

        Button addOvernightLocationButton = view.findViewById(R.id.addOvernightLocationButton);
        addOvernightLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addEmbeddings(getString(R.string.overnight_location) + location);
            }
        });

        Button removeOvernightLocationButton = view.findViewById(R.id.removeOvernightLocationButton);
        removeOvernightLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeEmbeddings(getString(R.string.overnight_location) + location);
            }
        });

        Button addWeekendsLocationButton = view.findViewById(R.id.addWeekendsLocationButton);
        addWeekendsLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addEmbeddings(getString(R.string.weekends_location) + location);
            }
        });

        Button removeWeekendsLocationButton = view.findViewById(R.id.removeWeekendsLocationButton);
        removeWeekendsLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeEmbeddings(getString(R.string.weekends_location) + location);
            }
        });

        Button addStationaryTimeButton = view.findViewById(R.id.addStationaryTimeButton);
        addStationaryTimeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addEmbeddings(getString(R.string.stationary_time) + duration);
            }
        });

        Button removeStationaryTimeButton = view.findViewById(R.id.removeStationaryTimeButton);
        removeStationaryTimeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeEmbeddings(getString(R.string.stationary_time) + duration);
            }
        });

        Button addPublicWiFiButton = view.findViewById(R.id.addPublicWiFiButton);
        addPublicWiFiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addEmbeddings(getString(R.string.public_wifi) + time);
            }
        });

        Button removePublicWiFiButton = view.findViewById(R.id.removePublicWiFiButton);
        removePublicWiFiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeEmbeddings(getString(R.string.public_wifi) + time);
            }
        });

        Button addPreferredNavigationButton = view.findViewById(R.id.addPreferredNavigationButton);
        addPreferredNavigationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addEmbeddings(getString(R.string.preferred_navigation) + app);
            }
        });

        Button removePreferredNavigationButton = view.findViewById(R.id.removePreferredNavigationButton);
        removePreferredNavigationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeEmbeddings(getString(R.string.preferred_navigation) + app);
            }
        });

        Button resetButton = view.findViewById(R.id.resetButton);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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

    private void addEmbeddings(String text) {
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
                    viewModel.insert(new Embedding(text, embedding));
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

    private void removeEmbeddings(String text) {
        List<Embedding> embeddings = viewModel.getAll();
        if (embeddings.isEmpty()) return;
        for (Embedding embedding : embeddings) {
            if (TextUtils.equals(text, embedding.text)) {
                viewModel.delete(embedding);
                Log.i(TAG, "embeddings deleted for " + text);
                updateEmbeddingsList();
                break;
            }
        }
    }

    private void removeAllEmbeddings() {
        Log.i(TAG, "embeddings deleted for all");
        viewModel.deleteAll();
        updateEmbeddingsList();
    }
}
