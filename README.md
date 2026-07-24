# Quran Audio Search

Aplikasi client-server untuk pencarian audio Al-Qur'an. Repository ini terdiri dari dua bagian utama:
1.  **Backend**: API untuk pemrosesan audio menggunakan **FastAPI** (Python).
2.  **QuranAudioSearch**: Aplikasi mobile Android (Client).

---

## 1. Cara Menjalankan Backend (FastAPI)

Backend dibangun menggunakan Python. Pastikan komputer kamu sudah terinstal **Python** sebelum memulai.

### Setup Virtual Environment (Wajib)
Gunakan virtual environment agar instalasi *library* aman dan terisolasi khusus untuk project ini. Buka terminal/Command Prompt, arahkan ke folder `Backend`, lalu jalankan perintah berikut:

1. Buat virtual environment bernama `venv`:
   ```bash
   python -m venv venv
   ```

2. Aktifkan virtual environment:
   * Pengguna **Windows**:
     ```bash
     venv\Scripts\activate
     ```
   * Pengguna **Mac/Linux**:
     ```bash
     source venv/bin/activate
     ```

### Prasyarat Instalasi
Setelah virtual environment aktif (biasanya ditandai dengan tulisan `(venv)` di awal baris terminal), instal semua *library* atau *dependencies* yang dibutuhkan dengan menjalankan perintah berikut:

```bash
pip install fastapi uvicorn python-multipart torch torchaudio scikit-learn transformers joblib numpy
```

---

## 2. Cara Menjalankan Aplikasi Android (QuranAudioSearch)

Aplikasi Android ini dibangun menggunakan Android Studio. Ada beberapa konfigurasi jaringan yang **wajib** disesuaikan agar aplikasi bisa terhubung dengan Backend.

### ⚠️ Konfigurasi IP Address (PENTING)
Agar aplikasi Android bisa mengirim data ke komputer (Backend), **keduanya harus terhubung ke jaringan WiFi/LAN yang sama**. 

Kamu perlu mengubah alamat IP di dalam *source code* Android Studio agar sesuai dengan alamat IP lokal komputermu (IPv4):

1. Buka folder/direktori berikut di Android Studio: 
   `QuranAudioSearch\app\src\main\java\com\example\quranaudiosearch\network`
2. Cari file konfigurasi API atau *Base URL* (misalnya `RetrofitClient` atau `ApiConfig`).
3. Ubah *Base URL* menggunakan IP komputermu. Contoh: `http://192.168.x.x:8000/`.

### Langkah-langkah Build (How to Build)
1. Buka aplikasi **Android Studio**.
2. Pilih menu **File > Open**, lalu arahkan dan pilih folder `QuranAudioSearch` dari repository ini.
3. Tunggu beberapa saat hingga proses **Gradle Sync** selesai secara otomatis (pastikan kamu terhubung ke internet).
4. Hubungkan HP Android fisik kamu menggunakan kabel USB (pastikan *USB Debugging* aktif) atau gunakan Emulator bawaan Android Studio.
5. Klik tombol **Run 'app'** (ikon segitiga hijau/Play) di *toolbar* atas.
6. Aplikasi akan di-*compile* dan otomatis terbuka di perangkatmu.

---

## Tech Stack
* **Backend**: Python, FastAPI, PyTorch, Transformers, Scikit-learn
* **Frontend**: Android (Java/Kotlin)
