package com.galiehneh.pdfcraft;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Pure-Java helpers: extension to MIME mapping and file-name sanitising. */
final class Mime {
    static final String DEFAULT_FILE_NAME = "document.pdf";
    private static final int MAX_NAME_LENGTH = 120;
    static final String OCTET_STREAM = "application/octet-stream";

    private static final Set<String> SAFE_MIMES = new HashSet<>(Arrays.asList(
            "application/pdf", "application/zip", "image/png", "image/jpeg", "image/webp", "image/gif",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "application/json"));
    private static final Set<String> SAFE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "zip", "png", "jpg", "jpeg", "webp", "gif", "docx", "xlsx", "pptx", "txt", "json"));

    private Mime() {}

    static boolean isOctetStream(String mime) {
        return OCTET_STREAM.equals(mime);
    }

    /** Mime and name for a JS-supplied save: anything outside the allowlists becomes octet-stream / .bin. */
    static String[] sanitizeForSave(String declaredMime, String fileName) {
        String mime = resolve(declaredMime, fileName);
        String name = finalFileName(fileName, mime);
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.US) : "";
        if (!SAFE_MIMES.contains(mime)) mime = OCTET_STREAM;
        if (!SAFE_EXTENSIONS.contains(ext)) {
            mime = OCTET_STREAM;
            name = (dot > 0 ? name.substring(0, dot) : name) + ".bin";
        }
        return new String[]{mime, name};
    }

    static String fromFileName(String path) {
        if (path == null) return OCTET_STREAM;
        String p = path.toLowerCase(Locale.US);
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "text/javascript";
        if (p.endsWith(".wasm")) return "application/wasm";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".txt")) return "text/plain";
        if (p.endsWith(".pdf")) return "application/pdf";
        if (p.endsWith(".zip")) return "application/zip";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (p.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (p.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        return OCTET_STREAM;
    }

    /** Prefers the blob's own type; falls back to the file extension, then PDF. */
    static String resolve(String declaredMime, String fileName) {
        if (declaredMime != null) {
            String m = declaredMime.split(";", 2)[0].trim().toLowerCase(Locale.US);
            if (!m.isEmpty() && !m.equals(OCTET_STREAM) && m.contains("/")) return m;
        }
        String byName = fromFileName(fileName);
        if (!byName.equals(OCTET_STREAM)) return byName;
        return fileName != null && fileName.lastIndexOf('.') > 0 ? OCTET_STREAM : "application/pdf";
    }

    /**
     * Maps WebView accept entries (".pdf", "application/pdf", "image/*") to MIME types for a picker.
     * Falls back to a single wildcard when empty or when any entry cannot be mapped.
     */
    static String[] acceptToMimeTypes(String[] accept) {
        final String[] any = {"*/*"};
        if (accept == null) return any;
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String raw : accept) {
            if (raw == null) continue;
            String t = raw.trim().toLowerCase(Locale.US);
            if (t.isEmpty()) continue;
            if (t.startsWith(".")) {
                t = fromFileName("x" + t);
                if (t.equals(OCTET_STREAM)) return any;
            } else if (!t.contains("/")) {
                return any;
            }
            if (t.equals("*/*")) return any;
            out.add(t);
        }
        return out.isEmpty() ? any : out.toArray(new String[0]);
    }

    /** Extension (without dot) for common MIME types, or null. */
    static String extensionFor(String mime) {
        if (mime == null) return null;
        switch (mime) {
            case "application/pdf": return "pdf";
            case "application/zip": return "zip";
            case "image/png": return "png";
            case "image/jpeg": return "jpg";
            case "image/webp": return "webp";
            case "image/gif": return "gif";
            case "image/svg+xml": return "svg";
            case "text/plain": return "txt";
            case "application/json": return "json";
            case "text/html": return "html";
            default: return null;
        }
    }

    /** Sanitised name; appends the extension implied by {@code mime} when the name has none. */
    static String finalFileName(String name, String mime) {
        boolean blank = name == null || name.trim().isEmpty();
        String clean = blank ? "document" : sanitizeFileName(name);
        if (clean.lastIndexOf('.') <= 0) {
            String ext = extensionFor(mime);
            if (ext != null) clean = clean + "." + ext;
        }
        return clean;
    }

    /** Strips path separators and control characters; falls back to document.pdf when empty. */
    static String sanitizeFileName(String name) {
        if (name == null) return DEFAULT_FILE_NAME;
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        while (cleaned.startsWith(".")) cleaned = cleaned.substring(1);
        if (cleaned.length() > MAX_NAME_LENGTH) cleaned = cleaned.substring(cleaned.length() - MAX_NAME_LENGTH);
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            return DEFAULT_FILE_NAME;
        }
        return cleaned;
    }
}
