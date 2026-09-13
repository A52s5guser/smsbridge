package com.smsbridge;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.telecom.TelecomManager;

public final class CallApi {
    private CallApi() {
    }

    public static boolean hasTelephonyPerms(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
                && c.checkSelfPermission(Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isDefaultDialer(Context c) {
        try {
            RoleManager rm = (RoleManager) c.getSystemService(Context.ROLE_SERVICE);
            return rm.isRoleHeld(RoleManager.ROLE_DIALER);
        } catch (Exception ignored) {
        }
        return false;
    }

    public static void requestDialerRole(Activity a, int requestCode) {
        RoleManager rm = (RoleManager) a.getSystemService(Context.ROLE_SERVICE);
        if (!rm.isRoleAvailable(RoleManager.ROLE_DIALER)) return;
        a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), requestCode);
    }

    /** Answers the ringing call. Requires the app to be the default dialer. */
    public static void answer(Context c) throws Exception {
        TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
        tm.acceptRingingCall();
        speaker(c, true);
    }

    /** Rejects an incoming or disconnects the active call. Requires default dialer role. */
    public static void end(Context c) throws Exception {
        TelecomManager tm = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
        tm.endCall();
    }

    /** Places a call to the number via the system dialer (or our CallActivity when default dialer). */
    public static void dial(Context c, String number) {
        Intent i = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    /** Turns speakerphone on/off and switches the audio mode so the call is audible hands-free. */
    public static void speaker(Context c, boolean on) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            am.setMode(on ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_NORMAL);
            am.setSpeakerphoneOn(on);
        } catch (Exception e) {
            android.util.Log.e("SmsBridge.CallApi", "speaker failed", e);
        }
    }

    public static boolean isValidNumber(String n) {
        if (n == null) return false;
        n = n.trim();
        if (n.isEmpty()) return false;
        for (char ch : n.toCharArray()) {
            if (!Character.isDigit(ch) && ch != '+' && ch != '*' && ch != '#') return false;
        }
        return true;
    }
}