# 🧠 HybridBrain Android — Multimodal Hybrid AI Workspace (Edge & Cloud)

> **Privacy-First Multimodal Second Brain untuk Android**  
> Menggabungkan kekuatan **Local On-Device AI (Google AI Edge / LiteRT-LM + Gemma 4)** dengan **Cloud AI (Gemini 3.8 / 4 via Google AI Studio BYOK)**.

---

## 🌟 Mengapa Hybrid AI di Perangkat Mobile?

Dalam aktivitas harian, perkuliahan, dan pekerjaan profesional, pengguna sering menghadapi dilema antara privasi data sensitif dan kebutuhan penalaran skala besar:
1. **🔒 Mode A: 100% Offline (Local Gemma Edge)**
   - Menjalankan model open-weights **Gemma 4 (E2B-it / QAT 4-bit)** secara lokal di HP menggunakan runtime resmi **Google AI Edge LiteRT-LM**.
   - Dokumen rahasia, catatan meeting, dan materi kuliah diproses murni di memori RAM/prosesor perangkat tanpa satu byte pun data yang dikirim ke internet.
   - Nol kuota data internet, nol biaya token API, dan 100% aman terlindungi.
2. **⚡ Mode B: Cloud Heavy (Gemini BYOK)**
   - Saat membutuhkan pencarian informasi eksternal terkini atau penalaran multi-dokumen ratusan halaman, pengguna dapat beralih ke **Gemini 3.8 Flash** atau **Gemini 4 Argon**.
   - Menggunakan prinsip **Bring Your Own Key (BYOK)**: Kunci Google AI Studio API disimpan secara aman di perangkat pengguna dan tidak pernah dikirim ke server pihak ketiga.

---

## 📁 Keterlihatan Berkas Model di File Manager Android

Sesuai permintaan desain privasi dan transparansi:
* Bobot model `.litertlm` disimpan langsung di folder unduhan publik perangkat Anda:
  ```text
  /sdcard/Download/HybridBrain/models/
  ├── gemma-4-E2B-it.litertlm           (~2.5 GB)
  └── embeddinggemma-2-740m.litertlm    (~740 MB)
  ```
* Anda dapat membuka aplikasi **File Manager bawaan Android** (misal *Files by Google*, *Samsung My Files*, atau *Xiaomi File Manager*), masuk ke folder **Download ➔ HybridBrain ➔ models**, dan melihat langsung berkas model tersebut.

---

## 🚀 Fitur-Fitur Utama Aplikasi

1. **Dual Engine Switcher**:
   - Berpindah instan antara **Mode A (🔒 Local Gemma Edge)** dan **Mode B (⚡ Cloud Gemini)** via menu Pengaturan.
   - Pilihan akselerasi perangkat keras: **CPU (XNNPACK / ARM Neon)**, **GPU (ML Drift / OpenCL)**, dan **NPU**.
2. **Pengunduh Bobot Model Langsung (Hugging Face)**:
   - Unduhan streaming resmi langsung dari repository Hugging Face tanpa perlu token atau login.
   - Tampilan progres real-time: persentase, ukuran MB terunduh, dan kecepatan transfer (MB/s).
   - Opsi **"Demo Stub"** untuk membuat berkas stub valid secara instan (untuk demo workshop tanpa kuota internet besar).
3. **PTAC Agentic RAG Otonom (Google ADK Style)**:
   - Siklus kognitif berulang (*Cognitive Loop* hingga 20 iterasi):
     - **👁️ PERCEIVE**: Analisis maksud pertanyaan & kriteria kelengkapan data.
     - **🧠 THINK**: Perumusan strategi pencarian & kueri retrieval semantik.
     - **⚡ ACT**: Pencarian vektor Cosine Similarity ke database SQLite lokal.
     - **🔍 CHECK**: Refleksi mandiri (*Anti-Hallucination Grounding*) untuk memverifikasi bukti faktual.
     - **🎯 RESULT**: Penyediaan konteks terverifikasi ke model AI.
4. **Ruang Obrolan Multi-Room & Isolasi Dokumen ("Curcol Rooms")**:
   - Membuat, mengganti nama, menghapus ruang, serta membersihkan riwayat obrolan.
   - **Isolasi Dokumen**: Dokumen yang diunggah ke suatu ruang hanya dapat diakses oleh ruang tersebut.
