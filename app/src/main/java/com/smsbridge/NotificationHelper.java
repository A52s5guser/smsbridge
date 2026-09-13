package com.smsbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public final class NotificationHelper {
    public static final String CH_SVC = "smsbridge_svc";
    public static final String CH_MSG = "smsbridge_msg";
    public static final String CH_CALL = "smsbridge_call";
    public static final int ID_SENDER = 1001;
    public static final int ID_RECEIVER = 1002;

    private NotificationHelper() {
    }

    public static void createChannels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(
                CH_SVC, c.getString(R.string.notif_channel_svc), NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(
                CH_MSG, c.getString(R.string.notif_channel_msg), NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(
                CH_CALL, c.getString(R.string.notif_channel_call), NotificationManager.IMPORTANCE_HIGH));
    }

    public static Notification serviceNotification(Context c, String title, String text) {
        return new Notification.Builder(c, CH_SVC)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    public static void incomingSms(Context c, String from, String body) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification n = new Notification.Builder(c, CH_MSG)
                .setSmallIcon(R.drawable.ic_notif_sms)
                .setContentTitle("SMS from " + (from == null ? "unknown" : from))
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();
        nm.notify((int) (System.currentTimeMillis() & 0x7FFFFFFF), n);
    }

    public static void incomingCall(Context c, String event, String number, String name) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String who = (name != null && !name.isEmpty()) ? (name + "  " + number) : number;
        if (who == null || who.isEmpty()) who = "unknown";

        String detail;
        int icon = R.drawable.ic_notif_call;
        final java.util.Map<String, String> what = new java.util.HashMap<>();
        what.put(Protocol.EV_RINGING, "is calling\u2026");
        what.put(Protocol.EV_DIALING, "dialling\u2026");
        what.put(Protocol.EV_ANSWERED, "\u2014 answered");
        what.put(Protocol.EV_MISSED, "\u2014 missed");
        what.put(Protocol.EV_ENDED, "\u2014 ended");
        what.put(Protocol.EV_IDLE, "\u2014 idle");
        detail = what.get(event);
        if (detail == null) detail = "\u2014 " + event;

        Notification n = new Notification.Builder(c, CH_CALL)
                .setSmallIcon(icon)
                .setContentTitle("Call " + detail)
                .setContentText(who)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();
        nm.notify((int) (System.currentTimeMillis() & 0x7FFFFFFF) ^ 0xCA, n);
    }

    public static void smsStatus(Context c, String to, boolean ok, String err) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String title = ok ? "SMS sent to " + to : ("SMS to " + to + " failed");
        String text = ok ? "\u2705 Delivered to the network" : (err == null ? "" : err);
        Notification n = new Notification.Builder(c, CH_MSG)
                .setSmallIcon(R.drawable.ic_notif_sms)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(ok ? Notification.PRIORITY_DEFAULT : Notification.PRIORITY_HIGH)
                .build();
        nm.notify((int) (System.currentTimeMillis() & 0x7FFFFFFF) ^ 0x5E, n);
    }
}