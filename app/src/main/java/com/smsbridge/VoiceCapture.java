package com.smsbridge;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

public final class VoiceCapture {
    public static final int SAMPLE_RATE = 16000;
    public static final int CHANNELS = 1;

    private VoiceCapture() {
    }

    /** Captures {@code seconds} of AudioSource.VOICE_CALL into {@code out} (16k mono 16-bit WAV). */
    public static String captureToFile(Context ctx, File out, int seconds) {
        long sumSq = 0;
        int frames = 0;
        byte[] pcm = null;
        int sr = SAMPLE_RATE;
        try {
            int min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord rec = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, sr * 2))
                    .build();
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                rec.release();
                return "AudioRecord NOT initialized (VOICE_CALL blocked?)";
            }
            rec.startRecording();
            int total = sr * seconds;
            short[] buf = new short[sr];
            java.io.ByteArrayOutputStream acc = new java.io.ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(acc);
            long started = System.currentTimeMillis();
            while (frames < total && System.currentTimeMillis() - started < (seconds + 3) * 1000L) {
                int n = rec.read(buf, 0, Math.min(buf.length, total - frames),
                        AudioRecord.READ_BLOCKING);
                if (n <= 0) break;
                for (int i = 0; i < n; i++) {
                    d.writeShort(buf[i]);
                    sumSq += (long) buf[i] * buf[i];
                }
                frames += n;
            }
            rec.stop();
            rec.release();
            pcm = acc.toByteArray();
        } catch (Throwable t) {
            return "EXCEPTION: " + (t.getMessage() == null ? t.toString() : t.getMessage());
        }
        double db = 20 * Math.log10(Math.sqrt(sumSq / (double) Math.max(frames, 1)) / 32768.0 + 1e-9);
        writeWav(out, pcm, sr, frames);
        return "frames=" + frames + " rms=" + String.format(Locale.US, "%.1f", db) + " dB"
                + (frames == 0 ? " (nothing captured)" : "");
    }

    private static void writeWav(File f, byte[] pcm, int sr, int frames) {
        try {
            DataOutputStream out = new DataOutputStream(new FileOutputStream(f));
            out.writeBytes("RIFF");
            out.writeInt(36 + pcm.length);
            out.writeBytes("WAVEfmt ");
            out.writeInt(16);
            out.writeShort(1);
            out.writeShort(CHANNELS);
            out.writeInt(sr);
            out.writeInt(sr * CHANNELS * 2);
            out.writeShort(CHANNELS * 2);
            out.writeShort(16);
            out.writeBytes("data");
            out.writeInt(pcm.length);
            out.write(pcm);
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}