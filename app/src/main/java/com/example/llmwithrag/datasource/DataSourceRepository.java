package com.example.llmwithrag.datasource;

import com.example.llmwithrag.datasource.connectivity.ConnectivityDataDao;

import java.util.List;

public abstract class DataSourceRepository<D, T> {
    protected final D mDataDao;

    public DataSourceRepository(D dataDao) {
        mDataDao = dataDao;
    }

    public abstract void insertData(T data);

    public abstract List<T> getAllData();

    public abstract void deleteAllData();
}
