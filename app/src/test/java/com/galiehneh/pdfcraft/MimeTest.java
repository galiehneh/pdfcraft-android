package com.galiehneh.pdfcraft;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MimeTest {
    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    // acceptToMimeTypes

    @Test
    public void accept_nullOrEmptyFallsBackToWildcard() {
        assertArrayEquals(new String[]{"*/*"}, Mime.acceptToMimeTypes(null));
        assertArrayEquals(new String[]{"*/*"}, Mime.acceptToMimeTypes(new String[0]));
        assertArrayEquals(new String[]{"*/*"}, Mime.acceptToMimeTypes(new String[]{"", "  ", null}));
    }

    @Test
    public void accept_mapsExtensionsAndMimeTypes() {
        assertArrayEquals(new String[]{"application/pdf"}, Mime.acceptToMimeTypes(new String[]{".pdf"}));
        assertArrayEquals(new String[]{"application/pdf", "image/png"},
                Mime.acceptToMimeTypes(new String[]{" .PDF ", "image/png", ".pdf"}));
        assertArrayEquals(new String[]{"image/*"}, Mime.acceptToMimeTypes(new String[]{"image/*"}));
    }

    @Test
    public void accept_unmappableEntryFallsBackToWildcard() {
        assertArrayEquals(new String[]{"*/*"}, Mime.acceptToMimeTypes(new String[]{".pdf", ".xyz"}));
        assertArrayEquals(new String[]{"*/*"}, Mime.acceptToMimeTypes(new String[]{"pdf"}));
        assertArrayEquals(new String[]{"*/*"}, Mime.acceptToMimeTypes(new String[]{".pdf", "*/*"}));
    }

    // resolve / fromFileName / extensionFor

    @Test
    public void resolve_prefersDeclaredMimeAndStripsParameters() {
        assertEquals("text/plain", Mime.resolve("Text/Plain; charset=utf-8", "a.pdf"));
    }

    @Test
    public void resolve_fallsBackToExtensionThenPdf() {
        assertEquals("image/png", Mime.resolve("application/octet-stream", "pic.PNG"));
        assertEquals("application/pdf", Mime.resolve(null, "noext"));
        assertEquals("application/pdf", Mime.resolve("", null));
        assertEquals(Mime.OCTET_STREAM, Mime.resolve(null, "data.xyz"));
        assertEquals(Mime.OCTET_STREAM, Mime.resolve("garbage", "data.xyz"));
    }

    @Test
    public void fromFileName_knownAndUnknown() {
        assertEquals(DOCX, Mime.fromFileName("x.docx"));
        assertEquals("application/wasm", Mime.fromFileName("a.wasm"));
        assertEquals(Mime.OCTET_STREAM, Mime.fromFileName(null));
        assertEquals(Mime.OCTET_STREAM, Mime.fromFileName("x.bin"));
    }

    @Test
    public void extensionFor_knownAndUnknown() {
        assertEquals("pdf", Mime.extensionFor("application/pdf"));
        assertEquals("jpg", Mime.extensionFor("image/jpeg"));
        assertNull(Mime.extensionFor(DOCX));
        assertNull(Mime.extensionFor(null));
    }

    // sanitizeFileName

    @Test
    public void sanitize_replacesSeparatorsAndStripsLeadingDots() {
        assertEquals("a_b.pdf", Mime.sanitizeFileName("a/b.pdf"));
        assertEquals("_x", Mime.sanitizeFileName("../x"));
        assertEquals("a_b_c_d.pdf", Mime.sanitizeFileName("a\\b:c*d.pdf"));
        assertEquals("a_b", Mime.sanitizeFileName("a\nb"));
    }

    @Test
    public void sanitize_emptyOrDotsGivesDefault() {
        assertEquals(Mime.DEFAULT_FILE_NAME, Mime.sanitizeFileName(null));
        assertEquals(Mime.DEFAULT_FILE_NAME, Mime.sanitizeFileName("   "));
        assertEquals(Mime.DEFAULT_FILE_NAME, Mime.sanitizeFileName(".."));
        assertEquals(Mime.DEFAULT_FILE_NAME, Mime.sanitizeFileName("..."));
    }

    @Test
    public void sanitize_longNameKeepsTailWithExtension() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) sb.append('a');
        String out = Mime.sanitizeFileName(sb + ".pdf");
        assertEquals(120, out.length());
        assertTrue(out.endsWith(".pdf"));
    }

    // finalFileName

    @Test
    public void finalFileName_appendsExtensionOnlyWhenMissing() {
        assertEquals("report.pdf", Mime.finalFileName("report", "application/pdf"));
        assertEquals("report.txt", Mime.finalFileName("report.txt", "application/pdf"));
        assertEquals("document.png", Mime.finalFileName(null, "image/png"));
        assertEquals("noext", Mime.finalFileName("noext", DOCX));
        assertEquals("document.pdf", Mime.finalFileName("   ", "application/pdf"));
    }

    // sanitizeForSave

    @Test
    public void sanitizeForSave_allowedTypePassesThrough() {
        assertArrayEquals(new String[]{"application/pdf", "a.pdf"},
                Mime.sanitizeForSave("application/pdf", "a.pdf"));
        assertArrayEquals(new String[]{DOCX, "r.docx"}, Mime.sanitizeForSave(DOCX, "r.docx"));
    }

    @Test
    public void sanitizeForSave_dangerousTypeBecomesOctetStreamBin() {
        assertArrayEquals(new String[]{Mime.OCTET_STREAM, "x.bin"},
                Mime.sanitizeForSave("text/html", "x.html"));
        assertArrayEquals(new String[]{Mime.OCTET_STREAM, "a.bin"},
                Mime.sanitizeForSave("application/x-evil", "a.exe"));
    }

    @Test
    public void sanitizeForSave_allowedMimeWithBadExtensionIsDowngraded() {
        String[] out = Mime.sanitizeForSave("application/pdf", "a.exe");
        assertEquals(Mime.OCTET_STREAM, out[0]);
        assertEquals("a.bin", out[1]);
    }

    @Test
    public void sanitizeForSave_nullsDefaultToPdf() {
        assertArrayEquals(new String[]{"application/pdf", "document.pdf"},
                Mime.sanitizeForSave(null, null));
    }

    @Test
    public void sanitizeForSave_neverKeepsPathTraversal() {
        String name = Mime.sanitizeForSave("application/pdf", "../../etc/passwd")[1];
        assertFalse(name.contains("/"));
        assertFalse(name.startsWith("."));
    }
}
