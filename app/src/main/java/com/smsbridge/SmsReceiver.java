package com.smsbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsBridge.SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        if (!Prefs.get(context).isSender()) {
            Log.d(TAG, "ignoring SMS: this device is not configured as sender");
            return;
        }

        try {
            StringBuilder body = new StringBuilder();
            String from = "";
            for (SmsMessage msg : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                if (from.isEmpty()) from = msg.getDisplayOriginatingAddress();
                if (msg.getMessageBody() != null) body.append(msg.getMessageBody());
            }
            if (body.length() == 0) {
                Log.d(TAG, "empty sms body, ignoring");
                return;
            }
            Log.d(TAG, "SMS received from " + from + ": " + body);

            Intent svc = new Intent(context, ForwarderService.class)
                    .setAction(ForwarderService.ACTION_FORWARD)
                    .putExtra(ForwarderService.EXTRA_FROM, from == null ? "" : from)
                    .putExtra(ForwarderService.EXTRA_BODY, body.toString())
                    .putExtra(ForwarderService.EXTRA_TS, System.currentTimeMillis());
            context.startForegroundService(svc);
        } catch (SecurityException se) {
            Log.e(TAG, "missing RECEIVE_SMS or service start blocked", se);
        } catch (Exception e) {
            Log.e(TAG, "failed to parse SMS", e);
        }
    }
}