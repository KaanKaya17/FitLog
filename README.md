# FitLog 🏋️‍♂️🍏

FitLog, modern ve kapsamlı bir Android akıllı sağlık ve beslenme takip uygulamasıdır. Gelişmiş Üretken Yapay Zeka (AI) yetenekleri ve güvenilir sunucu-altyapı teknolojilerini kullanarak kişiselleştirilmiş bir takip deneyimi sunar.

## 🌟 Öne Çıkan Özellikler

- **Yapay Zeka Destekli Öğün Analizi:** İsteseniz yemeğinizin fotoğrafını çekin, isterseniz metin olarak girin; FitLog **Google Gemini Vision AI** alt yapısı sayesinde o yemeğin makrolarını (Kalori, Protein, Karbonhidrat, Yağ) ve servis bilgilerini otomatik olarak hesaplar!
- **Dinamik Günlük Dashboard:** Günlük kalori hedefinizi, içtiğiniz su miktarını ve detaylı makro özetlerini son derece canlı ve modern bir ana ekran üzerinden anlık takip edin.
- **Akıllı Su Takibi:** Hedefinize özel olarak ilerleyen dinamik su takip eklentisi.
- **AI Koç Desteği:** Hedeflerinize, kilonuza ve genel sağlık profilinize dinamik olarak uyum sağlayan Gemini tabanlı Dijital Asistan sayesinde kişiye özel menü ve tavsiye planları.
- **Anlık Bildirimler (Push Notifications):** Firebase Cloud Messaging (FCM) altyapısı sayesinde uygulamanız arkaplanda bile olsa güncellemelerden ve hatırlatmalardan haberdar olun.
- **Güvenli Bulut Senkronizasyonu:** Tüm verileriniz arka planda **Supabase** mimarisi sayesinde anlık olarak sunucuya yedeklenir. Veri kaybı derdi yaşamazsınız.

## 🛠️ Kullanılan Teknolojiler

- **Platform:** Android (Java)
- **Backend & Veritabanı:** [Supabase](https://supabase.com/) (PostgreSQL & REST API)
- **Yapay Zeka Servisleri:** [Google Gemini API](https://aistudio.google.com/) (`gemini-2.5-flash` Multimodal Vision + Metin)
- **Ağ İstekleri:** Retrofit2 & OkHttp3
- **Analiz & Hata Ayıklama:** Firebase Analytics & Firebase Crashlytics
- **Bildirim Servisi:** Firebase Cloud Messaging (FCM)
- **Görsel Dinamikler:** Lottie by Airbnb & Glide

## 🚀 Kurulum & Başlangıç

### Gereksinimler
1. Android Studio
2. Bir Supabase projesi (URL + **anon / publishable** key)
3. Google Gemini API Key (yalnızca sunucuda kullanılır)
4. Firebase konfigürasyon dosyası (`google-services.json`)
5. [Supabase CLI](https://supabase.com/docs/guides/cli)

### 1. Veritabanı (Supabase)
Kimlik doğrulama **Supabase Auth** ile yapılır; şifreler uygulamada veya `public.users` tablosunda tutulmaz.
Her tabloda Row Level Security açıktır: her kullanıcı yalnızca kendi verisini görebilir.

```bash
supabase link --project-ref <PROJE_REF>
supabase db push
```

`supabase/migrations/` altındaki dosyalar sırasıyla:
- `..._auth_and_rls.sql`: `users.auth_id` kolonu, kayıt trigger'ı ve RLS politikaları.
- `..._migrate_existing_users.sql`: Eski kullanıcıları Supabase Auth'a taşır (şifreleri bcrypt ile hash'ler) ve düz metin `password_hash` kolonunu siler. **Çalıştırmadan önce yedek alın.**

### 2. Gemini proxy (Edge Function)
Gemini API anahtarı APK'ya **gömülmez**. Uygulama, kullanıcının oturum token'ı ile `gemini-proxy` fonksiyonunu çağırır; anahtar Supabase secret olarak sunucuda durur.

```bash
supabase secrets set GEMINI_API_KEY=<YENI_GEMINI_KEY>
supabase functions deploy gemini-proxy
```

### 3. Uygulama
Proje kökündeki **`local.properties`** dosyasına (git'e eklenmez) yalnızca herkese açık değerleri yazın:

```properties
SUPABASE_URL=https://<PROJE_REF>.supabase.co
SUPABASE_API_KEY=<SUPABASE_ANON_KEY>
```

Firebase Console'dan indirdiğiniz `google-services.json` dosyasını `app/` klasörüne koyun (bu dosya da git'e eklenmez).

> ⚠️ Buraya **asla** `service_role` anahtarı veya Gemini anahtarı koymayın; `BuildConfig` içindeki her değer APK'dan okunabilir.

## 📱 Ekran Görüntüleri ve Arayüz
*(Arayüz resimlerinizi veya projenizin demolarını daha sonra bu alana ekleyebilirsiniz.)*

## 💡 Geliştirici Notu
FitLog, modern yazılım mimarisi, güvenli ağ çağrıları ve yüksek çözünürlüklü asenkron işlemleri (Multi-threading + Retrofit) baz alarak ölçeklenebilir ve profesyonel bir tasarımla geliştirilmiştir. Hem sağlıklı kalmanız hem de harika bir teknoloji deneyimi yaşamanız dileğiyle!
