# Vibe Coding Viewer (VcV)

Aplikasi Android untuk membuka hasil vibe coding (ZIP / HTML / folder) di Chrome,
dengan daftar 10 proyek terbaru dan fitur bagikan ke HP siswa lewat QR.

- Paket: `id.tg.vcv` · Android 8.0+ · Kotlin + Jetpack Compose · tema gelap
- Server lokal: NanoHTTPD (gzip, file di RAM, cache ETag, antrean 24 koneksi)
- QR: ZXing

---

## 1. Cara build APK debug

1. Ekstrak ZIP ini, lalu buka foldernya di **Android Studio** (File → Open → pilih folder `VibeCodingViewer`).
2. Tunggu proses **Gradle Sync** selesai (pertama kali butuh internet untuk mengunduh Gradle & pustaka).
   - Jika Android Studio menawarkan *upgrade* Android Gradle Plugin, boleh disetujui.
3. (Opsional) Masukkan font merek ke `app/src/main/assets/fonts/` — lihat `BACA-SAYA.txt` di folder itu.
4. Sambungkan HP (USB debugging aktif) lalu tekan **Run ▶**,
   atau buat file APK: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   File jadi ada di `app/build/outputs/apk/debug/app-debug.apk`.

> Kode ini ditulis tanpa bisa dikompilasi di lingkungan pembuatnya.
> Jika muncul error saat build, salin pesan error dari panel **Build** dan kirimkan untuk diperbaiki.

---

## 2. Struktur kode

```
app/src/main/java/id/tg/vcv/
├─ VcvApp.kt                 Inisialisasi, kanal notifikasi, hapus salinan WA saat proyek ditutup
├─ MainActivity.kt           Satu-satunya layar; menangani intent Bagikan/Buka dengan/pintasan/notifikasi
├─ data/
│  ├─ ProjectRecord.kt       Data satu proyek di daftar
│  ├─ ProjectStore.kt        Daftar 10 terbaru (SharedPreferences + JSON)
│  ├─ Settings.kt            Peramban bawaan, waktu berhenti otomatis, port
│  ├─ ProjectInspector.kt    Cari index.html / file HTML lain, cek file pendamping, cek kesiapan offline
│  ├─ ProjectPreparer.kt     Baca ulang file asli → ekstrak ke cache/serve/
│  └─ UriInfo.kt             Nama & lokasi file yang ramah dibaca
├─ server/
│  ├─ VcvHttpServer.kt       Server statis (gzip, RAM, ETag, Range, antrean terbatas)
│  ├─ ServerController.kt    Satu server aktif, mode lokal / berbagi
│  ├─ VcvServerService.kt    Foreground service + notifikasi + berhenti otomatis + wake/WiFi lock
│  └─ NetUtil.kt             Alamat IP WiFi/hotspot
└─ ui/                       Tampilan Compose (beranda, sheet, dialog, QR, peramban internal, pengaturan)
```

---

## 3. Perilaku penting

| Hal | Perilaku |
|---|---|
| Daftar terbaru | Maks. 10. Proyek ke-11 masuk → yang terlama keluar dari daftar (file asli tidak dihapus). |
| Penyimpanan | Hanya alamat file asli. File dibaca ulang setiap kali dibuka. |
| File dari WhatsApp | Disalin sementara. Dihapus saat proyek ditutup (server berhenti / buka proyek lain). Tetap tampil di daftar dengan keterangan *Sekali buka*. Tombol **Simpan Proyek** (menu tekan lama & notifikasi) menyimpannya permanen. |
| Tidak ada index.html | Ditawarkan file HTML lain (mis. game.html). |
| Tidak ada HTML sama sekali | Pesan "File HTML tidak ditemukan", kembali ke beranda, tidak disimpan. |
| Nama sama | Pilihan Timpa / Abaikan. |
| HTML tunggal dengan file pendamping | Disarankan Import Folder. |
| Satu proyek aktif | Membuka proyek lain saat berbagi → konfirmasi "Hentikan berbagi?". |
| Server | Port 8080 (pindah otomatis bila terpakai). Mode lokal hanya bisa diakses HP ini; mode berbagi terbuka untuk jaringan WiFi/hotspot. |
| Berhenti otomatis | Setelah 5/10/15/30 menit tanpa permintaan (atur di Pengaturan). |

