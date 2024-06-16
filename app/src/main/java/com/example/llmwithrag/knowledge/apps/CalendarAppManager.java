package com.example.llmwithrag.knowledge.apps;

import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_DATE;
import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_EVENT;
import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_LOCATION;
import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_TIME;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeGraphManager;
import com.example.llmwithrag.kg.Relationship;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;
import com.example.llmwithrag.llm.EmbeddingManager;

import java.text.SimpleDateFormat;
import java.util.List;
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
                    String location = getCoordinatesFromReadableAddress(
                            (locationIndex >= 0) ? cursor.getString(locationIndex) : "");
                    String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(startDate);
                    String time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(startDate);

                    Entity eventEntity = new Entity(UUID.randomUUID().toString(),
                            ENTITY_TYPE_EVENT, title);
                    eventEntity.addAttribute("eventId", id);
                    eventEntity.addAttribute("title", title);
                    eventEntity.addAttribute("startDate", String.valueOf(startDate));
                    eventEntity.addAttribute("endDate", String.valueOf(endDate));
                    if (!location.isEmpty()) eventEntity.addAttribute("location", location);

                    Entity oldEventEntity = mKgManager.getEntity(eventEntity);
                    if (mKgManager.equals(oldEventEntity, eventEntity)) continue;
                    if (oldEventEntity != null) {
                        mKgManager.removeEntity(oldEventEntity);
                        mKgManager.removeEmbedding(mEmbeddingManager, oldEventEntity);
                    }
                    mKgManager.addEntity(eventEntity);

                    Entity locationEntity = null;
                    if (!location.isEmpty()) {
                        locationEntity = new Entity(UUID.randomUUID().toString(),
                                ENTITY_TYPE_LOCATION, title);
                        locationEntity.addAttribute("location", location);
                        Entity oldLocationEntity = mKgManager.getEntity(locationEntity);
                        if (oldLocationEntity == null) mKgManager.addEntity(locationEntity);
                        else locationEntity = oldLocationEntity;
                    }

                    Entity dateEntity = new Entity(UUID.randomUUID().toString(),
                            ENTITY_TYPE_DATE, date);
                    dateEntity.addAttribute("date", date);
                    Entity oldDateEntity = mKgManager.getEntity(dateEntity);
                    if (oldDateEntity == null) mKgManager.addEntity(dateEntity);
                    else dateEntity = oldDateEntity;

                    Entity timeEntity = new Entity(UUID.randomUUID().toString(),
                            ENTITY_TYPE_TIME, time);
                    timeEntity.addAttribute("time", time);
                    Entity oldTimeEntity = mKgManager.getEntity(timeEntity);
                    if (oldTimeEntity == null) mKgManager.addEntity(timeEntity);
                    else timeEntity = oldTimeEntity;

                    if (locationEntity != null) {
                        if (mKgManager.getRelationship(eventEntity.getId(),
                                locationEntity.getId(), "takes place at location") == null) {
                            mKgManager.addRelationship(new Relationship(eventEntity.getId(),
                                    locationEntity.getId(), "takes place at location"));
                        }
                    }

                    if (mKgManager.getRelationship(eventEntity.getId(),
                            dateEntity.getId(), "takes place on date") == null) {
                        mKgManager.addRelationship(new Relationship(eventEntity.getId(),
                                dateEntity.getId(), "takes place on date"));
                    }

                    if (mKgManager.getRelationship(eventEntity.getId(),
                            timeEntity.getId(), "takes place at time") == null) {
                        mKgManager.addRelationship(new Relationship(eventEntity.getId(),
                                timeEntity.getId(), "takes place at time"));
                    }

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

    private String getCoordinatesFromReadableAddress(String address) {
        if (TextUtils.isEmpty(address)) return "";
        Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(address, 1);
            if (addresses == null || addresses.isEmpty()) return "";
            Address location = addresses.get(0);
            double scale = Math.pow(10, 4);
            double latitude = Math.round(location.getLatitude() * scale) / scale;
            double longitude = Math.round(location.getLongitude() * scale) / scale;
            return latitude + ", " + longitude;
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return "";
    }
}
