package com.smsbridge;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CallActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_CALL.equals(intent.getAction())) {
            String number = numberFrom(intent);
            if (number == null) {
                finish();
                return;
            }
            placeCall(number);
            finish();
            return;
        }

        final String initial = numberFrom(intent);
        buildDialUi(initial);
    }

    private void buildDialUi(final String initial) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("SMSBridge dialer");
        title.setTextSize(22);
        root.addView(title);

        final EditText number = new EditText(this);
        number.setHint("Number");
        if (initial != null) number.setText(initial);
        root.addView(number);

        Button call = new Button(this);
        call.setText("Call");
        call.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String n = number.getText().toString().trim();
                if (CallApi.isValidNumber(n)) placeCall(n);
                finish();
            }
        });
        root.addView(call);

        Button close = new Button(this);
        close.setText("Cancel");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        root.addView(close);

        setContentView(root);
    }

    private void placeCall(String number) {
        try {
            TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
            tm.placeCall(Uri.fromParts("tel", number, null), null);
        } catch (Exception e) {
            android.util.Log.e("SmsBridge.CallActivity", "placeCall failed", e);
        }
    }

    private String numberFrom(Intent intent) {
        if (intent == null || intent.getData() == null) return null;
        String s = intent.getData().getSchemeSpecificPart();
        if (s != null && !s.isEmpty()) return s;
        return null;
    }
}