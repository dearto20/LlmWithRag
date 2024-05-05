package com.example.llmwithrag.datasource.movement;

import android.content.Context;

import com.example.llmwithrag.datasource.DataSourceDatabase;
import com.example.llmwithrag.datasource.DataSourceRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class MovementRepository extends
        DataSourceRepository<MovementDataDao, MovementData> {

    public MovementRepository(Context context) {
        super(DataSourceDatabase.getInstance(context).getStatusDataDao());
    }

    @Override
    public void insertData(MovementData data) {
        new Thread(() -> {
            mDataDao.insertData(data);
            mDataDao.deleteOldData();
        }).start();
    }

    /**
     * @noinspection unchecked
     */
    @Override
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

    @Override
    public void deleteAllData() {
        new Thread(mDataDao::deleteAllData).start();
    }
}
