Sunucu (FastAPI) - Hızlı başlatma

1. Sanal ortam oluşturun ve bağımlılıkları yükleyin:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

2. Sunucuyu başlatın:

```bash
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Android emulator'da uygulamanın `10.0.2.2:8000` adresine bağlanması beklenir.

3. Aktivasyon örnekleri:

-- Aktivasyon (POST):

```bash
curl -X POST "http://localhost:8000/activate?key=YOUR-DEVICE-KEY"
```

-- Doğrulama (GET):

```bash
curl "http://localhost:8000/validate?key=YOUR-DEVICE-KEY"
```

4. HTTPS (self-signed) hızlı test:

```bash
# Sunucu dizinine girin
cd server
# self-signed sertifika oluştur
openssl req -x509 -newkey rsa:4096 -nodes -out cert.pem -keyout key.pem -days 365 -subj '/CN=localhost'
# HTTPS ile başlat
uvicorn main:app --reload --host 0.0.0.0 --port 8443 --ssl-certfile cert.pem --ssl-keyfile key.pem
```

Not: Self-signed sertifikalar emulator/ci ortamlarda güven problemi çıkarabilir; gerçek dağıtım için Let's Encrypt veya benzeri TLS sağlayıcısı kullanın.
