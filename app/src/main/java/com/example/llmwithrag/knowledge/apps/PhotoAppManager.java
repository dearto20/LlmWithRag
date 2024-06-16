package com.example.llmwithrag.knowledge.apps;

import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_DATE;
import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_LOCATION;
import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_PHOTO;
import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_TIME;

import android.content.Context;
import android.os.Environment;
import android.os.FileObserver;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeGraphManager;
import com.example.llmwithrag.kg.Relationship;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;
import com.example.llmwithrag.llm.EmbeddingManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class PhotoAppManager extends FileObserver implements IKnowledgeComponent {
    private static final String TAG = PhotoAppManager.class.getSimpleName();
    private final Context mContext;
    private final KnowledgeGraphManager mKgManager;
    private final EmbeddingManager mEmbeddingManager;

    public PhotoAppManager(Context context, KnowledgeGraphManager kgManager,
                           EmbeddingManager embeddingManager) {
        super(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/",
                FileObserver.ALL_EVENTS);
        mContext = context;
        mKgManager = kgManager;
        mEmbeddingManager = embeddingManager;
    }

    @Override
    public void onEvent(int event, @Nullable String fileName) {
        if (fileName == null || fileName.startsWith(".")) return;
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/"
                + fileName;
        if (!new File(path).exists()) return;

        String title = getFileName(path);
        Date dateTaken = new Date(getDateTakenFromExif(path));
        String location = getLocationFromExif(path);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(dateTaken);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(dateTaken);

        Entity photoEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_PHOTO, title);
        photoEntity.addAttribute("filePath", path);
        photoEntity.addAttribute("dateTaken", String.valueOf(dateTaken.getTime()));
        if (!location.isEmpty()) photoEntity.addAttribute("location", location);
        Entity oldPhotoEntity = mKgManager.getEntity(photoEntity);
        if (oldPhotoEntity == null) mKgManager.addEntity(photoEntity);
        else return;

        Entity locationEntity = null;
        if (!location.isEmpty()) {
            locationEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_LOCATION, title);
            locationEntity.addAttribute("location", location);
            Entity oldLocationEntity = mKgManager.getEntity(locationEntity);
            if (oldLocationEntity == null) mKgManager.addEntity(locationEntity);
            else locationEntity = oldLocationEntity;
        }

        Entity dateEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_DATE, date);
        dateEntity.addAttribute("date", date);
        Entity oldDateEntity = mKgManager.getEntity(dateEntity);
        if (oldDateEntity == null) mKgManager.addEntity(dateEntity);
        else dateEntity = oldDateEntity;

        Entity timeEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_TIME, time);
        timeEntity.addAttribute("time", time);
        Entity oldTimeEntity = mKgManager.getEntity(timeEntity);
        if (oldTimeEntity == null) mKgManager.addEntity(timeEntity);
        else timeEntity = oldTimeEntity;

        if (locationEntity != null) {
            if (mKgManager.getRelationship(photoEntity.getId(),
                    locationEntity.getId(), "taken at location") == null) {
                mKgManager.addRelationship(new Relationship(photoEntity.getId(),
                        locationEntity.getId(), "taken at location"));
            }
        }

        if (mKgManager.getRelationship(photoEntity.getId(),
                dateEntity.getId(), "taken at date") == null) {
            mKgManager.addRelationship(new Relationship(photoEntity.getId(),
                    dateEntity.getId(), "taken on date"));
        }

        if (mKgManager.getRelationship(photoEntity.getId(),
                timeEntity.getId(), "taken at time") == null) {
            mKgManager.addRelationship(new Relationship(photoEntity.getId(),
                    timeEntity.getId(), "taken at time"));
        }

        mKgManager.removeEmbedding(mEmbeddingManager, photoEntity);
        mKgManager.addEmbedding(mEmbeddingManager, photoEntity);
        Log.i(TAG, "content id is " + photoEntity.getContentId());
        Log.i(TAG, "added " + photoEntity);
    }

    @Override
    public void deleteAll() {
    }

    @Override
    public void startMonitoring() {
        Log.i(TAG, "started");
        startWatching();
    }

    @Override
    public void stopMonitoring() {
        Log.i(TAG, "stopped");
        stopWatching();
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
}
