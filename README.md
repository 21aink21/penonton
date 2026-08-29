# 🎬 ERVANIME3d - Modern Android Donghua Streaming App

<p align="center">
  <img src="https://raw.githubusercontent.com/21aink21/donghua/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="ERVANIME3d Logo" />
  <br>
  <b>Aplikasi Streaming Donghua Cepat, Ringan, dan Sinematik untuk Android</b>
  <br><br>
  <a href="https://github.com/21aink21/donghua/releases/latest">
    <img src="https://img.shields.io/github/v/release/21aink21/donghua?color=crimson&label=Latest%20Release" alt="Latest Release" />
  </a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-green.svg" alt="Android Support" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-blue.svg" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Engine-Offline%20HLS.js-amber.svg" alt="Offline HLS" />
</p>

---

## 📥 Download APK Terbaru

👉 **[Download ERVANIME3d v1.0.0 APK](https://github.com/21aink21/donghua/releases/download/v1.0.0/ERVANIME3d-v1.0.0.apk)** *(Ukuran: ~8.3 MB)*

---

## ✨ Fitur Unggulan

- 🚀 **Offline HLS Engine**:
  - Menyematkan engine `hls.min.js` lokal di aset aplikasi untuk memutar stream HLS / Rumble secara instan tanpa terhalang proteksi Cloudflare.
- 🎯 **Multi-Server Streaming dengan Smart Priority**:
  - Mengutamakan server **GanJing** (`🇮🇩 GanJing`) sebagai server utama aktif.
  - Mendukung server alternatif: **INDO 4K-VIP**, **Rumble**, dan **1080p English Sub**.
- 🎨 **Hero Header Sinematik & Backdrop 100% Seamless**:
  - Poster vertikal rasio ~3:4 (`400dp`) dengan transisi *hardware alpha fade* (`PorterDuff.Mode.DST_IN` shader) yang menyatu sempurna tanpa garis pembatas ke latar belakang tema Obsidian-Crimson.
  - Judul megah mengambang (`32sp Bold`) dengan skor rating emas (`★ 9.8`), badge episode aktif, dan baris metadata beresolusi tinggi.
- 🎛️ **Pemutar Video Kaya Fitur**:
  - Gestur swipe vertikal untuk mengatur Kecerahan (layar kiri) & Volume (layar kanan).
  - Mode Fullscreen rotasi dinamis & Mode Gambar-dalam-Gambar (Picture-in-Picture / PiP).
  - Tombol Kunci Layar (Screen Lock) anti-sentuhan tidak sengaja.
  - Pengatur kecepatan putar (0.5x hingga 2.0x), toggle Danmaku, dan CC Subtitle.
  - Tombol lompat 10 detik dan tombol pintar *Lewati Opening (+85s)*.
- 📚 **Koleksi & Riwayat Lokal**:
  - Pencatatan otomatis riwayat tontonan per episode beserta menit/detik terakhir.
  - Fitur daftar favorit donghua tersimpan lokal di perangkat tanpa perlu login.

---

## 🛠️ Arsitektur & Teknologi

- **Bahasa**: Kotlin (Modern Coroutines & State Flow)
- **UI Architecture**: ViewBinding, CoordinatorLayout, Material Components 3
- **Media Engine**: Local Injected HLS.js HTML5 Engine + ExoPlayer Fallback
- **Asynchronous**: Coroutines, Dispatchers.IO, OkHttp3 with CookieJar
- **Parsing**: Jsoup HTML Scraper & Stream Extractor

---

## 📜 Riwayat Pembaruan (Changelog)

### [v1.0.0] - 2026-08-29
- **Initial Official Release**:
  - Implementasi penuh antarmuka bertema Obsidian-Crimson.
  - Integrasi engine offline HLS untuk pemutaran video tanpa kendala Cloudflare.
  - Perbaikan header detail poster dengan hardware alpha fade shader.
  - Pembaruan branding resmi menjadi **ErVanDongZ**.
  - Peningkatan ukuran teks dan kontras untuk keterbacaan optimal.

---

<p align="center">
  Dibuat dengan ❤️ untuk seluruh penggemar donghua Indonesia.
</p>
