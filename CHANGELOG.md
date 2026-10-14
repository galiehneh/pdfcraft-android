# Changelog - PdfCraft Android

Semua perubahan penting pada proyek **PdfCraft Android** didokumentasikan dalam berkas ini. Format mengikuti panduan penanggalan versi `YYYY.MM.DD[.patch]`.

---

## [2026.10.14.1] - 2026-10-14

### Perbaikan & Peningkatan Navigasi Mobile
- **Dismissal & Deselect Menu Aksi Seleksi Teks (Click-Outside):** Mengetuk atau mengklik di sembarang area luar menu aksi seleksi teks ("Salin / Sorot / Komentar") otomatis menutup menu popup dan membatalkan seleksi teks (*deselect*). Deteksi pointer touch dipertahankan saat release agar hit-testing akurat pada layar sentuh.
- **Koreksi Skala Raster Layar High-DPI:** Mengembalikan perhitungan skala raster 1:1 dengan rasio piksel perangkat (`render_scale`), menghilangkan bug kanvas/halaman putih berlebih di sisi kanan dan bawah pada layar ponsel berdensitas tinggi.
- **Eliminasi Konflik Pinch-to-Zoom:** Mendeteksi gestur multi-touch dan pinch zoom secara real-time. Seleksi teks dan drag selection ditekan saat 2 jari aktif atau dalam jeda 350ms setelah pinch selesai, sehingga menu aksi ("Salin / Sorot / Komentar") tidak muncul secara keliru saat mencubit layar untuk memperbesar/memperkecil.
- **Pembersihan Menu Konteks Desktop:** Mematikan popup menu konteks desktop ("Add a sticky note here", properti, panel komentar) pada tampilan ponsel kompak (`is_compact_mobile`), serta mencegah event `contextmenu` sintetis dari browser / WebView pada canvas.
- **Drag-to-Pan Halus:** Mengarahkan usapan satu jari pada mode seleksi standar untuk menggeser/menggulir (*pan / scroll*) dokumen secara mulus, bukan menyeleksi karakter acak di bawah jempol. Seleksi teks di ponsel tetap dapat diakses melalui ketukan ganda (*double-tap*) atau alat anotasi khusus.
- **Ketukan Ganda Smart Zoom:** Mengetuk layar dua kali (*double-tap*) pada ponsel otomatis beralih antara skala *Fit-to-Width* dan zoom baca nyaman 1.75x terpusat pada posisi ketukan.
- **Penyederhanaan Header Ponsel:** Menghapus tombol anotasi pensil duplikat dari bilah atas ponsel untuk memberikan ruang luas pada judul dokumen agar tidak terpotong. Akses alat anotasi tetap terpusat di bilah navigasi bawah yang mudah dijangkau ibu jari.

---

## [2026.10.13.3] - 2026-10-14

### Perbaikan
- **Menu seleksi teks (mobile):** di layar ponsel menu kini ringkas (Salin, Sorot, Komentar), diletakkan di atas baris terpilih dengan jarak aman dan dibatasi dalam layar, sehingga tidak menutupi teks maupun terpotong. Tombol Salin menyalin teks lalu menghapus seleksi.
- **Zoom/pinch lambat:** raster tajam ditahan untuk semua halaman selama gestur zoom di ponsel (tekstur lama diregangkan, raster baru dibuat setelah gestur selesai), dan kepadatan raster WASM dibatasi 2.0x agar tidak memproses puluhan megapiksel.

---

## [2026.10.13.2] - 2026-10-13

### Sinkronisasi Upstream v0.7.1 & Pembaruan Engine
- **Upstream Engine Parity (v0.7.1):**
  - Mengintegrasikan pembaruan core engine dari rilis resmi `storytold/pdfcraft` v0.7.1 (225+ perbaikan dan optimasi upstream).
  - Peningkatan kestabilan rendering dokumen kompleks dan cache tile decodes.
  - Perbaikan penanganan metadata, font encoding, dan keamanan verifikasi signature.
  - Adaptasi arsitektur `PageLayout` & `continuous` flag baru dari upstream tanpa mengubah UX mobile.
