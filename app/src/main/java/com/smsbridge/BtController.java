package com.smsbridge;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BtController {
    private static final String TAG = "SmsBridge.Bt";

    public static final String SERVICE_NAME = "SMSBridge";
    public static final UUID SVC_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef0123456789");

    private BtController() {
    }

    public static BluetoothAdapter adapter(Context c) {
        BluetoothManager mgr = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
        return mgr.getAdapter();
    }

    public static BluetoothServerSocket listen(Context c) throws IOException {
        return adapter(c).listenUsingRfcommWithServiceRecord(SERVICE_NAME, SVC_UUID);
    }

    public static BluetoothSocket connect(Context c, String mac) throws IOException {
        BluetoothDevice d = adapter(c).getRemoteDevice(mac);
        BluetoothSocket s = d.createRfcommSocketToServiceRecord(SVC_UUID);
        s.connect();
        return s;
    }

    /**
     * Connects, writes one encoded frame, waits for the receiver's ACK (up to 8 s),
     * then closes. Returns true when the peer acknowledged the frame.
     */
    public static boolean sendFrame(Context c, String mac, JSONObject frame) throws Exception {
        BluetoothSocket s = connect(c, mac);
        try {
            byte[] data = Protocol.encode(frame);
            OutputStream os = s.getOutputStream();
            os.write(data);
            os.flush();
            InputStream in = s.getInputStream();
            long deadline = System.currentTimeMillis() + 8000;
            int ack = 0;
            while (System.currentTimeMillis() < deadline) {
                if (in.available() > 0) {
                    ack = in.read();
                    break;
                }
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
            return ack == Protocol.ACK;
        } finally {
            closeQuietly(s);
        }
    }

    /** Long-lived accept-loop listener. Reads framed JSON until the socket dies, ACKs each frame. */
    public static final class BtListener {
        public interface FrameHandler {
            void onFrame(JSONObject frame);
        }

        private final Context ctx;
        private final FrameHandler handler;
        private final ExecutorService readers = Executors.newCachedThreadPool();
        private BluetoothServerSocket server;
        private volatile boolean running;
        private Thread thread;
        private int socketSeq;

        public BtListener(Context ctx, FrameHandler handler) {
            this.ctx = ctx.getApplicationContext();
            this.handler = handler;
        }

        public void start() {
            if (thread != null) return;
            running = true;
            thread = new Thread(this::loop, "smsbridge-accept");
            thread.setDaemon(true);
            thread.start();
        }

        public void stop() {
            running = false;
            if (server != null) {
                try {
                    server.close();
                } catch (IOException ignored) {
                }
            }
            if (thread != null) {
                thread.interrupt();
                thread = null;
            }
            readers.shutdownNow();
        }

        private void loop() {
            try {
                server = BtController.listen(ctx);
                Log.d(TAG, "listening on " + SERVICE_NAME);
            } catch (SecurityException e) {
                Log.e(TAG, "BLUETOOTH_CONNECT not granted", e);
                return;
            } catch (IOException e) {
                Log.e(TAG, "failed to open server socket", e);
                return;
            }

            while (running) {
                BluetoothSocket socket;
                try {
                    socket = server.accept();
                } catch (IOException e) {
                    if (running) {
                        Log.e(TAG, "accept interrupted", e);
                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException ignored) {
                            break;
                        }
                    }
                    break;
                }
                if (socket != null) {
                    final int seq = ++socketSeq;
                    readers.execute(() -> serve(socket, seq));
                }
            }
            closeQuietly(server);
            server = null;
        }

        private void serve(BluetoothSocket socket, int seq) {
            try {
                while (running) {
                    JSONObject frame = Protocol.readFrame(socket.getInputStream());
                    if (frame == null) break;
                    handler.onFrame(frame);
                    socket.getOutputStream().write(Protocol.ACK);
                    socket.getOutputStream().flush();
                }
            } catch (IOException e) {
                Log.d(TAG, "socket " + seq + " closed: " + e.getMessage());
            } finally {
                closeQuietly(socket);
            }
        }
    }

    public static void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception ignored) {
        }
    }

    public static String deviceLabel(BluetoothDevice d) {
        String name = d.getName();
        return (name == null ? d.getAddress() : name) + "  [" + d.getAddress() + "]";
    }
}