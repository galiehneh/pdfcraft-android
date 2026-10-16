package com.galiehneh.pdfcraft;

import android.app.Activity;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** JS bridge exposed to the web bundle as {@code window.PdfCraftAndroid}. */
class AndroidBridge {
    private static final String TAG = "PdfCraft";
    static final String OUT_DIR = "out";
    static final int MAX_CHUNK_BASE64_CHARS = 4 * 1024 * 1024;
    static final long MAX_SESSION_BYTES = 1L << 30;
    static final int MAX_SESSIONS = 4;
    private static final int MAX_RETAINED = 5;
    private static final long RETAIN_MS = 60 * 60 * 1000L;

    /** Finished output dirs kept for open/share; static so a recreated bridge does not lose them. */
    private static final LinkedList<Object[]> RETAINED = new LinkedList<>();

    private static synchronized void retain(String dirName) {
        RETAINED.addFirst(new Object[]{dirName, System.currentTimeMillis()});
    }

    /** Drops expired / surplus entries and returns the names still retained. */
    private static synchronized List<String> prunedRetained() {
        long now = System.currentTimeMillis();
        List<String> keep = new ArrayList<>();
        Iterator<Object[]> it = RETAINED.iterator();
        while (it.hasNext()) {
            Object[] e = it.next();
            if (keep.size() >= MAX_RETAINED || now - (Long) e[1] > RETAIN_MS) {
                it.remove();
            } else {
                keep.add((String) e[0]);
            }
        }
        return keep;
    }

    /** A fully written output file waiting for / past its final destination. */
    static final class SavedFile {
        final File file;
        final String name;
        final String mime;

        SavedFile(File file, String name, String mime) {
            this.file = file;
            this.name = name;
            this.mime = mime;
        }
    }

    /** Callbacks may arrive on any thread. */
    interface SaveListener {
        /** File is in Downloads; {@code finalName} is the actual stored name. */
        void onSaved(SavedFile file, String finalName);

        /** API 24-28: the user must pick a destination (SAF). */
        void onNeedsDestination(SavedFile file);

        void onFailed(String name, String message);
    }

    private static final class Session {
        final File dir;
        final File file;
        final String name;
        final String mime;
        final FileOutputStream out;
        long written;

        Session(File dir, File file, String name, String mime) throws IOException {
            this.dir = dir;
            this.file = file;
            this.name = name;
            this.mime = mime;
            this.out = new FileOutputStream(file);
        }
    }

    private final Activity activity;
    private final WebView webView;
    private final FileSaver fileSaver;
    private final Consumer<Boolean> onDocumentOpened;
    private final SaveListener saveListener;
    private final Runnable onAppReady;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    AndroidBridge(Activity activity, WebView webView, FileSaver fileSaver,
                  Consumer<Boolean> onDocumentOpened, SaveListener saveListener,
                  Runnable onAppReady) {
        this.activity = activity;
        this.webView = webView;
        this.fileSaver = fileSaver;
        this.onDocumentOpened = onDocumentOpened;
        this.saveListener = saveListener;
        this.onAppReady = onAppReady;
    }

    /** Called by the shim once the wasm app has initialised and painted its first frame. */
    @JavascriptInterface
    public void appReady() {
        activity.runOnUiThread(onAppReady);
    }

    @JavascriptInterface
    public void showKeyboard() {
        activity.runOnUiThread(() -> {
            webView.requestFocus();
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
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

    /** Starts a chunked save. Returns a session id, or "" on failure (failure is already reported). */
    @JavascriptInterface
    public String beginSave(String filename, String mime) {
        String[] safe = Mime.sanitizeForSave(mime, filename);
        String resolvedMime = safe[0];
        String name = safe[1];
        if (sessions.size() >= MAX_SESSIONS) {
            saveListener.onFailed(name, activity.getString(R.string.save_too_many));
            return "";
        }
        try {
            cleanupOldOutputs();
            String id = UUID.randomUUID().toString();
            File dir = new File(new File(activity.getCacheDir(), OUT_DIR), id);
            if (!dir.mkdirs()) throw new IOException("Cannot create " + dir);
            sessions.put(id, new Session(dir, new File(dir, name), name, resolvedMime));
            return id;
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "beginSave failed", e);
            saveListener.onFailed(name, activity.getString(R.string.save_prepare_failed));
            return "";
        }
    }

    @JavascriptInterface
    public boolean appendChunk(String id, String base64) {
        Session s = id == null ? null : sessions.get(id);
        if (s == null || base64 == null) return false;
        if (base64.length() > MAX_CHUNK_BASE64_CHARS) {
            fail(id, activity.getString(R.string.save_limit_exceeded));
            return false;
        }
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            if (s.written + data.length > MAX_SESSION_BYTES) {
                fail(id, activity.getString(R.string.save_limit_exceeded));
                return false;
            }
            s.out.write(data);
            s.written += data.length;
            return true;
        } catch (IOException | IllegalArgumentException e) {
            Log.e(TAG, "appendChunk failed", e);
            fail(id, activity.getString(R.string.save_write_failed));
            return false;
        }
    }

    @JavascriptInterface
    public void finishSave(String id) {
        Session s = id == null ? null : sessions.get(id);
        if (s == null) return;
        retain(s.dir.getName()); // before removal so cleanup never sees it as orphaned
        if (sessions.remove(id) == null) return;
        try {
            s.out.close();
        } catch (IOException e) {
            Log.e(TAG, "finishSave close failed", e);
            deleteRecursive(s.dir);
            saveListener.onFailed(s.name, activity.getString(R.string.save_finish_failed));
            return;
        }
        SavedFile saved = new SavedFile(s.file, s.name, s.mime);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            saveListener.onNeedsDestination(saved);
            return;
        }
        try {
            String finalName = fileSaver.saveToDownloads(s.file, s.name, s.mime);
            saveListener.onSaved(saved, finalName);
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "saveToDownloads failed", e);
            saveListener.onFailed(s.name, activity.getString(R.string.save_downloads_failed));
        }
    }

    @JavascriptInterface
    public void abortSave(String id) {
        Session s = id == null ? null : sessions.remove(id);
        if (s == null) return;
        closeQuietly(s);
        deleteRecursive(s.dir);
    }

    /** Aborts every in-progress save and removes its directory. */
    void close() {
        for (String id : new ArrayList<>(sessions.keySet())) {
            Session s = sessions.remove(id);
            if (s == null) continue;
            closeQuietly(s);
            deleteRecursive(s.dir);
        }
    }

    private void fail(String id, String message) {
        Session s = sessions.remove(id);
        if (s == null) return;
        closeQuietly(s);
        deleteRecursive(s.dir);
        saveListener.onFailed(s.name, message);
    }

    private static void closeQuietly(Session s) {
        try {
            s.out.close();
        } catch (IOException ignored) {
            // already failing
        }
    }

    /** Removes output dirs that are neither in progress nor among the recently finished ones (still shared/opened). */
    private void cleanupOldOutputs() {
        File[] dirs = new File(activity.getCacheDir(), OUT_DIR).listFiles();
        if (dirs == null) return;
        List<String> retained = prunedRetained();
        for (File dir : dirs) {
            if (!sessions.containsKey(dir.getName()) && !retained.contains(dir.getName())) {
                deleteRecursive(dir);
            }
        }
    }

    private static void deleteRecursive(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursive(c);
        }
        if (!f.delete() && f.exists()) Log.w(TAG, "Cannot delete " + f);
    }
}
