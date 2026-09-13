package com.smsbridge;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ContactsStore {
    private static final String FILE = "contacts.json";

    private final List<Contact> items = new ArrayList<>();
    private final File file;
    private static ContactsStore s;

    public static class Contact {
        public final String name;
        public final String number;

        public Contact(String name, String number) {
            this.name = name == null ? "" : name;
            this.number = number == null ? "" : number;
        }
    }

    private ContactsStore(Context c) {
        file = new File(c.getFilesDir(), FILE);
        load();
    }

    public static synchronized ContactsStore get(Context c) {
        if (s == null) s = new ContactsStore(c);
        return s;
    }

    public synchronized List<Contact> getAll() {
        return new ArrayList<>(items);
    }

    public synchronized int count() {
        return items.size();
    }

    public synchronized void replaceAll(List<Contact> contacts) {
        items.clear();
        items.addAll(contacts);
        persist();
    }

    private void load() {
        items.clear();
        try {
            FileInputStream in = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) break;
                off += n;
            }
            in.close();
            JSONArray arr = new JSONArray(new String(data, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                items.add(new Contact(
                        o.optString("name", ""),
                        o.optString("number", "")));
            }
        } catch (Exception ignored) {
        }
    }

    private void persist() {
        try {
            JSONArray arr = new JSONArray();
            for (Contact c : items) {
                JSONObject o = new JSONObject();
                o.put("name", c.name);
                o.put("number", c.number);
                arr.put(o);
            }
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            w.write(arr.toString());
            w.flush();
            w.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}