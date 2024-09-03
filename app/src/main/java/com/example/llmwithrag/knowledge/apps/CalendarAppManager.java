package com.example.llmwithrag.knowledge.apps;

import static com.example.llmwithrag.Utils.getReadableAddressFromCoordinates;
import static com.example.llmwithrag.datasource.IDataSourceTracker.KEY_DATE;
import static com.example.llmwithrag.datasource.IDataSourceTracker.KEY_END_DATE;
import static com.example.llmwithrag.datasource.IDataSourceTracker.KEY_ID;
import static com.example.llmwithrag.datasource.IDataSourceTracker.KEY_LOCATION;
import static com.example.llmwithrag.datasource.IDataSourceTracker.KEY_START_DATE;
import static com.example.llmwithrag.datasource.IDataSourceTracker.KEY_TIME;
import static com.example.llmwithrag.datasource.IDataSourceTracker.KEY_TITLE;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_DATE;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_EVENT_IN_THE_CALENDAR_APP;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_LOCATION;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_DATE;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_EVENT;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_LOCATION;
import static com.example.llmwithrag.kg.KnowledgeManager.RELATIONSHIP_HELD_AT_LOCATION;
import static com.example.llmwithrag.kg.KnowledgeManager.RELATIONSHIP_HELD_ON_DATE;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.example.llmwithrag.IKnowledgeListener;
import com.example.llmwithrag.MonitoringService;
import com.example.llmwithrag.datasource.IDataSourceListener;
import com.example.llmwithrag.datasource.IDataSourceTracker;
import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeManager;
import com.example.llmwithrag.knowledge.KnowledgeGenerator;
import com.example.llmwithrag.llm.EmbeddingManager;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class CalendarAppManager extends KnowledgeGenerator {
    private static final String TAG = CalendarAppManager.class.getSimpleName();
    private final Context mContext;
    private final KnowledgeManager mKnowledgeManager;
    private final EmbeddingManager mEmbeddingManager;
    private final IKnowledgeListener mListener;
    private final IDataSourceTracker mCalendarTracker;
    private long mStartDate;
    private long mEndDate;

    public CalendarAppManager(Map<String, IDataSourceTracker> trackers,
                              Context context, KnowledgeManager kgManager,
                              EmbeddingManager embeddingManager, IKnowledgeListener listener) {
        super(trackers);
        mContext = context;
        mKnowledgeManager = kgManager;
        mEmbeddingManager = embeddingManager;
        mListener = listener;
        mCalendarTracker = Objects.requireNonNull(trackers.get(TRACKER_CALENDAR));
    }

    @Override
    public void deleteAll() {
        mKnowledgeManager.removeEntity(mEmbeddingManager, ENTITY_TYPE_EVENT);
    }

    @Override
    public void startMonitoring() {
        Log.i(TAG, "started");
        mCalendarTracker.startMonitoring();
        mCalendarTracker.registerListener(mCalendarListener);
    }

    @Override
    public void stopMonitoring() {
        Log.i(TAG, "stopped");
        mCalendarTracker.unregisterListener(mCalendarListener);
        mCalendarTracker.stopMonitoring();
    }

    @Override
    public void update(int type, MonitoringService.EmbeddingResultListener listener) {
        listener.onSuccess();
    }

    private final IDataSourceListener mCalendarListener = new IDataSourceListener() {
        @Override
        public void onUpdate(Map<String, Object> data) {
            String id = (String) data.get(KEY_ID);
            String title = (String) data.get(KEY_TITLE);
            String date = (String) data.get(KEY_DATE);
            String time = (String) data.get(KEY_TIME);
            String location = (String) data.get(KEY_LOCATION);
            String startDate = (String) data.get(KEY_START_DATE);
            String endDate = (String) data.get(KEY_END_DATE);

            Entity eventEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_EVENT,
                    ENTITY_NAME_EVENT_IN_THE_CALENDAR_APP);
            eventEntity.addAttribute("eventId", id);
            eventEntity.addAttribute("title", title);
            eventEntity.addAttribute("date", date);
            eventEntity.addAttribute("time", time);
            if (!TextUtils.isEmpty(location)) eventEntity.addAttribute("location", location);
            eventEntity.addAttribute("startDate", startDate);
            eventEntity.addAttribute("endDate", endDate);
            mKnowledgeManager.addEntity(mEmbeddingManager, eventEntity,
                    new MonitoringService.EmbeddingResultListener() {
                        @Override
                        public void onSuccess() {
                            Entity dateEntity = null;
                            if (eventEntity.hasAttribute("date")) {
                                dateEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_DATE,
                                        ENTITY_NAME_DATE);
                                dateEntity.addAttribute("date", startDate);
                                mKnowledgeManager.addEntity(mEmbeddingManager, dateEntity);
                            }

                            Entity locationEntity = null;
                            if (eventEntity.hasAttribute("location")) {
                                locationEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_LOCATION,
                                        ENTITY_NAME_LOCATION);
                                locationEntity.addAttribute("coordinate", location);
                                locationEntity.addAttribute("location", getReadableAddressFromCoordinates(mContext, location));
                                mKnowledgeManager.addEntity(mEmbeddingManager, locationEntity);
                            }

                            if (dateEntity != null) {
                                mKnowledgeManager.addRelationship(mEmbeddingManager,
                                        eventEntity, RELATIONSHIP_HELD_ON_DATE, dateEntity);
                            }
                            if (locationEntity != null) {
                                mKnowledgeManager.addRelationship(mEmbeddingManager,
                                        eventEntity, RELATIONSHIP_HELD_AT_LOCATION, locationEntity);
                            }

                            mListener.onUpdate();
                        }
                    });
        }
    };
}
