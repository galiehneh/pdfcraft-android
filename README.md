# PdfCraft Android

> **The PDF Workbench for Android** — Clean-room PDF tool suite powered by Rust & WebAssembly.

Porting & Packaging dari [storytold/pdfcraft](https://github.com/storytold/pdfcraft) untuk perangkat Android. Berjalan 100% offline di sisi klien tanpa ketergantungan server eksternal, telemetry, atau akun.

---

## Fitur Utama

- **100% Offline & Private:** Semua pemrosesan dokumen (viewing, rendering, editing) dieksekusi langsung di perangkat Anda via WebAssembly engine.
- **Peralatan Lengkap Dokumen:**
  - PDF Reader / Viewer resolusi tinggi (dukungan WebGL2 / WebGPU hardware acceleration).
  - Organize: Rotasi, hapus, pisah (split), dan gabung (combine/merge) halaman PDF.
  - Form Filling & Annotations: Isi formulir interaktif, bubuhkan komentar, markup, highlight, serta tanda tangan digital.
  - Optimize & Clean: Kompresi ukuran berkas, OCR, dan sanitasi metadata.
- **Android Storage Integration:**
  - Terintegrasi langsung dengan Android Storage Access Framework (SAF) untuk memilih berkas dari memori internal / Google Drive / SD Card.
  - Fitur simpan berkas langsung mengalir ke folder publik `Download/` perangkat Android.

---

## Arsitektur & Teknologi

| Komponen | Spesifikasi / Teknologi |
|---|---|
| **Core Engine** | Pure Rust (`pdfcraft-engine`, `hayro`, `lopdf`) dikompilasi ke WebAssembly (`wasm32-unknown-unknown`) |
| **GUI Framework** | `egui` / `eframe` (v0.36) via WebGL2/WebGPU canvas |
| **Android Wrapper** | Native Android WebView (`com.galiehneh.pdfcraft`) |
| **Asset Interception** | `WebViewClient.shouldInterceptRequest` menyajikan aset lokal offline dengan MIME `application/wasm` |
| **Download Handler** | JavaScript Blob Bridge ke Android MediaStore (`Environment.DIRECTORY_DOWNLOADS`) |

---

## Spesifikasi Build

- **Application ID:** `com.galiehneh.pdfcraft`
- **Version Name:** `2026.10.11`
- **Version Code:** `20261011`
- **Minimum SDK:** Android 7.0 (API 24)
- **Target SDK:** Android 15 (API 35)

---

## Cara Build dari Source

### Prasyarat
- JDK 17
- Android SDK (Platform 35, Build-Tools 35.0.0+)

### Build APK Release
```bash
./gradlew assembleRelease
```
Berkas APK akan dihasilkan di:
`app/build/outputs/apk/release/app-release.apk`

---

## Lisensi & Atribusi

- Proyek ini dibundel di bawah lisensi [MIT](LICENSE).
- Upstream project: [storytold/pdfcraft](https://github.com/storytold/pdfcraft) oleh StoryTold Team (Dual-licensed bawah MIT / Apache 2.0).
