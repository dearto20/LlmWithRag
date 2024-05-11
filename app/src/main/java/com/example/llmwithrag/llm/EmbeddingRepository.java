package com.example.llmwithrag.llm;

import android.content.Context;
import android.util.Log;

import com.example.llmwithrag.LlmWithRagApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EmbeddingRepository {
    private static final String TAG = EmbeddingRepository.class.getSimpleName();
    private final EmbeddingDao mEmbeddingDao;
    private final ExecutorService mExecutorService;

    public EmbeddingRepository(Context context) {
        mEmbeddingDao = EmbeddingDatabase.getInstance(context).getEmbeddingDao();
        mExecutorService = Executors.newSingleThreadExecutor();
    }

    public void insert(Embedding embedding) {
        mExecutorService.execute(() -> mEmbeddingDao.insertAll(embedding));
    }

    public void delete(Embedding embedding) {
        mExecutorService.execute(() -> mEmbeddingDao.delete(embedding));
    }

    public void deleteAll() {
        mExecutorService.execute(mEmbeddingDao::deleteAll);
    }

    public List<Embedding> getAll() {
        final List<Embedding>[] result = new List[]{new ArrayList<>()};
        try {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            mExecutorService.execute(() -> {
                result[0] = mEmbeddingDao.getAll();
                countDownLatch.countDown();
            });
            countDownLatch.await(5, TimeUnit.SECONDS);
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return result[0];
    }
}
