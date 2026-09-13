package com.smsbridge;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ForwarderService extends Service {
    private static final String TAG = "SmsBridge.Forwarder";

    public static final String ACTION_START = "com.smsbridge.action.SENDER_START";
    public static final String ACTION_STOP = "com.smsbridge.action.SENDER_STOP";
    public static final String ACTION_FORWARD = "com.smsbridge.action.FORWARD";
    public static final String ACTION_SMS_STATUS = "com.smsbridge.action.SMS_STATUS";
    public static final String EXTRA_FROM = "from";
    public static final String EXTRA_BODY = "body";
    public static final String EXTRA_TS = "ts";

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private BtController.BtListener cmdListener;
    private CallMonitor callMonitor;
    private int msgSeq;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannels(this);
        cmdListener = new BtController.BtListener(this, new BtController.BtListener.FrameHandler() {
            @Override
            public void onFrame(JSONObject frame) {
                exec.execute(() -> dispatchInbound(frame));
            }
        });
        callMonitor = new CallMonitor(this);
        callMonitor.setSink(new CallMonitor.EventSink() {
            @Override
            public void onEvent(String event, String number, String name, long ts) {
                exec.execute(() -> sendCallEvent(event, number, name, ts));
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            Log.d(TAG, "stop requested");
            cmdListener.stop();
            callMonitor.stop();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NotificationHelper.ID_SENDER,
                NotificationHelper.serviceNotification(this,
                        getString(R.string.notif_sender_title),
                        getString(R.string.notif_sender_text)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);

        cmdListener.start();
        callMonitor.start();

        if (ACTION_FORWARD.equals(action)) {
            final String from = intent.getStringExtra(EXTRA_FROM);
            final String body = intent.getStringExtra(EXTRA_BODY);
            final long ts = intent.getLongExtra(EXTRA_TS, System.currentTimeMillis());
            exec.execute(() -> forwardSms(from, body, ts));
        }

        if (ACTION_SMS_STATUS.equals(action)) {
            final String to = intent.getStringExtra(SmsSentReceiver.EXTRA_TO);
            final String body = intent.getStringExtra(SmsSentReceiver.EXTRA_BODY);
            final String msgId = intent.getStringExtra(SmsSentReceiver.EXTRA_MSG_ID);
            final boolean ok = intent.getBooleanExtra(Protocol.FIELD_OK, false);
            final String err = intent.getStringExtra(Protocol.FIELD_ERR);
            exec.execute(() -> reportSmsStatus(to, body, msgId, ok, err));
        }

        return START_STICKY;
    }

    // ------------------------------------------------------- inbound (receiver -> sender)

    private void dispatchInbound(JSONObject frame) {
        String type = frame.optString(Protocol.FIELD_TYPE);
        if (Protocol.TYPE_CALL_CMD.equals(type)) {
            handleCmd(frame);
        } else if (Protocol.TYPE_SMS_SEND.equals(type)) {
            handleSendSms(frame);
        } else if (Protocol.TYPE_CONTACTS_REQ.equals(type)) {
            handleContactsReq();
        } else {
            log("✗ Unknown inbound frame type: " + type, false, type);
        }
    }

    // ------------------------------------------------------------------ SMS (receive -> place)

    private void handleSendSms(JSONObject frame) {
        final String to = frame.optString(Protocol.FIELD_TO, "");
        final String body = frame.optString(Protocol.FIELD_BODY, "");
        final String msgId = frame.optString(Protocol.FIELD_MSG_ID, "");
        String preview = "SMS to " + to + ": " + body;
        try {
            if (to.isEmpty() || body.isEmpty()) {
                log("✗ Bad sms.send frame", false, preview);
                return;
            }
            if (checkSelfPermission(android.Manifest.permission.SEND_SMS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                log("✗ SEND_SMS permission missing", false, preview);
                return;
            }
            String id = (msgId.isEmpty() ? String.valueOf((System.nanoTime() & 0xFFFFFFFFL)) : msgId);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(this,
                    (int) System.nanoTime(),
                    new android.content.Intent(SmsSentReceiver.ACTION_SMS_SENT)
                            .setPackage(getPackageName())
                            .putExtra(SmsSentReceiver.EXTRA_TO, to)
                            .putExtra(SmsSentReceiver.EXTRA_BODY, body)
                            .putExtra(SmsSentReceiver.EXTRA_MSG_ID, id),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                            | android.app.PendingIntent.FLAG_IMMUTABLE);
            try {
                android.telephony.SmsManager.getDefault().sendTextMessage(to, null, body, pi, null);
                log("⇒ SMS sent to " + to, true, preview);
            } catch (SecurityException se) {
                log("✗ SEND_SMS not granted: " + err(se), false, preview);
            }
        } catch (Exception e) {
            log("✗ sms.send failed: " + err(e), false, preview);
        }
    }

    private void reportSmsStatus(String to, String body, String msgId, boolean ok, String err) {
        String preview = "SMS to " + to + ": " + body;
        try {
            JSONObject frame = new JSONObject();
            frame.put(Protocol.FIELD_TYPE, Protocol.TYPE_SMS_STATUS);
            frame.put(Protocol.FIELD_TO, to);
            frame.put(Protocol.FIELD_BODY, body);
            frame.put(Protocol.FIELD_MSG_ID, msgId);
            frame.put(Protocol.FIELD_OK, ok);
            frame.put(Protocol.FIELD_ERR, err == null ? "" : err);
            frame.put(Protocol.FIELD_TS, System.currentTimeMillis());
            sendWithRetries(frame, preview);
        } catch (Exception e) {
            log("✗ status forward failed for " + to, false, preview);
        }
    }

    // ------------------------------------------------------------------ SMS

    private void forwardSms(String from, String body, long ts) {
        String preview = "SMS from " + (from == null ? "" : from) + ": " + (body == null ? "" : body);
        try {
            JSONObject frame = new JSONObject();
            frame.put(Protocol.FIELD_TYPE, Protocol.TYPE_SMS);
            frame.put(Protocol.FIELD_FROM, from);
            frame.put(Protocol.FIELD_BODY, body);
            frame.put(Protocol.FIELD_TS, ts);
            frame.put(Protocol.FIELD_DEVICE, Build.MODEL);
            sendWithRetries(frame, preview);
        } catch (Exception e) {
            log("✗ SMS forward failed: " + err(e), false, preview);
        }
    }

    // ------------------------------------------------------------------- calls (sender -> receiver)

    private void sendCallEvent(String event, String number, String name, long ts) {
        String preview = callPreview(event, number, name);
        try {
            JSONObject frame = new JSONObject();
            frame.put(Protocol.FIELD_TYPE, Protocol.TYPE_CALL);
            frame.put(Protocol.FIELD_EVENT, event);
            frame.put(Protocol.FIELD_NUMBER, number);
            frame.put(Protocol.FIELD_NAME, name);
            frame.put(Protocol.FIELD_TS, ts);
            frame.put(Protocol.FIELD_DEVICE, Build.MODEL);
            sendWithRetries(frame, preview);
        } catch (Exception e) {
            log("✗ " + preview + " (forward failed: " + err(e) + ")", false, preview);
        }
    }

    // ------------------------------------------------------------------- calls (receiver -> sender)

    private void handleCmd(JSONObject frame) {
        String cmd = frame.optString(Protocol.FIELD_CMD);
        String number = frame.optString(Protocol.FIELD_NUMBER);
        String preview = "Command " + cmd + " " + number;
        try {
            switch (cmd) {
                case Protocol.CMD_DIAL:
                    CallApi.dial(this, number);
                    log("⇆ Dialling " + number +
                            (CallApi.isDefaultDialer(this) ? " (SMSBridge)" : ""), true, preview);
                    break;
                case Protocol.CMD_ANSWER:
                    CallApi.answer(this);
                    log("⇆ Answered (speaker on)", true, preview);
                    break;
                case Protocol.CMD_REJECT:
                    CallApi.end(this);
                    log("⇆ Call rejected", true, preview);
                    break;
                case Protocol.CMD_HANGUP:
                    CallApi.end(this);
                    log("⇆ Call hung up", true, preview);
                    break;
                case Protocol.CMD_SPEAKER:
                    CallApi.speaker(this, !CallApi.isDefaultDialer(this));
                    log("⇆ Speaker toggled", true, preview);
                    break;
                case Protocol.CMD_VOICE_TEST:
                    runVoiceProbe();
                    break;
                default:
                    log("✗ Unknown command: " + cmd, false, preview);
            }
        } catch (SecurityException se) {
            log("✗ " + cmd + " needs SMSBridge as the phone app (set it in Settings → Apps → Choose default apps)",
                    false, preview);
        } catch (Exception e) {
            log("✗ " + cmd + " failed: " + err(e), false, preview);
        }
    }

    // ------------------------------------------------------------------ contacts (receiver -> sender)

    private void handleContactsReq() {
        String preview = "Contacts request";
        try {
            if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                log("✗ READ_CONTACTS permission missing", false, preview);
                return;
            }
            org.json.JSONArray out = new org.json.JSONArray();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            android.net.Uri uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] proj = {
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER};
            try (android.database.Cursor c = getContentResolver().query(
                    uri, proj, null, null,
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                            + " COLLATE NOCASE ASC")) {
                if (c != null) {
                    while (c.moveToNext() && out.length() < 2000) {
                        String name = c.getString(0);
                        String number = c.getString(1);
                        if (number == null || number.trim().isEmpty()) continue;
                        String clean = number.replaceAll("[^0-9+]", "");
                        if (clean.isEmpty() || !seen.add(clean)) continue;
                        org.json.JSONObject entry = new org.json.JSONObject();
                        entry.put("name", name == null ? "" : name);
                        entry.put("number", clean);
                        out.put(entry);
                    }
                }
            }
            org.json.JSONObject frame = new org.json.JSONObject();
            frame.put(Protocol.FIELD_TYPE, Protocol.TYPE_CONTACTS);
            frame.put(Protocol.FIELD_CONTACTS, out);
            sendWithRetries(frame, preview);
        } catch (Exception e) {
            log("✗ contacts request failed: " + err(e), false, preview);
        }
    }

    // ------------------------------------------------------------------ shared sender

    private void runVoiceProbe() {
        exec.execute(() -> log(VoiceCapture.captureToFile(
                this, new java.io.File(getFilesDir(), "voicetest.wav"), 3), true, "voice_test"));
    }

    private void sendWithRetries(JSONObject frame, String preview) {
        String peerMac = Prefs.get(this).getPeerMac();
        String peerName = Prefs.get(this).getPeerName();
        if (peerMac == null) {
            log("✗ No peer device selected", false, preview);
            return;
        }
        boolean ok = false;
        String msg = null;
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            try {
                Log.d(TAG, "sending frame to " + peerMac + " (attempt " + (attempt + 1) + ")");
                ok = BtController.sendFrame(this, peerMac, frame);
                msg = ok ? "✓ Delivered to " + peerName : "✗ No acknowledgement from " + peerName;
            } catch (Exception e) {
                Log.e(TAG, "send attempt " + (attempt + 1) + " failed", e);
                msg = "✗ Failed: " + err(e);
                try {
                    Thread.sleep(1200L);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
        log(ok ? msg : (msg == null ? "✗ Failed" : msg), ok, preview);
    }

    private void log(String text, boolean ok, String preview) {
        LogStore.get().add(text + "\n" + preview, ok);
        Intent i = new Intent(Bus.ACTION_LOG)
                .setPackage(getPackageName())
                .putExtra(Bus.EXTRA_TEXT, text + "\n" + preview)
                .putExtra(Bus.EXTRA_OK, ok)
                .putExtra(Bus.EXTRA_TS, System.currentTimeMillis());
        sendBroadcast(i);
    }

    private static String callPreview(String event, String number, String name) {
        StringBuilder sb = new StringBuilder("Call " + event);
        if (name != null && !name.isEmpty()) sb.append(" ").append(name);
        if (number != null && !number.isEmpty()) sb.append(" <").append(number).append(">");
        return sb.toString();
    }

    private static String err(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    @Override
    public void onDestroy() {
        cmdListener.stop();
        callMonitor.stop();
        exec.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}