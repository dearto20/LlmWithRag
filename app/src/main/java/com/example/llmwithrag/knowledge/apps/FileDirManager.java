package com.example.llmwithrag.knowledge.apps;

import static com.example.llmwithrag.Utils.getDate;
import static com.example.llmwithrag.Utils.getFileName;
import static com.example.llmwithrag.Utils.getReadableAddressFromCoordinates;
import static com.example.llmwithrag.Utils.getTime;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_DATE;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_LOCATION;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_PHOTO;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_DATE;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_LOCATION;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_PHOTO;
import static com.example.llmwithrag.kg.KnowledgeManager.RELATIONSHIP_ATTACHED_IN;
import static com.example.llmwithrag.kg.KnowledgeManager.RELATIONSHIP_TAKEN_AT_LOCATION;
import static com.example.llmwithrag.kg.KnowledgeManager.RELATIONSHIP_TAKEN_ON_DATE;

import android.content.Context;
import android.os.Environment;
import android.os.FileObserver;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.example.llmwithrag.IKnowledgeListener;
import com.example.llmwithrag.MonitoringService;
import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeManager;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;
import com.example.llmwithrag.llm.EmbeddingManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class FileDirManager extends FileObserver implements IKnowledgeComponent {
    private static final String TAG = FileDirManager.class.getSimpleName();
    private final Context mContext;
    private final KnowledgeManager mKnowledgeManager;
    private final EmbeddingManager mEmbeddingManager;
    private final IKnowledgeListener mListener;

    public FileDirManager(Context context, KnowledgeManager knowledgeManager,
                          EmbeddingManager embeddingManager, IKnowledgeListener listener) {
        super(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/",
                FileObserver.ALL_EVENTS);
        mContext = context;
        mKnowledgeManager = knowledgeManager;
        mEmbeddingManager = embeddingManager;
        mListener = listener;
    }

    @Override
    public void onEvent(int event, @Nullable String fileName) {
        if (fileName == null || fileName.startsWith(".")) return;
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/"
                + fileName;
        if (!new File(path).exists()) return;

        String name = getFileName(path);
        Date dateTaken = new Date(getDateTakenFromExif(path));
        String location = getLocationFromExif(path);
        String date = getDate(dateTaken.getTime());
        String time = getTime(dateTaken.getTime());

        Entity photoEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_PHOTO,
                ENTITY_NAME_PHOTO);
        photoEntity.addAttribute("filePath", path);
        photoEntity.addAttribute("date", date);
        photoEntity.addAttribute("time", time);
        if (!location.isEmpty()) photoEntity.addAttribute("location", location);
        if (!mKnowledgeManager.addEntity(mEmbeddingManager, photoEntity)) return;

        Entity dateEntity = null;
        if (photoEntity.hasAttribute("date")) {
            dateEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_DATE,
                    ENTITY_NAME_DATE);
            dateEntity.addAttribute("date", date);
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
                photoEntity, RELATIONSHIP_ATTACHED_IN, photoEntity);

        mListener.onUpdate();
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

    @Override
    public void update(int type, MonitoringService.EmbeddingResultListener listener) {
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
}
