package com.galiehneh.pdfcraft;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import org.json.JSONObject;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final String TAG = "PdfCraft";
    private static final String HOST = "appassets.androidplatform.net";
    private static final String ORIGIN = "https://" + HOST;
    private static final String BASE_URL = ORIGIN + "/index.html";
    private static final String SHIM_ASSET = "android/shim.js";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int DIRECT_PICKER_REQUEST = 1002;
    private static final int SAVE_AS_REQUEST = 1003;

    private FrameLayout rootLayout;
    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private FileImporter importer;
    private boolean shimViaScriptTag = false;
    private ValueCallback<Uri[]> filePathCallback;
    private String currentDocumentName = null;
    private TextView fab;
    private long backPressedTime = 0;
    private Toast exitToast = null;
    private FileSaver fileSaver;
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    private AndroidBridge.SavedFile pendingSaveAs;
    private boolean pendingSaveAsInitial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        applyWindowInsets(rootLayout);

        webView = createWebView();
        rootLayout.addView(webView, 0);

        // Floating Action Button "Buka PDF" (ditampilkan saat belum ada dokumen)
        fab = createFabButton();
        rootLayout.addView(fab);

        setContentView(rootLayout);

        importer = new FileImporter(this);
        fileSaver = new FileSaver(this);
        configureWebView();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBack();
            }
        });

        if (savedInstanceState == null) {
            Intent intent = getIntent();
            if (intent != null && FileImporter.isIncomingAction(intent)) {
                handleIncomingIntent(intent);
            } else {
                webView.loadUrl(BASE_URL);
            }
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private WebView createWebView() {
        WebView view = new WebView(this);
        view.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return view;
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        destroyWebView(webView);
        webView = null;
        saveExecutor.shutdown();
        super.onDestroy();
    }

    private void destroyWebView(WebView view) {
        if (view == null) return;
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) parent.removeView(view);
        view.stopLoading();
        view.destroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && FileImporter.isIncomingAction(intent)) {
            handleIncomingIntent(intent);
        }
    }

    private TextView createFabButton() {
        TextView fab = new TextView(this);
        fab.setText("Buka PDF");
        fab.setTextColor(Color.WHITE);
        fab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        fab.setTypeface(null, android.graphics.Typeface.BOLD);

        int padH = dpToPx(20);
        int padV = dpToPx(14);
        fab.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0A7563); // ArtCraft teal
        bg.setCornerRadius(dpToPx(28));
        fab.setBackground(bg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            fab.setElevation(dpToPx(8));
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.bottomMargin = dpToPx(24);
        params.rightMargin = dpToPx(20);
        fab.setLayoutParams(params);

        fab.setClickable(true);
        fab.setFocusable(true);
        fab.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            openDirectPdfPicker();
        });
        return fab;
    }

    private void applyWindowInsets(View view) {
        final int navHeight = getNavigationBarHeight();
        view.setPadding(0, 0, 0, navHeight);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.navigationBars() | android.view.WindowInsets.Type.displayCutout()
                );
                int bottom = bars.bottom > 0 ? bars.bottom : navHeight;
                v.setPadding(bars.left, 0, bars.right, bottom);
                return insets;
            });
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                int left = insets.getSystemWindowInsetLeft();
                int right = insets.getSystemWindowInsetRight();
                int bottom = insets.getSystemWindowInsetBottom();
                v.setPadding(left, 0, right, bottom > 0 ? bottom : navHeight);
                return insets;
            });
        }
    }

    private int getNavigationBarHeight() {
        int resId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resId > 0) {
            return getResources().getDimensionPixelSize(resId);
        }
        return dpToPx(48);
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()
        ));
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus(View.FOCUS_DOWN);

        webView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_UP:
                        if (!v.hasFocus()) {
                            v.requestFocus();
                        }
                        break;
                }
                return false;
            }
        });

        webView.addJavascriptInterface(
                new AndroidBridge(this, webView, fileSaver, this::onDocumentOpened, saveListener),
                "PdfCraftAndroid");

        assetLoader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .setHttpAllowed(false)
                .addPathHandler("/doc/", this::handleDocument)
                .addPathHandler("/android/", path -> openAsset("android/" + path))
                .addPathHandler("/", path -> openAsset("web/" + (path.isEmpty() ? "index.html" : path)))
                .build();

        installShim();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return null;
                Uri uri = request.getUrl();
                if (shimViaScriptTag && isIndexRequest(uri)) {
                    WebResourceResponse patched = indexWithShimTag();
                    if (patched != null) return patched;
                }
                return assetLoader.shouldInterceptRequest(uri);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.e(TAG, "Renderer process gone");
                if (view != webView) return true;
                recoverFromRendererLoss();
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                Intent intent = FileImporter.openDocumentIntent();
                if (params != null && params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }

                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (ActivityNotFoundException e) {
                    try {
                        startActivityForResult(FileImporter.getContentChooser(), FILE_CHOOSER_REQUEST);
                    } catch (Exception ex) {
                        Log.e(TAG, "Cannot launch file chooser", ex);
                        if (filePathCallback != null) {
                            filePathCallback.onReceiveValue(null);
                            filePathCallback = null;
                        }
                        return false;
                    }
                }
                return true;
            }
        });
    }

    /** Injects the native-owned shim at document start, restricted to the app origin. */
    private void installShim() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(
                        webView, readAssetText(SHIM_ASSET), Collections.singleton(ORIGIN));
                return;
            } catch (IOException e) {
                Log.e(TAG, "Cannot read shim asset", e);
            }
        }
        shimViaScriptTag = true;
    }

    private String readAssetText(String assetPath) throws IOException {
        try (InputStream is = getAssets().open(assetPath)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    private static boolean isIndexRequest(Uri uri) {
        if (!HOST.equalsIgnoreCase(uri.getHost())) return false;
        String path = uri.getPath();
        return path == null || path.isEmpty() || "/".equals(path) || "/index.html".equals(path);
    }

    /** Fallback when DOCUMENT_START_SCRIPT is unsupported: serve index.html with a shim script tag. */
    private WebResourceResponse indexWithShimTag() {
        try {
            String html = readAssetText("web/index.html")
                    .replaceFirst("<head>", "<head><script src=\"/android/shim.js\"></script>");
            return new WebResourceResponse("text/html", "UTF-8", 200, "OK", noCacheHeaders(),
                    new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            Log.e(TAG, "Cannot patch index.html", e);
            return null;
        }
    }

    private WebResourceResponse openAsset(String assetPath) {
        try {
            return new WebResourceResponse(Mime.fromFileName(assetPath), "UTF-8", 200, "OK",
                    noCacheHeaders(), getAssets().open(assetPath));
        } catch (IOException e) {
            Log.w(TAG, "Asset not found: " + assetPath);
            return notFound();
        }
    }

    /** Serves the imported document for any /doc/* request. */
    private WebResourceResponse handleDocument(String path) {
        File cachedFile = new File(getCacheDir(), FileImporter.CACHE_FILE_NAME);
        if (cachedFile.exists()) {
            try {
                return new WebResourceResponse("application/pdf", "UTF-8", 200, "OK",
                        noCacheHeaders(), new FileInputStream(cachedFile));
            } catch (IOException e) {
                Log.e(TAG, "Cannot stream cached PDF", e);
            }
        }
        return notFound();
    }

    private static Map<String, String> noCacheHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-cache");
        return headers;
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, null);
    }

    private void onDocumentOpened(boolean opened) {
        if (fab != null) {
            fab.setVisibility(opened ? View.GONE : View.VISIBLE);
        }
    }

    private void openDirectPdfPicker() {
        try {
            startActivityForResult(FileImporter.openDocumentIntent(), DIRECT_PICKER_REQUEST);
        } catch (ActivityNotFoundException e) {
            try {
                startActivityForResult(FileImporter.getContentChooser(), DIRECT_PICKER_REQUEST);
            } catch (Exception ex) {
                Toast.makeText(this, "Tidak ada aplikasi pemilih berkas", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleIncomingIntent(Intent intent) {
        Uri targetUri = FileImporter.uriFromIntent(intent);
        if (targetUri != null) {
            handlePdfUri(targetUri);
        }
    }

    private void handlePdfUri(Uri uri) {
        try {
            String name = importer.displayName(uri);
            this.currentDocumentName = name;
            importer.copyToCache(uri);

            String encodedName = Uri.encode(name);
            String url = BASE_URL + "?file=/doc/" + encodedName + "&t=" + System.currentTimeMillis();
            if (webView != null) {
                webView.loadUrl(url);
                if (fab != null) {
                    fab.setVisibility(View.GONE);
                }
                getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                Toast.makeText(this, "Membuka PDF: " + name, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling PDF URI: " + uri, e);
            Toast.makeText(this, "Gagal memproses berkas PDF", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    if (fab != null) {
                        fab.setVisibility(View.GONE);
                    }
                    results = FileImporter.urisFromChooserResult(data);
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        } else if (requestCode == SAVE_AS_REQUEST) {
            onSaveAsResult(resultCode, data);
        } else if (requestCode == DIRECT_PICKER_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    handlePdfUri(uri);
                }
            }
        }
    }

    // ---- Output files (save / open / share) ----

    private final AndroidBridge.SaveListener saveListener = new AndroidBridge.SaveListener() {
        @Override
        public void onSaved(AndroidBridge.SavedFile file, String finalName) {
            runOnUiThread(() -> onSaveSucceeded(file, finalName, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q));
        }

        @Override
        public void onNeedsDestination(AndroidBridge.SavedFile file) {
            runOnUiThread(() -> launchSaveAs(file, true));
        }

        @Override
        public void onFailed(String name, String message) {
            runOnUiThread(() -> onSaveFailed(name, message));
        }
    };

    private void onSaveSucceeded(AndroidBridge.SavedFile file, String name, boolean inDownloads) {
        reportSaveResult(true, name, "");
        if (isFinishing() || isDestroyed()) return;
        getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        new AlertDialog.Builder(this)
                .setTitle("Tersimpan")
                .setMessage("Tersimpan: " + name + (inDownloads ? "\nLokasi: folder Download" : ""))
                .setPositiveButton("Buka", (d, w) -> startSaveAction(SaveActions.view(this, file.file, file.mime)))
                .setNeutralButton("Bagikan", (d, w) -> startSaveAction(SaveActions.share(this, file.file, file.mime)))
                .setNegativeButton("Simpan sebagai\u2026", (d, w) -> launchSaveAs(file, false))
                .show();
    }

    private void onSaveFailed(String name, String message) {
        reportSaveResult(false, name, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void startSaveAction(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "No handler for saved file action", e);
            Toast.makeText(this, "Tidak ada aplikasi yang dapat menangani berkas ini", Toast.LENGTH_LONG).show();
        }
    }

    private void launchSaveAs(AndroidBridge.SavedFile file, boolean initial) {
        pendingSaveAs = file;
        pendingSaveAsInitial = initial;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(file.mime)
                .putExtra(Intent.EXTRA_TITLE, file.name);
        try {
            startActivityForResult(intent, SAVE_AS_REQUEST);
        } catch (ActivityNotFoundException e) {
            pendingSaveAs = null;
            onSaveFailed(file.name, "Tidak ada aplikasi untuk memilih lokasi simpan");
        }
    }

    private void onSaveAsResult(int resultCode, Intent data) {
        AndroidBridge.SavedFile file = pendingSaveAs;
        boolean initial = pendingSaveAsInitial;
        pendingSaveAs = null;
        if (file == null) return;
        Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (uri == null) {
            if (initial) onSaveFailed(file.name, "Penyimpanan dibatalkan");
            return;
        }
        saveExecutor.execute(() -> {
            try {
                fileSaver.saveToUri(file.file, uri);
                String shown = fileSaver.displayName(uri);
                String name = shown != null ? shown : file.name;
                runOnUiThread(() -> {
                    if (initial) {
                        onSaveSucceeded(file, name, false);
                    } else {
                        Toast.makeText(this, "Tersimpan: " + name, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Save-as failed", e);
                runOnUiThread(() -> onSaveFailed(file.name, "Gagal menyimpan berkas"));
            }
        });
    }

    /** Tells the web side how the save ended: window.onSaveResult(ok, name, message). */
    private void reportSaveResult(boolean ok, String name, String message) {
        if (webView == null) return;
        webView.evaluateJavascript("(window.onSaveResult && window.onSaveResult(" + ok + ","
                + JSONObject.quote(name == null ? "" : name) + ","
                + JSONObject.quote(message == null ? "" : message) + "))", null);
    }

    private void recoverFromRendererLoss() {
        destroyWebView(webView);
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        webView = createWebView();
        rootLayout.addView(webView, 0);
        configureWebView();
        currentDocumentName = null;
        fab.setVisibility(View.VISIBLE);
        webView.loadUrl(BASE_URL);
        Toast.makeText(this, "Tampilan dimuat ulang karena kehabisan memori. Dokumen yang belum disimpan hilang.",
                Toast.LENGTH_LONG).show();
    }

    private boolean isDocumentOpen() {
        return fab != null && fab.getVisibility() == View.GONE;
    }

    private void onBack() {
        if (isDocumentOpen() && webView != null) {
            // Escape closes dialogs/modes first; only confirm when nothing consumed it.
            webView.evaluateJavascript(
                    "(window.handleAndroidBack ? window.handleAndroidBack() : false)",
                    value -> {
                        if (!"true".equals(value)) confirmCloseDocument();
                    });
            return;
        }

        // Di Beranda: user harus tekan 2x dalam 2 detik untuk keluar
        long currentTime = System.currentTimeMillis();
        if (currentTime - backPressedTime < 2000) {
            if (exitToast != null) {
                exitToast.cancel();
            }
            finish();
        } else {
            backPressedTime = currentTime;
            if (exitToast != null) {
                exitToast.cancel();
            }
            exitToast = Toast.makeText(this, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT);
            exitToast.show();
            getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    private void confirmCloseDocument() {
        if (isFinishing() || isDestroyed() || !isDocumentOpen()) return;
        new AlertDialog.Builder(this)
                .setTitle("Tutup dokumen?")
                .setMessage("Perubahan yang belum disimpan akan hilang.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Tutup", (d, w) -> {
                    fab.setVisibility(View.VISIBLE);
                    currentDocumentName = null;
                    backPressedTime = 0;
                    webView.loadUrl(BASE_URL);
                    webView.clearHistory();
                })
                .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }
}
