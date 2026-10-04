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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class MainActivity extends Activity {

    private static final String USB_PERMISSION =
            "com.programmers4hl.printora.USB_PERMISSION";

    private UsbManager usbManager;
    private UsbDevice printer;
    private UsbDeviceConnection connection;

    private TextView status;
    private TextView details;

    private final BroadcastReceiver receiver =
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

                printer = device;

                status.setText(
                        "USB permission granted"
                );

                inspectPrinter();

            } else {

                status.setText(
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

        registerReceiver(
                receiver,
                new IntentFilter(
                        USB_PERMISSION),
                Context.RECEIVER_NOT_EXPORTED);

        createUI();

        findPrinter();
    }

    private TextView makeText(
            String value,
            float size) {

        TextView t =
                new TextView(this);

        t.setText(value);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(size);

        t.setPadding(
                16,
                12,
                16,
                12);

        return t;
    }

    private Button makeButton(
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

        root.addView(
                makeText(
                        "Printora",
                        28));

        root.addView(
                makeText(
                        "Epson L3110 USB Diagnostic",
                        14));

        status =
                makeText(
                        "Searching...",
                        16);

        root.addView(status);

        Button detect =
                makeButton(
                        "DETECT PRINTER");

        detect.setOnClickListener(
                v -> findPrinter());

        root.addView(detect);

        Button inspect =
                makeButton(
                        "INSPECT USB");

        inspect.setOnClickListener(
                v -> inspectPrinter());

        root.addView(inspect);

        Button deviceId =
                makeButton(
                        "READ PRINTER ID");

        deviceId.setOnClickListener(
                v -> readDeviceId());

        root.addView(deviceId);

        details =
                makeText(
                        "No diagnostic data yet.",
                        14);

        ScrollView scroll =
                new ScrollView(this);

        scroll.addView(details);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1));

        setContentView(root);
    }    private void findPrinter() {

        deviceListClear();

        HashMap<String, UsbDevice> devices =
                usbManager.getDeviceList();

        if (devices.isEmpty()) {

            status.setText(
                    "No USB device found"
            );

            details.setText(
                    "Connect the Epson L3110 with USB OTG."
            );

            return;
        }

        for (UsbDevice device :
                devices.values()) {

            if (device.getVendorId() == 0x04B8 &&
                    device.getProductId() == 0x1142) {

                printer = device;

                status.setText(
                        "Epson L3110 detected"
                );

                addPrinterButton(device);

                if (usbManager.hasPermission(
                        device)) {

                    inspectPrinter();

                } else {

                    details.setText(
                            "Epson L3110 found.\n" +
                            "USB permission is required."
                    );
                }

                return;
            }
        }

        status.setText(
                "USB device found, but not L3110"
        );

        for (UsbDevice device :
                devices.values()) {

            addPrinterButton(device);
        }
    }

    private void deviceListClear() {

        LinearLayout parent =
                (LinearLayout)
                        details.getParent();

        if (parent == null) {
            return;
        }
    }

    private void addPrinterButton(
            UsbDevice device) {

        Button b =
                makeButton(
                        "CONNECT " +
                        String.format(
                                "%04X:%04X",
                                device.getVendorId(),
                                device.getProductId()
                        )
                );

        b.setOnClickListener(
                v -> {

                    printer = device;

                    if (usbManager.hasPermission(
                            device)) {

                        inspectPrinter();

                    } else {

                        requestPermission(
                                device
                        );
                    }
                }
        );

        LinearLayout root =
                (LinearLayout)
                        status.getParent();

        root.addView(
                b
        );
    }

    private void requestPermission(
            UsbDevice device) {

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        0,
                        new Intent(
                                USB_PERMISSION),
                        PendingIntent.FLAG_IMMUTABLE
                );

        usbManager.requestPermission(
                device,
                pendingIntent
        );
    }

    private void inspectPrinter() {

        if (printer == null) {

            status.setText(
                    "No printer selected"
            );

            return;
        }

        if (!usbManager.hasPermission(
                printer)) {

            status.setText(
                    "USB permission required"
            );

            requestPermission(
                    printer
            );

            return;
        }

        closeConnection();

        connection =
                usbManager.openDevice(
                        printer
                );

        if (connection == null) {

            status.setText(
                    "Cannot open Epson L3110"
            );

            return;
        }

        StringBuilder report =
                new StringBuilder();

        report.append(
                "EPSON L3110\n\n"
        );

        report.append(
                "VID: "
        );

        report.append(
                String.format(
                        "%04X",
                        printer.getVendorId()
                )
        );

        report.append(
                "\nPID: "
        );

        report.append(
                String.format(
                        "%04X",
                        printer.getProductId()
                )
        );

        report.append(
                "\nInterfaces: "
        );

        report.append(
                printer.getInterfaceCount()
        );

        report.append(
                "\n\n"
        );

        for (int i = 0;
             i < printer.getInterfaceCount();
             i++) {

            UsbInterface intf =
                    printer.getInterface(i);

            report.append(
                    "Interface "
            );

            report.append(i);

            report.append(
                    "\nClass: "
            );

            report.append(
                    intf.getInterfaceClass()
            );

            report.append(
                    "\nSubclass: "
            );

            report.append(
                    intf.getInterfaceSubclass()
            );

            report.append(
                    "\nProtocol: "
            );

            report.append(
                    intf.getInterfaceProtocol()
            );

            report.append(
                    "\nEndpoints: "
            );

            report.append(
                    intf.getEndpointCount()
            );

            report.append(
                    "\n"
            );

            for (int j = 0;
                 j < intf.getEndpointCount();
                 j++) {

                UsbEndpoint ep =
                        intf.getEndpoint(j);

                report.append(
                        "  EP "
                );

                report.append(
                        String.format(
                                "0x%02X",
                                ep.getAddress()
                        )
                );

                report.append(
                        " type="
                );

                report.append(
                        ep.getType()
                );

                report.append(
                        " direction="
                );

                report.append(
                        ep.getDirection()
                );

                report.append(
                        " maxPacket="
                );

                report.append(
                        ep.getMaxPacketSize()
                );

                report.append(
                        "\n"
                );
            }

            report.append("\n");
        }

        details.setText(
                report.toString()
        );

        status.setText(
                "USB inspection complete"
        );
    }    private void readDeviceId() {

        if (printer == null) {

            status.setText(
                    "No Epson printer selected"
            );

            return;
        }

        if (!usbManager.hasPermission(
                printer)) {

            requestPermission(
                    printer
            );

            return;
        }

        if (connection == null) {

            connection =
                    usbManager.openDevice(
                            printer
                    );
        }

        if (connection == null) {

            status.setText(
                    "Cannot open printer"
            );

            return;
        }

        new Thread(() -> {

            String result =
                    getPrinterDeviceId();

            runOnUiThread(() -> {

                details.setText(
                        result
                );

                if (result.startsWith(
                        "DEVICE ID READ")) {

                    status.setText(
                            "Printer ID received"
                    );

                } else {

                    status.setText(
                            "Device ID not available"
                    );
                }
            });

        }).start();
    }

    private String getPrinterDeviceId() {

        for (int i = 0;
             i < printer.getInterfaceCount();
             i++) {

            UsbInterface intf =
                    printer.getInterface(i);

            int interfaceClass =
                    intf.getInterfaceClass();

            if (interfaceClass !=
                    UsbConstants.USB_CLASS_PRINTER) {

                continue;
            }

            boolean claimed =
                    connection.claimInterface(
                            intf,
                            true
                    );

            if (!claimed) {

                continue;
            }

            byte[] buffer =
                    new byte[4096];

            int length =
                    connection.controlTransfer(
                            0xA1,
                            0x00,
                            0x0000,
                            intf.getId(),
                            buffer,
                            buffer.length,
                            5000
                    );

            if (length > 0) {

                String id =
                        new String(
                                buffer,
                                0,
                                length,
                                StandardCharsets.US_ASCII
                        );

                return
                        "DEVICE ID READ\n\n" +
                        id;
            }
        }

        return
                "DEVICE ID READ FAILED\n\n" +
                "No IEEE-1284 printer ID was returned.";
    }

    private void closeConnection() {

        if (connection != null) {

            try {

                connection.close();

            } catch (Exception ignored) {
            }
        }

        connection = null;
    }    @Override
    protected void onDestroy() {

        closeConnection();

        try {

            unregisterReceiver(
                    receiver
            );

        } catch (Exception ignored) {
        }

        super.onDestroy();
    }
}