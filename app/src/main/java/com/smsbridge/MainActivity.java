package com.smsbridge;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
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
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
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
    private NavigationBarView tabGroup;
    private android.widget.AutoCompleteTextView peerDropdown;
    private TextView statusText;
    private TextView headerSubtitle;
    private ListView chatList;
    private ListView callList;
    private ListView contactList;
    private LinearLayout threadPanel;
    private ListView threadList;
    private TextView threadTitle;
    private TextView threadSub;
    private View threadBack;
    private Button threadCall;
    private View composerPanel;
    private View dialPad;
    private Button simulateButton;
    private Button dialButton;
    private Button answerButton;
    private Button rejectButton;
    private EditText callNumber;
    private EditText smsToNumber;
    private EditText smsBody;
    private View smsSend;
    private View settingsPanel;
    private View callsPage;
    private EditText searchInput;
    private View searchPill;
    private String searchQuery = "";
    private long lastBackPress = 0;
    private boolean firstRefresh = true;
    private int lastTabId = -1;

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
        applySavedTheme();
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
        com.google.android.material.chip.ChipGroup themeChips = findViewById(R.id.themeChips);
        if (themeChips != null) {
            String cur = Prefs.get(this).getTheme();
            int checked = R.id.themeSystem;
            if (Prefs.THEME_LIGHT.equals(cur)) checked = R.id.themeLight;
            else if (Prefs.THEME_DARK.equals(cur)) checked = R.id.themeDark;
            else if (Prefs.THEME_MONO.equals(cur)) checked = R.id.themeMono;
            themeChips.check(checked);
            themeChips.setOnCheckedStateChangeListener(
                    new com.google.android.material.chip.ChipGroup.OnCheckedStateChangeListener() {
                @Override
                public void onCheckedChanged(com.google.android.material.chip.ChipGroup group,
                                             java.util.List<Integer> checkedIds) {
                    if (checkedIds == null || checkedIds.isEmpty()) return;
                    int sel = checkedIds.get(0);
                    String picked = Prefs.THEME_SYSTEM;
                    if (sel == R.id.themeLight) picked = Prefs.THEME_LIGHT;
                    else if (sel == R.id.themeDark) picked = Prefs.THEME_DARK;
                    else if (sel == R.id.themeMono) picked = Prefs.THEME_MONO;
                    if (!picked.equals(Prefs.get(MainActivity.this).getTheme())) {
                        Prefs.get(MainActivity.this).setTheme(picked);
                        View chip = group.findViewById(sel);
                        tap(chip != null ? chip : group);
                        recreate();
                    }
                }
            });
        }
        peerDropdown = findViewById(R.id.peerDropdown);
        if (peerDropdown != null) {
            peerDropdown.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { peerDropdown.showDropDown(); }
            });
            peerDropdown.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (position < 0 || position >= peers.size()) return;
                    BluetoothDevice dev = peers.get(position);
                    Prefs.get(MainActivity.this).setPeer(dev.getAddress(), dev.getName());
                    tap(view);
                    refreshAll();
                }
            });
        }
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
        smsToNumber = findViewById(R.id.smsToNumber);
        smsBody = findViewById(R.id.smsBody);
        smsSend = findViewById(R.id.smsSend);
        Button startButton = findViewById(R.id.startButton);
        Button stopButton = findViewById(R.id.stopButton);
        Button refreshButton = findViewById(R.id.refreshButton);
        settingsPanel = findViewById(R.id.settingsPanel);
        callsPage = findViewById(R.id.callsPage);
        searchInput = findViewById(R.id.searchInput);
        searchPill = findViewById(R.id.searchPill);
        com.google.android.material.appbar.MaterialToolbar headerBar = findViewById(R.id.headerBar);
        if (headerBar != null) {
            headerBar.setOnMenuItemClickListener(new androidx.appcompat.widget.Toolbar.OnMenuItemClickListener() {
                @Override public boolean onMenuItemClick(android.view.MenuItem item) {
                    if (item.getItemId() == R.id.actionSettings) {
                        tap(headerBar);
                        tabGroup.setSelectedItemId(R.id.tabSettings);
                        return true;
                    }
                    return false;
                }
            });
        }
        // search
        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
                @Override public void onTextChanged(CharSequence s,int a,int b,int c){}
                @Override public void afterTextChanged(Editable s){ searchQuery = s.toString().trim().toLowerCase(Locale.US); rebuildLists(); }
            });
            View clear = findViewById(R.id.searchClear);
            if (clear != null) clear.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){ searchInput.setText(""); tap(v);} });
        }

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
                tap(v);
                exitThread();
            }
        });
        threadCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirm(v);
                if (openThreadNumber != null) doCall(openThreadNumber);
            }
        });
        setupSwipeBack();

        loadSavedState();
        setupRoleToggle();
        setupTabs();
        tabGroup.setSelectedItemId(R.id.tabChat);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirm(v);
                onStartPressed();
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tap(v);
                stopServices();
            }
        });
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tap(v);
                refreshPeers();
            }
        });
        simulateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tap(v);
                simulateSms();
            }
        });
        dialButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirm(v);
                dialRemote();
            }
        });
        answerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirm(v);
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
                buzzReject(v);
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
        smsSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirm(v);
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
        RadioButton senderBtn = findViewById(R.id.roleSender);
        RadioButton receiverBtn = findViewById(R.id.roleReceiver);
        if (senderBtn != null) senderBtn.setText(roleLabel("Sender",
                "Holds the SIM. Forwards texts and calls."));
        if (receiverBtn != null) receiverBtn.setText(roleLabel("Receiver",
                "No SIM needed. Shows messages here."));
        if (Prefs.get(this).isSender()) {
            senderBtn.setChecked(true);
        } else {
            receiverBtn.setChecked(true);
        }
    }

    private CharSequence roleLabel(String title, String desc) {
        android.text.SpannableString ss = new android.text.SpannableString(title + "\n" + desc);
        ss.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                0, title.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new android.text.style.AbsoluteSizeSpan(15, true),
                0, title.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new android.text.style.ForegroundColorSpan(getThemeColor(R.attr.colorOnSurfaceVariant)),
                title.length() + 1, ss.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new android.text.style.AbsoluteSizeSpan(12, true),
                title.length() + 1, ss.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    private void setupRoleToggle() {
        roleGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean sender = checkedId == R.id.roleSender;
                tap(group.findViewById(checkedId));
                Prefs.get(MainActivity.this).setRole(
                        sender ? Prefs.ROLE_SENDER : Prefs.ROLE_RECEIVER);
                refreshAll();
            }
        });
    }

    private void setupTabs() {
        tabGroup.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(android.view.MenuItem item) {
                int id = item.getItemId();
                if (openThreadNumber != null && id != R.id.tabChat) {
                    clearThreadUi();
                }
                tap(tabGroup);
                // selection commits after this returns, so refresh on next loop
                tabGroup.post(new Runnable() {
                    @Override public void run() { refreshAll(); }
                });
                return true;
            }
        });
        tabGroup.setOnItemReselectedListener(new NavigationBarView.OnItemReselectedListener() {
            @Override
            public void onNavigationItemReselected(android.view.MenuItem item) {
                tap(tabGroup);
            }
        });
    }

    private void setupSwipeBack(){
        // swipe right on threadPanel to go back (iOS style)
        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy){
                if(e1==null||e2==null) return false;
                float dx=e2.getX()-e1.getX();
                float dy=Math.abs(e2.getY()-e1.getY());
                if(dx>120 && Math.abs(vx)>400 && dy<200 && openThreadNumber!=null){
                    tap(threadPanel); exitThread(); return true;
                }
                return false;
            }
        });
        View.OnTouchListener tl=new View.OnTouchListener(){
            @Override public boolean onTouch(View v, MotionEvent e){ gd.onTouchEvent(e); return false; }
        };
        if(threadPanel!=null) threadPanel.setOnTouchListener(tl);
        if(threadList!=null) threadList.setOnTouchListener(tl);
        if(threadBack!=null){
            // also allow swipe on the back button itself
            final float[] downX=new float[1];
            threadBack.setOnTouchListener(new View.OnTouchListener(){
                @Override public boolean onTouch(View v, MotionEvent e){
                    if(e.getAction()==MotionEvent.ACTION_DOWN) downX[0]=e.getX();
                    if(e.getAction()==MotionEvent.ACTION_UP){
                        float dx=e.getX()-downX[0];
                        if(Math.abs(dx)>30 && openThreadNumber!=null){ tap(v); exitThread(); return true; }
                    }
                    gd.onTouchEvent(e);
                    return false;
                }
            });
            // keep click handling via separate listener? we already have click, need to preserve - use OnClick via touch will still fire click if not consumed
        }
    }

    @Override
    public void onBackPressed(){
        if(openThreadNumber!=null){ tap(threadPanel); exitThread(); return; }
        if(!onChatTab()){
            tap(tabGroup);
            tabGroup.setSelectedItemId(R.id.tabChat);
            return;
        }
        long now=System.currentTimeMillis();
        if(now - lastBackPress < 2000){
            super.onBackPressed();
            return;
        }
        lastBackPress=now;
        Snackbar.make(findViewById(R.id.root), "Press back again to exit", Snackbar.LENGTH_SHORT).show();
    }

    private boolean onChatTab() {
        return tabGroup.getSelectedItemId() == R.id.tabChat;
    }

    private boolean onContactsTab() {
        return tabGroup.getSelectedItemId() == R.id.tabContacts;
    }
    private boolean onCallsTab(){ return tabGroup.getSelectedItemId()==R.id.tabCall; }

    private void refreshAll() {
        boolean sender = Prefs.get(this).isSender();
        headerSubtitle.setText(sender ? R.string.header_sender : R.string.header_receiver);
        if(statusText!=null) statusText.setText(sender ? R.string.status_sender : R.string.status_receiver);

        // tabs always visible except in thread
        if(tabGroup!=null) tabGroup.setVisibility(openThreadNumber!=null ? View.GONE : View.VISIBLE);
        if(searchPill!=null) searchPill.setVisibility((openThreadNumber!=null || isSettingsTab()) ? View.GONE : View.VISIBLE);
        if(simulateButton!=null) simulateButton.setVisibility(sender ? View.VISIBLE : View.GONE);

        // settings version
        TextView ver=findViewById(R.id.aboutVersion);
        if(ver!=null) try{ ver.setText("v"+getPackageManager().getPackageInfo(getPackageName(),0).versionName);}catch(Exception ignored){}

        if (sender) {
            boolean isSet=isSettingsTab();
            boolean chat=onChatTab();
            boolean calls=onCallsTab();
            chatList.setVisibility(isSet?View.GONE:(chat?View.VISIBLE:View.GONE));
            contactList.setVisibility(View.GONE);
            if(callsPage!=null) callsPage.setVisibility(isSet?View.GONE:(calls?View.VISIBLE:View.GONE));
            else if(callList!=null) callList.setVisibility(isSet?View.GONE:(calls?View.VISIBLE:View.GONE));
            if(settingsPanel!=null) settingsPanel.setVisibility(isSet?View.VISIBLE:View.GONE);
            threadPanel.setVisibility(View.GONE);
            if(composerPanel!=null) composerPanel.setVisibility(View.GONE);
            if(dialPad!=null) dialPad.setVisibility(isSet?View.GONE:(calls?View.VISIBLE:View.GONE));
            if(smsToNumber!=null) smsToNumber.setVisibility(View.GONE);
        } else if (openThreadNumber != null) {
            chatList.setVisibility(View.GONE);
            contactList.setVisibility(View.GONE);
            if(callsPage!=null) callsPage.setVisibility(View.GONE);
            if(callList!=null) callList.setVisibility(View.GONE);
            if(settingsPanel!=null) settingsPanel.setVisibility(View.GONE);
            threadPanel.setVisibility(View.VISIBLE);
            if(composerPanel!=null) composerPanel.setVisibility(View.VISIBLE);
            if(dialPad!=null) dialPad.setVisibility(View.GONE);
            if(smsToNumber!=null) smsToNumber.setVisibility(View.GONE);
        } else {
            boolean chat = onChatTab();
            boolean contacts = onContactsTab();
            boolean calls = onCallsTab();
            boolean settings = isSettingsTab();
            chatList.setVisibility(chat ? View.VISIBLE : View.GONE);
            contactList.setVisibility(contacts ? View.VISIBLE : View.GONE);
            if(callsPage!=null) callsPage.setVisibility(calls ? View.VISIBLE : View.GONE);
            if(callList!=null) callList.setVisibility(calls ? View.VISIBLE : View.GONE);
            if(settingsPanel!=null) settingsPanel.setVisibility(settings ? View.VISIBLE : View.GONE);
            threadPanel.setVisibility(View.GONE);
            if(composerPanel!=null) composerPanel.setVisibility(chat ? View.VISIBLE : View.GONE);
            if(dialPad!=null) dialPad.setVisibility(calls ? View.VISIBLE : View.GONE);
            if(smsToNumber!=null) smsToNumber.setVisibility(chat ? View.VISIBLE : View.GONE);
        }

        rebuildLists();
        // liquid slide for content switch
        View newVisible=null;
        if(openThreadNumber!=null) newVisible=threadPanel;
        else if(isSettingsTab()) newVisible=settingsPanel;
        else if(onChatTab()) newVisible=chatList;
        else if(onContactsTab()) newVisible=contactList;
        else if(onCallsTab()) newVisible=callsPage!=null?callsPage:callList;
        if(!firstRefresh && newVisible!=null && newVisible.getVisibility()==View.VISIBLE){
            int curTab = tabGroup!=null?tabGroup.getSelectedItemId():-1;
            if(openThreadNumber!=null) curTab = -2; // thread is separate
            if(curTab != lastTabId){
                float d=16*getResources().getDisplayMetrics().density;
                newVisible.setTranslationX(d); newVisible.setAlpha(0f);
                newVisible.animate().translationX(0).alpha(1f).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                lastTabId=curTab;
            }
        } else if(firstRefresh){
            firstRefresh=false;
            lastTabId = openThreadNumber!=null? -2 : (tabGroup!=null?tabGroup.getSelectedItemId():-1);
        }
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
        if (bonded != null) {
            peers.addAll(bonded);
        }
        if (peerDropdown != null) {
            java.util.List<String> labels = new java.util.ArrayList<>();
            String savedMac = Prefs.get(this).getPeerMac();
            int savedPos = -1;
            for (int i = 0; i < peers.size(); i++) {
                BluetoothDevice d = peers.get(i);
                String label = BtController.deviceLabel(d);
                labels.add(label);
                if (savedMac != null && savedMac.equals(d.getAddress())) savedPos = i;
            }
            if (labels.isEmpty()) labels.add(getString(R.string.peer_none));
            ArrayAdapter<String> dropAdapter = new ArrayAdapter<>(this,
                    com.google.android.material.R.layout.mtrl_auto_complete_simple_item, labels);
            peerDropdown.setAdapter(dropAdapter);
            if (savedPos >= 0) peerDropdown.setText(labels.get(savedPos), false);
            else if (!peers.isEmpty()) peerDropdown.setText(labels.get(0), false);
            else peerDropdown.setText(getString(R.string.peer_none), false);
        }
    }

    private void onStartPressed() {
        Prefs p = Prefs.get(this);
        String savedMac = p.getPeerMac();
        if (savedMac != null) {
            for (BluetoothDevice dev : peers) {
                if (dev.getAddress().equals(savedMac)) {
                    p.setPeer(dev.getAddress(), dev.getName());
                    break;
                }
            }
        } else if (!peers.isEmpty()) {
            BluetoothDevice dev = peers.get(0);
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
        final String msgId = String.valueOf(System.nanoTime() & 0xFFFFFFFFL);
        OutboxStore.get(this).add(finalTo, finalBody, "queued", msgId);
        smsBody.setText("");
        sendAnim(smsSend);
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
                new MaterialAlertDialogBuilder(MainActivity.this)
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
                new MaterialAlertDialogBuilder(MainActivity.this)
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
                tap(view);
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
                tap(view);
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
                tap(view);
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
                tap(view);
                if (row.kind == KIND_SYNC) {
                    syncContacts();
                    return;
                }
                if (row.number != null && !row.number.isEmpty()) {
                    openThread(row.number);
                }
            }
        });
        contactList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                UiRow row = contactAdapter.getItem(position);
                if (row == null || row.kind != KIND_NORMAL
                        || row.number == null || row.number.isEmpty()) return false;
                tap(view);
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
            tabGroup.setSelectedItemId(R.id.tabChat);
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
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(MainActivity.this)
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
            log = filterRows(log);
            addRows(chatAdapter, log, KEY_LOG);
            addRows(callAdapter, new ArrayList<>(log), KEY_LOG);
        } else {
            List<UiRow> people=filterRows(buildPeople());
            List<UiRow> calls=filterRows(buildReceiverCalls());
            addRows(chatAdapter, people, KEY_CHAT);
            addRows(callAdapter, calls, KEY_CALL);
            buildContactsFiltered(contactAdapter);
            if (openThreadNumber != null) {
                List<UiRow> thr=filterRows(buildThreadRows(openThreadNumber));
                addRows(threadAdapter, thr, openThreadNumber);
            }
        }
        chatAdapter.notifyDataSetChanged();
        callAdapter.notifyDataSetChanged();
        contactAdapter.notifyDataSetChanged();
        threadAdapter.notifyDataSetChanged();
    }
    private List<UiRow> filterRows(List<UiRow> in){
        if(searchQuery==null||searchQuery.isEmpty()) return in;
        List<UiRow> out=new ArrayList<>();
        for(UiRow r: in){
            if(r.kind!=KIND_NORMAL){ out.add(r); continue; }
            String hay=(r.title+" "+r.subtitle+" "+r.number+" "+r.name).toLowerCase(Locale.US);
            if(hay.contains(searchQuery)) out.add(r);
        }
        return out;
    }
    private void buildContactsFiltered(RowListAdapter out){
        // filtered
        List<ContactsStore.Contact> contacts = ContactsStore.get(this).getAll();
        List<UiRow> rows=new ArrayList<>();
        UiRow sync = new UiRow(KIND_SYNC, R.drawable.ic_send, getThemeColor(R.attr.colorPrimary), getString(R.string.contacts_sync), contacts.isEmpty()?getString(R.string.contacts_empty):getString(R.string.contacts_count, contacts.size()));
        rows.add(sync);
        for (ContactsStore.Contact c : contacts) {
            UiRow row = new UiRow(R.drawable.ic_contacts, getThemeColor(R.attr.colorPrimary), c.name.isEmpty()?c.number:c.name, c.number, "");
            row.number=c.number; row.name=c.name; rows.add(row);
        }
        rows=filterRows(rows);
        for(UiRow r: rows) out.add(r);
    }

    private void addRows(RowListAdapter out, List<UiRow> rows, String key) {
        if (rows.isEmpty()) {
            out.add(new UiRow(R.drawable.ic_notif_sms, getThemeColor(R.attr.colorOnSurfaceVariant),
                    "Nothing here yet", "", ""));
            return;
        }
        // Chats list and open thread always show everything, no collapsing
        if (KEY_CHAT.equals(key) || (openThreadNumber != null && openThreadNumber.equals(key))
                || expanded.contains(key) || rows.size() <= SEE_MORE_LIMIT) {
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
                    (e.ok ? getThemeColor(R.attr.colorPrimary) : getThemeColor(R.attr.colorError)),
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
                    (o ? getThemeColor(R.attr.colorPrimary) : getThemeColor(R.attr.colorPrimary)),
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
                    getThemeColor(R.attr.colorPrimary),
                    name.isEmpty() ? m.from : name, m.body, fmt.format(new Date(m.ts)), m, null)});
        }
        for (OutboxStore.Sent s : OutboxStore.get(this).getAll()) {
            if (!cleanNum(s.to).equals(key)) continue;
            String status = s.ok ? "sent" : (s.status == null || s.status.isEmpty() ? "queued" : s.status);
            merged.add(new Object[]{s.ts, new UiRow(R.drawable.ic_send,
                    (s.ok ? getThemeColor(R.attr.colorPrimary) : getThemeColor(R.attr.colorError)),
                    "You", s.body + "\n  \u2022 " + status, fmt.format(new Date(s.ts)), null, s)});
        }
        return sortedRowsAsc(merged);
    }

    private List<UiRow> sortedRowsAsc(List<Object[]> merged) {
        Collections.sort(merged, new java.util.Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                return Long.compare((Long) a[0], (Long) b[0]);
            }
        });
        List<UiRow> out = new ArrayList<>();
        for (Object[] row : merged) {
            out.add((UiRow) row[1]);
        }
        return out;
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
                    color = getThemeColor(R.attr.colorTertiary);
                    title = "Incoming call";
                    break;
                case Protocol.EV_DIALING:
                    color = getThemeColor(R.attr.colorPrimary);
                    title = "Outgoing call";
                    break;
                case Protocol.EV_MISSED:
                    color = getThemeColor(R.attr.colorError);
                    title = "Missed call";
                    break;
                case Protocol.EV_ANSWERED:
                    color = getThemeColor(R.attr.colorPrimary);
                    title = "Answered";
                    break;
                case Protocol.EV_ENDED:
                    color = getThemeColor(R.attr.colorOutline);
                    title = "Call ended";
                    break;
                default:
                    color = getThemeColor(R.attr.colorOnSurfaceVariant);
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
        scrollThreadToLatest();
    }

    private void scrollThreadToLatest() {
        if (threadList == null || threadAdapter == null) return;
        final int last = threadAdapter.getCount() - 1;
        if (last < 0) return;
        threadList.post(new Runnable() {
            @Override public void run() { threadList.setSelection(last); }
        });
    }

    private void exitThread() {
        if (openThreadNumber == null) return;
        clearThreadUi();
        tabGroup.setSelectedItemId(R.id.tabChat);
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
                getThemeColor(R.attr.colorPrimary),
                getString(R.string.contacts_sync), contacts.isEmpty()
                        ? getString(R.string.contacts_empty)
                        : getString(R.string.contacts_count, contacts.size()));
        out.add(sync);
        for (ContactsStore.Contact c : contacts) {
            UiRow row = new UiRow(R.drawable.ic_contacts,
                    getThemeColor(R.attr.colorPrimary), c.name.isEmpty() ? c.number : c.name,
                    c.number, "");
            row.number = c.number;
            row.name = c.name;
            out.add(row);
        }
    }

    private void liquidSlide(View from, View to){
        if(from==null||to==null||from==to) return;
        float d = 16 * getResources().getDisplayMetrics().density;
        // from out to left
        from.animate().translationX(-d).alpha(0f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).withEndAction(new Runnable(){@Override public void run(){ from.setVisibility(View.GONE); from.setTranslationX(0); from.setAlpha(1f); }}).start();
        // to in from right
        to.setVisibility(View.VISIBLE);
        to.setTranslationX(d); to.setAlpha(0f);
        to.animate().translationX(0).alpha(1f).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }
    private void tap(View v){ if(v!=null) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); }
    private void sendAnim(View v){
        if(v==null) return;
        v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(90)
                .withEndAction(new Runnable(){
                    @Override public void run(){
                        v.animate().scaleX(1f).scaleY(1f).setDuration(140)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
                    }
                }).start();
    }
    private void confirm(View v){ if(v!=null) v.performHapticFeedback(HapticFeedbackConstants.CONFIRM); }
    private void buzzReject(View v){ if(v!=null) v.performHapticFeedback(HapticFeedbackConstants.REJECT); }
    private boolean isSettingsTab(){ return tabGroup!=null && tabGroup.getSelectedItemId()==R.id.tabSettings; }
    private String initialsFor(String name){
        if(name==null||name.isEmpty()) return "?";
        String[] parts=name.trim().split("\\s+");
        if(parts.length==1) return parts[0].substring(0,1).toUpperCase(Locale.US);
        return (parts[0].substring(0,1)+parts[parts.length-1].substring(0,1)).toUpperCase(Locale.US);
    }
    private int avatarColorFor(String key){
        if(key==null) key="";
        int[] palette=new int[]{
            0xFF7FB069, 0xFFE07A5F, 0xFF81B29A, 0xFFF2CC8F, 0xFF9A8C98, 0xFFC9ADA7,
            0xFFA7C4BC, 0xFFB5838D, 0xFF8D99AE, 0xFFEDAFB8, 0xFF90BE6D, 0xFFF4A261,
            0xFF9AB8D3, 0xFFD8B4FE, 0xFFA9DEF9, 0xFFFFC6FF
        };
        int h=Math.abs(key.hashCode());
        int c = palette[h % palette.length];
        if (Prefs.get(this).isMono()) {
            int gray = (int)(0.299 * ((c >> 16) & 0xFF) + 0.587 * ((c >> 8) & 0xFF) + 0.114 * (c & 0xFF));
            if (gray < 70) gray = 70;
            if (gray > 200) gray = 200;
            return 0xFF000000 | (gray << 16) | (gray << 8) | gray;
        }
        return c;
    }

    private void applySavedTheme() {
        String t = Prefs.get(this).getTheme();
        if (Prefs.THEME_MONO.equals(t)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            setTheme(R.style.AppTheme_Mono);
        } else if (Prefs.THEME_LIGHT.equals(t)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (Prefs.THEME_DARK.equals(t)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    private int getThemeColor(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        try {
            getTheme().resolveAttribute(attr, tv, true);
            return tv.data;
        } catch (Throwable t) {
            return 0xFF6750A4;
        }
    }

    private int getColorRobust(int res) {
        try {
            return getColor(res);
        } catch (Throwable t) {
            return 0xFF6750A4;
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
        @Override public int getViewTypeCount(){ return 3; }
        @Override public int getItemViewType(int pos){
            UiRow r=getItem(pos);
            if(r!=null && r.snt!=null) return 1;
            if(r!=null && r.rcv!=null) return 2;
            return 0;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            UiRow row = getItem(position);
            MainActivity act = (MainActivity) getContext();
            int type=getItemViewType(position);
            View v = convertView;
            // inflate correct layout for type
            if (v == null || (type==1 && v.findViewById(R.id.bubbleText)==null) || (type==0 && v.findViewById(R.id.avatarWrap)!=null && v.findViewById(R.id.bubbleText)!=null) || (type==0 && v.findViewById(R.id.avatarWrap)==null)) {
                if(type==1) v = android.view.LayoutInflater.from(getContext()).inflate(R.layout.row_bubble_sent, parent, false);
                else if(type==2) v = android.view.LayoutInflater.from(getContext()).inflate(R.layout.row_bubble_received, parent, false);
                else v = android.view.LayoutInflater.from(getContext()).inflate(R.layout.list_item_row, parent, false);
            } else if(type==1 && v.findViewById(R.id.bubbleText)==null){
                v = android.view.LayoutInflater.from(getContext()).inflate(R.layout.row_bubble_sent, parent, false);
            } else if(type==2 && v.findViewById(R.id.bubbleText)==null){
                v = android.view.LayoutInflater.from(getContext()).inflate(R.layout.row_bubble_received, parent, false);
            } else if(type==0 && v.findViewById(R.id.bubbleText)!=null){
                v = android.view.LayoutInflater.from(getContext()).inflate(R.layout.list_item_row, parent, false);
            }
            if(type==1 || type==2){
                TextView bt=v.findViewById(R.id.bubbleText);
                TextView tm=v.findViewById(R.id.bubbleTime);
                if(row.snt!=null){ bt.setText(row.snt.body); tm.setText(row.time); }
                else if(row.rcv!=null){ bt.setText(row.rcv.body); tm.setText(row.time); }
                else { bt.setText(row.title); tm.setText(row.time); }
                return v;
            }
            // normal row with avatar
            ImageView icon = v.findViewById(R.id.rowIcon);
            TextView avatarText=v.findViewById(R.id.avatarText);
            View avatarWrap=v.findViewById(R.id.avatarWrap);
            if(avatarText!=null){
                String initials=act.initialsFor(row.title);
                avatarText.setText(initials);
                if(row.kind!=0){ if(avatarWrap!=null) avatarWrap.setVisibility(View.GONE); if(icon!=null) icon.setVisibility(View.VISIBLE); }
                else {
                    if(avatarWrap!=null){
                        avatarWrap.setVisibility(View.VISIBLE);
                        avatarWrap.setBackgroundTintList(ColorStateList.valueOf(
                                act.avatarColorFor(row.title!=null?row.title:row.number)));
                    }
                    if(icon!=null) icon.setVisibility(View.GONE);
                }
            }
            if(icon!=null){
                icon.setImageResource(row.icon);
                icon.setColorFilter(row.color, PorterDuff.Mode.SRC_IN);
                if (row.kind != 0) icon.setColorFilter(act.getThemeColor(R.attr.colorPrimary), PorterDuff.Mode.SRC_IN);
            }
            TextView title = v.findViewById(R.id.rowTitle);
            TextView sub = v.findViewById(R.id.rowSub);
            TextView time = v.findViewById(R.id.rowTime);
            if (row.kind == KIND_SEE_MORE) {
                int extra = 0;
                try { extra = Integer.parseInt(row.title); } catch (Exception ignored) {}
                title.setText(extra > 0 ? act.getString(R.string.see_more_n, extra) : act.getString(R.string.see_more));
                title.setTextColor(act.getThemeColor(R.attr.colorPrimary));
                sub.setText(""); time.setText("");
                if(avatarWrap!=null) avatarWrap.setVisibility(View.GONE);
                if(icon!=null){ icon.setVisibility(View.VISIBLE); icon.setColorFilter(act.getThemeColor(R.attr.colorPrimary), PorterDuff.Mode.SRC_IN); }
            } else {
                if (row.title == null || row.title.isEmpty()) title.setText(act.getString(R.string.see_more));
                else title.setText(row.title);
                title.setTextColor(act.getThemeColor(R.attr.colorOnSurface));
                sub.setText(row.subtitle);
                sub.setTextColor(act.getThemeColor(R.attr.colorOnSurfaceVariant));
                time.setText(row.time);
            }
            return v;
        }
    }
}