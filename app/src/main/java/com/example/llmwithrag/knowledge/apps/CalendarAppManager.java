package com.example.llmwithrag.knowledge.apps;

import static com.example.llmwithrag.Utils.getCoordinatesFromReadableAddress;
import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_EVENT;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeGraphManager;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;
import com.example.llmwithrag.llm.EmbeddingManager;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.UUID;

public class CalendarAppManager extends ContentObserver implements IKnowledgeComponent {
    private static final String TAG = CalendarAppManager.class.getSimpleName();
    private final ContentResolver mContentResolver;
    private final Context mContext;
    private final KnowledgeGraphManager mKgManager;
    private final EmbeddingManager mEmbeddingManager;
    private long mStartDate;
    private long mEndDate;

    public CalendarAppManager(Context context, KnowledgeGraphManager kgManager,
                              EmbeddingManager embeddingManager) {
        super(new Handler(Looper.getMainLooper()));
        mContentResolver = context.getApplicationContext().getContentResolver();
        mContext = context;
        mKgManager = kgManager;
        mEmbeddingManager = embeddingManager;

        try {
            mStartDate = System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 16;
            mEndDate = System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 16;
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    @Override
    public void deleteAll() {
    }

    @Override
    public void startMonitoring() {
        Log.i(TAG, "started");
        mContentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, this);
    }

    @Override
    public void stopMonitoring() {
        Log.i(TAG, "stopped");
        mContentResolver.unregisterContentObserver(this);
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
                    String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(startDate);
                    String time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(startDate);

                    Entity eventEntity = new Entity(UUID.randomUUID().toString(),
                            ENTITY_TYPE_EVENT, title);
                    eventEntity.addAttribute("eventId", id);
                    eventEntity.addAttribute("title", title);
                    eventEntity.addAttribute("date", date);
                    eventEntity.addAttribute("time", time);
                    if (!location.isEmpty()) eventEntity.addAttribute("location", location);
                    eventEntity.addAttribute("startDate", String.valueOf(startDate));
                    eventEntity.addAttribute("endDate", String.valueOf(endDate));

                    Entity oldEventEntity = mKgManager.getEntity(eventEntity);
                    Log.i(TAG, "iterating entity : " + eventEntity);
                    Log.i(TAG, "has entity ?" + mKgManager.equals(oldEventEntity, eventEntity));

                    if (mKgManager.equals(oldEventEntity, eventEntity)) continue;
                    if (oldEventEntity != null) {
                        mKgManager.removeEntity(oldEventEntity);
                        mKgManager.removeEmbedding(mEmbeddingManager, oldEventEntity);
                    }
                    mKgManager.addEntity(eventEntity);
                    mKgManager.removeEmbedding(mEmbeddingManager, eventEntity);
                    mKgManager.addEmbedding(mEmbeddingManager, eventEntity);
                    Log.i(TAG, "added " + eventEntity);
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }
}
