package com.example.llmwithrag;

import android.util.Log;

import androidx.lifecycle.ViewModel;
import androidx.room.Room;

import com.example.llmwithrag.llm.Embedding;
import com.example.llmwithrag.llm.EmbeddingDatabase;
import com.example.llmwithrag.llm.EmbeddingRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EmbeddingViewModel extends ViewModel {
    private static final String TAG = EmbeddingViewModel.class.getSimpleName();
    private final EmbeddingRepository mRepository;

    public EmbeddingViewModel() {
        mRepository = new EmbeddingRepository(Room.databaseBuilder(
                        LlmWithRagApplication.getInstance(), EmbeddingDatabase.class, "vector_db")
                .fallbackToDestructiveMigration()
                .build().getEmbeddingDao());
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

    private static class Element {
        public Embedding embedding;
        public double distance;
    }

    public List<Embedding> findSimilarOnes(Embedding embedding) {
        List<Embedding> result = new ArrayList<>();
        List<Embedding> embeddings = mRepository.getAll();
        if (embeddings.size() == 0) return result;
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
            result.add(element.embedding);
            if (++count == 10) break;
        }
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
}
