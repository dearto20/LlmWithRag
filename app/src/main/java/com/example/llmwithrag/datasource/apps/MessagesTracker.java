package com.example.llmwithrag.datasource.apps;

import static com.example.llmwithrag.Utils.getContactNameByPhoneNumber;
import static com.example.llmwithrag.Utils.getDate;
import static com.example.llmwithrag.Utils.getSharedPreferenceLong;
import static com.example.llmwithrag.Utils.getTime;
import static com.example.llmwithrag.Utils.setSharedPreferenceLong;

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

import com.example.llmwithrag.datasource.IDataSourceListener;
import com.example.llmwithrag.datasource.IDataSourceTracker;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MessagesTracker extends ContentObserver implements IDataSourceTracker {
    private static final String TAG = MessagesTracker.class.getSimpleName();
    private static final String NAME_SHARED_PREFS = "msg_shared_prefs";
    private static final String KEY_LAST_UPDATED = "key_last_updated";
    private final ContentResolver mContentResolver;
    private final Context mContext;
    private final List<IDataSourceListener> mListeners = new ArrayList<>();
    private long mLastUpdated;
    private boolean mStarted;

    public MessagesTracker(Context context) {
        super(new Handler(Looper.getMainLooper()));
        mContentResolver = context.getApplicationContext().getContentResolver();
        mContext = context;
        mLastUpdated = getSharedPreferenceLong(mContext, NAME_SHARED_PREFS, KEY_LAST_UPDATED,
                System.currentTimeMillis());
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
        mContentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, this);
        mContentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, this);
        mLastUpdated = getSharedPreferenceLong(mContext, NAME_SHARED_PREFS, KEY_LAST_UPDATED,
                System.currentTimeMillis());
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

        Map<String, Object> data = new HashMap<>();
        data.put(KEY_ID, messageId);
        data.put(KEY_NAME, name);
        data.put(KEY_ADDRESS, address);
        data.put(KEY_SENDER, sender);
        data.put(KEY_BODY, body);
        data.put(KEY_DATE, dateString);
        data.put(KEY_TIME, timeString);

        Iterator<IDataSourceListener> iterator = mListeners.iterator();
        while (iterator.hasNext()) {
            IDataSourceListener listener = iterator.next();
            listener.onUpdate(data);
        }
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
