# 🎬 PENONTON - Modern Android Movie & Series Streaming App

<p align="center">
  <img src="https://raw.githubusercontent.com/21aink21/penonton/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="penonton Logo" />
  <br>
  <b>Aplikasi Streaming Film & Serial Drama Cepat, Ringan, dan Sinematik untuk Android</b>
  <br><br>
  <a href="https://github.com/21aink21/penonton/releases/latest">
    <img src="https://img.shields.io/badge/Release-v1.0.0-crimson.svg" alt="Latest Release" />
  </a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-green.svg" alt="Android Support" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-blue.svg" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Engine-PlayCDN%20HLS-amber.svg" alt="HLS Engine" />
</p>

---

## ✨ Fitur Unggulan

- 🚀 **PlayCDN & Videonode HLS Engine**:
  - Resolusi instan stream video HLS `.m3u8`.
- 🎯 **Dukungan Movie & Series Terpadu**:
  - Scraping katalog Film bioskop.
  - Scraping serial drama, drakor, anime, dan serial barat.
  - REST API pencarian instan terpusat.
- 🎨 **Hero Header Sinematik & Backdrop 100% Seamless**:
  - Poster rasio ~3:4 dengan transisi hardware alpha fade shader ke tema Obsidian-Crimson.
  - Skor rating emas (`★ 9.0+`), badge kualitas HD/CAM/EPS, dan baris metadata beresolusi tinggi.
- 🎛️ **Pemutar Video Kaya Fitur**:
  - Gestur swipe vertikal untuk Kecerahan (layar kiri) & Volume (layar kanan).
  - Mode Fullscreen rotasi dinamis & Mode Picture-in-Picture (PiP).
  - Tombol Screen Lock anti-sentuh, pengatur kecepatan putar (0.5x - 2.0x), lompat 10 detik, dan 2X Speed Press & Hold.
- 📚 **Koleksi & Riwayat Lokal**:
  - Pencatatan otomatis riwayat tontonan per episode / film beserta posisi menit/detik terakhir.
  - Fitur daftar favorit tersimpan lokal di perangkat tanpa perlu login.

---

## 🛠️ Arsitektur & Teknologi

- **Bahasa**: Kotlin (Coroutines & State Flow)
- **UI Architecture**: ViewBinding, CoordinatorLayout, Material Components 3
- **Media Engine**: ExoPlayer (Media3) + Local Injected HLS.js HTML5 Engine Fallback
- **Asynchronous**: Coroutines, Dispatchers.IO, OkHttp3
- **Parsing**: Jsoup HTML Scraper, JSON Islands (`season-data`, `watch-history-data`) & REST Search API

---

<p align="center">
  Dibuat dengan ❤️ untuk seluruh penggemar Film & Serial Drama Indonesia.
</p>

