package com.smsbridge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.net.Uri;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Per-number profile pictures, stored as small PNGs under files/avatars/. */
public final class AvatarStore {
    private static final String DIR = "avatars";
    private static final int SAVE_PX = 256;
    private static final LruCache<String, Bitmap> cache = new LruCache<>(32);

    private AvatarStore() {
    }

    public static File fileFor(Context c, String number) {
        String clean = number == null ? "" : number.replaceAll("[^0-9+]", "");
        if (clean.isEmpty()) return null;
        return new File(new File(c.getFilesDir(), DIR), clean + ".png");
    }

    public static boolean hasAvatar(Context c, String number) {
        File f = fileFor(c, number);
        return f != null && f.exists();
    }

    /** Circular-cropped bitmap, or null if none set. */
    public static Bitmap load(Context c, String number) {
        File f = fileFor(c, number);
        if (f == null || !f.exists()) return null;
        String key = f.getAbsolutePath() + "@" + f.lastModified();
        Bitmap cached = cache.get(key);
        if (cached != null) return cached;
        try {
            Bitmap raw = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (raw == null) return null;
            Bitmap out = circular(raw);
            if (out != raw) raw.recycle();
            cache.put(key, out);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** Downscales + center-crops the picked image and saves it. Runs off the UI thread. */
    public static boolean saveFromUri(Context c, String number, Uri uri) {
        File f = fileFor(c, number);
        if (f == null || uri == null) return false;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;
            int sample = 1;
            while ((bounds.outWidth / sample) > 512 || (bounds.outHeight / sample) > 512) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap decoded;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                decoded = BitmapFactory.decodeStream(in, null, opts);
            }
            if (decoded == null) return false;
            // Camera photos are often stored rotated with an EXIF flag.
            int rotation = exifRotation(c, uri);
            if (rotation != 0) {
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(
                        decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), m, true);
                if (rotated != decoded) decoded.recycle();
                decoded = rotated;
            }
            int side = Math.min(decoded.getWidth(), decoded.getHeight());
            int x = (decoded.getWidth() - side) / 2;
            int y = (decoded.getHeight() - side) / 2;
            Bitmap cropped = Bitmap.createBitmap(decoded, x, y, side, side);
            Bitmap scaled = Bitmap.createScaledBitmap(cropped, SAVE_PX, SAVE_PX, true);
            if (scaled != cropped) cropped.recycle();
            if (scaled != decoded) decoded.recycle();
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(f)) {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            scaled.recycle();
            evict(c, number);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void remove(Context c, String number) {
        File f = fileFor(c, number);
        evict(c, number);
        if (f != null && f.exists()) f.delete();
    }

    private static void evict(Context c, String number) {
        File f = fileFor(c, number);
        if (f == null) {
            cache.evictAll();
            return;
        }
        // Drop every cached size stamp for this file.
        java.util.Map<String, Bitmap> snapshot = cache.snapshot();
        for (String key : snapshot.keySet()) {
            if (key.startsWith(f.getAbsolutePath())) cache.remove(key);
        }
    }

    /** Degrees to rotate so the image matches how it was taken. */
    private static int exifRotation(Context c, Uri uri) {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;
            android.media.ExifInterface exif = new android.media.ExifInterface(in);
            int o = exif.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL);
            switch (o) {
                case android.media.ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case android.media.ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case android.media.ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    public static Bitmap circular(Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        if (size <= 0) return src;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        c.drawBitmap(src, (size - src.getWidth()) / 2f, (size - src.getHeight()) / 2f, p);
        return out;
    }
}