package com.programmers4hl.printora;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class MainActivity extends Activity {

    private static final String USB_PERMISSION =
            "com.programmers4hl.printora.USB_PERMISSION";

    private UsbManager usbManager;
    private LinearLayout deviceList;
    private TextView statusText;

    private UsbDevice selectedDevice;
    private UsbDeviceConnection connection;
    private UsbEndpoint outEndpoint;

    private final BroadcastReceiver usbReceiver =
            new BroadcastReceiver() {

        @Override
        public void onReceive(
                Context context,
                Intent intent) {

            if (!USB_PERMISSION.equals(
                    intent.getAction())) {
                return;
            }

            UsbDevice device =
                    intent.getParcelableExtra(
                            UsbManager.EXTRA_DEVICE);

            boolean granted =
                    intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED,
                            false);

            if (granted && device != null) {

                statusText.setText(
                        "USB permission granted"
                );

                connectPrinter(device);

            } else {

                statusText.setText(
                        "USB permission denied"
                );
            }
        }
    };

    @Override
    protected void onCreate(
            Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        usbManager =
                (UsbManager)
                        getSystemService(
                                USB_SERVICE);

        IntentFilter filter =
                new IntentFilter(
                        USB_PERMISSION);

        registerReceiver(
                usbReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED);

        createUI();

        detectPrinters();
    }

    private TextView text(
            String value,
            float size) {

        TextView t =
                new TextView(this);

        t.setText(value);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(size);

        t.setPadding(
                16,
                14,
                16,
                14);

        return t;
    }

    private Button button(
            String value) {

        Button b =
                new Button(this);

        b.setText(value);
        b.setAllCaps(false);

        return b;
    }

    private void createUI() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL);

        root.setPadding(
                20,
                35,
                20,
                20);

        root.setBackgroundColor(
                0xFF0B0B0D);

        TextView title =
                text(
                        "Printora",
                        28);

        title.setGravity(
                Gravity.CENTER_VERTICAL);

        root.addView(title);

        TextView subtitle =
                text(
                        "Direct USB • Epson ESC/P-R",
                        14);

        subtitle.setTextColor(
                0xFFBBBBBB);

        root.addView(subtitle);

        statusText =
                text(
                        "Looking for USB printer...",
                        15);

        root.addView(statusText);

        Button detect =
                button(
                        "DETECT PRINTER");

        detect.setOnClickListener(
                v -> detectPrinters());

        root.addView(detect);

        Button connect =
                button(
                        "CONNECT EPSON");

        connect.setOnClickListener(
                v -> connectSelected());

        root.addView(connect);

        Button test =
                button(
                        "SEND USB TEST");

        test.setOnClickListener(
                v -> sendTest());

        root.addView(test);

        deviceList =
                new LinearLayout(this);

        deviceList.setOrientation(
                LinearLayout.VERTICAL);

        ScrollView scroll =
                new ScrollView(this);

        scroll.addView(deviceList);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1));

        setContentView(root);
    }    private void detectPrinters() {

        deviceList.removeAllViews();

        HashMap<String, UsbDevice> devices =
                usbManager.getDeviceList();

        if (devices.isEmpty()) {

            statusText.setText(
                    "No USB device found"
            );

            deviceList.addView(
                    text(
                            "Connect the Epson L3110 using USB OTG.",
                            16
                    )
            );

            return;
        }

        for (UsbDevice device :
                devices.values()) {

            String info =
                    "USB Device\n" +
                    "VID: " +
                    String.format(
                            "%04X",
                            device.getVendorId()
                    ) +
                    "\nPID: " +
                    String.format(
                            "%04X",
                            device.getProductId()
                    ) +
                    "\nInterfaces: " +
                    device.getInterfaceCount();

            TextView infoView =
                    text(
                            info,
                            16
                    );

            deviceList.addView(
                    infoView
            );

            Button permission =
                    button(
                            usbManager.hasPermission(device)
                                    ? "CONNECT"
                                    : "ALLOW USB"
                    );

            permission.setOnClickListener(
                    v -> {

                        selectedDevice = device;

                        if (usbManager.hasPermission(
                                device)) {

                            connectPrinter(device);

                        } else {

                            requestPermission(device);
                        }
                    }
            );

            deviceList.addView(
                    permission
            );
        }

        statusText.setText(
                "USB printer detected"
        );
    }

    private void requestPermission(
            UsbDevice device) {

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        0,
                        new Intent(
                                USB_PERMISSION
                        ),
                        PendingIntent.FLAG_IMMUTABLE
                );

        usbManager.requestPermission(
                device,
                pendingIntent
        );
    }

    private void connectSelected() {

        if (selectedDevice == null) {

            Toast.makeText(
                    this,
                    "Select the Epson printer first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (!usbManager.hasPermission(
                selectedDevice)) {

            requestPermission(
                    selectedDevice
            );

            return;
        }

        connectPrinter(
                selectedDevice
        );
    }

    private void connectPrinter(
            UsbDevice device) {

        closeConnection();

        UsbInterface printerInterface =
                null;

        UsbEndpoint endpoint =
                null;

        for (int i = 0;
             i < device.getInterfaceCount();
             i++) {

            UsbInterface usbInterface =
                    device.getInterface(i);

            for (int j = 0;
                 j < usbInterface
                         .getEndpointCount();
                 j++) {

                UsbEndpoint ep =
                        usbInterface
                                .getEndpoint(j);

                if (ep.getType() ==
                        UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    ep.getDirection() ==
                        UsbConstants.USB_DIR_OUT) {

                    printerInterface =
                            usbInterface;

                    endpoint =
                            ep;

                    break;
                }
            }

            if (endpoint != null) {
                break;
            }
        }

        if (printerInterface == null ||
                endpoint == null) {

            statusText.setText(
                    "No USB print endpoint found"
            );

            return;
        }

        connection =
                usbManager.openDevice(
                        device
                );

        if (connection == null) {

            statusText.setText(
                    "Unable to open USB printer"
            );

            return;
        }

        if (!connection.claimInterface(
                printerInterface,
                true)) {

            statusText.setText(
                    "Unable to claim printer USB interface"
            );

            closeConnection();

            return;
        }

        selectedDevice = device;
        outEndpoint = endpoint;

        statusText.setText(
                "EPSON USB READY"
        );

        Toast.makeText(
                this,
                "Printer connected",
                Toast.LENGTH_SHORT
        ).show();
    }    private void sendTest() {

        if (connection == null ||
                outEndpoint == null) {

            Toast.makeText(
                    this,
                    "Connect the printer first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        statusText.setText(
                "Sending USB test..."
        );

        new Thread(() -> {

            try {

                byte[] data =
                        buildTestData();

                int result =
                        connection.bulkTransfer(
                                outEndpoint,
                                data,
                                data.length,
                                10000
                        );

                runOnUiThread(() -> {

                    if (result >= 0) {

                        statusText.setText(
                                "USB data sent: " +
                                result +
                                " bytes"
                        );

                        Toast.makeText(
                                this,
                                "USB test sent",
                                Toast.LENGTH_SHORT
                        ).show();

                    } else {

                        statusText.setText(
                                "USB transfer failed"
                        );
                    }
                });

            } catch (Exception e) {

                runOnUiThread(() ->
                        statusText.setText(
                                "USB error: " +
                                e.getMessage()
                        )
                );
            }

        }).start();
    }

    private byte[] buildTestData() {

        String text =
                "\n\n" +
                "PRINTORA USB TEST\n" +
                "EPSON PRINTER\n" +
                "------------------------\n" +
                "USB connection detected.\n" +
                "This is a transport test.\n" +
                "\n\n";

        byte[] textBytes =
                text.getBytes(
                        StandardCharsets.US_ASCII
                );

        byte[] init = new byte[]{
                0x1B,
                0x40
        };

        byte[] feed = new byte[]{
                0x0A,
                0x0A,
                0x0A
        };

        byte[] result =
                new byte[
                        init.length +
                        textBytes.length +
                        feed.length
                ];

        int position = 0;

        System.arraycopy(
                init,
                0,
                result,
                position,
                init.length
        );

        position += init.length;

        System.arraycopy(
                textBytes,
                0,
                result,
                position,
                textBytes.length
        );

        position += textBytes.length;

        System.arraycopy(
                feed,
                0,
                result,
                position,
                feed.length
        );

        return result;
    }

    private void closeConnection() {

        if (connection != null) {

            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }

        connection = null;
        outEndpoint = null;
    }    @Override
    protected void onDestroy() {

        closeConnection();

        try {

            unregisterReceiver(
                    usbReceiver
            );

        } catch (Exception ignored) {
        }

        super.onDestroy();
    }
}