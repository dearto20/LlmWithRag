package com.example.llmwithrag.datasource.apps;

import static com.example.llmwithrag.BuildConfig.EMAIL_ADDRESS;
import static com.example.llmwithrag.BuildConfig.EMAIL_PASSWORD;
import static com.example.llmwithrag.Utils.getContactNameByEmail;
import static com.example.llmwithrag.Utils.getDate;
import static com.example.llmwithrag.Utils.getTime;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;

import com.example.llmwithrag.datasource.IDataSourceListener;
import com.example.llmwithrag.datasource.IDataSourceTracker;
import com.example.llmwithrag.knowledge.apps.EmailAppManager;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMultipart;

public class EmailTracker implements IDataSourceTracker {
    private static final String TAG = EmailTracker.class.getSimpleName();
    private static final String EMAIL_STORE_PROTOCOL = "imaps";
    private static final String EMAIL_HOST_ADDRESS = "imap.gmail.com";
    private static final String EMAIL_HOST_PORT = "993";
    private static final String EMAIL_HOST_SSL_ENABLE = "true";
    private static final String EMAIL_HOST_FOLDER = "INBOX";
    private final Context mContext;
    private final Handler mHandler;
    private final List<IDataSourceListener> mListeners = new ArrayList<>();

    private IMAPFolder mInbox;
    private IMAPStore mStore;
    private boolean mStarted;

    public EmailTracker(Context context) {
        mContext = context;
        HandlerThread handlerThread = new HandlerThread(EmailAppManager.class.getSimpleName());
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
        mInbox = null;
        mStore = null;
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
        mHandler.post(() -> {
            try {
                checkEmails();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        mStarted = true;
    }

    @Override
    public void stopMonitoring() {
        if (!mStarted) return;
        Log.i(TAG, "stopped");
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
                        String name = TextUtils.isEmpty(sender) ? address : sender;
                        Date date = message.getReceivedDate();
                        String dateString = getDate(date.getTime());
                        String timeString = getTime(date.getTime());
                        String subject = message.getSubject();
                        String body = getBody(message);

                        Map<String, Object> data = new HashMap<>();
                        data.put(KEY_ADDRESS, address);
                        data.put(KEY_SENDER, sender);
                        data.put(KEY_SUBJECT, subject);
                        data.put(KEY_BODY, body);
                        data.put(KEY_DATE, dateString);
                        data.put(KEY_TIME, timeString);
                        data.put(KEY_NAME, name);

                        Iterator<IDataSourceListener> iterator = mListeners.iterator();
                        while (iterator.hasNext()) {
                            IDataSourceListener listener = iterator.next();
                            listener.onUpdate(data);
                        }
                    } catch (Throwable e) {
                        Log.e(TAG, e.toString());
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void checkEmails() throws Exception {
        while (mStarted) {
            try {
                connectToStore();
                if (!mInbox.isOpen()) {
                    Log.i(TAG, "open inbox");
                    openInbox();
                }
                if (mInbox.isOpen()) {
                    Log.i(TAG, "inbox is opened");
                    mInbox.idle();
                } else {
                    Log.i(TAG, "inbox not opened");
                }
                Thread.sleep(1000 * 60 * 10);
            } catch (MessagingException e2) {
                if (mStarted) {
                    Log.e(TAG, e2.toString());
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
                Log.i(TAG, "type 1");
                return message.getContent().toString();
            } else if (message.isMimeType("text/html")) {
                Log.i(TAG, "type 2");
                return Jsoup.parse((String) message.getContent()).text();
            } else if (message.isMimeType("multipart/*")) {
                Log.i(TAG, "type 3");
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
