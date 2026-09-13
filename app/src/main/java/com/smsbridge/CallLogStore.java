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

public final class CallLogStore {
    private static final String FILE = "call_log.json";

    private final List<Call> items = new ArrayList<>();
    private final File file;
    private static CallLogStore s;

    public static class Call {
        public final String event;
        public final String number;
        public final String name;
        public final long ts;

        public Call(String event, String number, String name, long ts) {
            this.event = event;
            this.number = number;
            this.name = name == null ? "" : name;
            this.ts = ts;
        }
    }

    private CallLogStore(Context c) {
        file = new File(c.getFilesDir(), FILE);
        load();
    }

    public static synchronized CallLogStore get(Context c) {
        if (s == null) s = new CallLogStore(c);
        return s;
    }

    public synchronized List<Call> getAll() {
        List<Call> out = new ArrayList<>(items);
        Collections.reverse(out);
        return out;
    }

    public synchronized void add(Call call) {
        items.add(call);
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
                items.add(new Call(
                        o.optString(Protocol.FIELD_EVENT, ""),
                        o.optString(Protocol.FIELD_NUMBER, ""),
                        o.optString(Protocol.FIELD_NAME, ""),
                        o.optLong(Protocol.FIELD_TS, 0L)));
            }
        } catch (Exception ignored) {
        }
    }

    private void persist() {
        try {
            JSONArray arr = new JSONArray();
            for (Call c : items) {
                JSONObject o = new JSONObject();
                o.put(Protocol.FIELD_EVENT, c.event);
                o.put(Protocol.FIELD_NUMBER, c.number);
                o.put(Protocol.FIELD_NAME, c.name);
                o.put(Protocol.FIELD_TS, c.ts);
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