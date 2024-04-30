package com.example.llmwithrag.datasource.connectivity;

import android.content.Context;

import com.example.llmwithrag.datasource.DataSourceDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ConnectivityRepository {
    private final ConnectivityDataDao mDataDao;

    public ConnectivityRepository(Context context) {
        DataSourceDatabase db = DataSourceDatabase.getInstance(context);
        mDataDao = db.getConnectivityDataDao();
    }

    public void insertData(ConnectivityData data) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mDataDao.insertData(data);
                mDataDao.deleteOldData();
            }
        }).start();
    }

    public List<ConnectivityData> getAllData() {
        try {
            final List<ConnectivityData>[] result = new List[]{null};
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
