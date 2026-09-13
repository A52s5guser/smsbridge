package com.smsbridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_PERMS = 1;
    private static final int REQ_DIALER_ROLE = 2;

    private static final int KIND_NORMAL = 0;
    private static final int KIND_SEE_MORE = 1;
    private static final int KIND_SYNC = 2;
    private static final int SEE_MORE_LIMIT = 3;
    private static final String KEY_LOG = "log";
    private static final String KEY_CHAT = "chat";
    private static final String KEY_CALL = "call";

    private RadioGroup roleGroup;
    private RadioGroup tabGroup;
    private Spinner peerSpinner;
    private TextView statusText;
    private TextView headerSubtitle;
    private ListView chatList;
    private ListView callList;
    private ListView contactList;
    private LinearLayout threadPanel;
    private ListView threadList;
    private TextView threadTitle;
    private TextView threadSub;
    private Button threadBack;
    private Button threadCall;
    private View composerPanel;
    private View dialPad;
    private Button simulateButton;
    private Button dialButton;
    private Button answerButton;
    private Button rejectButton;
    private Button dialerButton;
    private EditText callNumber;
    private EditText smsToNumber;
    private EditText smsBody;
    private Button smsSend;

    private final List<BluetoothDevice> peers = new ArrayList<>();
    private RowListAdapter chatAdapter;
    private RowListAdapter callAdapter;
    private RowListAdapter contactAdapter;
    private RowListAdapter threadAdapter;
    private String openThreadNumber;
    private final java.util.Set<String> expanded = new java.util.HashSet<>();
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private final SimpleDateFormat fmt = new SimpleDateFormat("MMM d, HH:mm", Locale.US);

    private final BroadcastReceiver uiEvents = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshAll();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final View content = findViewById(R.id.root);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return WindowInsets.CONSUMED;
                }
            });
        } else {
            getWindow().setDecorFitsSystemWindows(true);
        }

        roleGroup = findViewById(R.id.roleGroup);
        tabGroup = findViewById(R.id.tabGroup);
        peerSpinner = findViewById(R.id.peerSpinner);
        statusText = findViewById(R.id.statusText);
        headerSubtitle = findViewById(R.id.headerSubtitle);
        chatList = findViewById(R.id.chatList);
        callList = findViewById(R.id.callList);
        contactList = findViewById(R.id.contactList);
        threadPanel = findViewById(R.id.threadPanel);
        threadList = findViewById(R.id.threadList);
        threadTitle = findViewById(R.id.threadTitle);
        threadSub = findViewById(R.id.threadSub);
        threadBack = findViewById(R.id.threadBack);
        threadCall = findViewById(R.id.threadCall);
        composerPanel = findViewById(R.id.composerPanel);
        dialPad = findViewById(R.id.dialPad);
        simulateButton = findViewById(R.id.simulateButton);
        callNumber = findViewById(R.id.callNumber);
        dialButton = findViewById(R.id.callDialButton);
        answerButton = findViewById(R.id.callAnswerButton);
        rejectButton = findViewById(R.id.callRejectButton);
        dialerButton = findViewById(R.id.callDialerButton);
        smsToNumber = findViewById(R.id.smsToNumber);
        smsBody = findViewById(R.id.smsBody);
        smsSend = findViewById(R.id.smsSend);
        Button startButton = findViewById(R.id.startButton);
        Button stopButton = findViewById(R.id.stopButton);
        Button refreshButton = findViewById(R.id.refreshButton);

        chatAdapter = new RowListAdapter(this);
        callAdapter = new RowListAdapter(this);
        contactAdapter = new RowListAdapter(this);
        threadAdapter = new RowListAdapter(this);
        chatList.setAdapter(chatAdapter);
        callList.setAdapter(callAdapter);
        contactList.setAdapter(contactAdapter);
        threadList.setAdapter(threadAdapter);
        setupChatLongPress();
        setupChatClicks();
        setupCallClicks();
        setupContactClicks();
        threadBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitThread();
            }
        });
        threadCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (openThreadNumber != null) doCall(openThreadNumber);
            }
        });

        loadSavedState();
        setupRoleToggle();
        setupTabs();

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStartPressed();
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopServices();
            }
        });
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshPeers();
            }
        });
        simulateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simulateSms();
            }
        });
        dialButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialRemote();
            }
        });
        answerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Prefs.get(MainActivity.this).isSender()) {
                    actLocal("answer", new Runnable() {
                        @Override
                        public void run() {
                            try {
                                CallApi.answer(MainActivity.this);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                } else {
                    CallRinger.off();
                    sendCmd(Protocol.CMD_ANSWER);
                }
            }
        });
        rejectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Prefs.get(MainActivity.this).isSender()) {
                    actLocal("reject/hangup", new Runnable() {
                        @Override
                        public void run() {
                            try {
                                CallApi.end(MainActivity.this);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                } else {
                    CallRinger.off();
                    sendCmd(Protocol.CMD_REJECT);
                }
            }
        });
        dialerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                makeDefaultDialer();
            }
        });
        smsSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSms();
            }
        });

        requestPermissionsAndSetup();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(Bus.ACTION_MSG);
        f.addAction(Bus.ACTION_LOG);
        f.addAction(Bus.ACTION_CALL);
        f.addAction(Bus.ACTION_SMS_STATUS);
        f.addAction(Bus.ACTION_CONTACTS);
        registerReceiver(uiEvents, f, Context.RECEIVER_NOT_EXPORTED);
        refreshPeers();
        refreshAll();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(uiEvents);
        } catch (Exception ignored) {
        }
    }

    private void requestPermissionsAndSetup() {
        List<String> need = new ArrayList<>();
        need.add(android.Manifest.permission.RECEIVE_SMS);
        need.add(android.Manifest.permission.SEND_SMS);
        need.add(android.Manifest.permission.BLUETOOTH_CONNECT);
        need.add(android.Manifest.permission.READ_PHONE_STATE);
        need.add(android.Manifest.permission.READ_CALL_LOG);
        need.add(android.Manifest.permission.CALL_PHONE);
        need.add(android.Manifest.permission.READ_CONTACTS);
        need.add(android.Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33) {
            need.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        List<String> missing = new ArrayList<>();
        for (String p : need) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQ_PERMS);
        }
        requestIgnoreBatteryOptimizations();
    }

    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < 23) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQ_PERMS) {
            if (checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.toast_needs_sms, Toast.LENGTH_LONG).show();
            }
            refreshPeers();
            refreshAll();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DIALER_ROLE) {
            refreshAll();
        }
    }

    // ------------------------------------------------------------------- wiring

    private void loadSavedState() {
        if (Prefs.get(this).isSender()) {
            ((RadioButton) findViewById(R.id.roleSender)).setChecked(true);
        } else {
            ((RadioButton) findViewById(R.id.roleReceiver)).setChecked(true);
        }
    }

    private void setupRoleToggle() {
        roleGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean sender = checkedId == R.id.roleSender;
                Prefs.get(MainActivity.this).setRole(
                        sender ? Prefs.ROLE_SENDER : Prefs.ROLE_RECEIVER);
                refreshAll();
            }
        });
    }

    private void setupTabs() {
        tabGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (openThreadNumber != null && checkedId != R.id.tabChat) {
                    clearThreadUi();
                }
                refreshAll();
            }
        });
    }

    private boolean onChatTab() {
        return tabGroup.getCheckedRadioButtonId() == R.id.tabChat;
    }

    private boolean onContactsTab() {
        return tabGroup.getCheckedRadioButtonId() == R.id.tabContacts;
    }

    private void refreshAll() {
        boolean sender = Prefs.get(this).isSender();
        headerSubtitle.setText(sender ? R.string.header_sender : R.string.header_receiver);
        statusText.setText(sender ? R.string.status_sender : R.string.status_receiver);

        tabGroup.setVisibility(sender ? View.GONE : View.VISIBLE);
        simulateButton.setVisibility(sender ? View.VISIBLE : View.GONE);

        if (sender) {
            chatList.setVisibility(View.VISIBLE);
            callList.setVisibility(View.GONE);
            contactList.setVisibility(View.GONE);
            threadPanel.setVisibility(View.GONE);
            composerPanel.setVisibility(View.GONE);
            dialPad.setVisibility(View.VISIBLE);
        } else if (openThreadNumber != null) {
            chatList.setVisibility(View.GONE);
            contactList.setVisibility(View.GONE);
            callList.setVisibility(View.GONE);
            threadPanel.setVisibility(View.VISIBLE);
            composerPanel.setVisibility(View.VISIBLE);
            dialPad.setVisibility(View.GONE);
        } else {
            boolean chat = onChatTab();
            boolean contacts = onContactsTab();
            chatList.setVisibility(chat ? View.VISIBLE : View.GONE);
            contactList.setVisibility(contacts ? View.VISIBLE : View.GONE);
            callList.setVisibility((!chat && !contacts) ? View.VISIBLE : View.GONE);
            threadPanel.setVisibility(View.GONE);
            composerPanel.setVisibility(chat ? View.VISIBLE : View.GONE);
            dialPad.setVisibility((!chat && !contacts) ? View.VISIBLE : View.GONE);
        }

        if (sender) {
            if (CallApi.isDefaultDialer(this)) {
                dialerButton.setText(R.string.call_dialer_ok);
                dialerButton.setEnabled(false);
            } else {
                dialerButton.setText(R.string.call_set_dialer);
                dialerButton.setEnabled(true);
            }
        }

        rebuildLists();
    }

    // ------------------------------------------------------------------- peer & service

    private void refreshPeers() {
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        BluetoothAdapter adapter = BtController.adapter(this);
        if (adapter == null) return;
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        peers.clear();
        ArrayAdapter<String> peerAdapter =
                (ArrayAdapter<String>) peerSpinner.getAdapter();
        if (peerAdapter == null) {
            peerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
            peerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            peerSpinner.setAdapter(peerAdapter);
        }
        peerAdapter.clear();
        if (bonded != null) {
            peers.addAll(bonded);
            for (BluetoothDevice d : peers) {
                peerAdapter.add(BtController.deviceLabel(d));
            }
        }
        if (peers.isEmpty()) {
            peerAdapter.add(getString(R.string.peer_none));
        }
        peerAdapter.notifyDataSetChanged();

        String savedMac = Prefs.get(this).getPeerMac();
        if (savedMac != null) {
            for (int i = 0; i < peers.size(); i++) {
                if (peers.get(i).getAddress().equals(savedMac)) {
                    peerSpinner.setSelection(i);
                    return;
                }
            }
        }
    }

    private void onStartPressed() {
        Prefs p = Prefs.get(this);
        if (peers.size() > 0 && peerSpinner.getSelectedItemPosition() >= 0
                && peerSpinner.getSelectedItemPosition() < peers.size()) {
            BluetoothDevice dev = peers.get(peerSpinner.getSelectedItemPosition());
            p.setPeer(dev.getAddress(), dev.getName());
        }

        boolean sender = p.isSender();
        Intent svc = new Intent(this, sender ? ForwarderService.class : ReceiverService.class)
                .setAction(sender ? ForwarderService.ACTION_START : ReceiverService.ACTION_START);
        try {
            if (sender) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to start service: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return;
        }
        statusText.setText(sender ? R.string.status_sender : R.string.status_receiver);
    }

    private void stopServices() {
        startService(new Intent(this, ForwarderService.class)
                .setAction(ForwarderService.ACTION_STOP));
        startService(new Intent(this, ReceiverService.class)
                .setAction(ReceiverService.ACTION_STOP));
        statusText.setText(R.string.status);
    }

    private void simulateSms() {
        Prefs p = Prefs.get(this);
        if (!p.isSender()) {
            Toast.makeText(this, "Simulate only works in Sender mode", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent svc = new Intent(this, ForwarderService.class)
                .setAction(ForwarderService.ACTION_FORWARD)
                .putExtra(ForwarderService.EXTRA_FROM, "SIMULATED")
                .putExtra(ForwarderService.EXTRA_BODY,
                        "Test message from SMSBridge debug simulator " + fmt.format(new Date()))
                .putExtra(ForwarderService.EXTRA_TS, System.currentTimeMillis());
        startForegroundService(svc);
        Toast.makeText(this, "Simulated SMS queued", Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------- call controls

    private void makeDefaultDialer() {
        CallApi.requestDialerRole(this, REQ_DIALER_ROLE);
    }

    private void dialRemote() {
        String number = callNumber.getText().toString().trim();
        if (!CallApi.isValidNumber(number)) {
            Toast.makeText(this, R.string.toast_no_number, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Prefs.get(this).isSender()) {
            CallApi.dial(this, number);
        } else {
            sendCmd(Protocol.CMD_DIAL, number);
        }
    }

    private void sendCmd(String cmd) {
        sendCmd(cmd, "");
    }

    private void sendCmd(final String cmd, final String number) {
        final String mac = Prefs.get(this).getPeerMac();
        if (mac == null) {
            Toast.makeText(this, "Select a peer device first", Toast.LENGTH_SHORT).show();
            return;
        }
        exec.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    org.json.JSONObject frame = new org.json.JSONObject();
                    frame.put(Protocol.FIELD_TYPE, Protocol.TYPE_CALL_CMD);
                    frame.put(Protocol.FIELD_CMD, cmd);
                    if (number != null && !number.isEmpty()) {
                        frame.put(Protocol.FIELD_NUMBER, number);
                    }
                    BtController.sendFrame(MainActivity.this, mac, frame);
                } catch (Exception e) {
                    String msg = "Call command failed: "
                            + (e.getMessage() == null ? e.toString() : e.getMessage());
                    LogStore.get().add(msg, false);
                    sendBroadcast(new Intent(Bus.ACTION_LOG)
                            .setPackage(getPackageName())
                            .putExtra(Bus.EXTRA_TEXT, "Call command " + cmd + " failed")
                            .putExtra(Bus.EXTRA_OK, false)
                            .putExtra(Bus.EXTRA_TS, System.currentTimeMillis()));
                }
            }
        });
    }

    private void actLocal(final String what, final Runnable action) {
        exec.execute(new Runnable() {
            @Override
            public void run() {
                boolean ok = true;
                String msg = "Local " + what + " ok\nCall control from SMSBridge";
                try {
                    action.run();
                    LogStore.get().add(msg, true);
                } catch (Exception e) {
                    ok = false;
                    msg = "Local " + what + " failed: "
                            + (e.getMessage() == null ? e.toString() : e.getMessage());
                    LogStore.get().add(msg, false);
                }
                sendBroadcast(new Intent(Bus.ACTION_LOG)
                        .setPackage(getPackageName())
                        .putExtra(Bus.EXTRA_TEXT, msg)
                        .putExtra(Bus.EXTRA_OK, ok)
                        .putExtra(Bus.EXTRA_TS, System.currentTimeMillis()));
            }
        });
    }

    // ------------------------------------------------------------------- sms compose

    private void sendSms() {
        String to = smsToNumber.getText().toString().trim();
        String body = smsBody.getText().toString().trim();
        if (!CallApi.isValidNumber(to)) {
            Toast.makeText(this, R.string.toast_no_to, Toast.LENGTH_SHORT).show();
            return;
        }
        if (body.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_body, Toast.LENGTH_SHORT).show();
            return;
        }
        final String mac = Prefs.get(this).getPeerMac();
        if (mac == null) {
            Toast.makeText(this, "Select a peer device first", Toast.LENGTH_SHORT).show();
            return;
        }
        final String finalTo = to;
        final String finalBody = body;
        OutboxStore.get(this).add(finalTo, finalBody, "queued");
        final String msgId = String.valueOf(System.nanoTime() & 0xFFFFFFFFL);
        exec.execute(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    org.json.JSONObject frame = new org.json.JSONObject();
                    frame.put(Protocol.FIELD_TYPE, Protocol.TYPE_SMS_SEND);
                    frame.put(Protocol.FIELD_TO, finalTo);
                    frame.put(Protocol.FIELD_BODY, finalBody);
                    frame.put(Protocol.FIELD_MSG_ID, msgId);
                    frame.put(Protocol.FIELD_TS, System.currentTimeMillis());
                    ok = BtController.sendFrame(MainActivity.this, mac, frame);
                } catch (Exception e) {
                    envoyLog(false, "SMS send failed: "
                            + (e.getMessage() == null ? e.toString() : e.getMessage()));
                    return;
                }
                envoyLog(ok, ok ? "\u21a6 SMS sent \u2192 peer" : "No ack from peer");
            }
        });
    }

    private void envoyLog(boolean ok, String text) {
        sendBroadcast(new Intent(Bus.ACTION_LOG)
                .setPackage(getPackageName())
                .putExtra(Bus.EXTRA_TEXT, text)
                .putExtra(Bus.EXTRA_OK, ok)
                .putExtra(Bus.EXTRA_TS, System.currentTimeMillis()));
    }

    // ------------------------------------------------------------------- list rendering

    private void setupChatLongPress() {
        chatList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                UiRow row = chatAdapter.getItem(position);
                if (row == null || row.kind != KIND_NORMAL
                        || row.number == null || row.number.isEmpty()) return false;
                final String number = row.number;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(row.title)
                        .setMessage("Delete this whole conversation?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                deleteThread(number);
                                Toast.makeText(MainActivity.this, R.string.msg_deleted,
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            }
        });
        threadList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                UiRow row = threadAdapter.getItem(position);
                if (row == null || row.kind != KIND_NORMAL
                        || (row.rcv == null && row.snt == null)) return false;
                final UiRow selected = row;
                String label = row.rcv != null ? "From " + row.rcv.from : "To " + row.snt.to;
                String body = row.rcv != null ? row.rcv.body : row.snt.body;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(label)
                        .setMessage(body + "\n\nDelete this message from the queue?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                if (selected.rcv != null) {
                                    MessagesStore.get(MainActivity.this).remove(selected.rcv);
                                } else if (selected.snt != null) {
                                    OutboxStore.get(MainActivity.this).remove(selected.snt);
                                }
                                rebuildLists();
                                Toast.makeText(MainActivity.this, R.string.msg_deleted,
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            }
        });
    }

    private void setupChatClicks() {
        chatList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                UiRow row = chatAdapter.getItem(position);
                if (row == null) return;
                if (row.kind == KIND_SEE_MORE) {
                    toggleExpanded(KEY_CHAT);
                    return;
                }
                openThread(row.number);
            }
        });
        threadList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                UiRow row = threadAdapter.getItem(position);
                if (row == null) return;
                if (row.kind == KIND_SEE_MORE) {
                    toggleExpanded(openThreadNumber);
                    return;
                }
                if (row.snt != null) {
                    smsBody.setText(row.snt.body);
                } else if (row.rcv != null) {
                    smsBody.setText(row.rcv.body);
                }
            }
        });
    }

    private void setupCallClicks() {
        callList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                UiRow row = callAdapter.getItem(position);
                if (row == null) return;
                if (row.kind == KIND_SEE_MORE) {
                    toggleExpanded(KEY_CALL);
                    return;
                }
                if (row.number != null && !row.number.isEmpty()) {
                    showNumberActions(row.number, row.name);
                }
            }
        });
    }

    private void setupContactClicks() {
        contactList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                UiRow row = contactAdapter.getItem(position);
                if (row == null) return;
                if (row.kind == KIND_SYNC) {
                    syncContacts();
                    return;
                }
                if (row.number != null && !row.number.isEmpty()) {
                    openCompose(row.number, "");
                }
            }
        });
        contactList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                UiRow row = contactAdapter.getItem(position);
                if (row == null || row.kind != KIND_NORMAL
                        || row.number == null || row.number.isEmpty()) return false;
                showNumberActions(row.number, row.name);
                return true;
            }
        });
    }

    private void toggleExpanded(String key) {
        if (expanded.contains(key)) {
            expanded.remove(key);
        } else {
            expanded.add(key);
        }
        rebuildLists();
    }

    private void openMessage(UiRow row) {
        if (row.rcv == null && row.snt == null) return;
        String number = row.rcv != null ? row.rcv.from : row.snt.to;
        if (number == null || number.isEmpty()) return;
        openCompose(number, row.rcv != null ? row.rcv.body : "");
    }

    private void openCompose(String number, String body) {
        if (!Prefs.get(this).isSender()) {
            ((RadioButton) findViewById(R.id.tabChat)).setChecked(true);
        }
        smsToNumber.setText(number);
        if (body != null && !body.isEmpty()) {
            smsBody.setText(body);
        }
        smsBody.requestFocus();
        refreshAll();
    }

    private void showNumberActions(final String number, final String name) {
        final String title = (name != null && !name.isEmpty()) ? name + "  " + number : number;
        AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this)
                .setTitle(title)
                .setItems(new String[]{
                        getString(R.string.act_call),
                        getString(R.string.act_sms),
                        getString(R.string.act_open)}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            doCall(number);
                        } else if (which == 1) {
                            openCompose(number, "");
                        } else {
                            callNumber.setText(number);
                        }
                    }
                });
        b.show();
    }

    private void doCall(final String number) {
        callNumber.setText(number);
        if (Prefs.get(this).isSender()) {
            CallApi.dial(this, number);
        } else {
            sendCmd(Protocol.CMD_DIAL, number);
        }
    }

    private void syncContacts() {
        final String mac = Prefs.get(this).getPeerMac();
        if (mac == null) {
            Toast.makeText(this, R.string.toast_no_peer, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.contacts_sync, Toast.LENGTH_SHORT).show();
        exec.execute(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    org.json.JSONObject frame = new org.json.JSONObject();
                    frame.put(Protocol.FIELD_TYPE, Protocol.TYPE_CONTACTS_REQ);
                    ok = BtController.sendFrame(MainActivity.this, mac, frame);
                } catch (Exception e) {
                    envoyLog(false, "Contact sync failed: "
                            + (e.getMessage() == null ? e.toString() : e.getMessage()));
                    return;
                }
                envoyLog(ok, ok ? "Contact sync \u2192 peer sent" : "No ack from peer");
            }
        });
    }

    private void rebuildLists() {
        boolean sender = Prefs.get(this).isSender();
        chatAdapter.clear();
        callAdapter.clear();
        contactAdapter.clear();
        threadAdapter.clear();

        if (sender) {
            List<UiRow> log = buildSenderLog();
            addRows(chatAdapter, log, KEY_LOG);
            addRows(callAdapter, new ArrayList<>(log), KEY_LOG);
        } else {
            addRows(chatAdapter, buildPeople(), KEY_CHAT);
            addRows(callAdapter, buildReceiverCalls(), KEY_CALL);
            buildContacts(contactAdapter);
            if (openThreadNumber != null) {
                addRows(threadAdapter, buildThreadRows(openThreadNumber), openThreadNumber);
            }
        }
        chatAdapter.notifyDataSetChanged();
        callAdapter.notifyDataSetChanged();
        contactAdapter.notifyDataSetChanged();
        threadAdapter.notifyDataSetChanged();
    }

    private void addRows(RowListAdapter out, List<UiRow> rows, String key) {
        if (rows.isEmpty()) {
            out.add(new UiRow(R.drawable.ic_notif_sms, getColorRobust(R.color.text_secondary),
                    "Nothing here yet", "", ""));
            return;
        }
        if (expanded.contains(key) || rows.size() <= SEE_MORE_LIMIT) {
            for (UiRow r : rows) out.add(r);
            return;
        }
        for (int i = 0; i < SEE_MORE_LIMIT; i++) out.add(rows.get(i));
        out.add(new UiRow(KIND_SEE_MORE, rows.size() - SEE_MORE_LIMIT, key));
    }

    private List<UiRow> buildSenderLog() {
        List<UiRow> out = new ArrayList<>();
        for (LogStore.Entry e : LogStore.get().all()) {
            String[] lines = e.text.split("\n");
            out.add(new UiRow(
                    R.drawable.ic_notif_sms,
                    getColorRobust(e.ok ? R.color.accent : R.color.err),
                    lines[0],
                    lines.length > 1 ? lines[1] : "",
                    fmt.format(new Date(e.ts))));
        }
        return out;
    }

    private String cleanNum(String number) {
        if (number == null) return "";
        return number.replaceAll("[^0-9+]", "");
    }

    private String contactNameFor(String number) {
        try {
            String key = cleanNum(number);
            for (ContactsStore.Contact ct : ContactsStore.get(this).getAll()) {
                if (cleanNum(ct.number).equals(key) && !ct.name.isEmpty()) return ct.name;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private List<UiRow> buildPeople() {
        List<Object[]> merged = new ArrayList<>();
        java.util.Map<String, Long> ts = new java.util.HashMap<>();
        java.util.Map<String, Boolean> out = new java.util.HashMap<>();
        java.util.Map<String, String> last = new java.util.HashMap<>();
        java.util.Map<String, String> raw = new java.util.HashMap<>();
        for (MessagesStore.Received m : MessagesStore.get(this).getAll()) {
            String num = cleanNum(m.from);
            if (num.isEmpty()) continue;
            raw.put(num, m.from);
            if (!ts.containsKey(num) || ts.get(num) < m.ts) {
                ts.put(num, m.ts);
                out.put(num, false);
                last.put(num, m.body);
            }
        }
        for (OutboxStore.Sent s : OutboxStore.get(this).getAll()) {
            String num = cleanNum(s.to);
            if (num.isEmpty()) continue;
            raw.put(num, s.to);
            if (!ts.containsKey(num) || ts.get(num) < s.ts) {
                ts.put(num, s.ts);
                out.put(num, true);
                last.put(num, s.body);
            }
        }
        for (String num : ts.keySet()) {
            String name = contactNameFor(num);
            String shown = name.isEmpty() ? (raw.get(num) == null ? num : raw.get(num)) : name;
            boolean o = out.get(num);
            String sub = (o ? "You: " : "In: ") + last.get(num);
            UiRow row = new UiRow(o ? R.drawable.ic_send : R.drawable.ic_notif_sms,
                    getColorRobust(o ? R.color.accent : R.color.row_tint),
                    shown, sub, fmt.format(new Date(ts.get(num))));
            row.number = num;
            row.name = name;
            merged.add(new Object[]{ts.get(num), row});
        }
        return sortedRows(merged);
    }

    private List<UiRow> buildThreadRows(String number) {
        String key = cleanNum(number);
        String name = contactNameFor(key);
        List<Object[]> merged = new ArrayList<>();
        for (MessagesStore.Received m : MessagesStore.get(this).getAll()) {
            if (!cleanNum(m.from).equals(key)) continue;
            merged.add(new Object[]{m.ts, new UiRow(R.drawable.ic_notif_sms,
                    getColorRobust(R.color.row_tint),
                    name.isEmpty() ? m.from : name, m.body, fmt.format(new Date(m.ts)), m, null)});
        }
        for (OutboxStore.Sent s : OutboxStore.get(this).getAll()) {
            if (!cleanNum(s.to).equals(key)) continue;
            String status = s.ok ? "sent" : (s.status == null || s.status.isEmpty() ? "queued" : s.status);
            merged.add(new Object[]{s.ts, new UiRow(R.drawable.ic_send,
                    getColorRobust(s.ok ? R.color.accent : R.color.err),
                    "You", s.body + "\n  \u2022 " + status, fmt.format(new Date(s.ts)), null, s)});
        }
        return sortedRows(merged);
    }

    private List<UiRow> sortedRows(List<Object[]> merged) {
        Collections.sort(merged, new java.util.Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                return Long.compare((Long) b[0], (Long) a[0]);
            }
        });
        List<UiRow> out = new ArrayList<>();
        for (Object[] row : merged) {
            out.add((UiRow) row[1]);
        }
        return out;
    }

    private List<Object[]> callRows() {
        List<Object[]> merged = new ArrayList<>();
        for (CallLogStore.Call c : CallLogStore.get(this).getAll()) {
            int color;
            String title;
            switch (c.event) {
                case Protocol.EV_RINGING:
                    color = getColorRobust(R.color.call_in);
                    title = "Incoming call";
                    break;
                case Protocol.EV_DIALING:
                    color = getColorRobust(R.color.call_out);
                    title = "Outgoing call";
                    break;
                case Protocol.EV_MISSED:
                    color = getColorRobust(R.color.call_missed);
                    title = "Missed call";
                    break;
                case Protocol.EV_ANSWERED:
                    color = getColorRobust(R.color.accent);
                    title = "Answered";
                    break;
                case Protocol.EV_ENDED:
                    color = getColorRobust(R.color.call_ended);
                    title = "Call ended";
                    break;
                default:
                    color = getColorRobust(R.color.text_secondary);
                    title = c.event;
            }
            String who = (c.name != null && !c.name.isEmpty())
                    ? c.name + "  " + c.number
                    : (c.number == null || c.number.isEmpty() ? "Unknown" : c.number);
            UiRow row = new UiRow(R.drawable.ic_notif_call, color, title, who,
                    fmt.format(new Date(c.ts)));
            row.number = c.number;
            row.name = c.name;
            merged.add(new Object[]{c.ts, row});
        }
        return merged;
    }

    private List<UiRow> buildReceiverCalls() {
        return sortedRows(callRows());
    }

    private void clearThreadUi() {
        openThreadNumber = null;
        smsToNumber.setEnabled(true);
        smsToNumber.setAlpha(1f);
        smsToNumber.setText("");
        smsBody.setText("");
    }

    private void openThread(String number) {
        if (number == null || number.isEmpty()) return;
        openThreadNumber = number;
        String name = contactNameFor(number);
        threadTitle.setText(name.isEmpty() ? number : name);
        threadSub.setText(number);
        smsToNumber.setEnabled(false);
        smsToNumber.setAlpha(0.7f);
        smsToNumber.setText(number);
        smsBody.setText("");
        refreshAll();
        threadList.setSelection(0);
    }

    private void exitThread() {
        if (openThreadNumber == null) return;
        clearThreadUi();
        ((RadioButton) findViewById(R.id.tabChat)).setChecked(true);
        refreshAll();
    }

    private void deleteThread(String number) {
        String key = cleanNum(number);
        MessagesStore ms = MessagesStore.get(this);
        for (MessagesStore.Received m : new ArrayList<>(ms.getAll())) {
            if (cleanNum(m.from).equals(key)) ms.remove(m);
        }
        OutboxStore os = OutboxStore.get(this);
        for (OutboxStore.Sent s : new ArrayList<>(os.getAll())) {
            if (cleanNum(s.to).equals(key)) os.remove(s);
        }
        if (key.equals(openThreadNumber)) {
            clearThreadUi();
        }
        rebuildLists();
    }

    private void buildContacts(RowListAdapter out) {
        List<ContactsStore.Contact> contacts = ContactsStore.get(this).getAll();
        UiRow sync = new UiRow(KIND_SYNC, R.drawable.ic_send,
                getColorRobust(R.color.primary),
                getString(R.string.contacts_sync), contacts.isEmpty()
                        ? getString(R.string.contacts_empty)
                        : getString(R.string.contacts_count, contacts.size()));
        out.add(sync);
        for (ContactsStore.Contact c : contacts) {
            UiRow row = new UiRow(R.drawable.ic_launcher,
                    getColorRobust(R.color.row_tint), c.name.isEmpty() ? c.number : c.name,
                    c.number, "");
            row.number = c.number;
            row.name = c.name;
            out.add(row);
        }
    }

    private int getColorRobust(int res) {
        try {
            return getColor(res);
        } catch (Throwable t) {
            return 0xFF3D5AFE;
        }
    }

    // ----------------------------------------------------------- row model & adapter

    static class UiRow {
        final int icon;
        final int color;
        final String title;
        final String subtitle;
        final String time;
        final MessagesStore.Received rcv;
        final OutboxStore.Sent snt;
        int kind = KIND_NORMAL;
        String number;
        String name;

        UiRow(int icon, int color, String title, String subtitle, String time) {
            this(icon, color, title, subtitle, time, null, null);
        }

        UiRow(int icon, int color, String title, String subtitle,
              String time, MessagesStore.Received rcv, OutboxStore.Sent snt) {
            this.icon = icon;
            this.color = color;
            this.title = title;
            this.subtitle = subtitle;
            this.time = time;
            this.rcv = rcv;
            this.snt = snt;
        }

        UiRow(int kind, int icon, int color, String title, String subtitle) {
            this.kind = kind;
            this.icon = icon;
            this.color = color;
            this.title = title;
            this.subtitle = subtitle;
            this.time = "";
            this.rcv = null;
            this.snt = null;
        }

        UiRow(int kind, String title, String subtitle, String time) {
            this.kind = kind;
            this.icon = R.drawable.ic_send;
            this.color = 0;
            this.title = title;
            this.subtitle = subtitle;
            this.time = time;
            this.rcv = null;
            this.snt = null;
        }

        UiRow(int kind, int count, String key) {
            this.kind = kind;
            this.icon = R.drawable.ic_send;
            this.color = 0;
            this.title = count <= 0 ? key : String.valueOf(count);
            this.subtitle = "";
            this.time = "";
            this.rcv = null;
            this.snt = null;
        }
    }

    static class RowListAdapter extends ArrayAdapter<UiRow> {
        RowListAdapter(Context c) {
            super(c, R.layout.list_item_row, R.id.rowTitle);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = android.view.LayoutInflater.from(getContext())
                        .inflate(R.layout.list_item_row, parent, false);
            }
            UiRow row = getItem(position);
            MainActivity act = (MainActivity) getContext();
            ImageView icon = v.findViewById(R.id.rowIcon);
            icon.setImageResource(row.icon);
            icon.setColorFilter(row.color, PorterDuff.Mode.SRC_IN);
            if (row.kind != 0) icon.setColorFilter(0xFF3D5AFE, PorterDuff.Mode.SRC_IN);
            TextView title = v.findViewById(R.id.rowTitle);
            TextView sub = v.findViewById(R.id.rowSub);
            TextView time = v.findViewById(R.id.rowTime);
            if (row.kind == KIND_SEE_MORE) {
                int extra = 0;
                try {
                    extra = Integer.parseInt(row.title);
                } catch (Exception ignored) {
                }
                title.setText(extra > 0
                        ? act.getString(R.string.see_more_n, extra)
                        : act.getString(R.string.see_more));
                title.setTextColor(act.getColorRobust(R.color.primary));
                sub.setText("");
                time.setText("");
            } else {
                if (row.title == null || row.title.isEmpty()) {
                    title.setText(act.getString(R.string.see_more));
                } else {
                    title.setText(row.title);
                }
                title.setTextColor(row.color);
                sub.setText(row.subtitle);
                time.setText(row.time);
            }
            return v;
        }
    }
}