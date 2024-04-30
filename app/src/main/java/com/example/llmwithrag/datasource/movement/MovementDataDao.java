package com.example.llmwithrag.datasource.movement;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MovementDataDao {
    @Insert
    void insertData(MovementData statusData);

    @Query("SELECT * FROM movement_data")
    List<MovementData> getAllData();

    @Query("DELETE FROM movement_data")
    void deleteAllData();

    @Query("DELETE FROM movement_data WHERE id NOT IN (SELECT id FROM movement_data ORDER BY id DESC LIMIT 1024)")
    void deleteOldData();
}
