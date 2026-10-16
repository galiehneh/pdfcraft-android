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
  - Simpan berkas besar secara streaming (batas 1 GiB per berkas) dengan MIME/ekstensi benar, lalu aksi Buka/Bagikan.
  - Pemilih berkas mengikuti tipe yang diminta aplikasi; impor tidak memblokir UI.
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
- **Version Name:** `2026.10.16`
- **Version Code:** `20261025` (counter rilis, bukan tanggal)
- **Minimum SDK:** Android 7.0 (API 24)
- **Target SDK:** Android 15 (API 35)

Riwayat perubahan lengkap dapat dilihat di [CHANGELOG.md](CHANGELOG.md).

---

## Fitur Mobile Friendly (Appllama Design Benchmark)

- **Responsive Mobile Layout:** Adaptif pada layar ponsel (< 680px), menyembunyikan dual toolbar desktop yang memakan ruang.
- **Top App Bar & Smart Navigation:** Header ringkas dengan tombol kembali ke beranda, pencarian terintegrasi, dan menu popover tanpa emoji.
- **Bottom Navigation & Quick Tools Ribbon:** Kontrol halaman di bagian bawah ibu jari, rotasi cepat, toggle zoom *Fit-to-Width*, serta pita alat anotasi horizontal (Highlight, Note, Draw, Fill & Sign).
- **Responsive Home & Empty State:** Tombol buka berkas besar (min. target sentuh 48px), grid kartu peralatan 2-kolom, dan tampilan status riwayat berkas yang informatif.
- **Viewport Modal Clamping:** Seluruh dialog modal (Properties, Password, About) dibatasi agar tidak meluap ke luar layar.
- **Android System Integration:** Penanganan tombol *Hardware Back* Android yang intuitif (menutup dokumen/kembali ke Beranda sebelum keluar aplikasi) dan integrasi getar haptik.

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
`app/build/outputs/apk/release/`

Rilis memakai R8 (shrink kode dan resource). Bila `signing/release-signing.properties` ada (gitignored, lihat `.example`), APK ditandatangani (v1/v2/v3); jika tidak, hasilnya unsigned.

### Tes & Lint
```bash
./gradlew lint testDebugUnitTest assembleDebug
```

---

## Cara Install

1. Unduh APK dari halaman [Releases](../../releases) (dibuat otomatis oleh CI pada tag `v*`), atau ambil artifact debug dari run GitHub Actions.
2. Di Android, izinkan instalasi dari sumber ini ("Install unknown apps"), lalu buka APK.
3. Atau via ADB: `adb install -r app-release.apk`.

Android 7.0 (API 24) atau lebih baru. Asal bundle web: [BUNDLE.md](app/src/main/assets/web/BUNDLE.md).

---

## Lisensi & Atribusi

- Proyek ini dibundel di bawah lisensi [MIT](LICENSE).
- Upstream project: [storytold/pdfcraft](https://github.com/storytold/pdfcraft) oleh StoryTold Team (Dual-licensed bawah MIT / Apache 2.0).
