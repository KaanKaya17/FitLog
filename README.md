# FitLog 🏋️‍♂️🍏

[![Android CI](https://github.com/KaanKaya17/FitLog/actions/workflows/android.yml/badge.svg)](https://github.com/KaanKaya17/FitLog/actions/workflows/android.yml)

FitLog, yapay zeka destekli bir Android beslenme, su ve kilo takip uygulamasıdır. Öğünlerini fotoğrafla, metinle ya da barkodla kaydedersin; hedeflerini profiline göre hesaplar, ilerlemeni grafiklerle gösterir ve son 7 gününü bilen bir AI koçla konuşmanı sağlar.

## 🌟 Özellikler

**Öğün takibi**
- Fotoğraf, metin veya **barkod** (Open Food Facts) ile besin değeri analizi (Gemini 2.5 Flash)
- Öğün türü (kahvaltı / öğle / akşam / ara öğün) ve listede türe göre gruplama
- **Favoriler ve son yenenler** ile tek dokunuşla ekleme
- Sonucu kaydetmeden önce **porsiyon** (½× – 2×) ve değerleri elle düzenleme
- Geçmiş bir güne öğün ekleme

**Hedefler ve ilerleme**
- Profile göre otomatik **kalori, makro ve su hedefi** (Mifflin-St Jeor + aktivite seviyesi)
- **Kilo takibi**: geçmiş, çizgi grafik, hedef kiloya kalan ve ilerleme çubuğu
- **Geçmiş**: 7/30 günlük kalori ve su grafikleri, ortalamalar, güncel/en iyi **seri** ve su hedefi serisi

**Su**
- Hızlı ekleme, **geri al**, günü sıfırlama
- Ayarlanabilir hatırlatma sıklığı ve **sessiz saatler**; cihaz yeniden başlayınca hatırlatıcı yeniden kurulur
- Bildirim panelinde **"+250 ml su" hızlı ayar kutucuğu** (uygulamayı açmadan ekleme)
- **Ana ekran widget'ı**: bugünün kalori ve su ilerlemesi, tek dokunuşla +250 ml

**Hatırlatıcılar**
- **Öğün hatırlatıcısı**: kahvaltı, öğle ve akşam yemeği girilmemişse 10:00 / 14:00 / 20:00'de bildirim (sessiz saatlere uyar)

**AI Koç**
- Profilin ve son 7 günün verisiyle kişiselleştirilmiş günlük plan
- Koçla **sohbet** (geçmiş yalnızca cihazda, bellekte tutulur)
- Plandan **alışveriş listesi** (kategoriye göre gruplu, paylaşılabilir)

**Hesap ve uygulama**
- E-posta/şifre ve **Google ile giriş** (Supabase Auth, OAuth + PKCE)
- **Şifre sıfırlama** ve **e-posta onayı**: e-postadaki bağlantı uygulamayı açar (PKCE, `fitlog://auth-callback`)
- **Gizlilik**: analitik ve hata raporları yalnızca kullanıcı onayıyla açılır (Ayarlar → Gizlilik); [gizlilik politikası](PRIVACY.md)
- Uygulama içinden **hesap silme** (tüm veriler sunucudan silinir)
- **Açık / koyu / sistem** teması; Türkçe, İngilizce, Yunanca
- **Çevrimdışı çalışma**: su, öğün ve kilo bağlantı yokken kuyruğa alınır ve sonra otomatik gönderilir; ekranlar son kaydedilen verilerle açılır
- **Haftalık özet bildirimi** (her pazar 20:00)
- **Verilerini dışa aktar**: öğün, su ve kilo geçmişi CSV olarak (ZIP) paylaşılabilir

## 🔐 Güvenlik

- Şifreler yalnızca Supabase Auth'ta (bcrypt) tutulur; uygulama şifre görmez/karşılaştırmaz.
- Her tabloda **Row Level Security**: kullanıcı yalnızca kendi satırlarını okuyup yazabilir; engellenen kullanıcıların erişimi sunucuda kesilir.
- **Gemini anahtarı APK'da değildir**; `gemini-proxy` Edge Function'ında secret olarak durur. Fonksiyon oturumu doğrular, modeli sabitler, istek boyutunu ve günlük kullanım kotasını sınırlar.
- Token'lar `EncryptedSharedPreferences` ile saklanır; release build'de debug logları R8 ile silinir.
- Barkod sorguları ayrı bir HTTP istemcisiyle yapılır; Supabase token'ı üçüncü tarafa gitmez.

## 🏗️ Mimari

```
ui (Fragment) ──► ViewModel (LiveData) ──► FitLogRepository ──► Retrofit/OkHttp ──► Supabase (PostgREST, Auth, Edge Functions)
                                                 │                                   └─► Open Food Facts
                                                 └─► OfflineQueue (Room) ──► SyncWorker (WorkManager)
domain/  saf, test edilebilir mantık: DateRanges, NutritionCalculator, StreakCalculator, HistoryAggregator, FoodMath, Pkce
```

- **Saat dilimi:** "Bugün" kullanıcının yerel günüdür; yerel gün sınırları UTC'ye çevrilerek sorgulanır.
- **Grafikler:** Harici kütüphane yerine temaya uyumlu hafif özel `View`'lar (`BarChartView`, `LineChartView`).

## 🛠️ Teknolojiler

Android (Java, minSdk 33) · Supabase (PostgreSQL, Auth, Edge Functions) · Gemini 2.5 Flash · Retrofit2 & OkHttp3 · AndroidX Lifecycle, Room, WorkManager, Navigation · ML Kit Code Scanner · Firebase Analytics, Crashlytics, FCM · Lottie

## 🚀 Kurulum

### Gereksinimler
1. Android Studio
2. Bir Supabase projesi (URL + **anon / publishable** key)
3. Google Gemini API Key (yalnızca sunucuda kullanılır)
4. Firebase konfigürasyon dosyası (`google-services.json`)
5. [Supabase CLI](https://supabase.com/docs/guides/cli)

### 1. Veritabanı
```bash
supabase link --project-ref <PROJE_REF>
supabase db push
```

`supabase/migrations/` sırasıyla:
- `..._auth_and_rls.sql`: `users.auth_id`, kayıt trigger'ı ve RLS politikaları.
- `..._migrate_existing_users.sql`: Eski kullanıcıları Supabase Auth'a taşır ve düz metin `password_hash` kolonunu siler. **Önce yedek alın.**
- `..._features_v2.sql`: Kilo kayıtları, favori öğünler, öğün türü, profil hedefleri, AI kotası ve `delete_my_account()`.

### 2. Gemini proxy (Edge Function)
```bash
supabase secrets set GEMINI_API_KEY=<GEMINI_KEY>
supabase secrets set AI_DAILY_LIMIT=60   # isteğe bağlı, kullanıcı başına günlük istek
supabase functions deploy gemini-proxy
```

### 3. Auth yönlendirmesi ve Google ile giriş
1. Supabase → Authentication → URL Configuration → **Redirect URLs** listesine `fitlog://auth-callback` ekleyin. Şifre sıfırlama, e-posta onayı ve Google girişi bu adresle uygulamaya döner.
2. (İsteğe bağlı) Google Cloud Console'da bir OAuth istemcisi (Web) oluşturun.
3. (İsteğe bağlı) Supabase → Authentication → Providers → **Google**'ı açıp istemci kimliğini girin.

### 4. Uygulama
Proje kökündeki **`local.properties`** dosyasına (git'e eklenmez) yalnızca herkese açık değerleri yazın:

```properties
SUPABASE_URL=https://<PROJE_REF>.supabase.co
SUPABASE_API_KEY=<SUPABASE_ANON_KEY>
```

Firebase Console'dan indirdiğiniz `google-services.json` dosyasını `app/` klasörüne koyun (bu dosya da git'e eklenmez).

> ⚠️ Buraya **asla** `service_role` anahtarı veya Gemini anahtarı koymayın; `BuildConfig` içindeki her değer APK'dan okunabilir.

### 5. Release imzası (Play Store)
Keystore'u repoya koymayın (`*.jks` git'e eklenmez). Yolunu ve şifrelerini `local.properties`'e ya da CI'da aynı adlı ortam değişkenlerine yazın:

```properties
RELEASE_STORE_FILE=keystore/fitlog-release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=fitlog
RELEASE_KEY_PASSWORD=...
```

Tanımlı değilse release derlemesi imzasız üretilir. `./gradlew bundleRelease` ile Play Store için AAB oluşturulur.

### Testler
```bash
./gradlew testDebugUnitTest lintDebug
```
Arayüz testleri (bağlı cihaz veya emülatör gerekir; cihazdaki yerel oturumu sıfırlar):
```bash
./gradlew connectedDebugAndroidTest
```

Birim testleri tarih aralıklarını, kalori/makro hesabını, seri ve geçmiş toplamalarını, Open Food Facts ayrıştırmasını, Gemini yanıt ayrıştırmasını, PKCE'yi ve `MockWebServer` ile repository'nin gönderdiği gerçek HTTP isteklerini (çevrimdışı kuyruk dahil) doğrular.

## 📱 Ekran Görüntüleri
*(Yakında)*
