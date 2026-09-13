package com.smsbridge;

public final class Bus {
    public static final String ACTION_MSG = "com.smsbridge.event.MESSAGE";
    public static final String ACTION_LOG = "com.smsbridge.event.LOG";
    public static final String ACTION_CALL = "com.smsbridge.event.CALL";
    public static final String ACTION_SMS_STATUS = "com.smsbridge.event.SMS_STATUS";
    public static final String ACTION_CONTACTS = "com.smsbridge.event.CONTACTS";
    public static final String EXTRA_FROM = "from";
    public static final String EXTRA_BODY = "body";
    public static final String EXTRA_TS = "ts";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_OK = "ok";
    public static final String EXTRA_EVENT = "event";

    private Bus() {
    }
}