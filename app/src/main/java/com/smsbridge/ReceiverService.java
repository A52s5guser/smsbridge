package com.smsbridge;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;

public class ReceiverService extends Service {
    private static final String TAG = "SmsBridge.Receiver";

    public static final String ACTION_START = "com.smsbridge.action.RECEIVER_START";
    public static final String ACTION_STOP = "com.smsbridge.action.RECEIVER_STOP";

    private BtController.BtListener listener;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannels(this);
        listener = new BtController.BtListener(this, new BtController.BtListener.FrameHandler() {
            @Override
            public void onFrame(JSONObject frame) {
                dispatch(frame);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            Log.d(TAG, "stop requested");
            listener.stop();
            stopSelf();
            return START_NOT_STICKY;
        }

        listener.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        listener.stop();
        super.onDestroy();
    }

    private void dispatch(JSONObject frame) {
        String type = frame.optString(Protocol.FIELD_TYPE);
        if (Protocol.TYPE_SMS.equals(type)) {
            onMessage(frame);
        } else if (Protocol.TYPE_CALL.equals(type)) {
            onCall(frame);
        } else if (Protocol.TYPE_SMS_STATUS.equals(type)) {
            onSmsStatus(frame);
        } else if (Protocol.TYPE_CONTACTS.equals(type)) {
            onContacts(frame);
        } else {
            Log.w(TAG, "unknown frame type " + type);
        }
    }

    private void onMessage(JSONObject frame) {
        try {
            String from = frame.optString(Protocol.FIELD_FROM, "");
            String body = frame.optString(Protocol.FIELD_BODY, "");
            long ts = frame.optLong(Protocol.FIELD_TS, System.currentTimeMillis());
            String device = frame.optString(Protocol.FIELD_DEVICE, "");
            Log.d(TAG, "received sms: from=" + from + " device=" + device);

            MessagesStore.get(this).add(new MessagesStore.Received(from, body, ts, device));
            NotificationHelper.incomingSms(this, from, body);

            Intent i = new Intent(Bus.ACTION_MSG)
                    .setPackage(getPackageName())
                    .putExtra(Bus.EXTRA_FROM, from)
                    .putExtra(Bus.EXTRA_BODY, body)
                    .putExtra(Bus.EXTRA_TS, ts);
            sendBroadcast(i);
        } catch (Exception e) {
            Log.e(TAG, "failed to process sms frame", e);
        }
    }

    private void onSmsStatus(JSONObject frame) {
        try {
            String to = frame.optString(Protocol.FIELD_TO, "");
            String body = frame.optString(Protocol.FIELD_BODY, "");
            boolean ok = frame.optBoolean(Protocol.FIELD_OK, false);
            String err = frame.optString(Protocol.FIELD_ERR, "");
            long ts = frame.optLong(Protocol.FIELD_TS, System.currentTimeMillis());
            String msgId = frame.optString(Protocol.FIELD_MSG_ID, "");
            Log.d(TAG, "sms status to=" + to + " ok=" + ok + " err=" + err);

            OutboxStore.get(this).addStatus(to, body, ts, ok, err, msgId);
            NotificationHelper.smsStatus(this, to, ok, err);

            Intent i = new Intent(Bus.ACTION_SMS_STATUS)
                    .setPackage(getPackageName())
                    .putExtra(Protocol.FIELD_TO, to)
                    .putExtra(Protocol.FIELD_BODY, body)
                    .putExtra(Protocol.FIELD_OK, ok)
                    .putExtra(Protocol.FIELD_ERR, err)
                    .putExtra(Bus.EXTRA_TS, ts);
            sendBroadcast(i);
        } catch (Exception e) {
            Log.e(TAG, "failed to process sms status frame", e);
        }
    }

    private void onCall(JSONObject frame) {
        try {
            String event = frame.optString(Protocol.FIELD_EVENT, "");
            String number = frame.optString(Protocol.FIELD_NUMBER, "");
            String name = frame.optString(Protocol.FIELD_NAME, "");
            long ts = frame.optLong(Protocol.FIELD_TS, System.currentTimeMillis());
            String device = frame.optString(Protocol.FIELD_DEVICE, "");
            Log.d(TAG, "received call event: " + event + " number=" + number + " device=" + device);

            CallLogStore.get(this).add(new CallLogStore.Call(event, number, name, ts));
            NotificationHelper.incomingCall(this, event, number, name);
            if (Protocol.EV_RINGING.equals(event)) {
                CallRinger.on(this);
            } else {
                CallRinger.off();
            }

            Intent i = new Intent(Bus.ACTION_CALL)
                    .setPackage(getPackageName())
                    .putExtra(Bus.EXTRA_EVENT, event)
                    .putExtra(Protocol.FIELD_NUMBER, number)
                    .putExtra(Protocol.FIELD_NAME, name)
                    .putExtra(Bus.EXTRA_TS, ts);
            sendBroadcast(i);
        } catch (Exception e) {
            Log.e(TAG, "failed to process call frame", e);
        }
    }

    private void onContacts(JSONObject frame) {
        try {
            org.json.JSONArray arr = frame.optJSONArray(Protocol.FIELD_CONTACTS);
            if (arr == null) return;
            java.util.List<ContactsStore.Contact> contacts = new java.util.ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                contacts.add(new ContactsStore.Contact(
                        o.optString("name", ""),
                        o.optString("number", "")));
            }
            ContactsStore.get(this).replaceAll(contacts);
            Log.d(TAG, "contacts updated: " + contacts.size());
            sendBroadcast(new Intent(Bus.ACTION_CONTACTS).setPackage(getPackageName()));
        } catch (Exception e) {
            Log.e(TAG, "failed to process contacts frame", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}