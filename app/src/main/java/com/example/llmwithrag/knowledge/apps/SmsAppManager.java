package com.example.llmwithrag.knowledge.apps;

import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_DATE;
import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_MESSAGE;
import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_TIME;
import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_USER;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeGraphManager;
import com.example.llmwithrag.kg.Relationship;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;
import com.example.llmwithrag.llm.EmbeddingManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class SmsAppManager extends ContentObserver implements IKnowledgeComponent {
    private static final String TAG = SmsAppManager.class.getSimpleName();
    private final ContentResolver mContentResolver;
    private final KnowledgeGraphManager mKgManager;
    private final EmbeddingManager mEmbeddingManager;
    private long mLastUpdated;

    public SmsAppManager(Context context, KnowledgeGraphManager kgManager,
                         EmbeddingManager embeddingManager) {
        super(new Handler(Looper.getMainLooper()));
        mContentResolver = context.getApplicationContext().getContentResolver();
        mKgManager = kgManager;
        mEmbeddingManager = embeddingManager;
        mLastUpdated = 0;
    }

    @Override
    public void deleteAll() {
    }

    @Override
    public void startMonitoring() {
        Log.i(TAG, "started");
        mContentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, this);

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = sdf.parse("2024-05-31");
            mLastUpdated = date != null ? date.getTime() : System.currentTimeMillis();
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    @Override
    public void stopMonitoring() {
        Log.i(TAG, "stopped");
        mContentResolver.unregisterContentObserver(this);
        mLastUpdated = 0;
    }

    @Override
    public void onChange(boolean selfChange, @Nullable Uri uri) {
        super.onChange(selfChange, uri);
        Log.i(TAG, "change observed : " + uri);
        if (uri == null) return;

        try (Cursor cursor = mContentResolver.query(Telephony.Sms.CONTENT_URI,
                null,
                Telephony.Sms.DATE + " > ?",
                new String[]{String.valueOf(mLastUpdated)},
                null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
                    String address = (addressIndex >= 0) ? cursor.getString(addressIndex) : "";
                    int bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY);
                    String body = (bodyIndex >= 0) ? cursor.getString(bodyIndex) : "";
                    int dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE);
                    long date = (dateIndex >= 0) ? cursor.getLong(dateIndex) : -1;
                    String userName = getContactName(address);
                    String dateString = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(date);
                    String timeString = new SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(date);

                    Entity messageEntity = new Entity(UUID.randomUUID().toString(),
                            ENTITY_TYPE_MESSAGE, body);
                    messageEntity.addAttribute("body", body);
                    messageEntity.addAttribute("address", address);
                    messageEntity.addAttribute("date", String.valueOf(date));
                    Entity oldMessageEntity = mKgManager.getEntity(messageEntity);
                    if (oldMessageEntity == null) mKgManager.addEntity(messageEntity);
                    else continue;

                    Entity userEntity = new Entity(UUID.randomUUID().toString(),
                            ENTITY_TYPE_USER, userName);
                    userEntity.addAttribute("name", userName);
                    Entity oldUserEntity = mKgManager.getEntity(userEntity);
                    if (oldUserEntity == null) mKgManager.addEntity(userEntity);
                    else userEntity = oldUserEntity;

                    Entity dateEntity = new Entity(UUID.randomUUID().toString(),
                            ENTITY_TYPE_DATE, dateString);
                    dateEntity.addAttribute("date", dateString);
                    Entity oldDateEntity = mKgManager.getEntity(dateEntity);
                    if (oldDateEntity == null) mKgManager.addEntity(dateEntity);
                    else dateEntity = oldDateEntity;

                    Entity timeEntity = new Entity(UUID.randomUUID().toString(),
                            ENTITY_TYPE_TIME, timeString);
                    timeEntity.addAttribute("time", timeString);
                    Entity oldTimeEntity = mKgManager.getEntity(timeEntity);
                    if (oldTimeEntity == null) mKgManager.addEntity(timeEntity);
                    else timeEntity = oldTimeEntity;

                    if (mKgManager.getRelationship(messageEntity.getId(),
                            userEntity.getId(), "sent by") == null) {
                        mKgManager.addRelationship(new Relationship(messageEntity.getId(),
                                userEntity.getId(), "sent by"));
                    }

                    if (mKgManager.getRelationship(messageEntity.getId(),
                            dateEntity.getId(), "sent on date") == null) {
                        mKgManager.addRelationship(new Relationship(messageEntity.getId(),
                                dateEntity.getId(), "sent on date"));
                    }

                    if (mKgManager.getRelationship(messageEntity.getId(),
                            timeEntity.getId(), "sent at time") == null) {
                        mKgManager.addRelationship(new Relationship(messageEntity.getId(),
                                timeEntity.getId(), "sent at time"));
                    }

                    mKgManager.removeEmbedding(mEmbeddingManager, messageEntity);
                    mKgManager.addEmbedding(mEmbeddingManager, messageEntity);
                    Log.i(TAG, "added " + messageEntity);
                    if (date > mLastUpdated) mLastUpdated = date;
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    private String getContactName(String phoneNumber) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber));
        String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};
        try (Cursor cursor = mContentResolver.query(uri, projection, null, null, null)) {
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                    return (nameIndex >= 0) ? cursor.getString(nameIndex) : "";
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return phoneNumber;
    }
}
