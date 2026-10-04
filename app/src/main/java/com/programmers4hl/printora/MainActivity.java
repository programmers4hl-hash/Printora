package com.programmers4hl.printora;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
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
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

public class MainActivity extends Activity {

    private static final String ACTION_USB_PERMISSION =
            "com.programmers4hl.printora.USB_PERMISSION";

    private static final int PICK_PDF = 1001;

    private UsbManager usbManager;
    private LinearLayout deviceList;

    private TextView selectedPdfText;
    private Uri selectedPdfUri;

    private final BroadcastReceiver usbReceiver =
            new BroadcastReceiver() {

        @Override
        public void onReceive(
                Context context,
                Intent intent) {

            if (!ACTION_USB_PERMISSION.equals(
                    intent.getAction())) {
                return;
            }

            UsbDevice device =
                    intent.getParcelableExtra(
                            UsbManager.EXTRA_DEVICE
                    );

            boolean granted =
                    intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED,
                            false
                    );

            if (granted) {

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
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        usbManager =
                (UsbManager) getSystemService(
                        USB_SERVICE
                );

        IntentFilter filter =
                new IntentFilter(
                        ACTION_USB_PERMISSION
                );

        registerReceiver(
                usbReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
        );

        createInterface();

        detectPrinters();
    }

    private TextView makeText(
            String value,
            float size) {

        TextView view =
                new TextView(this);

        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);

        view.setPadding(
                20,
                16,
                20,
                16
        );

        return view;
    }

    private Button makeButton(
            String title) {

        Button button =
                new Button(this);

        button.setText(title);
        button.setAllCaps(false);

        return button;
    }