---

## 4. Daftar uji coba

Centang satu per satu di HP Bapak.

**A. Dasar**
- [ ] Aplikasi terpasang, ikon VcV tampil di layar HP, layar pembuka tampil.
- [ ] Android 13+: muncul izin notifikasi → Izinkan.
- [ ] Beranda kosong tampil dengan logo stiker dan tombol Import ZIP / File HTML / Folder.

**B. Import & buka**
- [ ] Import ZIP berisi `index.html` → langsung terbuka di Chrome.
- [ ] Import ZIP dengan proyek di dalam subfolder (mis. `proyek/index.html`) → terbuka benar, gambar & CSS tampil.
- [ ] Import ZIP tanpa index.html tapi ada `game.html` → muncul tawaran "Ditemukan game.html".
- [ ] Import ZIP tanpa HTML sama sekali → pesan "File HTML tidak ditemukan", daftar tidak bertambah.
- [ ] Import file yang namanya sama dengan proyek di daftar → Timpa / Abaikan bekerja.
- [ ] Import HTML tunggal yang memanggil gambar di sebelahnya → muncul saran Import Folder.
- [ ] Import Folder → terbuka lengkap.
- [ ] Import 11 proyek → daftar tetap 10, yang paling lama hilang.
- [ ] Buka ulang proyek dari daftar → naik ke paling atas.
- [ ] Pindahkan/hapus file asli lalu buka dari daftar → "File asli tidak ditemukan. Hapus dari daftar?".

**C. WhatsApp**
- [ ] Di WhatsApp, tekan file ZIP → Bagikan → VcV → proyek terbuka, lencana WA.
- [ ] Hentikan server dari notifikasi → proyek tetap di daftar dengan keterangan "Sekali buka".
- [ ] Ketuk proyek WA itu → pesan "Salinan sudah dihapus…".
- [ ] Bagikan ulang dari WA → saat masih terbuka, tekan **Simpan Proyek** → pilih folder Download → lencana berubah jadi ZIP dan bisa dibuka kapan saja.

**D. Menu tekan lama**
- [ ] Buka di Chrome · Buka di peramban internal (tombol muat ulang & buka di Chrome berfungsi).
- [ ] Tambah ke layar utama HP → pintasan muncul, ketuk → proyek terbuka.
- [ ] Ganti nama tampilan · Hapus dari daftar.

**E. Server & notifikasi**
- [ ] Notifikasi "Server aktif · nama proyek" muncul dengan tombol Hentikan / Buka lagi.
- [ ] Pindah ke Chrome, kunci layar 2 menit, buka lagi → proyek masih bisa dimuat ulang.
- [ ] Atur berhenti otomatis 5 menit, diamkan → server berhenti sendiri, notifikasi hilang.

**F. Bagikan ke HP siswa**
- [ ] Nyalakan hotspot HP Bapak (atau sambung ke router), pilih Bagikan ke HP siswa (QR).
- [ ] HP lain tersambung ke hotspot → pindai QR → proyek terbuka di HP itu.
- [ ] Angka "Terhubung" bertambah sesuai jumlah HP.
- [ ] Proyek yang memakai CDN/Google Fonts → muncul peringatan "Butuh internet".
- [ ] Buka proyek lain saat berbagi → muncul "Hentikan berbagi?".
- [ ] Tombol Hentikan berbagi → HP siswa tidak bisa memuat lagi.

**G. Pengaturan**
- [ ] Peramban bawaan Internal → ketuk proyek terbuka di dalam aplikasi.
- [ ] Ganti port (mis. 8090) → proyek berikutnya memakai port baru.
- [ ] Kosongkan daftar → daftar kosong, file asli tetap ada.

---

## 5. Catatan teknis

- Proyek yang berbeda dibuka di alamat yang sama (`localhost:8080`), sehingga data `localStorage`
  milik satu proyek bisa terbaca oleh proyek lain. Jika ini mengganggu, gunakan port berbeda per proyek.
- Hotspot HP umumnya hanya menerima sekitar 8–10 perangkat. Untuk satu kelas penuh gunakan router WiFi.
- Beberapa merek HP (Xiaomi, Oppo, Vivo) punya penghemat baterai agresif. Jika server sering mati,
  izinkan VcV berjalan di latar belakang lewat pengaturan baterai HP.
