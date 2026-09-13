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
import java.util.Collections;
import java.util.List;

public final class MessagesStore {
    private static final String FILE = "received_messages.json";

    private final List<Received> items = new ArrayList<>();
    private final File file;
    private static MessagesStore s;

    public static class Received {
        public final String from;
        public final String body;
        public final long ts;
        public final String device;

        public Received(String from, String body, long ts, String device) {
            this.from = from;
            this.body = body;
            this.ts = ts;
            this.device = device == null ? "" : device;
        }
    }

    private MessagesStore(Context c) {
        file = new File(c.getFilesDir(), FILE);
        load();
    }

    public static synchronized MessagesStore get(Context c) {
        if (s == null) s = new MessagesStore(c);
        return s;
    }

    public synchronized List<Received> getAll() {
        List<Received> out = new ArrayList<>(items);
        Collections.reverse(out);
        return out;
    }

    public synchronized void add(Received r) {
        items.add(r);
        persist();
    }

    public synchronized void remove(Received r) {
        items.remove(r);
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
                items.add(new Received(
                        o.optString(Protocol.FIELD_FROM, ""),
                        o.optString(Protocol.FIELD_BODY, ""),
                        o.optLong(Protocol.FIELD_TS, 0L),
                        o.optString(Protocol.FIELD_DEVICE, "")));
            }
        } catch (Exception ignored) {
        }
    }

    private void persist() {
        try {
            JSONArray arr = new JSONArray();
            for (Received r : items) {
                JSONObject o = new JSONObject();
                o.put(Protocol.FIELD_FROM, r.from);
                o.put(Protocol.FIELD_BODY, r.body);
                o.put(Protocol.FIELD_TS, r.ts);
                o.put(Protocol.FIELD_DEVICE, r.device);
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