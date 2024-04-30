package com.example.llmwithrag.datasource.movement;

import android.content.Context;

import com.example.llmwithrag.datasource.DataSourceDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class MovementRepository {
    private final MovementDataDao mDataDao;

    public MovementRepository(Context context) {
        DataSourceDatabase db = DataSourceDatabase.getInstance(context);
        mDataDao = db.getStatusDataDao();
    }

    public void insertData(MovementData data) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mDataDao.insertData(data);
                mDataDao.deleteOldData();
            }
        }).start();
    }

    public List<MovementData> getAllData() {
        try {
            final List<MovementData>[] result = new List[]{null};
            CountDownLatch countDownLatch = new CountDownLatch(1);
            new Thread(() -> {
                result[0] = mDataDao.getAllData();
                countDownLatch.countDown();
            }).start();
            countDownLatch.await();
            return result[0];
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public void deleteAllData() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mDataDao.deleteAllData();
            }
        }).start();
    }
}