5. **Persona AI per Ruang**:
   - 🧠 *General Assistant*
   - 🛡️ *Cybersecurity Auditor* (OWASP & Threat Modeling)
   - 🎓 *Academic Thesis Reviewer* (Metodologi & sitasi ilmiah)
   - 💻 *Code Pair Programmer* (Clean code & arsitektur sistem)
   - ⚡ *Executive Summarizer* (TL;DR & Actionable points)
   - 🎨 *Custom Persona* (Instruksi sistem kustom)
6. **Multimodal Ingestion (RAG & Per-Bubble Attachment)**:
   - **Attachment Langsung di Chat (Opsi C Hybrid)**: Pengguna dapat melampirkan berkas (PDF, Gambar, Audio, TXT) langsung pada kolom chat untuk dianalisis secara instan (*ephemeral*), disertai tombol 1-klik *"Simpan ke Memori Room"* jika ingin dokumen tersebut diindeks permanen ke database vektor room.
   - **Vision-Assisted Ingestion**: Ekstraksi cerdas pada dokumen visual dan PDF berbasis pindaian sehingga menghasilkan representasi teks terstruktur yang ramah terhadap alokasi KV-cache model lokal Gemma.
   - Database vektor lokal SQLite dengan pencarian semantik Cosine Similarity 768 dimensi dan isolasi per-room.
7. **🔍 Source Context Inspector**:
   - Akordeon di bawah setiap respon asisten yang menampilkan nomor chunk, nama berkas, nomor halaman, persentase relevansi, dan kutipan teks dokumen.
8. **📥 Ekspor Chat ke Format Markdown (.md)**:
   - Ekspor riwayat obrolan lengkap beserta tabel metadata dan rujukan sitasi RAG yang dapat langsung disimpan atau dibagikan via Android Share Sheet.
9. **💡 Smart Auto-Routing Heuristic**:
   - Analisis cerdas yang merekomendasikan pengguna beralih ke Mode Cloud jika kueri membutuhkan informasi eksternal terkini atau memiliki struktur penalaran multi-tahap yang sangat kompleks.
10. **🎨 Rich Markdown Response Viewer**:
    - Respons asisten AI (baik saat live streaming maupun riwayat percakapan) di-render secara dinamis menggunakan AST Markdown renderer natif Material 3 (`multiplatform-markdown-renderer-m3`).
    - Heading bertingkat, blok kode (*syntax styling*), kutipan, pemisah horizontal, bullet points, dan daftar berangka ditampilkan dengan tipografi elegan yang menyatu dengan tema gelap HybridBrain.

---

## 📲 Panduan Instalasi ke Perangkat Android

### 1. Prasyarat
* Android 8.0 (API level 26) atau yang lebih baru.
* Fitur **USB Debugging** telah diaktifkan di menu *Developer Options* HP Anda.

### 2. Instalasi APK via ADB
Jalankan perintah berikut di terminal:
```powershell
# Pasang APK ke perangkat yang terhubung
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 3. Memasukkan Berkas Bobot Model ke HP
Jika Anda sudah memiliki berkas model lokal di laptop (di folder `referensi/models/`), Anda dapat mentransfernya langsung ke HP tanpa mengunduh ulang via internet:
```powershell
# Buat folder target di HP
adb shell mkdir -p /sdcard/Download/HybridBrain/models

# Push model Gemma 4 (~2.5 GB)
adb push referensi\models\gemma-4-E2B-it.litertlm /sdcard/Download/HybridBrain/models/

# Push model Embedding (~484 MB)
adb push referensi\models\embeddinggemma-2-740m.litertlm /sdcard/Download/HybridBrain/models/
```

Alternatifnya, Anda dapat langsung mengunduh atau menekan tombol **"Demo Stub"** di dalam aplikasi pada sheet **Kelola Bobot Model LiteRT**.

---

## 🛠️ Kompilasi & Build dari Source Code

```powershell
# Kompilasi kode Kotlin
.\gradlew.bat compileDebugKotlin

# Build file APK Debug
.\gradlew.bat assembleDebug
```
Berkas APK akan dihasilkan di `app\build\outputs\apk\debug\app-debug.apk`.

---

## 🛡️ Kebijakan Privasi & Keamanan
* **Zero Telemetry**: Pada Mode Lokal, seluruh proses RAG dan komputasi inferensi berjalan murni di memori RAM dan prosesor HP Anda.
* **BYOK Security**: Kunci API Google AI Studio disimpan secara privat di penyimpanan lokal aplikasi dan hanya dikirimkan melalui koneksi TLS aman ke endpoint resmi Google AI Studio.