- **Mobile Layout & Wrapper Preservation:**
  - Tetap mempertahankan 100% tata letak mobile Appllama: Single-Page Stretch (Edge-to-Edge tanpa gutter), Responsive Home (hero CTA besar & 2-column quick tools), Bottom Navigation Bar, dan Floating Annotation Ribbon.
  - Integrasi native Android Storage Access Framework (SAF), MediaStore download bridge, dan window insets navigation bar tetap aktif.

---

## [2026.10.13.1] - 2026-10-13

### Fitur Baru & Peningkatan Mobile View
- **Single Page Stretch Mode (Edge-to-Edge):**
  - Mengeliminasi margin samping desktop (`SIDE = 70.0px`) pada layar smartphone (< 680px), sehingga halaman PDF dapat membentang 100% penuh dari tepi kiri ke kanan layar tanpa bilah hitam (*pillarboxing*).
  - Mengubah fungsi tombol expand/maximize di bilah navigasi bawah (`maximize-2` / `minimize-2`) menjadi sakelar toggle **On/Off Per-Page View Stretch**.
  - Saat mode aktif (*On*): Tampilan terkunci pada halaman tunggal (*Single Page*) dengan skala *Fit-to-Width* 100% penuh layar. Pengguna tidak perlu melakukan *pinch-to-zoom* manual untuk membaca tiket, barcode, faktur, atau dokumen teks kecil.
  - Saat mode nonaktif (*Off*): Tampilan kembali ke mode ringkasan gulir vertikal berkesinambungan (*Continuous Page View*) dengan skala *Fit-to-Page*.
  - Menetapkan mode *Single Page Stretch* sebagai default otomatis saat membuka berkas PDF di perangkat mobile.
  - Mengoptimalkan margin vertikal pada tampilan halaman tunggal menjadi lebih ringkas (4.0px).
- **Dokumentasi Versi:**
  - Menambahkan berkas `CHANGELOG.md` untuk mencatat riwayat perubahan aplikasi sejak versi perdana.

---

## [2026.10.13] - 2026-10-13

### Peningkatan Antarmuka Mobile (Appllama Design Benchmark)
- **Responsive Mobile Home (`home.rs`):**
  - Menghapus banner desktop Discord pada tampilan ponsel.
  - Margin beranda diperkecil menjadi 16px dengan hero header minimalis (*"PdfCraft — The Offline PDF Workbench"*).
  - Tombol aksi utama (*Primary Hero CTA*) diperbesar: *"Open PDF Document"* (tinggi 52px, radius 16px, ArtCraft teal `#0A7563`, target sentuh ≥ 48px).
  - Grid alat cepat 2-kolom (*Organize*, *Annotate*, *Fill & Sign*, *Protect*) dengan penyesuaian lebar otomatis.
  - *Composed empty state* yang rapi dan informatif saat belum ada riwayat berkas yang dibuka.
- **Modal Viewport Clamping (`dialogs.rs`):**
  - Membatasi lebar seluruh jendela dialog modal (Properties, Password, About, Export) maksimal selebar layar perangkat (`viewport - 24px`) agar tidak meluap atau terpotong ke samping.
- **Top App Bar & Smart Navigation (`chrome.rs`):**
  - Header ringkas 48px dengan tombol kembali (`chevron-left`) yang mengembalikan pengguna ke beranda via `request_close_tab`.
  - Judul dokumen terpusat rapi.
  - Menu popover dibersihkan dari emoji dan diganti ikon vektor Lucide dengan area sentuh ≥ 44px.
  - Menambahkan opsi eksplisit *"Close document"* pada menu.
