package com.example.llmwithrag.knowledge.apps;

import static com.example.llmwithrag.Utils.getCoordinatesFromReadableAddress;
import static com.example.llmwithrag.Utils.getDate;
import static com.example.llmwithrag.Utils.getReadableAddressFromCoordinates;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_DATE;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_EVENT_IN_THE_CALENDAR_APP;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_LOCATION;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_DATE;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_EVENT;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_LOCATION;
import static com.example.llmwithrag.kg.KnowledgeManager.RELATIONSHIP_HELD_AT_LOCATION;
import static com.example.llmwithrag.kg.KnowledgeManager.RELATIONSHIP_HELD_ON_DATE;

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

import com.example.llmwithrag.MonitoringService;
import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeManager;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;
import com.example.llmwithrag.llm.EmbeddingManager;

import java.util.UUID;

public class CalendarAppManager extends ContentObserver implements IKnowledgeComponent {
    private static final String TAG = CalendarAppManager.class.getSimpleName();
    private final ContentResolver mContentResolver;
    private final Context mContext;
    private final KnowledgeManager mKnowledgeManager;
    private final EmbeddingManager mEmbeddingManager;
    private long mStartDate;
    private long mEndDate;

    public CalendarAppManager(Context context, KnowledgeManager kgManager,
                              EmbeddingManager embeddingManager) {
        super(new Handler(Looper.getMainLooper()));
        mContentResolver = context.getApplicationContext().getContentResolver();
        mContext = context;
        mKnowledgeManager = kgManager;
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
    public void update(int type, MonitoringService.EmbeddingResultListener listener) {
        listener.onSuccess();
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

                    Entity eventEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_EVENT,
                            ENTITY_NAME_EVENT_IN_THE_CALENDAR_APP);
                    eventEntity.addAttribute("eventId", id);
                    eventEntity.addAttribute("title", title);
                    eventEntity.addAttribute("date", startDateString);
                    eventEntity.addAttribute("time", startDateString);
                    if (!location.isEmpty()) eventEntity.addAttribute("location", location);
                    eventEntity.addAttribute("startDate", startDateString);
                    eventEntity.addAttribute("endDate", endDateString);
                    if (!mKnowledgeManager.addEntity(mEmbeddingManager, eventEntity)) continue;

                    Entity dateEntity = null;
                    if (eventEntity.hasAttribute("date")) {
                        dateEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_DATE,
                                ENTITY_NAME_DATE);
                        dateEntity.addAttribute("date", startDateString);
                        mKnowledgeManager.addEntity(mEmbeddingManager, dateEntity);
                    }

                    Entity locationEntity = null;
                    if (eventEntity.hasAttribute("location")) {
                        locationEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_LOCATION,
                                ENTITY_NAME_LOCATION);
                        locationEntity.addAttribute("coordinate", location);
                        locationEntity.addAttribute("location", getReadableAddressFromCoordinates(mContext, location));
                    }

                    if (dateEntity != null) {
                        mKnowledgeManager.addRelationship(mEmbeddingManager,
                                eventEntity, RELATIONSHIP_HELD_ON_DATE, dateEntity);
                    }
                    if (locationEntity != null) {
                        mKnowledgeManager.addRelationship(mEmbeddingManager,
                                eventEntity, RELATIONSHIP_HELD_AT_LOCATION, locationEntity);
                    }
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }
}
