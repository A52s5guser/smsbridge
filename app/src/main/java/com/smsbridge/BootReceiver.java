package com.smsbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (Prefs.get(context).isServiceStopped()) return;
        Intent svc = new Intent(context, Prefs.get(context).isSender()
                ? ForwarderService.class : ReceiverService.class)
                .setAction(Prefs.get(context).isSender()
                        ? ForwarderService.ACTION_START : ReceiverService.ACTION_START);
        try {
            context.startForegroundService(svc);
        } catch (Exception ignored) {
        }
    }
}