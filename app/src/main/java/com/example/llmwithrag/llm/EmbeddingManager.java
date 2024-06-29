package com.example.llmwithrag.llm;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.example.llmwithrag.LlmWithRagApplication;
import com.example.llmwithrag.MonitoringService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EmbeddingManager {
    private static final String TAG = EmbeddingManager.class.getSimpleName();
    private final Context mContext;
    private final EmbeddingRepository mRepository;

    public EmbeddingManager(Context context) {
        mContext = context;
        mRepository = new EmbeddingRepository(LlmWithRagApplication.getInstance());
    }

    public void insert(Embedding embedding) {
        mRepository.insert(embedding);
    }

    public void delete(Embedding embedding) {
        mRepository.delete(embedding);
    }

    public void deleteAll() {
        mRepository.deleteAll();
    }

    public List<Embedding> getAll() {
        return mRepository.getAll();
    }

    public String getEmbeddingByTag(String tag) {
        String result = "";
        List<Embedding> embeddings = getAll();
        if (embeddings != null && !embeddings.isEmpty()) {
            for (Embedding embedding : embeddings) {
                if (embedding.description.contains(tag)) {
                    result = embedding.text;
                    break;
                }
            }
        }
        return result;
    }

    private static class Element {
        public Embedding embedding;
        public double distance;
    }

    public List<String> findSimilarOnes(String query) {
        return findSimilarOnes(query, 1);
    }

    public List<String> findSimilarOnes(String query, int type) {
        Embedding embedding = fetchEmbeddings(query);
        List<String> result = new ArrayList<>();
        List<Embedding> embeddings = mRepository.getAll();
        if (embeddings.isEmpty()) return result;
        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            Element element = new Element();
            element.embedding = embeddings.get(i);
            element.distance = cosineSimilarity(element.embedding.vector, embedding.vector);
            elements.add(element);
        }

        elements.sort(Comparator.comparingDouble((Element element) -> element.distance).reversed());

        Log.i(TAG, "sorted");
        int count = 0;
        for (Element element : elements) {
            Log.i(TAG, "* " + element.distance + " : " + element.embedding.text);
            result.add(type == 0 ? element.embedding.text : element.embedding.description);
            if (++count == 32) break;
        }

        /* TODO : yong4531 Test Result [
        result.clear();
        result.add("Location During the Day is 37.2582, 127.0554");
        result.add("Location During the Night is 37.3861, 127.071");
        result.add("Location During the Weekend is Unavailable");
        result.add("Time for Not Using the Phone is from 00:56 to 07:20");
        //result.add("Time for Using Enterprise Wi-Fi is 09:25 to 16:38");
        result.add("Time for Using Enterprise Wi-Fi is Unavailable");
        result.add("Time for Using Personal Wi-Fi is Unavailable");
         */
        return result;
    }

    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normalizedVectorA = 0.0;
        double normalizedVectorB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            float valueA = vectorA[i], valueB = vectorB[i];
            dotProduct += valueA * valueB;
            normalizedVectorA += Math.pow(valueA, 2);
            normalizedVectorB += Math.pow(valueB, 2);
        }
        return dotProduct / (Math.sqrt(normalizedVectorA) * Math.sqrt(normalizedVectorB));
    }

    private boolean hasText(String text) {
        List<Embedding> embeddings = getAll();
        if (embeddings.isEmpty()) return false;
        for (Embedding embedding : embeddings) {
            if (TextUtils.equals(text, embedding.text)) {
                return true;
            }
        }
        return false;
    }

    public void addEmbeddings(String text, String description, String category,
                              MonitoringService.EmbeddingResultListener listener) {
        if (hasText(text)) {
            listener.onSuccess();
            return;
        }
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
                    insert(new Embedding(text, description, category, embedding));
                    Log.i(TAG, "[" + getAll().size() + "] embeddings added for " + category);
                    listener.onSuccess();
                }
                listener.onError();
            }

            @Override
            public void onFailure(Call<EmbeddingResponse> call, Throwable t) {
                Log.e(TAG, "failed in fetching embeddings: " + t.getMessage());
                listener.onFailure();
            }
        });
    }

    public void removeEmbeddings(String category) {
        removeEmbeddings(category, new MonitoringService.EmbeddingResultListener());
    }

    public void removeEmbeddings(String category,
                                 MonitoringService.EmbeddingResultListener listener) {
        List<Embedding> embeddings = getAll();
        for (Embedding embedding : embeddings) {
            if (TextUtils.equals(category, embedding.category)) {
                delete(embedding);
                Log.i(TAG, "[" + getAll().size() + "] embeddings deleted for " + category);
                listener.onSuccess();
                break;
            }
        }
        listener.onSuccess();
    }

    private Embedding fetchEmbeddings(String text) {
        EmbeddingRequest request = new EmbeddingRequest(text, "text-embedding-3-small", "float");
        OpenAiService service = RetrofitClient.getInstance().create(OpenAiService.class);
        Call<EmbeddingResponse> call = service.getEmbedding(request);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final Embedding[] result = {null};
        try {
            new Thread(() -> {
                try {
                    Response<EmbeddingResponse> response = call.execute();
                    if (response.isSuccessful() && response.body() != null) {
                        float[] embedding = response.body().data.get(0).embedding;
                        Log.i(TAG, "response: " + Arrays.toString(embedding));
                        result[0] = new Embedding(text, "", "", embedding);
                    }
                    countDownLatch.countDown();
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                    e.printStackTrace();
                }
            }).start();
            countDownLatch.await();
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return result[0];
    }
}
