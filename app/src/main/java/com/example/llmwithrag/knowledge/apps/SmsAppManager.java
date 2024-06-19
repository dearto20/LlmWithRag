package com.example.llmwithrag.knowledge.apps;

import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_MESSAGE;
import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_PHOTO;
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
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeGraphManager;
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

public class SmsAppManager extends ContentObserver implements IKnowledgeComponent {
    private static final String TAG = SmsAppManager.class.getSimpleName();
    private final ContentResolver mContentResolver;
    private final Context mContext;
    private final KnowledgeGraphManager mKgManager;
    private final EmbeddingManager mEmbeddingManager;
    private long mLastUpdated;

    public SmsAppManager(Context context, KnowledgeGraphManager kgManager,
                         EmbeddingManager embeddingManager) {
        super(new Handler(Looper.getMainLooper()));
        mContentResolver = context.getApplicationContext().getContentResolver();
        mContext = context;
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
        mContentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, this);

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
                    Log.i(TAG, address + ", " + body + ", " + date);
                    handleMessage(address, body, date);
                }
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
                    handleImage(messageId, getContactName(address));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)) * 1000L;
                    handleMessage(address, body, date);
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
        }
    }

    private void handleMessage(String address, String body, long date) {
        String userName = getContactName(address);
        String dateString = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(date);
        String timeString = new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(date);

        Entity messageEntity = new Entity(UUID.randomUUID().toString(),
                ENTITY_TYPE_MESSAGE, body);
        messageEntity.addAttribute("sentBy", getContactName(address));
        messageEntity.addAttribute("body", body);
        messageEntity.addAttribute("address", address);
        messageEntity.addAttribute("date", dateString);
        messageEntity.addAttribute("time", timeString);
        Entity oldMessageEntity = mKgManager.getEntity(messageEntity);
        if (oldMessageEntity == null) mKgManager.addEntity(messageEntity);
        else return;

        Entity userEntity = new Entity(UUID.randomUUID().toString(),
                ENTITY_TYPE_USER, userName);
        userEntity.addAttribute("name", userName);
        mKgManager.addEntity(userEntity);
        mKgManager.removeEmbedding(mEmbeddingManager, messageEntity);
        mKgManager.addEmbedding(mEmbeddingManager, messageEntity);
        Log.i(TAG, "added " + messageEntity);
        if (date > mLastUpdated) mLastUpdated = date;
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

    private void extractPhotoMetadata(Context context, Uri dataUri, String sentBy) {
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

                handlePhoto(tempFile.getAbsolutePath(), sentBy);

                // Delete the temporary file
                tempFile.delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleImage(String id, String sentBy) {
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
                    extractPhotoMetadata(mContext, dataUri, sentBy);
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

    private String getFileName(String filePath) {
        return new File(filePath).getName();
    }

    private void handlePhoto(String path, String sentBy) {
        String title = getFileName(path);
        Date dateTaken = new Date(getDateTakenFromExif(path));
        String location = getLocationFromExif(path);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(dateTaken);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(dateTaken);

        Entity photoEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_PHOTO, title);
        photoEntity.addAttribute("sentBy", sentBy);
        photoEntity.addAttribute("filePath", path);
        photoEntity.addAttribute("date", date);
        photoEntity.addAttribute("time", time);
        if (!location.isEmpty()) photoEntity.addAttribute("location", location);
        mKgManager.addEntity(photoEntity);
        mKgManager.removeEmbedding(mEmbeddingManager, photoEntity);
        mKgManager.addEmbedding(mEmbeddingManager, photoEntity);
        Log.i(TAG, "content id is " + photoEntity.getContentId());
        Log.i(TAG, "added " + photoEntity);
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
