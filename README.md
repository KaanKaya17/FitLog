# FitLog 🏋️‍♂️🍏

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

**AI Koç**
- Profilin ve son 7 günün verisiyle kişiselleştirilmiş günlük plan
- Koçla **sohbet** (geçmiş yalnızca cihazda, bellekte tutulur)

**Hesap ve uygulama**
- E-posta/şifre ve **Google ile giriş** (Supabase Auth, OAuth + PKCE), şifre sıfırlama
- Uygulama içinden **hesap silme** (tüm veriler sunucudan silinir)
- **Açık / koyu / sistem** teması; Türkçe, İngilizce, Yunanca
- **Çevrimdışı kayıt**: su, öğün ve kilo bağlantı yokken cihazda kuyruğa alınır, bağlantı gelince otomatik gönderilir

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

### 3. Google ile giriş (isteğe bağlı)
1. Google Cloud Console'da bir OAuth istemcisi (Web) oluşturun.
2. Supabase → Authentication → Providers → **Google**'ı açıp istemci kimliğini girin.
3. Supabase → Authentication → URL Configuration → **Redirect URLs** listesine `fitlog://auth-callback` ekleyin.

### 4. Uygulama
Proje kökündeki **`local.properties`** dosyasına (git'e eklenmez) yalnızca herkese açık değerleri yazın:

```properties
SUPABASE_URL=https://<PROJE_REF>.supabase.co
SUPABASE_API_KEY=<SUPABASE_ANON_KEY>
```

Firebase Console'dan indirdiğiniz `google-services.json` dosyasını `app/` klasörüne koyun (bu dosya da git'e eklenmez).

> ⚠️ Buraya **asla** `service_role` anahtarı veya Gemini anahtarı koymayın; `BuildConfig` içindeki her değer APK'dan okunabilir.

### Testler
```bash
./gradlew testDebugUnitTest lintDebug
```
Birim testleri tarih aralıklarını, kalori/makro hesabını, seri ve geçmiş toplamalarını, Open Food Facts ayrıştırmasını, Gemini yanıt ayrıştırmasını, PKCE'yi ve `MockWebServer` ile repository'nin gönderdiği gerçek HTTP isteklerini (çevrimdışı kuyruk dahil) doğrular.

## 📱 Ekran Görüntüleri
*(Yakında)*
