package com.galiehneh.pdfcraft;

import android.app.Activity;
import android.util.Base64;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import java.util.function.Consumer;

/** JS bridge exposed to the web bundle as {@code window.PdfCraftAndroid}. */
class AndroidBridge {
    private static final String TAG = "PdfCraft";

    private final Activity activity;
    private final WebView webView;
    private final FileSaver fileSaver;
    private final Consumer<Boolean> onDocumentOpened;

    AndroidBridge(Activity activity, WebView webView, FileSaver fileSaver, Consumer<Boolean> onDocumentOpened) {
        this.activity = activity;
        this.webView = webView;
        this.fileSaver = fileSaver;
        this.onDocumentOpened = onDocumentOpened;
    }

    @JavascriptInterface
    public void showKeyboard() {
        activity.runOnUiThread(() -> {
            webView.requestFocus();
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) {
                boolean ok = imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
                if (!ok) {
                    ok = imm.showSoftInput(webView, 0);
                }
                if (!ok) {
                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                }
            }
        });
    }

    @JavascriptInterface
    public void hideKeyboard() {
        activity.runOnUiThread(() -> {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(webView.getWindowToken(), 0);
            }
        });
    }

    @JavascriptInterface
    public void setDocumentOpened(boolean opened) {
        activity.runOnUiThread(() -> onDocumentOpened.accept(opened));
    }

    @JavascriptInterface
    public void saveBlob(String dataUrl, String filename) {
        if (dataUrl == null || !dataUrl.contains(",")) return;
        String base64Data = dataUrl.substring(dataUrl.indexOf(",") + 1);
        try {
            byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
            String safeName = Mime.sanitizeFileName(filename);
            boolean saved = fileSaver.saveToDownloads(safeName, bytes);
            activity.runOnUiThread(() -> {
                if (saved) {
                    activity.getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    Toast.makeText(activity, "Berhasil disimpan ke Download: " + safeName, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(activity, "Gagal menyimpan berkas", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed saving blob", e);
        }
    }
}
