package com.example.llmwithrag.knowledge.apps;

import static com.example.llmwithrag.BuildConfig.EMAIL_ADDRESS;
import static com.example.llmwithrag.BuildConfig.EMAIL_PASSWORD;
import static com.example.llmwithrag.Utils.getContactNameByEmail;
import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_EMAIL;
import static com.example.llmwithrag.kg.KnowledgeGraphManager.ENTITY_TYPE_USER;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeGraphManager;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;
import com.example.llmwithrag.llm.EmbeddingManager;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.internet.InternetAddress;

public class EmailAppManager implements IKnowledgeComponent {
    private static final String TAG = EmailAppManager.class.getSimpleName();
    private static final String EMAIL_STORE_PROTOCOL = "imaps";
    private static final String EMAIL_HOST_ADDRESS = "imap.gmail.com";
    private static final String EMAIL_HOST_PORT = "993";
    private static final String EMAIL_HOST_SSL_ENABLE = "true";
    private static final String EMAIL_HOST_FOLDER = "INBOX";
    private final Context mContext;
    private final KnowledgeGraphManager mKgManager;
    private final EmbeddingManager mEmbeddingManager;

    private boolean mRunning = false;
    private Handler mHandler;
    private IMAPFolder mInbox;
    private IMAPStore mStore;

    public EmailAppManager(Context context, KnowledgeGraphManager kgManager,
                           EmbeddingManager embeddingManager) {
        mContext = context;
        mKgManager = kgManager;
        mEmbeddingManager = embeddingManager;
        HandlerThread handlerThread = new HandlerThread(EmailAppManager.class.getSimpleName());
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }

    @Override
    public void deleteAll() {

    }

    @Override
    public void startMonitoring() {
        if (mRunning) return;
        Log.i(TAG, "started");
        mRunning = true;
        mHandler.post(() -> {
            try {
                checkEmails();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void stopMonitoring() {
        Log.i(TAG, "stopped");
        mRunning = false;
        mHandler.removeCallbacksAndMessages(null);
        try {
            if (mInbox != null && mInbox.isOpen()) {
                mInbox.close(true);
            }
            if (mStore != null && mStore.isConnected()) {
                mStore.close();
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    private void checkEmails() throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", EMAIL_STORE_PROTOCOL);
        properties.put("mail.imap.host", EMAIL_HOST_ADDRESS);
        properties.put("mail.imap.port", EMAIL_HOST_PORT);
        properties.put("mail.imap.ssl.enable", EMAIL_HOST_SSL_ENABLE);

        Session session = Session.getDefaultInstance(properties);
        mStore = (IMAPStore) session.getStore(EMAIL_STORE_PROTOCOL);
        mStore.connect(EMAIL_HOST_ADDRESS, EMAIL_ADDRESS, EMAIL_PASSWORD);
        mInbox = (IMAPFolder) mStore.getFolder(EMAIL_HOST_FOLDER);
        mInbox.open(Folder.READ_ONLY);

        mInbox.addMessageCountListener(new MessageCountAdapter() {
            public void messagesAdded(MessageCountEvent ev) {
                Message[] messages = ev.getMessages();
                for (Message message : messages) {
                    try {
                        String address = ((InternetAddress) message.getFrom()[0]).getAddress();
                        String sender = getContactNameByEmail(mContext, address);
                        Date date = message.getReceivedDate();
                        String dateString = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(date);
                        String timeString = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                .format(date);
                        String subject = message.getSubject();
                        String body = message.getContent().toString();

                        Entity emailEntity = new Entity(UUID.randomUUID().toString(),
                                ENTITY_TYPE_EMAIL, body);
                        emailEntity.addAttribute("address", address);
                        emailEntity.addAttribute("sender", sender);
                        emailEntity.addAttribute("subject", subject);
                        emailEntity.addAttribute("body", body);
                        emailEntity.addAttribute("date", dateString);
                        emailEntity.addAttribute("time", timeString);

                        Entity oldEmailEntity = mKgManager.getEntity(emailEntity);
                        if (mKgManager.equals(oldEmailEntity, emailEntity)) continue;
                        if (oldEmailEntity != null) {
                            mKgManager.removeEntity(oldEmailEntity);
                            mKgManager.removeEmbedding(mEmbeddingManager, oldEmailEntity);
                        }
                        mKgManager.addEntity(emailEntity);
                        mKgManager.removeEmbedding(mEmbeddingManager, emailEntity);
                        mKgManager.addEmbedding(mEmbeddingManager, emailEntity);
                        Log.i(TAG, "added " + emailEntity);

                        Entity userEntity = new Entity(UUID.randomUUID().toString(),
                                ENTITY_TYPE_USER, address);
                        userEntity.addAttribute("name", sender);

                        Entity oldUserEntity = mKgManager.getEntity(userEntity);
                        if (mKgManager.equals(oldUserEntity, userEntity)) continue;
                        if (oldUserEntity != null) {
                            mKgManager.removeEntity(oldUserEntity);
                            mKgManager.removeEmbedding(mEmbeddingManager, oldUserEntity);
                        }
                        mKgManager.addEntity(userEntity);
                        mKgManager.removeEmbedding(mEmbeddingManager, userEntity);
                        mKgManager.addEmbedding(mEmbeddingManager, userEntity);
                        Log.i(TAG, "added " + userEntity);
                    } catch (Throwable e) {
                        Log.e(TAG, e.toString());
                        e.printStackTrace();
                    }
                }
            }
        });

        while (mRunning) {
            try {
                mInbox.idle();
            } catch (MessagingException e1) {
                if (mRunning) {
                    Log.e(TAG, e1.toString());
                    Thread.sleep(10000);
                }
            } catch (Throwable e2) {
                Log.e(TAG, e2.toString());
                e2.printStackTrace();
            }
        }
    }
}
