package com.example.llmwithrag.datasource.connectivity;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ConnectivityDataDao {
    @Insert
    void insertData(ConnectivityData connectivityData);

    @Query("SELECT * FROM connectivity_data")
    List<ConnectivityData> getAllData();

    @Query("DELETE FROM connectivity_data")
    void deleteAllData();

    @Query("DELETE FROM connectivity_data WHERE id NOT IN (SELECT id FROM connectivity_data ORDER BY id DESC LIMIT 10240)")
    void deleteOldData();
}
