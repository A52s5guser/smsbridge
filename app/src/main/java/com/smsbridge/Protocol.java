package com.smsbridge;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class Protocol {
    public static final String TYPE_SMS = "sms";
    public static final String TYPE_CALL = "call";
    public static final String TYPE_CALL_CMD = "call.cmd";
    public static final String TYPE_SMS_SEND = "sms.send";
    public static final String TYPE_SMS_STATUS = "sms.status";
    public static final String TYPE_CONTACTS_REQ = "contacts.req";
    public static final String TYPE_CONTACTS = "contacts";

    public static final String FIELD_TYPE = "type";
    public static final String FIELD_FROM = "from";
    public static final String FIELD_BODY = "body";
    public static final String FIELD_TS = "ts";
    public static final String FIELD_DEVICE = "device";
    public static final String FIELD_NUMBER = "number";
    public static final String FIELD_EVENT = "event";
    public static final String FIELD_CMD = "cmd";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_TO = "to";
    public static final String FIELD_MSG_ID = "msg_id";
    public static final String FIELD_OK = "ok";
    public static final String FIELD_ERR = "err";
    public static final String FIELD_CONTACTS = "contacts";

    public static final int MAX_FRAME = 1 << 20;
    public static final int ACK = 1;

    public static final String EV_RINGING = "ringing";
    public static final String EV_DIALING = "dialing";
    public static final String EV_ANSWERED = "answered";
    public static final String EV_MISSED = "missed";
    public static final String EV_ENDED = "ended";
    public static final String EV_IDLE = "idle";

    public static final String CMD_DIAL = "dial";
    public static final String CMD_ANSWER = "answer";
    public static final String CMD_REJECT = "reject";
    public static final String CMD_HANGUP = "hangup";
    public static final String CMD_SPEAKER = "speaker";
    public static final String CMD_VOICE_TEST = "voice_test";

    private Protocol() {
    }

    public static byte[] encode(JSONObject o) throws Exception {
        byte[] payload = o.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[4 + payload.length];
        out[0] = (byte) ((payload.length >>> 24) & 0xFF);
        out[1] = (byte) ((payload.length >>> 16) & 0xFF);
        out[2] = (byte) ((payload.length >>> 8) & 0xFF);
        out[3] = (byte) (payload.length & 0xFF);
        System.arraycopy(payload, 0, out, 4, payload.length);
        return out;
    }

    public static byte[] encodeSms(String from, String body, long ts, String device) throws Exception {
        JSONObject o = new JSONObject();
        o.put(FIELD_TYPE, TYPE_SMS);
        o.put(FIELD_FROM, from);
        o.put(FIELD_BODY, body);
        o.put(FIELD_TS, ts);
        o.put(FIELD_DEVICE, device == null ? "" : device);
        return encode(o);
    }

    /** Reads one length-prefixed JSON frame. Returns null on clean EOF before any bytes. */
    public static JSONObject readFrame(InputStream in) throws IOException {
        int len = readInt(in);
        if (len < 0) return null;
        if (len <= 0 || len > MAX_FRAME) throw new IOException("bad frame length: " + len);
        byte[] buf = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(buf, off, len - off);
            if (n < 0) throw new IOException("stream closed mid-frame");
            off += n;
        }
        String s = new String(buf, StandardCharsets.UTF_8);
        try {
            return new JSONObject(s);
        } catch (Exception e) {
            throw new IOException("bad json payload", e);
        }
    }

    private static int readInt(InputStream in) throws IOException {
        int b0 = in.read();
        if (b0 < 0) return -1;
        int b1 = in.read();
        if (b1 < 0) return -1;
        int b2 = in.read();
        if (b2 < 0) return -1;
        int b3 = in.read();
        if (b3 < 0) return -1;
        return ((b0 & 0xFF) << 24) | ((b1 & 0xFF) << 16) | ((b2 & 0xFF) << 8) | (b3 & 0xFF);
    }
}