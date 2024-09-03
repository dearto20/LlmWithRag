package com.example.llmwithrag.knowledge.apps;

import static com.example.llmwithrag.datasource.IDataSourceTracker.KEY_ADDRESS;
import static com.example.llmwithrag.datasource.IDataSourceTracker.KEY_BODY;
import static com.example.llmwithrag.datasource.IDataSourceTracker.KEY_DATE;
import static com.example.llmwithrag.datasource.IDataSourceTracker.KEY_NAME;
import static com.example.llmwithrag.datasource.IDataSourceTracker.KEY_SENDER;
import static com.example.llmwithrag.datasource.IDataSourceTracker.KEY_SUBJECT;
import static com.example.llmwithrag.datasource.IDataSourceTracker.KEY_TIME;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_DATE;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_MESSAGE_IN_THE_EMAIL_APP;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_USER;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_DATE;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_EMAIL;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_USER;
import static com.example.llmwithrag.kg.KnowledgeManager.RELATIONSHIP_SENT_BY_USER;
import static com.example.llmwithrag.kg.KnowledgeManager.RELATIONSHIP_SENT_ON_DATE;

import android.content.Context;
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

public class EmailAppManager extends KnowledgeGenerator {
    private static final String TAG = EmailAppManager.class.getSimpleName();
    private static final String EMAIL_STORE_PROTOCOL = "imaps";
    private static final String EMAIL_HOST_ADDRESS = "imap.gmail.com";
    private static final String EMAIL_HOST_PORT = "993";
    private static final String EMAIL_HOST_SSL_ENABLE = "true";
    private static final String EMAIL_HOST_FOLDER = "INBOX";
    private final Context mContext;
    private final KnowledgeManager mKnowledgeManager;
    private final EmbeddingManager mEmbeddingManager;
    private final IKnowledgeListener mListener;
    private final IDataSourceTracker mEmailTracker;

    public EmailAppManager(Map<String, IDataSourceTracker> trackers,
                           Context context, KnowledgeManager knowledgeManager,
                           EmbeddingManager embeddingManager, IKnowledgeListener listener) {
        super(trackers);
        mContext = context;
        mKnowledgeManager = knowledgeManager;
        mEmbeddingManager = embeddingManager;
        mListener = listener;
        mEmailTracker = Objects.requireNonNull(trackers.get(TRACKER_EMAIL));
    }

    @Override
    public void deleteAll() {
        mKnowledgeManager.removeEntity(mEmbeddingManager, ENTITY_TYPE_EMAIL);
    }

    @Override
    public void startMonitoring() {
        Log.i(TAG, "started");
        mEmailTracker.startMonitoring();
        mEmailTracker.registerListener(mEmailListener);
    }

    @Override
    public void stopMonitoring() {
        Log.i(TAG, "stopped");
        mEmailTracker.unregisterListener(mEmailListener);
        mEmailTracker.stopMonitoring();
    }

    @Override
    public void update(int type, MonitoringService.EmbeddingResultListener listener) {
        listener.onSuccess();
    }

    private final IDataSourceListener mEmailListener = new IDataSourceListener() {
        @Override
        public void onUpdate(Map<String, Object> data) {
            String address = (String) data.get(KEY_ADDRESS);
            String sender = (String) data.get(KEY_SENDER);
            String subject = (String) data.get(KEY_SUBJECT);
            String body = (String) data.get(KEY_BODY);
            String date = (String) data.get(KEY_DATE);
            String time = (String) data.get(KEY_TIME);
            String name = (String) data.get(KEY_NAME);

            Entity emailEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_EMAIL,
                    ENTITY_NAME_MESSAGE_IN_THE_EMAIL_APP);
            emailEntity.addAttribute("address", address);
            emailEntity.addAttribute("sender", sender);
            emailEntity.addAttribute("subject", subject);
            emailEntity.addAttribute("body", body);
            emailEntity.addAttribute("date", date);
            emailEntity.addAttribute("time", time);
            mKnowledgeManager.addEntity(mEmbeddingManager, emailEntity,
                    new MonitoringService.EmbeddingResultListener() {
                        @Override
                        public void onSuccess() {

                            Entity userEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_USER,
                                    ENTITY_NAME_USER);
                            userEntity.addAttribute("name", name);
                            mKnowledgeManager.addEntity(mEmbeddingManager, userEntity);

                            Entity dateEntity = new Entity(UUID.randomUUID().toString(), ENTITY_TYPE_DATE,
                                    ENTITY_NAME_DATE);
                            dateEntity.addAttribute("date", date);
                            mKnowledgeManager.addEntity(mEmbeddingManager, dateEntity);

                            mKnowledgeManager.addRelationship(mEmbeddingManager,
                                    emailEntity, RELATIONSHIP_SENT_BY_USER, userEntity);
                            mKnowledgeManager.addRelationship(mEmbeddingManager,
                                    emailEntity, RELATIONSHIP_SENT_ON_DATE, dateEntity);
                            mListener.onUpdate();
                        }
                    });
        }
    };
}
