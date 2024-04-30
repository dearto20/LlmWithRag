package com.example.llmwithrag.datasource;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.llmwithrag.datasource.location.LocationData;
import com.example.llmwithrag.datasource.location.LocationDataDao;
import com.example.llmwithrag.datasource.movement.MovementData;
import com.example.llmwithrag.datasource.movement.MovementDataDao;
import com.example.llmwithrag.datasource.connectivity.ConnectivityData;
import com.example.llmwithrag.datasource.connectivity.ConnectivityDataDao;

@Database(entities = {LocationData.class, ConnectivityData.class, MovementData.class}, version = 1, exportSchema = false)
public abstract class DataSourceDatabase extends RoomDatabase {
    public abstract LocationDataDao getLocationDataDao();
    public abstract ConnectivityDataDao getConnectivityDataDao();
    public abstract MovementDataDao getStatusDataDao();

    private static volatile DataSourceDatabase sInstance = null;

    public static DataSourceDatabase getInstance(Context context) {
        if (sInstance == null) {
            synchronized (DataSourceDatabase.class) {
                if (sInstance == null) {
                    sInstance = Room.databaseBuilder(context.getApplicationContext(),
                            DataSourceDatabase.class, "monitoring_db")
                            .build();
                }
            }
        }
        return sInstance;
    }
}
