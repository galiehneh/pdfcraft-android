package com.galiehneh.pdfcraft;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Incoming files: picker intents, intent parsing, and copying into the app cache. */
class FileImporter {
    static final String CACHE_FILE_NAME = "current_document.pdf";
    private final Context context;

    FileImporter(Context context) {
        this.context = context;
    }

    static Intent openDocumentIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        return intent;
    }

    static Intent getContentChooser() {
        Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
        fallback.addCategory(Intent.CATEGORY_OPENABLE);
        fallback.setType("application/pdf");
        return Intent.createChooser(fallback, "Pilih Dokumen PDF");
    }

    static boolean isIncomingAction(Intent intent) {
        String action = intent.getAction();
        return Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action);
    }

    /** Extracts the file URI from a VIEW or SEND intent, or null. */
    static Uri uriFromIntent(Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            return intent.getData();
        }
        if (Intent.ACTION_SEND.equals(action)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            }
            return (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        return null;
    }

    /** Result of a chooser pick: single data URI or all clip items; null when nothing picked. */
    static Uri[] urisFromChooserResult(Intent data) {
        if (data == null) return null;
        if (data.getData() != null) {
            return new Uri[]{data.getData()};
        }
        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            Uri[] results = new Uri[count];
            for (int i = 0; i < count; i++) {
                results[i] = data.getClipData().getItemAt(i).getUri();
            }
            return results;
        }
        return null;
    }

    /** Copies the URI content into cacheDir/current_document.pdf. */
    void copyToCache(Uri uri) throws IOException {
        File dest = new File(context.getCacheDir(), CACHE_FILE_NAME);
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(dest)) {
            if (is == null) throw new IOException("Cannot open " + uri);
            byte[] buffer = new byte[32768];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }
    }

    String displayName(Uri uri) {
        String result = Mime.DEFAULT_FILE_NAME;
        if (uri == null) return result;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        String name = cursor.getString(nameIndex);
                        if (name != null && !name.trim().isEmpty()) {
                            result = name;
                        }
                    }
                }
            } catch (Exception ignored) {}
        } else {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    result = path.substring(cut + 1);
                }
            }
        }
        return result;
    }
}
