package com.programmers4hl.printora;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.hardware.usb.*;
import android.net.Uri;
import android.os.*;
import android.print.*;
import android.view.View;
import android.widget.*;

import java.io.*;
import java.util.HashMap;

public class MainActivity extends Activity {

    private static final String USB_ACTION =
            "com.programmers4hl.printora.USB_PERMISSION";

    private static final int PICK_PDF = 100;

    private UsbManager usbManager;
    private LinearLayout list;
    private TextView pdfInfo;
    private Uri pdfUri;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (!USB_ACTION.equals(intent.getAction())) return;

            boolean granted = intent.getBooleanExtra(
                    UsbManager.EXTRA_PERMISSION_GRANTED, false);

            Toast.makeText(
                    MainActivity.this,
                    granted ? "USB permission granted"
                            : "USB permission denied",
                    Toast.LENGTH_SHORT
            ).show();

            detectUSB();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        usbManager = (UsbManager) getSystemService(USB_SERVICE);

        registerReceiver(
                receiver,
                new IntentFilter(USB_ACTION),
                Context.RECEIVER_NOT_EXPORTED
        );

        buildUI();
        detectUSB();
    }

    private TextView text(String s, float size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setPadding(16, 12, 16, 12);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private void buildUI() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 35, 20, 20);
        root.setBackgroundColor(Color.rgb(11, 11, 13));

        root.addView(text("Printora", 28));

        TextView sub = text(
                "PDF printing • USB printer",
                14
        );
        sub.setTextColor(Color.LTGRAY);
        root.addView(sub);

        Button select = button("SELECT
