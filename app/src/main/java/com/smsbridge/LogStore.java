package com.smsbridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LogStore {
    private static final int MAX = 200;

    private final List<Entry> entries = new ArrayList<>();
    private static LogStore s;

    public static class Entry {
        public final long ts;
        public final String text;
        public final boolean ok;

        public Entry(long ts, String text, boolean ok) {
            this.ts = ts;
            this.text = text;
            this.ok = ok;
        }
    }

    public static synchronized LogStore get() {
        if (s == null) s = new LogStore();
        return s;
    }

    public synchronized void add(String text, boolean ok) {
        entries.add(0, new Entry(System.currentTimeMillis(), text, ok));
        while (entries.size() > MAX) entries.remove(entries.size() - 1);
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized List<Entry> all() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }
}