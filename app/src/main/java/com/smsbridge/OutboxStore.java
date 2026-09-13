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

public final class OutboxStore {
    private static final String FILE = "outbox.json";

    private final List<Sent> items = new ArrayList<>();
    private final File file;
    private static OutboxStore s;

    public static class Sent {
        public final String to;
        public final String body;
        public final long ts;
        public final boolean ok;
        public final String err;
        public final String status;

        public Sent(String to, String body, long ts, boolean ok, String err, String status) {
            this.to = to == null ? "" : to;
            this.body = body == null ? "" : body;
            this.ts = ts;
            this.ok = ok;
            this.err = err == null ? "" : err;
            this.status = status == null ? "" : status;
        }
    }

    private OutboxStore(Context c) {
        file = new File(c.getFilesDir(), FILE);
        load();
    }

    public static synchronized OutboxStore get(Context c) {
        if (s == null) s = new OutboxStore(c);
        return s;
    }

    public synchronized List<Sent> getAll() {
        List<Sent> out = new ArrayList<>(items);
        Collections.reverse(out);
        return out;
    }

    /** Adds a pending (queued) sent message; status tracks delivery afterwards. */
    public synchronized void add(String to, String body, String status) {
        items.add(new Sent(to, body, System.currentTimeMillis(), false, "", status));
        persist();
    }

    public synchronized void addStatus(String to, String body, long ts, boolean ok, String err) {
        items.add(new Sent(to, body, ts, ok, err, ok ? "sent" : ("failed: " + err)));
        persist();
    }

    public synchronized void remove(Sent s) {
        items.remove(s);
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
                items.add(new Sent(
                        o.optString("to", ""),
                        o.optString("body", ""),
                        o.optLong("ts", 0L),
                        o.optBoolean("ok", false),
                        o.optString("err", ""),
                        o.optString("status", "")));
            }
        } catch (Exception ignored) {
        }
    }

    private void persist() {
        try {
            JSONArray arr = new JSONArray();
            for (Sent s : items) {
                JSONObject o = new JSONObject();
                o.put("to", s.to);
                o.put("body", s.body);
                o.put("ts", s.ts);
                o.put("ok", s.ok);
                o.put("err", s.err);
                o.put("status", s.status);
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