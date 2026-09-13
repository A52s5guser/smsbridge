package com.smsbridge;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

public final class CallRinger {
    private static Ringtone ringtone;
    private static Vibrator vibrator;

    private CallRinger() {
    }

    public static void on(Context ctx) {
        Context c = ctx.getApplicationContext();
        stopInternal();
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        int mode = am.getRingerMode();
        if (mode == AudioManager.RINGER_MODE_SILENT) return;

        if (mode == AudioManager.RINGER_MODE_VIBRATE) {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                vibrator = v;
                long[] pattern = {0, 600, 700};
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    v.vibrate(pattern, 0);
                }
            }
            return;
        }

        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (uri == null) uri = Settings.System.DEFAULT_RINGTONE_URI;
        Ringtone r = RingtoneManager.getRingtone(c, uri);
        if (r == null) return;
        if (Build.VERSION.SDK_INT >= 21) {
            r.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        } else {
            r.setStreamType(AudioManager.STREAM_RING);
        }
        r.setLooping(true);
        ringtone = r;
        r.play();
    }

    public static void off() {
        stopInternal();
    }

    private static void stopInternal() {
        if (ringtone != null) {
            ringtone.stop();
            ringtone = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    public static boolean isActive() {
        return ringtone != null || vibrator != null;
    }
}