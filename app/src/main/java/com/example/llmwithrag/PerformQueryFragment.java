package com.example.llmwithrag;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.llmwithrag.llm.CompletionMessage;
import com.example.llmwithrag.llm.CompletionRequest;
import com.example.llmwithrag.llm.CompletionResponse;
import com.example.llmwithrag.llm.Embedding;
import com.example.llmwithrag.llm.EmbeddingRequest;
import com.example.llmwithrag.llm.EmbeddingResponse;
import com.example.llmwithrag.llm.OpenAiService;
import com.example.llmwithrag.llm.RetrofitClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PerformQueryFragment extends Fragment {
    private static final String TAG = PerformQueryFragment.class.getSimpleName();
    private EmbeddingViewModel viewModel;
    private TextView resultDisplay;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_perform_query, container, false);
        viewModel = new ViewModelProvider(this).get(EmbeddingViewModel.class);

        EditText queryInput = view.findViewById(R.id.queryInput);
        Button queryButton = view.findViewById(R.id.queryButton);
        resultDisplay = view.findViewById(R.id.resultDisplay);
        queryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String text = queryInput.getText().toString();
                queryInput.setText("");
                hideKeyboardFromFragment();
                resultDisplay.setText(R.string.wait_answer);
                Log.i(TAG, "perform query of " + text);
                performQuery(text);
            }
        });
        return view;
    }

    private Embedding fetchEmbeddings(String text) {
        EmbeddingRequest request = new EmbeddingRequest(text, "text-embedding-3-small", "float");
        OpenAiService service = RetrofitClient.getInstance().create(OpenAiService.class);
        Call<EmbeddingResponse> call = service.getEmbedding(request);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final Embedding[] result = {null};
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Response<EmbeddingResponse> response = call.execute();
                        if (response.isSuccessful() && response.body() != null) {
                            float[] embedding = response.body().data.get(0).embedding;
                            Log.i(TAG, "response: " + Arrays.toString(embedding));
                            result[0] = new Embedding(text, embedding);
                        }
                        countDownLatch.countDown();
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                        e.printStackTrace();
                    }
                }
            }).start();
            countDownLatch.await();
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return result[0];
    }

    private void hideKeyboardFromFragment() {
        Activity activity = getActivity();
        if (activity != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            View view = activity.getCurrentFocus();
            if (view != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private void runNaviApp(String destination) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.google.android.apps.maps", "com.google.android.maps.MapsActivity"));
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("google.navigation:q=" + Uri.encode(destination) + "&mode=r"));
        startActivity(intent);
    }

    private void performQuery(String query) {
        List<CompletionMessage> messages = new ArrayList<>();
        List<Embedding> embeddings = viewModel.findSimilarOnes(fetchEmbeddings(query));
        if (embeddings.size() > 0) {
            StringBuilder sb = new StringBuilder("now you're a navigation launcher, and see info below");
            for (Embedding embedding : embeddings) {
                sb.append("\n").append(embedding.text);
            }
            sb.append("\n").append(query);
            sb.append("\n").append("if asked finding route, first, figure out \"the destination\" based on the info");
            sb.append("\n").append("and only provide the address of the destination, and do not ever put any other comments or something");
            sb.append("\n").append("Make sure, you only return proper address, which could be used for opening GoogleMaps as is");
            query = sb.toString();
        }

        Log.i(TAG, "query: " + query);

        messages.add(new CompletionMessage("user", query));
        CompletionRequest request = new CompletionRequest("gpt-4-turbo", messages);
        OpenAiService service = RetrofitClient.getInstance().create(OpenAiService.class);
        Call<CompletionResponse> call = service.getCompletions(request);
        call.enqueue(new Callback<CompletionResponse>() {
            @Override
            public void onResponse(Call<CompletionResponse> call, Response<CompletionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String completion = response.body().choices.get(0).message.content;
                    Log.i(TAG, "response: " + completion);
                    resultDisplay.setText(completion);
                    runNaviApp(completion);
                }
            }

            @Override
            public void onFailure(Call<CompletionResponse> call, Throwable t) {
                Log.e(TAG, "failed in performing query: " + t.getMessage());
                resultDisplay.setText(t.getMessage());
            }
        });
    }
}
