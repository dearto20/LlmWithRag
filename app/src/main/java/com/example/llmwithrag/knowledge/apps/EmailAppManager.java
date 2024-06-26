package com.example.llmwithrag.knowledge.apps;

import static com.example.llmwithrag.BuildConfig.EMAIL_ADDRESS;
import static com.example.llmwithrag.BuildConfig.EMAIL_PASSWORD;
import static com.example.llmwithrag.Utils.getContactNameByEmail;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_EMAIL;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_TYPE_USER;
import static com.example.llmwithrag.kg.KnowledgeManager.ENTITY_NAME_MESSAGE_IN_THE_EMAIL_APP;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.example.llmwithrag.MonitoringService;
import com.example.llmwithrag.kg.Entity;
import com.example.llmwithrag.kg.KnowledgeManager;
import com.example.llmwithrag.knowledge.IKnowledgeComponent;
import com.example.llmwithrag.llm.EmbeddingManager;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMultipart;

public class EmailAppManager implements IKnowledgeComponent {
    private static final String TAG = EmailAppManager.class.getSimpleName();
    private static final String EMAIL_STORE_PROTOCOL = "imaps";
    private static final String EMAIL_HOST_ADDRESS = "imap.gmail.com";
    private static final String EMAIL_HOST_PORT = "993";
    private static final String EMAIL_HOST_SSL_ENABLE = "true";
    private static final String EMAIL_HOST_FOLDER = "INBOX";
    private final Context mContext;
    private final KnowledgeManager mKnowledgeManager;
    private final EmbeddingManager mEmbeddingManager;
    private final Handler mHandler;

    private boolean mRunning = false;
    private IMAPFolder mInbox;
    private IMAPStore mStore;

    public EmailAppManager(Context context, KnowledgeManager knowledgeManager,
                           EmbeddingManager embeddingManager) {
        mContext = context;
        mKnowledgeManager = knowledgeManager;
        mEmbeddingManager = embeddingManager;
        HandlerThread handlerThread = new HandlerThread(EmailAppManager.class.getSimpleName());
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
        mInbox = null;
        mStore = null;
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
        Thread thread = mHandler.getLooper().getThread();
        try {
            thread.stop();
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

    @Override
    public void update(int type, MonitoringService.EmbeddingResultListener listener) {
        listener.onSuccess();
    }

    private void connectToStore() throws MessagingException {
        if (mStore == null || !mStore.isConnected()) {
            Properties properties = new Properties();
            properties.put("mail.store.protocol", EMAIL_STORE_PROTOCOL);
            properties.put("mail.imap.host", EMAIL_HOST_ADDRESS);
            properties.put("mail.imap.port", EMAIL_HOST_PORT);
            properties.put("mail.imap.ssl.enable", EMAIL_HOST_SSL_ENABLE);

            Session session = Session.getDefaultInstance(properties);
            mStore = (IMAPStore) session.getStore(EMAIL_STORE_PROTOCOL);
            mStore.connect(EMAIL_HOST_ADDRESS, EMAIL_ADDRESS, EMAIL_PASSWORD);
        }
        openInbox();
        addMessageCountListener();
    }

    private void openInbox() throws MessagingException {
        mInbox = (IMAPFolder) mStore.getFolder(EMAIL_HOST_FOLDER);
        mInbox.open(Folder.READ_ONLY);
    }

    private void addMessageCountListener() {
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
                        String body = getBody(message);

                        Log.i(TAG, "address : " + address);
                        Log.i(TAG, "sender : " + sender);
                        Log.i(TAG, "subject : " + subject);
                        Log.i(TAG, "body : " + body);
                        Log.i(TAG, "date : " + dateString);
                        Log.i(TAG, "time : " + timeString);


                        Entity emailEntity = new Entity(UUID.randomUUID().toString(),
                                ENTITY_TYPE_EMAIL, ENTITY_NAME_MESSAGE_IN_THE_EMAIL_APP);
                        emailEntity.addAttribute("address", address);
                        emailEntity.addAttribute("sender", sender);
                        emailEntity.addAttribute("subject", subject);
                        emailEntity.addAttribute("body", body);
                        emailEntity.addAttribute("date", dateString);
                        emailEntity.addAttribute("time", timeString);

                        Entity oldEmailEntity = mKnowledgeManager.getEntity(emailEntity);
                        if (mKnowledgeManager.equals(oldEmailEntity, emailEntity)) continue;
                        if (oldEmailEntity != null) {
                            mKnowledgeManager.removeEntity(oldEmailEntity);
                            mKnowledgeManager.removeEmbedding(mEmbeddingManager, oldEmailEntity);
                        }
                        mKnowledgeManager.addEntity(emailEntity);
                        mKnowledgeManager.removeEmbedding(mEmbeddingManager, emailEntity);
                        mKnowledgeManager.addEmbedding(mEmbeddingManager, emailEntity, date.getTime());
                        Log.i(TAG, "added " + emailEntity);

                        Entity userEntity = new Entity(UUID.randomUUID().toString(),
                                ENTITY_TYPE_USER, address);
                        userEntity.addAttribute("name", sender);

                        Entity oldUserEntity = mKnowledgeManager.getEntity(userEntity);
                        if (mKnowledgeManager.equals(oldUserEntity, userEntity)) continue;
                        if (oldUserEntity != null) {
                            mKnowledgeManager.removeEntity(oldUserEntity);
                            mKnowledgeManager.removeEmbedding(mEmbeddingManager, oldUserEntity);
                        }
                        mKnowledgeManager.addEntity(userEntity);
                        mKnowledgeManager.removeEmbedding(mEmbeddingManager, userEntity);
                        mKnowledgeManager.addEmbedding(mEmbeddingManager, userEntity, date.getTime());
                        Log.i(TAG, "added " + userEntity);
                    } catch (Throwable e) {
                        Log.e(TAG, e.toString());
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void checkEmails() throws Exception {
        while (mRunning) {
            try {
                connectToStore();
                if (!mInbox.isOpen()) openInbox();
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

    private String getBody(Message message) {
        try {
            if (message.isMimeType("text/plain")) {
                Log.i(TAG, "1");
                return message.getContent().toString();
            } else if (message.isMimeType("text/html")) {
                Log.i(TAG, "2");
                return Jsoup.parse((String) message.getContent()).text();
            } else if (message.isMimeType("multipart/*")) {
                Log.i(TAG, "3");
                MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
                return getTextFromMimeMultipart(mimeMultipart);
            }
        } catch (Throwable e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return "";
    }

    private String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws MessagingException, IOException {
        StringBuilder htmlText = new StringBuilder();
        StringBuilder plainText = new StringBuilder();
        int count = mimeMultipart.getCount();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                plainText.append(bodyPart.getContent().toString());
            } else if (bodyPart.isMimeType("text/html")) {
                htmlText.append(Jsoup.parse((String) bodyPart.getContent()).text());
            } else if (bodyPart.isMimeType("multipart/*")) {
                if (bodyPart.isMimeType("text/plain")) {
                    plainText.append(bodyPart.getContent().toString());
                } else if (bodyPart.isMimeType("text/html")) {
                    htmlText.append(Jsoup.parse((String) bodyPart.getContent()).text());
                }
            }
        }
        return htmlText.length() > 0 ? htmlText.toString() : plainText.toString();
    }
}
