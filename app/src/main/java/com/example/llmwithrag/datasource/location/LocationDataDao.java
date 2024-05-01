package com.example.llmwithrag.datasource.location;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LocationDataDao {
    @Insert
    void insertData(LocationData locationData);

    @Query("SELECT * FROM location_data")
    List<LocationData> getAllData();

    @Query("DELETE FROM location_data")
    void deleteAllData();

    @Query("DELETE FROM location_data WHERE id NOT IN (SELECT id FROM location_data ORDER BY id DESC LIMIT 10240)")
    void deleteOldData();
}
