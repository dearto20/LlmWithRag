package com.example.llmwithrag.datasource.apps;

import static com.example.llmwithrag.Utils.getCoordinatesFromReadableAddress;
import static com.example.llmwithrag.Utils.getDate;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.llmwithrag.datasource.IDataSourceListener;
import com.example.llmwithrag.datasource.IDataSourceTracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CalendarTracker extends ContentObserver implements IDataSourceTracker {
    private static final String TAG = CalendarTracker.class.getSimpleName();
    private final ContentResolver mContentResolver;
    private final Context mContext;
    private final List<IDataSourceListener> mListeners = new ArrayList<>();
    private long mStartDate;
    private long mEndDate;
    private boolean mStarted;

    public CalendarTracker(Context context) {
        super(new Handler(Looper.getMainLooper()));
        mContentResolver = context.getApplicationContext().getContentResolver();
        mContext = context;

        try {
            mStartDate = System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 16;
            mEndDate = System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 16;
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        mStarted = false;
    }

    @Override
    public void deleteAllData() {
    }

    @Override
    public List<Object> getAllData() {
        return Collections.emptyList();
    }

    @Override
    public void startMonitoring() {
        if (mStarted) return;
        Log.i(TAG, "started");
        mContentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, this);
        mStarted = true;
    }

    @Override
    public void stopMonitoring() {
        if (!mStarted) return;
        Log.i(TAG, "stopped");
        mContentResolver.unregisterContentObserver(this);
        mListeners.clear();
        mStarted = false;
    }

    @Override
    public void registerListener(IDataSourceListener listener) {
        mListeners.add(listener);
    }

    @Override
    public void unregisterListener(IDataSourceListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void onChange(boolean selfChange, @Nullable Uri uri) {
        super.onChange(selfChange, uri);
        Log.i(TAG, "change observed : " + uri);
        if (uri == null) return;

        try (Cursor cursor = mContentResolver.query(CalendarContract.Events.CONTENT_URI,
                null,
                CalendarContract.Events.DTSTART + " > ? AND " +
                        CalendarContract.Events.DTEND + " < ?",
                new String[]{String.valueOf(mStartDate), String.valueOf(mEndDate)},
                null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int idIndex = cursor.getColumnIndex(CalendarContract.Events._ID);
                    String id = (idIndex >= 0) ? cursor.getString(idIndex) : "";
                    int titleIndex = cursor.getColumnIndex(CalendarContract.Events.TITLE);
                    String title = (titleIndex >= 0) ? cursor.getString(titleIndex) : "";
                    int startDateIndex = cursor.getColumnIndex(CalendarContract.Events.DTSTART);
                    long startDate = (startDateIndex >= 0) ? cursor.getLong(startDateIndex) : -1;
                    int endDateIndex = cursor.getColumnIndex(CalendarContract.Events.DTEND);
                    long endDate = (endDateIndex >= 0) ? cursor.getLong(endDateIndex) : -1;
                    int locationIndex = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION);
                    String location = getCoordinatesFromReadableAddress(mContext,
                            (locationIndex >= 0) ? cursor.getString(locationIndex) : "");
                    String startDateString = getDate(startDate);
                    String endDateString = getDate(endDate);

                    Map<String, Object> data = new HashMap<>();
                    data.put(KEY_ID, id);
                    data.put(KEY_TITLE, title);
                    data.put(KEY_DATE, startDateString);
                    data.put(KEY_TIME, startDateString);
                    if (!TextUtils.isEmpty(location)) data.put(KEY_LOCATION, location);
                    data.put(KEY_START_DATE, startDateString);
                    data.put(KEY_END_DATE, endDateString);

                    Iterator<IDataSourceListener> iterator = mListeners.iterator();
                    while (iterator.hasNext()) {
                        IDataSourceListener listener = iterator.next();
                        listener.onUpdate(data);
                    }
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }
}