    private void createInterface() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                20,
                40,
                20,
                20
        );

        root.setBackgroundColor(
                Color.rgb(11, 11, 13)
        );

        TextView title =
                makeText(
                        "Printora",
                        28
                );

        root.addView(title);

        TextView subtitle =
                makeText(
                        "PDF printing • USB printer",
                        14
                );

        subtitle.setTextColor(
                Color.LTGRAY
        );

        root.addView(subtitle);

        Button selectPdf =
                makeButton(
                        "SELECT PDF"
                );

        selectPdf.setOnClickListener(
                v -> selectPdf()
        );

        root.addView(selectPdf);

        selectedPdfText =
                makeText(
                        "No PDF selected",
                        15
                );

        selectedPdfText.setTextColor(
                Color.LTGRAY
        );

        root.addView(
                selectedPdfText
        );

        Button printPdf =
                makeButton(
                        "PRINT SELECTED PDF"
                );

        printPdf.setOnClickListener(
                v -> printSelectedPdf()
        );

        root.addView(printPdf);

        Button detect =
                makeButton(
                        "DETECT USB PRINTERS"
                );

        detect.setOnClickListener(
                v -> detectPrinters()
        );

        root.addView(detect);

        deviceList =
                new LinearLayout(this);

        deviceList.setOrientation(
                LinearLayout.VERTICAL
        );

        ScrollView scroll =
                new ScrollView(this);

        scroll.addView(deviceList);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(root);
    }

    private void selectPdf() {

        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType(
                "application/pdf"
        );

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(
                intent,
                PICK_PDF
        );
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != PICK_PDF ||
                resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null) {

            return;
        }

        selectedPdfUri =
                data.getData();

        try {

            getContentResolver()
                    .takePersistableUriPermission(
                            selectedPdfUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );

        } catch (Exception ignored) {
        }

        String fileName =
                getFileName(
                        selectedPdfUri
                );

        int pages =
                getPdfPageCount(
                        selectedPdfUri
                );

        selectedPdfText.setText(
                "Selected: " +
                fileName +
                "\nPages: " +
                (pages > 0
                        ? pages
                        : "Unknown")
        );
    }

    private String getFileName(
            Uri uri) {

        Cursor cursor = null;

        try {

            cursor =
                    getContentResolver().query(
                            uri,
                            new String[]{
                                    "_display_name"
                            },
                            null,
                            null,
                            null
                    );

            if (cursor != null &&
                    cursor.moveToFirst()) {

                int index =
                        cursor.getColumnIndex(
                                "_display_name"
                        );

                if (index >= 0) {
                    return cursor.getString(index);
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

    private int getPdfPageCount(
            Uri uri) {

        ParcelFileDescriptor descriptor =
                null;

        PdfRenderer renderer =
                null;

        try {

            descriptor =
                    getContentResolver()
                            .openFileDescriptor(
                                    uri,
                                    "r"
                            );

            if (descriptor == null) {
                return -1;
            }

            renderer =
                    new PdfRenderer(
                            descriptor
                    );

            return renderer.getPageCount();

        } catch (Exception e) {

            return -1;

        } finally {

            try {

                if (renderer != null) {
                    renderer.close();
                }

            } catch (Exception ignored) {
            }

            try {

                if (descriptor != null) {
                    descriptor.close();
                }

            } catch (Exception ignored) {
            }
        }
    }

    private void printSelectedPdf() {

        if (selectedPdfUri == null) {

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

        PrintAttributes attributes =
                new PrintAttributes.Builder()
                        .setMediaSize(
                                PrintAttributes.MediaSize.ISO_A4
                        )
                        .setColorMode(
                                PrintAttributes.COLOR_MODE_COLOR
                        )
                        .setMinMargins(
                                PrintAttributes.Margins.NO_MARGINS
                        )
                        .build();

        printManager.print(
                "Printora - " +
                        getFileName(
                                selectedPdfUri
                        ),

                new PdfPrintAdapter(
                        this,
                        selectedPdfUri,
                        getFileName(
                                selectedPdfUri
                        )
                ),

                attributes
        );
    }

    private void detectPrinters() {

        if (deviceList == null) {
            return;
        }

        deviceList.removeAllViews();

        HashMap<String, UsbDevice> devices =
                usbManager.getDeviceList();

        if (devices.isEmpty()) {

            deviceList.addView(
                    makeText(
                            "No USB device detected.\n\n" +
                            "Connect the Epson printer using " +
                            "a USB-OTG adapter and tap " +
                            "Detect USB Printers.",
                            16
                    )
            );

            return;
        }

        for (UsbDevice device :
                devices.values()) {

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
                    Color.rgb(25, 25, 29)
            );

            String information =
                    "USB device\n" +
                    "VID: " +
                    String.format(
                            "%04X",
                            device.getVendorId()
                    ) +
                    "   PID: " +
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

            Button permission =
                    makeButton(
                            usbManager.hasPermission(device)
                                    ? "USB READY"
                                    : "REQUEST USB PERMISSION"
                    );

            permission.setOnClickListener(
                    v ->
                            requestUsbPermission(
                                    device
                            )
            );

            card.addView(permission);

            Button refresh =
                    makeButton(
                            "REFRESH"
                    );

            refresh.setOnClickListener(
                    v -> detectPrinters()
            );

            card.addView(refresh);

            deviceList.addView(
                    card,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    )
            );
        }
    }

    private void requestUsbPermission(
            UsbDevice device) {

        if (usbManager.hasPermission(device)) {

            Toast.makeText(
                    this,
                    "USB device permission already granted",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Intent intent =
                new Intent(
                        ACTION_USB_PERMISSION
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
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        try {

            unregisterReceiver(
                    usbReceiver
            );

        } catch (Exception ignored) {
        }
    }

    private static class PdfPrintAdapter
            extends PrintDocumentAdapter {

        private final Context context;
        private final Uri pdfUri;
        private final String fileName;

        private int pageCount;

        PdfPrintAdapter(
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

            pageCount =
                    getPageCount();

            PrintDocumentInfo info =
                    new PrintDocumentInfo.Builder(
                            fileName
                    )
                    .setContentType(
                            PrintDocumentInfo.CONTENT_TYPE_DOCUMENT
                    )
                    .setPageCount(
                            pageCount > 0
                                    ? pageCount
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

            PdfDocument output = null;

            try {

                output =
                        createPrintablePdf(
                                pages,
                                cancellationSignal
                        );

                if (cancellationSignal.isCanceled()) {

                    callback.onWriteCancelled();

                    return;
                }

                FileOutputStream stream =
                        new FileOutputStream(
                                destination.getFileDescriptor()
                        );

                output.writeTo(stream);

                stream.flush();

                stream.close();

                callback.onWriteFinished(
                        new PageRange[]{
                                PageRange.ALL_PAGES
                        }
                );

            } catch (Exception e) {

                callback.onWriteFailed(
                        e.getMessage() != null
                                ? e.getMessage()
                                : "Print preparation failed"
                );

            } finally {

                if (output != null) {

                    try {
                        output.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        private int getPageCount() {

            ParcelFileDescriptor descriptor =
                    null;

            PdfRenderer renderer =
                    null;

            try {

                descriptor =
                        context.getContentResolver()
                                .openFileDescriptor(
                                        pdfUri,
                                        "r"
                                );

                if (descriptor == null) {
                    return -1;
                }

                renderer =
                        new PdfRenderer(
                                descriptor
                        );

                return renderer.getPageCount();

            } catch (Exception e) {

                return -1;

            } finally {

                try {

                    if (renderer != null) {
                        renderer.close();
                    }

                } catch (Exception ignored) {
                }

                try {

                    if (descriptor != null) {
                        descriptor.close();
                    }

                } catch (Exception ignored) {
                }
            }
        }

        private PdfDocument createPrintablePdf(
         