- **Bottom Navigation & Floating Tool Ribbon (`chrome.rs`):**
  - Toolbar bawah 52px dengan target tombol 40px dan nomor halaman monospaced tabular (`1 / 5`).
  - Pita alat anotasi horizontal melayang di atas toolbar bawah (Select, Pan, Highlight, Note, Draw, Fill & Sign, Close), menggantikan toolbar vertikal desktop yang menutupi konten dokumen.
- **Android System Integration (`MainActivity.java`):**
  - Penanganan tombol *Hardware Back* Android: menutup modal atau mengembalikan tampilan dokumen ke Beranda sebelum keluar dari aplikasi.
  - FAB beranda diperbarui ke gaya Material Extended ArtCraft teal tanpa emoji.
  - Integrasi umpan balik getar haptik (*Haptic Feedback*) pada pembukaan berkas, penyimpanan berkas, dan penekanan tombol.

---

## [2026.10.11.3] - 2026-10-12

### Perbaikan Tampilan Layar
- **Window Insets & Safe Area:**
  - Menangani padding insets navigasi Android (`navigationBars` dan `displayCutout`) pada layout utama (`rootLayout`) untuk mencegah tombol FAB terpotong oleh bilah navigasi 3-tombol maupun bilah gestur sistem.
  - Penyesuaian elevasi dan margin bawah FAB.

---

## [2026.10.11.2] - 2026-10-12

### Pengenalan Tata Letak Mobile
- **Core WASM Responsive Viewport:**
  - Menambahkan deteksi layar compact ponsel (`is_compact_mobile` untuk lebar < 680px) pada engine UI Rust/egui.
  - Menggantikan dua bilah atas desktop ganda (`tab_strip` 40px + `mode_bar` 48px) dengan bilah atas tunggal ramah ponsel.
  - Menyembunyikan side rail kanan tetap (48px) dan bilah cepat melayang pojok kiri atas pada tampilan ponsel.
  - Menambahkan bilah navigasi bawah (*bottom bar*) untuk perpindahan halaman dan tombol rotasi tampilan.
  - Mengatur zoom awal dokumen ke mode *Fit-to-Width*.

---

## [2026.10.11.1] - 2026-10-11

### Integrasi Sistem Berkas Android & Intent Handler
- **Android Intent Filters:**
  - Menambahkan dukungan `android.intent.action.VIEW` dan `android.intent.action.SEND` pada `AndroidManifest.xml`.
  - Mendaftarkan PdfCraft sebagai aplikasi pembaca PDF default dan target menu Berbagi (*Share menu*) sistem Android.
- **Storage Access Framework (SAF):**
  - Memperbaiki normalisasi MIME type pada `onShowFileChooser` dari ekstensi `.pdf` menjadi standar `application/pdf`, mengatasi crash pemilih berkas sistem.
  - Menambahkan tombol pembuka berkas langsung (*Floating Action Button*) pada antarmuka wrapper.
  - Streaming dokumen cache lokal via `WebViewClient.shouldInterceptRequest` dengan nama berkas asli dokumen.

---

## [2026.10.11] - 2026-10-11

### Rilis Perdana (Initial Release)
- **Porting & Packaging Android:**
  - Pembungkusan *clean-room* PDF workbench berbasis Rust & WebAssembly ([storytold/pdfcraft](https://github.com/storytold/pdfcraft)) ke format APK Android standalone (`com.galiehneh.pdfcraft`).
  - Eksekusi 100% offline di sisi klien tanpa analitik, telemetry, atau ketergantungan server eksternal.
  - Penyajian aset WebAssembly in-memory via `shouldInterceptRequest` dengan MIME `application/wasm` dan dukungan WebGL2.
  - Integrasi jembatan JavaScript Blob ke Android MediaStore publik (`Environment.DIRECTORY_DOWNLOADS`).
  - Dukungan minimum SDK Android 7.0 (API 24) hingga Android 15 (API 35).
