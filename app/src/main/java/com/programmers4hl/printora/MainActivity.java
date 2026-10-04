package com.programmers4hl.printora;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;

public class MainActivity extends Activity {

    private static final String USB_PERMISSION =
            "com.programmers4hl.printora.USB_PERMISSION";

    private static final int PICK_PDF = 100;

    private UsbManager usbManager;
    private LinearLayout deviceList;
    private TextView pdfInfo;
    private Uri selectedPdf;

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

            boolean granted =
                    intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED,
                            false);

            if (granted) {
                Toast.makeText(
                        MainActivity.this,
                        "USB permission granted",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(
                        MainActivity.this,
                        "USB permission denied",
                        Toast.LENGTH_SHORT).show();
            }

            detectUSB();
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
        detectUSB();
    }

    private TextView makeText(
            String text,
            float size) {

        TextView view =
                new TextView(this);

        view.setText(text);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(size);

        view.setPadding(
                16, 14, 16, 14);

        return view;
    }

    private Button makeButton(
            String text) {

        Button button =
                new Button(this);

        button.setText(text);
        button.setAllCaps(false);

        return button;
    }

    private void createUI() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL);

        root.setPadding(
                20, 35, 20, 20);

        root.setBackgroundColor(
                0xFF0B0B0D);

        TextView title =
                makeText(
                        "Printora",
                        28);

        title.setGravity(
                Gravity.CENTER_VERTICAL);

        root.addView(title);

        TextView subtitle =
                makeText(
                        "PDF printing • USB printer",
                        14);

        subtitle.setTextColor(
                0xFFBBBBBB);

        root.addView(subtitle);

        Button selectPDF =
                makeButton(
                        "SELECT PDF");

        selectPDF.setOnClickListener(
                v -> selectPDF());

        root.addView(selectPDF);

        pdfInfo =
                makeText(
                        "No PDF selected",
                        15);

        pdfInfo.setTextColor(
                0xFFBBBBBB);

        root.addView(pdfInfo);        Button print =
                makeButton(
                        "PRINT SELECTED PDF");

        print.setOnClickListener(
                v -> printPDF());

        root.addView(print);

        Button detect =
                makeButton(
                        "DETECT USB PRINTERS");

        detect.setOnClickListener(
                v -> detectUSB());

        root.addView(detect);

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
    }

    private void selectPDF() {

        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT);

        intent.addCategory(
                Intent.CATEGORY_OPENABLE);

        intent.setType(
                "application/pdf");

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        startActivityForResult(
                intent,
                PICK_PDF);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data);

        if (requestCode != PICK_PDF) {
            return;
        }

        if (resultCode != RESULT_OK) {
            return;
        }

        if (data == null) {
            return;
        }

        Uri uri = data.getData();

        if (uri == null) {
            return;
        }

        selectedPdf = uri;

        try {
            getContentResolver()
                    .takePersistableUriPermission(
                            selectedPdf,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }

        String name =
                getFileName(selectedPdf);

        int pages =
                getPageCount(selectedPdf);

        String pageText;

        if (pages > 0) {
            pageText =
                    String.valueOf(pages);
        } else {
            pageText = "Unknown";
        }

        pdfInfo.setText(
                "Selected: " +
                name +
                "\nPages: " +
                pageText);
    }

    private String getFileName(
            Uri uri) {

        Cursor cursor = null;

        try {

            cursor =
                    getContentResolver()
                            .query(
                                    uri,
                                    new String[]{
                                            "_display_name"
                                    },
                                    null,
                                    null,
                                    null);

            if (cursor != null &&
                    cursor.moveToFirst()) {

                int index =
                        cursor.getColumnIndex(
                                "_display_name");

                if (index >= 0) {

                    String name =
                            cursor.getString(index);

                    if (name != null &&
                            !name.isEmpty()) {

                        return name;
                    }
                }
            }

        } catch (Exception ignored) {

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }

        return "document.pdf";
    }

    private int getPageCount(
            Uri uri) {

        ParcelFileDescriptor descriptor =
                null;

        android.graphics.pdf.PdfRenderer renderer =
                null;

        try {

            descriptor =
                    getContentResolver()
                            .openFileDescriptor(
                                    uri,
                                    "r");

            if (descriptor == null) {
                return -1;
            }

            renderer =
                    new android.graphics.pdf.PdfRenderer(
                            descriptor);

            return renderer.getPageCount();

        } catch (Exception e) {

            return -1;

        } finally {

            if (renderer != null) {

                try {
                    renderer.close();
                } catch (Exception ignored) {
                }
            }

            if (descriptor != null) {

                try {
                    descriptor.close();
                } catch (Exception ignored) {
                }
            }
        }
    }    private void printPDF() {

        if (selectedPdf == null) {

            Toast.makeText(
                    this,
                    "Select a PDF first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        PrintManager printManager =
                (PrintManager)
                        getSystemService(
                                PRINT_SERVICE
                        );

        if (printManager == null) {

            Toast.makeText(
                    this,
                    "Printing service unavailable",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        PrintAttributes attributes =
                new PrintAttributes.Builder()
                        .setMediaSize(
                                PrintAttributes.MediaSize.ISO_A4
                        )
                        .setColorMode(
                                PrintAttributes.COLOR_MODE_COLOR
                        )
                        .build();

        String name =
                getFileName(selectedPdf);

        printManager.print(
                "Printora - " + name,

                new PDFPrintAdapter(
                        this,
                        selectedPdf,
                        name
                ),

                attributes
        );
    }

    private void detectUSB() {

        if (deviceList == null) {
            return;
        }

        deviceList.removeAllViews();

        if (usbManager == null) {

            deviceList.addView(
                    makeText(
                            "USB service unavailable.",
                            16
                    )
            );

            return;
        }

        HashMap<String, UsbDevice> devices =
                usbManager.getDeviceList();

        if (devices.isEmpty()) {

            TextView empty =
                    makeText(
                            "No USB device detected.\n\n" +
                            "Connect the Epson L3110 " +
                            "using a USB-OTG adapter.",
                            16
                    );

            deviceList.addView(empty);

            return;
        }

        for (UsbDevice device :
                devices.values()) {

            addUSBDevice(device);
        }
    }

    private void addUSBDevice(
            UsbDevice device) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                10,
                10,
                10,
                10
        );

        card.setBackgroundColor(
                0xFF19191D
        );

        String information =
                "USB device\n" +
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
                "\nClass: " +
                device.getDeviceClass() +
                "\nInterfaces: " +
                device.getInterfaceCount();

        card.addView(
                makeText(
                        information,
                        16
                )
        );

        boolean ready =
                usbManager.hasPermission(
                        device
                );

        Button permission =
                makeButton(
                        ready
                                ? "USB READY"
                                : "REQUEST USB PERMISSION"
                );

        permission.setOnClickListener(
                v ->
                        requestUSBPermission(
                                device
                        )
        );

        card.addView(permission);

        deviceList.addView(
                card,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );
    }

    private void requestUSBPermission(
            UsbDevice device) {

        if (usbManager.hasPermission(
                device)) {

            Toast.makeText(
                    this,
                    "USB device permission already granted",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Intent intent =
                new Intent(
                        USB_PERMISSION
                );

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
    }    @Override
    protected void onDestroy() {

        try {

            unregisterReceiver(
                    usbReceiver
            );

        } catch (Exception ignored) {
        }

        super.onDestroy();
    }

    private static class PDFPrintAdapter
            extends PrintDocumentAdapter {

        private final Context context;
        private final Uri pdfUri;
        private final String fileName;

        PDFPrintAdapter(
                Context context,
                Uri pdfUri,
                String fileName) {

            this.context = context;
            this.pdfUri = pdfUri;
            this.fileName = fileName;
        }

        @Override
        public void onLayout(
                PrintAttributes oldAttributes,
                PrintAttributes newAttributes,
                CancellationSignal cancellationSignal,
                LayoutResultCallback callback,
                Bundle extras) {

            if (cancellationSignal.isCanceled()) {

                callback.onLayoutCancelled();
                return;
            }

            int pages = getPages();

            PrintDocumentInfo info =
                    new PrintDocumentInfo.Builder(
                            fileName
                    )
                    .setContentType(
                            PrintDocumentInfo.CONTENT_TYPE_DOCUMENT
                    )
                    .setPageCount(
                            pages > 0
                                    ? pages
                                    : PrintDocumentInfo.PAGE_COUNT_UNKNOWN
                    )
                    .build();

            callback.onLayoutFinished(
                    info,
                    true
            );
        }

        @Override
        public void onWrite(
                PageRange[] pages,
                ParcelFileDescriptor destination,
                CancellationSignal cancellationSignal,
                WriteResultCallback callback) {

            InputStream input = null;
            FileOutputStream output = null;

            try {

                input =
                        context
                                .getContentResolver()
                                .openInputStream(
                                        pdfUri
                                );

                if (input == null) {
                    throw new Exception(
                            "Unable to open PDF"
                    );
                }

                output =
                        new FileOutputStream(
                                destination
                                        .getFileDescriptor()
                        );

                byte[] buffer =
                        new byte[8192];

                int count;

                while ((count =
                        input.read(buffer)) != -1) {

                    if (cancellationSignal
                            .isCanceled()) {

                        callback.onWriteCancelled();
                        return;
                    }

                    output.write(
                            buffer,
                            0,
                            count
                    );
                }

                output.flush();

                callback.onWriteFinished(
                        new PageRange[]{
                                PageRange.ALL_PAGES
                        }
                );

            } catch (Exception e) {

                if (cancellationSignal
                        .isCanceled()) {

                    callback.onWriteCancelled();

                } else {

                    String message =
                            e.getMessage();

                    if (message == null ||
                            message.isEmpty()) {

                        message =
                                "Unable to prepare PDF";
                    }

                    callback.onWriteFailed(
                            message
                    );
                }

            } finally {

                if (input != null) {

                    try {
                        input.close();
                    } catch (Exception ignored) {
                    }
                }

                if (output != null) {

                    try {
                        output.flush();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        private int getPages() {

            ParcelFileDescriptor descriptor =
                    null;

            android.graphics.pdf.PdfRenderer renderer =
                    null;

            try {

                descriptor =
                        context
                                .getContentResolver()
                                .openFileDescriptor(
                                        pdfUri,
                                        "r"
                                );

                if (descriptor == null) {
                    return -1;
                }

                renderer =
                        new android.graphics.pdf.PdfRenderer(
                                descriptor
                        );

                return renderer.getPageCount();

            } catch (Exception e) {

                return -1;

            } finally {

                if (renderer != null) {

                    try {
                        renderer.close();
                    } catch (Exception ignored) {
                    }
                }

                if (descriptor != null) {

                    try {
                        descriptor.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}