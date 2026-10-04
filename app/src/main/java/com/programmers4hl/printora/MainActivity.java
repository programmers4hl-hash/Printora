package com.programmers4hl.printora;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;

public class MainActivity extends Activity {

    private static final String ACTION_USB_PERMISSION =
            "com.programmers4hl.printora.USB_PERMISSION";

    private UsbManager usbManager;
    private LinearLayout deviceList;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    Toast.makeText(
                            MainActivity.this,
                            "USB permission granted",
                            Toast.LENGTH_SHORT
                    ).show();
                    detectPrinters();
                } else {
                    Toast.makeText(
                            MainActivity.this,
                            "USB permission denied",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);

        registerReceiver(
                usbReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
        );

        createInterface();
        detectPrinters();
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setPadding(20, 16, 20, 16);
        return view;
    }

    private void createInterface() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 40, 20, 20);
        root.setBackgroundColor(Color.rgb(11, 11, 13));

        TextView title = text("Printora", 28);
        root.addView(title);

        TextView subtitle = text(
                "USB printer test • Epson L3110 bridge",
                14
        );
        subtitle.setTextColor(Color.LTGRAY);
        root.addView(subtitle);

        Button detect = new Button(this);
        detect.setText("DETECT USB PRINTERS");
        detect.setOnClickListener(v -> detectPrinters());
        root.addView(detect);

        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(deviceList);

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(root);
    }

    private void detectPrinters() {

        deviceList.removeAllViews();

        HashMap<String, UsbDevice> devices =
                usbManager.getDeviceList();

        if (devices.isEmpty()) {
            deviceList.addView(
                    text(
                            "No USB device detected.\n\n" +
                            "Connect the Epson printer using a USB-OTG adapter " +
                            "and tap Detect USB Printers.",
                            16
                    )
            );
            return;
        }

        for (UsbDevice device : devices.values()) {

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(10, 10, 10, 10);
            card.setBackgroundColor(Color.rgb(25, 25, 29));

            String information =
                    "USB device\n" +
                    "VID: " + String.format(
                            "%04X", device.getVendorId()) +
                    "   PID: " + String.format(
                            "%04X", device.getProductId()) +
                    "\nClass: " + device.getDeviceClass() +
                    "\nInterfaces: " + device.getInterfaceCount();

            card.addView(text(information, 16));

            Button permission = new Button(this);

            if (usbManager.hasPermission(device)) {
                permission.setText("USB READY");
            } else {
                permission.setText("REQUEST USB PERMISSION");
            }

            permission.setOnClickListener(v -> requestUsbPermission(device));

            card.addView(permission);

            deviceList.addView(
                    card,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    )
            );
        }
    }

    private void requestUsbPermission(UsbDevice device) {

        if (usbManager.hasPermission(device)) {
            Toast.makeText(
                    this,
                    "USB device permission already granted",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Intent intent = new Intent(ACTION_USB_PERMISSION);

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE
                );

        usbManager.requestPermission(
                device,
                pendingIntent
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            unregisterReceiver(usbReceiver);
        } catch (Exception ignored) {
        }
    }
              }
