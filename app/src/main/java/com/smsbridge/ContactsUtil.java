package com.smsbridge;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

public final class ContactsUtil {
    private ContactsUtil() {
    }

    /** Looks up a display name for a phone number, or "" when unknown / no permission. */
    public static String lookupName(Context c, String number) {
        if (number == null || number.isEmpty()) return "";
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) return "";
        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor cur = c.getContentResolver().query(uri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                return cur.getString(0) == null ? "" : cur.getString(0);
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}