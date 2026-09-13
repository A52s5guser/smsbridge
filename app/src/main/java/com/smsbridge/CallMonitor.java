package com.smsbridge;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public final class CallMonitor {
    public interface EventSink {
        void onEvent(String event, String number, String name, long ts);
    }

    private final Context ctx;
    private final TelephonyManager tm;
    private PhoneStateListener listener;
    private EventSink sink;

    private int lastState = TelephonyManager.CALL_STATE_IDLE;
    private String activeNumber = "";

    public CallMonitor(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.tm = (TelephonyManager) this.ctx.getSystemService(Context.TELEPHONY_SERVICE);
    }

    public void setSink(EventSink sink) {
        this.sink = sink;
    }

    @SuppressWarnings("deprecation")
    public void start() {
        if (listener != null) return;
        if (ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.d("SmsBridge.CallMonitor", "READ_PHONE_STATE not granted, monitor idle");
            return;
        }
        try {
            listener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String phoneNumber) {
                    handleState(state, phoneNumber);
                }
            };
            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
        } catch (Exception e) {
            android.util.Log.e("SmsBridge.CallMonitor", "listen failed", e);
            listener = null;
        }
    }

    @SuppressWarnings("deprecation")
    public void stop() {
        if (listener != null) {
            try {
                tm.listen(listener, PhoneStateListener.LISTEN_NONE);
            } catch (Exception ignored) {
            }
            listener = null;
        }
    }

    private void handleState(int state, String phoneNumber) {
        if (state != TelephonyManager.CALL_STATE_IDLE) {
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                activeNumber = phoneNumber;
            }
        }

        String event = null;
        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                event = Protocol.EV_RINGING;
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                event = lastState == TelephonyManager.CALL_STATE_RINGING
                        ? Protocol.EV_ANSWERED : Protocol.EV_DIALING;
                break;
            case TelephonyManager.CALL_STATE_IDLE:
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    event = Protocol.EV_MISSED;
                } else if (lastState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    event = Protocol.EV_ENDED;
                }
                activeNumber = "";
                break;
            default:
                break;
        }
        lastState = state;

        if (event != null && sink != null) {
            String number = activeNumber == null ? "" : activeNumber;
            String name = ContactsUtil.lookupName(ctx, number);
            sink.onEvent(event, number, name, System.currentTimeMillis());
        }
    }
}