package com.galiehneh.pdfcraft;

import java.util.Locale;

/** Pure-Java helpers: extension to MIME mapping and file-name sanitising. */
final class Mime {
    static final String DEFAULT_FILE_NAME = "document.pdf";
    private static final String OCTET_STREAM = "application/octet-stream";

    private Mime() {}

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
        return OCTET_STREAM;
    }

    /** Strips path separators and control characters; falls back to document.pdf when empty. */
    static String sanitizeFileName(String name) {
        if (name == null) return DEFAULT_FILE_NAME;
        String cleaned = name.replaceAll("[\\\\/\\p{Cntrl}]", "_").trim();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            return DEFAULT_FILE_NAME;
        }
        return cleaned;
    }
}
