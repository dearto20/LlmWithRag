package com.example.llmwithrag.datasource.location;

import android.content.Context;

import com.example.llmwithrag.datasource.DataSourceDatabase;
import com.example.llmwithrag.datasource.DataSourceRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class LocationRepository extends
        DataSourceRepository<LocationDataDao, LocationData> {
    public LocationRepository(Context context) {
        super(DataSourceDatabase.getInstance(context).getLocationDataDao());
    }

    @Override
    public void insertData(LocationData data) {
        new Thread(() -> {
            mDataDao.insertData(data);
            mDataDao.deleteOldData();
        }).start();
    }

    /**
     * @noinspection unchecked
     */
    @Override
    public List<LocationData> getAllData() {
        try {
            final List<LocationData>[] result = new List[]{null};
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

    @Override
    public void deleteAllData() {
        new Thread(mDataDao::deleteAllData).start();
    }
}
