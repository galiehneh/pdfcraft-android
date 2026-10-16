# Web bundle provenance

Dokumen ini mencatat asal bundle web yang divendor di direktori ini. Jangan edit bundle
(`*.js`, `*_bg.wasm`, `index.html`) dengan tangan. Hanya informasi yang bisa diverifikasi dari
repo ini yang ditulis; sisanya ditandai "tidak diketahui".

## Berkas bundle saat ini

| Berkas | SHA-256 |
|---|---|
| `pdfcraft-web-fbf633af65f78c82_bg.wasm` | `126feded18a7b6fea9336a75680c003a04ca8ca909bdf6639679eeda0de75f8f` |
| `pdfcraft-web-fbf633af65f78c82.js` | `2313b563fae546e55e2bb26a44fc0abc90785383c4d92aceac84407d9333463a` |

Ukuran wasm sekitar 36 MB (git: 37.928.362 byte pada commit `56d455f`). Upstream dilaporkan sekitar
13 MB; penyebab selisih belum diselidiki.

Nama berkas memakai hash konten (`pdfcraft-web-<hash>`) dan dirujuk dari `index.html`. Setiap
rebuild mengganti hash; perbarui rujukan di `index.html` dan hapus berkas lama.

Shim Android (`window.PdfCraftAndroid`, hook IME, dll.) berada di `../android/shim.js`, bukan di
bundle ini.

## Asal

- Upstream: [storytold/pdfcraft](https://github.com/storytold/pdfcraft), MIT OR Apache-2.0
  (lihat `LICENSE-MIT`, `LICENSE-APACHE`, `NOTICE`).
- Versi upstream terakhir yang tercatat: **v0.7.1** (CHANGELOG 2026.10.13.2, commit `bd26746`).
- Fork Rust yang menghasilkan bundle (URL repo dan commit): **tidak diketahui**. Bundle ini memuat
  perubahan mobile (layout kompak, bilah bawah, single-page stretch, seleksi teks, pinch zoom)
  yang tidak ada di upstream, dibangun di luar repo ini. Sumbernya tidak tersimpan di repo.
- Riwayat perubahan bundle di repo ini: `git log -- app/src/main/assets/web`.

## Perintah build

**Tidak diketahui** untuk bundle ini. `HOSTING.md` menyebut `packaging/web/package.sh` pada
upstream sebagai penghasil `pdfcraft-web-<version>.zip` (situs statis). Toolchain yang disebut di
README proyek: Rust, target `wasm32-unknown-unknown`, `egui`/`eframe` 0.36. Flag pasti
(wasm-bindgen, wasm-opt, opt-level) tidak terverifikasi.

## Cara memperbarui bundle

1. Bangun ulang dari fork Rust (di luar repo ini) dan hasilkan `pdfcraft-web-<hash>.js`,
   `pdfcraft-web-<hash>_bg.wasm`, `index.html`.
2. Ganti berkas lama di direktori ini, perbarui rujukan hash.
3. Perbarui tabel SHA-256 di atas dan catat commit fork di bagian "Asal".
4. Bump versi dan CHANGELOG sesuai aturan rilis di `CLAUDE.md`.
