package com.galiehneh.pdfcraft;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Writes output files into the public Downloads folder. */
class FileSaver {
    private static final String TAG = "PdfCraft";
    private final Context context;

    FileSaver(Context context) {
        this.context = context.getApplicationContext();
    }

    boolean saveToDownloads(String filename, byte[] bytes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
            if (uri == null) return false;
            try (OutputStream os = resolver.openOutputStream(uri)) {
                if (os != null) {
                    os.write(bytes);
                    return true;
                }
            } catch (IOException e) {
                Log.e(TAG, "Error writing to MediaStore", e);
            }
            return false;
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, filename);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bytes);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error writing to file", e);
            return false;
        }
    }
}
