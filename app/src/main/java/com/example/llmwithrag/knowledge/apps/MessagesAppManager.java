package com.example.llmwithrag.knowledge.apps;

import static com.example.llmwithrag.Utils.getContactNameByPhoneNumber;
import static com.example.llmwithrag.Utils.getDate;
import static com.example.llmwithrag.Utils.getFileName;
import static com.example.llmwithrag.Utils.getReadableAddressFromCoordinates;
import static com.example.llmwithrag.Utils.getSharedPreferenceLong;
import static com.example.llmwithrag.Utils.getTime;
import static com.example.llmwithrag.Utils.setSharedPreferenceLong;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_DATE;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_LOCATION;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_MESSAGE_IN_THE_MESSAGES_APP;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_PHOTO;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_USER;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_DATE;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_LOCATION;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_MESSAGE;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_PHOTO;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_USER;
import static com.example.llmwithrag.kg.KnowledgeManager.RELATIONSHIP_ATTACHED_IN;
import static com.example.llmwithrag.kg.KnowledgeManager.RELATIONSHIP_SENT_BY_USER;
import static com.example.llmwithrag.kg.KnowledgeManager.RELATIONSHIP_SENT_ON_DATE;
import static com.example.llmwithrag.kg.KnowledgeManager.RELATIONSHIP_TAKEN_AT_LOCATION;
import static com.example.llmwithrag.kg.KnowledgeManager.RELATIONSHIP_TAKEN_ON_DATE;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.example.llmwithrag.MonitoringService;
import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeManager;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;
import com.example.llmwithrag.llm.EmbeddingManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class MessagesAppManager extends ContentObserver implements IKnowledgeComponent {
    private static final String TAG = MessagesAppManager.class.getSimpleName();
    private static final String NAME_SHARED_PREFS = "msg_shared_prefs";
    private static final String KEY_LAST_UPDATED = "key_last_updated";
    private final ContentResolver mContentResolver;
    private final Context mContext;
    private final KnowledgeManager mKnowledgeManager;
    private final EmbeddingManager mEmbeddingManager;
    private long mLastUpdated;

    public MessagesAppManager(Context context, KnowledgeManager knowledgeManager,
                              EmbeddingManager embeddingManager) {
        super(new Handler(Looper.getMainLooper()));
        mContentResolver = context.getApplicationContext().getContentResolver();
        mContext = context;
        mKnowledgeManager = knowledgeManager;
        mEmbeddingManager = embeddingManager;
        mLastUpdated = getSharedPreferenceLong(mContext, NAME_SHARED_PREFS, KEY_LAST_UPDATED,
                System.currentTimeMillis());
    }

    @Override
    public void deleteAll() {
    }

    @Override
    public void startMonitoring() {
        Log.i(TAG, "started");
        mContentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, this);
        mContentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, this);
        mLastUpdated = getSharedPreferenceLong(mContext, NAME_SHARED_PREFS, KEY_LAST_UPDATED,
                System.currentTimeMillis());

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
        handleSms();
        handleMms();
    }

    private void handleSms() {
        try (Cursor cursor = mContentResolver.query(Telephony.Sms.CONTENT_URI,
                null,
                Telephony.Sms.DATE + " > ?",
                new String[]{String.valueOf(mLastUpdated)},
                null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                    if (date < mLastUpdated) continue;
                    handleMessage(address, body, date);
                    mLastUpdated = date;
                }
                setSharedPreferenceLong(mContext, NAME_SHARED_PREFS, KEY_LAST_UPDATED, mLastUpdated);
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
        }
    }

    private void handleMms() {
        try (Cursor cursor = mContentResolver.query(Telephony.Mms.CONTENT_URI,
                null,
                Telephony.Mms.DATE + " > ?",
                new String[]{String.valueOf(mLastUpdated / 1000)},
                null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String messageId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms._ID));
                    String address = getMmsAddress(messageId);
                    String body = getMmsText(messageId);
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)) * 1000L;
                    if (date < mLastUpdated) continue;
                    handleMessage(messageId, address, body, date);
                    mLastUpdated = date;
                }
                setSharedPreferenceLong(mContext, NAME_SHARED_PREFS, KEY_LAST_UPDATED, mLastUpdated);
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
        }
    }

    private void handleMessage(String address, String body, long date) {
        handleMessage(null, address, body, date);
    }

    private void handleMessage(String messageId, String address, String body, long date) {
        String sender = getContactNameByPhoneNumber(mContext, address);
        String name = TextUtils.isEmpty(sender) ? address : sender;
        String dateString = getDate(date);
        String timeString = getTime(date);

        Entity messageEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_MESSAGE,
                ENTITY_NAME_MESSAGE_IN_THE_MESSAGES_APP);
        messageEntity.addAttribute("address", address);
        messageEntity.addAttribute("sender", sender);
        messageEntity.addAttribute("body", body);
        messageEntity.addAttribute("date", dateString);
        messageEntity.addAttribute("time", timeString);
        if (!mKnowledgeManager.addEntity(mEmbeddingManager, messageEntity)) return;

        Entity userEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_USER,
                ENTITY_NAME_USER);
        userEntity.addAttribute("name", name);
        mKnowledgeManager.addEntity(mEmbeddingManager, userEntity);

        Entity dateEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_DATE,
                ENTITY_NAME_DATE);
        dateEntity.addAttribute("date", dateString);
        mKnowledgeManager.addEntity(mEmbeddingManager, dateEntity);

        mKnowledgeManager.addRelationship(mEmbeddingManager,
                messageEntity, RELATIONSHIP_SENT_BY_USER, userEntity);
        mKnowledgeManager.addRelationship(mEmbeddingManager,
                messageEntity, RELATIONSHIP_SENT_ON_DATE, dateEntity);

        if (date > mLastUpdated) mLastUpdated = date;
        if (messageId != null) handleImage(messageId, messageEntity);
    }

    private String getMmsAddress(String id) {
        Uri uri = Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, id + "/addr");
        Cursor cursor = mContentResolver.query(uri, null, null, null, null);
        if (cursor == null) return null;
        try {
            while (cursor.moveToNext()) {
                String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS));
                if (!TextUtils.isEmpty(address)) {
                    return address;
                }
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    private void extractPhotoMetadata(Context context, Uri dataUri, Entity messageEntity) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(dataUri);
            if (inputStream != null) {
                // Save the input stream to a temporary file to read EXIF metadata
                File tempFile = File.createTempFile("mms_image", ".jpg", context.getCacheDir());
                FileOutputStream outputStream = new FileOutputStream(tempFile);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                }
                outputStream.close();
                inputStream.close();

                handlePhoto(tempFile.getAbsolutePath(), messageEntity);

                // Delete the temporary file
                tempFile.delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleImage(String id, Entity messageEntity) {
        Uri partUri = Telephony.Mms.CONTENT_URI.buildUpon().appendPath(id).appendPath("part").build();
        Cursor cursor = mContentResolver.query(partUri, null, null, null, null);
        if (cursor == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            while (cursor.moveToNext()) {
                String type = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE));
                if ("image/jpeg".equals(type) || "image/bmp".equals(type) ||
                        "image/gif".equals(type) || "image/jpg".equals(type) ||
                        "image/png".equals(type)) {
                    int index = cursor.getColumnIndex("_id");
                    if (index < 0) return;
                    String partId = cursor.getString(index);
                    Uri dataUri = Uri.parse("content://mms/part/" + partId);
                    extractPhotoMetadata(mContext, dataUri, messageEntity);
                }
            }
        } finally {
            cursor.close();
        }
    }

    private String getLocationFromExif(String filePath) {
        try {
            ExifInterface exifInterface = new ExifInterface(filePath);
            double[] latLong = exifInterface.getLatLong();
            if (latLong != null && latLong.length == 2) {
                double scale = Math.pow(10, 4);
                double latitude = Math.round(latLong[0] * scale) / scale;
                double longitude = Math.round(latLong[1] * scale) / scale;
                return latitude + ", " + longitude;
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return "";
    }

    private long getDateTakenFromExif(String filePath) {
        try {
            ExifInterface exifInterface = new ExifInterface(filePath);
            String dateTime = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            if (dateTime == null) {
                dateTime = exifInterface.getAttribute(ExifInterface.TAG_DATETIME);
            }
            if (dateTime != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss",
                        Locale.getDefault());
                Date date = sdf.parse(dateTime);
                if (date != null) return date.getTime();
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        Log.i(TAG, "failed to find creation time");
        return System.currentTimeMillis();
    }

    private void handlePhoto(String path, Entity messageEntity) {
        Date dateTaken = new Date(getDateTakenFromExif(path));
        String name = getFileName(path);
        String sender = messageEntity.getAttributes().get("sender");
        String body = messageEntity.getAttributes().get("body");
        String location = getLocationFromExif(path);
        String dateString = getDate(dateTaken.getTime());
        String timeString = getTime(dateTaken.getTime());

        Entity photoEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_PHOTO,
                ENTITY_NAME_PHOTO);
        photoEntity.addAttribute("sender", sender);
        photoEntity.addAttribute("body", body);
        photoEntity.addAttribute("filePath", path);
        photoEntity.addAttribute("date", dateString);
        photoEntity.addAttribute("time", timeString);
        if (!location.isEmpty()) photoEntity.addAttribute("location", location);
        if (!mKnowledgeManager.addEntity(mEmbeddingManager, photoEntity)) return;

        Entity dateEntity = null;
        if (photoEntity.hasAttribute("date")) {
            dateEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_DATE,
                    ENTITY_NAME_DATE);
            dateEntity.addAttribute("date", dateString);
            mKnowledgeManager.addEntity(mEmbeddingManager, dateEntity);
        }

        Entity locationEntity = null;
        if (photoEntity.hasAttribute("location")) {
            locationEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_LOCATION,
                    ENTITY_NAME_LOCATION);
            locationEntity.addAttribute("coordinate", location);
            locationEntity.addAttribute("location", getReadableAddressFromCoordinates(mContext, location));
        }

        if (dateEntity != null) {
            mKnowledgeManager.addRelationship(mEmbeddingManager,
                    photoEntity, RELATIONSHIP_TAKEN_ON_DATE, dateEntity);
        }
        if (locationEntity != null) {
            mKnowledgeManager.addRelationship(mEmbeddingManager,
                    photoEntity, RELATIONSHIP_TAKEN_AT_LOCATION, locationEntity);
        }
        mKnowledgeManager.addRelationship(mEmbeddingManager,
                photoEntity, RELATIONSHIP_ATTACHED_IN, messageEntity);
    }

    private String getMmsText(String id) {
        Uri partUri = Telephony.Mms.CONTENT_URI.buildUpon().appendPath(id).appendPath("part").build();
        Cursor cursor = mContentResolver.query(partUri, null, null, null, null);
        if (cursor == null) return null;
        try {
            StringBuilder sb = new StringBuilder();
            while (cursor.moveToNext()) {
                String type = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE));
                if ("text/plain".equals(type)) {
                    String data = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part._DATA));
                    if (data != null) {
                        Uri dataUri = Telephony.Mms.Part.CONTENT_URI.buildUpon().appendPath(
                                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part._ID))).build();
                        sb.append(readDataFromUri(dataUri));
                    } else {
                        sb.append(cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT)));
                    }
                }
            }
            return sb.toString();
        } finally {
            cursor.close();
        }
    }

    private String readDataFromUri(Uri uri) {
        InputStream is = null;
        StringBuilder sb = new StringBuilder();
        try {
            is = mContentResolver.openInputStream(uri);
            if (is != null) {
                InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                char[] buffer = new char[256];
                int len;
                while ((len = isr.read(buffer)) != -1) {
                    sb.append(buffer, 0, len);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sb.toString();
    }
}
