package com.smsbridge;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    public static final String ROLE_SENDER = "sender";
    public static final String ROLE_RECEIVER = "receiver";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_MONO = "mono";

    private static final String FILE = "smsbridge_prefs";
    private static final String K_ROLE = "role";
    private static final String K_PEER_MAC = "peer_mac";
    private static final String K_PEER_NAME = "peer_name";
    private static final String K_THEME = "theme";

    private final SharedPreferences sp;
    private static Prefs s;

    private Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized Prefs get(Context c) {
        if (s == null) s = new Prefs(c.getApplicationContext());
        return s;
    }

    public String getRole() {
        return sp.getString(K_ROLE, ROLE_SENDER);
    }

    public boolean isSender() {
        return ROLE_SENDER.equals(getRole());
    }

    public void setRole(String role) {
        sp.edit().putString(K_ROLE, role).apply();
    }

    public String getPeerMac() {
        return sp.getString(K_PEER_MAC, null);
    }

    public String getPeerName() {
        return sp.getString(K_PEER_NAME, "Peer");
    }

    public void setPeer(String mac, String name) {
        sp.edit().putString(K_PEER_MAC, mac)
                .putString(K_PEER_NAME, name == null ? "Peer" : name)
                .apply();
    }

    public String getTheme() {
        return sp.getString(K_THEME, THEME_SYSTEM);
    }

    public boolean isMono() {
        return THEME_MONO.equals(getTheme());
    }

    public void setTheme(String theme) {
        sp.edit().putString(K_THEME, theme).apply();
    }
}