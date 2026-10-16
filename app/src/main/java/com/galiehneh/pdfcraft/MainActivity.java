package com.galiehneh.pdfcraft;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.activity.EdgeToEdge;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
    private static final long OVERLAY_TIMEOUT_MS = 15000;
    private static final String PDF_MIME = "application/pdf";

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
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideOverlay = () -> setOverlayVisible(false);
    private View loadingOverlay;
    private TextView loadingText;
    private final ActivityResultLauncher<Intent> chooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> onChooserResult(r.getResultCode(), r.getData()));
    private final ActivityResultLauncher<Intent> directPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                Uri uri = r.getResultCode() == RESULT_OK && r.getData() != null ? r.getData().getData() : null;
                if (uri != null) handlePdfUri(uri);
            });
    private final ActivityResultLauncher<Intent> saveAsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> onSaveAsResult(r.getResultCode(), r.getData()));
    private AndroidBridge.SavedFile pendingSaveAs;
    private boolean pendingSaveAsInitial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

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

        loadingOverlay = createLoadingOverlay();
        rootLayout.addView(loadingOverlay);

        applyThemeColors();
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
            importer.cleanup(null);
            Intent intent = getIntent();
            if (intent != null && FileImporter.isIncomingAction(intent)) {
                setOverlayVisible(true);
                handleIncomingIntent(intent);
            } else {
                loadUrlWithOverlay(BASE_URL);
            }
        } else {
            setOverlayVisible(true);
            webView.restoreState(savedInstanceState);
        }
    }

    private View createLoadingOverlay() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setClickable(true); // swallow touches while covering the WebView
        box.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            box.setElevation(dpToPx(16)); // above the FAB
        }
        box.addView(new ProgressBar(this));
        loadingText = new TextView(this);
        loadingText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        loadingText.setPadding(0, dpToPx(12), 0, 0);
        box.addView(loadingText);
        box.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return box;
    }

    /** Shows or hides the overlay; while shown it self-hides after a safety timeout. */
    private void setOverlayVisible(boolean visible) {
        mainHandler.removeCallbacks(hideOverlay);
        if (loadingOverlay == null) return;
        loadingOverlay.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) mainHandler.postDelayed(hideOverlay, OVERLAY_TIMEOUT_MS);
    }

    private void loadUrlWithOverlay(String url) {
        loadingText.setText("");
        setOverlayVisible(true);
        webView.loadUrl(url);
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
        mainHandler.removeCallbacks(hideOverlay);
        saveExecutor.shutdown();
        importExecutor.shutdown();
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
        fab.setText(R.string.open_pdf);
        fab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        fab.setTypeface(null, android.graphics.Typeface.BOLD);

        int padH = dpToPx(20);
        int padV = dpToPx(14);
        fab.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
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
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /** Applies theme-dependent colors; also called on uiMode changes since the activity is not recreated. */
    private void applyThemeColors() {
        getWindow().setBackgroundDrawable(new ColorDrawable(getColor(R.color.window_background)));
        if (loadingOverlay != null) loadingOverlay.setBackgroundColor(getColor(R.color.window_background));
        if (loadingText != null) loadingText.setTextColor(getColor(R.color.overlay_text));
        if (fab != null) {
            fab.setTextColor(getColor(R.color.fab_text));
            ((GradientDrawable) fab.getBackground()).setColor(getColor(R.color.fab_background));
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        EdgeToEdge.enable(this);
        applyThemeColors();
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
                new AndroidBridge(this, webView, fileSaver, this::onDocumentOpened, saveListener,
                        () -> setOverlayVisible(false)),
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
                String[] accept = params != null ? params.getAcceptTypes() : null;
                boolean multiple = params != null && params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
                if (!launchPicker(chooserLauncher, accept, multiple)) {
                    filePathCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });
    }

    /** Launches OPEN_DOCUMENT, falling back to GET_CONTENT. Returns false when no picker exists. */
    private boolean launchPicker(ActivityResultLauncher<Intent> launcher, String[] accept, boolean multiple) {
        try {
            launcher.launch(FileImporter.pickIntent(Intent.ACTION_OPEN_DOCUMENT, accept, multiple));
            return true;
        } catch (ActivityNotFoundException e) {
            try {
                launcher.launch(Intent.createChooser(
                        FileImporter.pickIntent(Intent.ACTION_GET_CONTENT, accept, multiple), getString(R.string.choose_file)));
                return true;
            } catch (ActivityNotFoundException ex) {
                Log.e(TAG, "Cannot launch file picker", ex);
                return false;
            }
        }
    }

    private void onChooserResult(int resultCode, Intent data) {
        ValueCallback<Uri[]> callback = filePathCallback;
        filePathCallback = null;
        if (callback == null) return;
        callback.onReceiveValue(resultCode == RESULT_OK ? FileImporter.urisFromChooserResult(data) : null);
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

    /** Serves an imported document for /doc/<id>/<name>. */
    private WebResourceResponse handleDocument(String path) {
        String id = FileImporter.docIdFromPath(path);
        File cachedFile = id == null ? null : importer.docFile(id);
        if (cachedFile != null && cachedFile.exists()) {
            try {
                return new WebResourceResponse(PDF_MIME, "UTF-8", 200, "OK",
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
        if (!launchPicker(directPickerLauncher, new String[]{PDF_MIME}, false)) {
            Toast.makeText(this, getString(R.string.no_picker_app), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleIncomingIntent(Intent intent) {
        Uri targetUri = FileImporter.uriFromIntent(intent);
        if (targetUri != null) {
            handlePdfUri(targetUri);
        }
    }

    /** Copies the document off the UI thread behind the overlay, then loads it in the viewer. */
    private void handlePdfUri(Uri uri) {
        loadingText.setText(getString(R.string.opening_pdf));
        setOverlayVisible(true);
        importExecutor.execute(() -> {
            try {
                String name = importer.displayName(uri);
                String id = importer.copyToCache(uri);
                runOnUiThread(() -> onImported(id, name));
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Error handling PDF URI: " + uri, e);
                runOnUiThread(() -> {
                    setOverlayVisible(false);
                    if (webView != null && webView.getUrl() == null) loadUrlWithOverlay(BASE_URL);
                    Toast.makeText(this, getString(R.string.open_pdf_failed), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void onImported(String id, String name) {
        if (isFinishing() || isDestroyed() || webView == null) return;
        currentDocumentName = name;
        webView.loadUrl(BASE_URL + "?file=/doc/" + id + "/" + Uri.encode(name) + "&t=" + System.currentTimeMillis());
        if (fab != null) {
            fab.setVisibility(View.GONE);
        }
        importExecutor.execute(() -> importer.cleanup(id));
        getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        Toast.makeText(this, getString(R.string.opening_pdf_name, name), Toast.LENGTH_SHORT).show();
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
                .setTitle(getString(R.string.saved_title))
                .setMessage(getString(R.string.saved_message, name)
                        + (inDownloads ? "\n" + getString(R.string.saved_location_downloads) : ""))
                .setPositiveButton(getString(R.string.action_open), (d, w) -> startSaveAction(SaveActions.view(this, file.file, file.mime)))
                .setNeutralButton(getString(R.string.action_share), (d, w) -> startSaveAction(SaveActions.share(this, file.file, file.mime)))
                .setNegativeButton(getString(R.string.action_save_as), (d, w) -> launchSaveAs(file, false))
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
            Toast.makeText(this, getString(R.string.no_handler_app), Toast.LENGTH_LONG).show();
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
            saveAsLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            pendingSaveAs = null;
            onSaveFailed(file.name, getString(R.string.no_save_location_app));
        }
    }

    private void onSaveAsResult(int resultCode, Intent data) {
        AndroidBridge.SavedFile file = pendingSaveAs;
        boolean initial = pendingSaveAsInitial;
        pendingSaveAs = null;
        if (file == null) return;
        Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (uri == null) {
            if (initial) onSaveFailed(file.name, getString(R.string.save_cancelled));
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
                        Toast.makeText(this, getString(R.string.saved_message, name), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Save-as failed", e);
                runOnUiThread(() -> onSaveFailed(file.name, getString(R.string.save_failed)));
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
        loadUrlWithOverlay(BASE_URL);
        Toast.makeText(this, getString(R.string.renderer_reloaded),
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
            exitToast = Toast.makeText(this, getString(R.string.exit_hint), Toast.LENGTH_SHORT);
            exitToast.show();
            getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    private void confirmCloseDocument() {
        if (isFinishing() || isDestroyed() || !isDocumentOpen()) return;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.close_doc_title))
                .setMessage(getString(R.string.close_doc_message))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.close), (d, w) -> {
                    fab.setVisibility(View.VISIBLE);
                    currentDocumentName = null;
                    backPressedTime = 0;
                    loadUrlWithOverlay(BASE_URL);
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
