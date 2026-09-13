package com.smsbridge;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;

public class SmsSentReceiver extends BroadcastReceiver {
    public static final String ACTION_SMS_SENT = "com.smsbridge.action.SMS_SENT";
    public static final String EXTRA_TO = "to";
    public static final String EXTRA_BODY = "body";
    public static final String EXTRA_MSG_ID = "msg_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_SMS_SENT.equals(intent.getAction())) return;
        boolean ok = getResultCode() == Activity.RESULT_OK;
        String err = ok ? "" : describe(getResultCode());
        Intent rep = new Intent(context, ForwarderService.class)
                .setAction(ForwarderService.ACTION_SMS_STATUS)
                .putExtra(EXTRA_TO, intent.getStringExtra(EXTRA_TO))
                .putExtra(EXTRA_BODY, intent.getStringExtra(EXTRA_BODY))
                .putExtra(EXTRA_MSG_ID, intent.getStringExtra(EXTRA_MSG_ID))
                .putExtra(Protocol.FIELD_OK, ok)
                .putExtra(Protocol.FIELD_ERR, err);
        context.startForegroundService(rep);
    }

    static String describe(int code) {
        switch (code) {
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                return "generic failure";
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                return "radio off";
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                return "no service";
            case SmsManager.RESULT_ERROR_NULL_PDU:
                return "null PDU";
            case SmsManager.RESULT_ERROR_LIMIT_EXCEEDED:
                return "limit exceeded";
            case SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE:
                return "FDN check failed";
            default:
                return "error " + code;
        }
    }
}