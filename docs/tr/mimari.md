# FitLog nasıl çalışır

[🇬🇧 English](../en/architecture.md) · 🇹🇷 Türkçe

Bu doküman kodun nasıl düzenlendiğini anlatır; değişiklik yapmadan önce yolunu bulman için. Kurulum için [kurulum rehberine](kurulum.md) bak.

## Genel resim

```
┌──────────────────────── Android uygulaması (Java) ─────────────────┐
│ Fragment ──► ViewModel (LiveData) ──► FitLogRepository             │
│                                         │        │                  │
│                                         │        └─► OfflineQueue ──► SyncWorker
│                                         ▼            (Room)          (WorkManager)
│                         Retrofit / OkHttp                           │
└─────────────┬───────────────────┬────────────────────┬─────────────┘
              ▼                   ▼                    ▼
     Supabase PostgREST    Supabase Auth      Edge Function gemini-proxy ──► Gemini 2.5 Flash
     (tablolar + RLS)      (e-posta, Google)  (kullanıcı, kota kontrolü)

     Open Food Facts (barkod; ayrı HTTP istemcisi, Supabase token'ı gitmez)
```

- **Özel bir backend sunucusu yok.** Uygulama doğrudan Supabase ile konuşur. Kimin neyi okuyup yazabileceğine **Row Level Security** karar verir.
- **Tek sunucu kodu bir Edge Function**: `gemini-proxy`. Gemini anahtarını cihazdan uzak tutar.

## Proje yapısı

```
app/src/main/java/com/example/fitlog/
├── *Fragment.java         ekranlar (nav_graph.xml'deki her hedef için bir tane)
├── ui/                    ViewModel'ler, grafik view'ları, ortak UI yardımcıları
├── data/                  repository, çevrimdışı kuyruk, önbellekler, arka plan işleri
├── domain/                Android'e bağımlı olmayan saf Java mantığı; tamamı birim testli
├── AuthManager.java       giriş, kayıt, PKCE, şifre sıfırlama, token yenileme
├── *Receiver.java         alarmlar: su ve öğün hatırlatıcıları, açılış
├── TodayWidget.java       ana ekran widget'ı
└── WaterTileService.java  "+250 ml" hızlı ayar kutucuğu
supabase/
├── migrations/            veritabanı şeması, ad sırasıyla uygulanır
└── functions/gemini-proxy Edge Function (Deno / TypeScript)
```

### Ekranlar → ViewModel'ler

| Ekran | Fragment | ViewModel |
|---|---|---|
| Ana sayfa (bugünün özeti) | `HomeFragment` | `HomeViewModel` |
| Öğün ekle (fotoğraf, metin, barkod) | `AddMealFragment` | `AddMealViewModel` |
| Günün öğünleri | `MealListFragment` | `MealListViewModel` |
| Su | `WaterTrackFragment` | `WaterViewModel` |
| Kilo | `WeightFragment` | `WeightViewModel` |
| Geçmiş (7/30 gün, seriler) | `HistoryFragment` | `HistoryViewModel` |
| AI koç / sohbet | `AiCoachFragment`, `AiChatFragment` | `AiCoachViewModel`, `AiChatViewModel` |
| Profil, ayarlar, hesap | `ProfileFragment`, `EditProfileFragment`, `SettingsFragment`, `ChangePasswordFragment` | `ProfileViewModel`, `AccountViewModel`, `ExportViewModel` |
| Giriş / kayıt | `LoginFragment`, `RegisterFragment` | (doğrudan `AuthManager` kullanır) |

## Veri akışı

1. **Fragment**, **ViewModel**'den gelen `LiveData<Resource<T>>`'yi izler. `Resource` yükleniyor, başarılı ya da hata durumundadır.
2. **ViewModel**, repository çağrılarını `BaseViewModel.load(...)` ile arka plan iş parçacığında (`AppExecutors`) çalıştırır.
3. Ağı bilen tek sınıf **`FitLogRepository`**'dir. PostgREST sorgularını (`SupabaseApi`) kurar, hataları `ApiException`'a çevirir ve ayrıca:
   - son okumaları önbelleğe alır (`ReadCache`); böylece ekranlar anında ve çevrimdışıyken de açılır;
   - cihaz çevrimdışı olduğu için başarısız olan yazmaları **`OfflineQueue`**'ya (Room) koyar. Ağ geri gelince **`SyncWorker`** bunları gönderir.
4. Bu nesneleri **`ServiceLocator`** birbirine bağlar. Bir DI kütüphanesi kullanılmıyor.

## Kimlik doğrulama

- Giriş **Supabase Auth** REST API'siyle (`SupabaseAuthApi`) yapılır. Access ve refresh token'lar `EncryptedSharedPreferences`'ta (`PrefManager`) saklanır. `RetrofitClient` içindeki bir OkHttp interceptor'ı access token'ın süresi dolmadan hemen önce onu yeniler; sunucu yine de 401 dönerse isteği bir kez tekrarlar.
- Google girişi, şifre sıfırlama ve e-posta onayı **PKCE** kullanır:
  1. Uygulama bir verifier ve challenge üretir (`domain/Pkce`).
  2. Bağlantıyı açar; Supabase `fitlog://auth-callback?code=…` adresine geri yönlendirir.
  3. `MainActivity` bağlantıyı yakalar, `AuthManager.handleAuthCallback` kodu oturuma çevirir.
  4. Şifre sıfırlama bağlantısı `ChangePasswordFragment`'ı "yeni şifre belirle" modunda açar.
- Biri kaydolduğunda `public.users` satırını veritabanı trigger'ı `handle_new_auth_user` oluşturur. Uygulama bu satırın kimliğini hiçbir zaman kendisi yazmaz.

## Veritabanı ve güvenlik

| Tablo | İçerik |
|---|---|
| `users` | Profil ve hedefler. `auth_id` ile `auth.users`'a bağlı |
| `meal_log` | Öğünler: ad, porsiyon, kcal, makrolar, öğün türü, zaman |
| `water_log` | Su kayıtları (ml) |
| `weight_log` | Kilo kayıtları |
| `favorite_meals` | Tek dokunuşla eklemek için kaydedilen öğünler |
| `ai_meal_plans` | AI koçun günlük planı (gün başına önbellek) |
| `ai_usage` | Kullanıcı başına günlük AI isteği (kota) |
| `admin_notifications` | Yöneticinin kullanıcıya gönderdiği mesajlar |

- **Her tabloda RLS açık.** Politikalar `user_id`'yi `current_user_id()` ile karşılaştırır. Bu fonksiyon oturumdaki Supabase kullanıcısını kendi satırına eşler. Engellenmiş bir kullanıcı (`is_blocked = true`) için hiçbir şey döndürmez, böylece o kullanıcının tüm erişimi sunucuda kesilir.
- Kullanıcı `users` tablosunun yalnızca bazı kolonlarını güncelleyebilir (kolon düzeyinde `GRANT`). Örneğin kendi engelini kaldıramaz.
- `delete_my_account()` kullanıcının tüm satırlarını ve giriş hesabını siler. Play Store uygulama içinden hesap silmeyi zorunlu tutuyor.
- `ai_usage` tablosuna yalnızca `consume_ai_quota()` yazabilir.

## AI özellikleri

Tüm Gemini çağrıları `GeminiNutritionService` → `gemini-proxy` yolundan geçer. Fonksiyon:

1. geçerli oturumu olmayan istekleri ve engellenmiş kullanıcıları reddeder;
2. isteği `AI_DAILY_LIMIT` kotasından düşer (`consume_ai_quota`);
3. yalnızca izin verilen alanları iletir, modeli sabitler (`gemini-2.5-flash`), gövde boyutunu ve çıktı uzunluğunu sınırlar.

| Özellik | Girdi | Çıktı |
|---|---|---|
| Öğün analizi | Fotoğraf (base64) ya da metin | JSON: ad, porsiyon, kcal, makrolar, güven |
| Günlük plan | Profil ve son 7 gün (`CoachContext`) | Metin plan; `ai_meal_plans` tablosuna kaydedilir |
| Koç sohbeti | Konuşma ve bağlam | Metin; geçmiş yalnızca cihazın belleğinde kalır |
| Alışveriş listesi | Bugünün planı | Gruplu, paylaşılabilir liste |

Prompt'lar `res/values*/strings.xml` içinde durur; böylece her dil kendi dilinde yanıt alır.

## Arka plan işleri ve sistem entegrasyonu

| Ne | Nerede | Nasıl |
|---|---|---|
| Su hatırlatıcıları | `WaterReminderReceiver`, `ReminderScheduler` | AlarmManager; sessiz saatlere uyar |
| Öğün hatırlatıcıları | `MealReminderReceiver` | 10:00, 14:00 ve 20:00'de alarm; yalnızca o öğün hâlâ girilmemişse |
| Haftalık özet | `WeeklySummaryWorker` | WorkManager, pazar 20:00 |
| Çevrimdışı senkronizasyon | `SyncWorker` | WorkManager, ağ varken çalışır |
| Yeniden başlatma sonrası kurulum | `BootReceiver` | |
| Hızlı ayar kutucuğu | `WaterTileService` | Uygulamayı açmadan 250 ml ekler |
| Ana ekran widget'ı | `TodayWidget` | Kalori ve su ilerlemesi, +250 ml düğmesi |
| Anlık bildirimler | `MyFirebaseMessagingService` | FCM; token `users.fcm_token` alanına kaydedilir |

## Saf mantık (`domain/`)

Bu sınıfların Android bağımlılığı yok ve birim testleriyle kapsanıyorlar:

- `NutritionCalculator`: Mifflin-St Jeor BMR × aktivite seviyesi → kalori, makro ve su hedefleri
- `DateRanges`: kullanıcının yerel gününü sorgular için UTC aralığına çevirir
- `StreakCalculator`, `HistoryAggregator`: seriler, ortalamalar ve grafik verisi
- `FoodMath`: porsiyon ölçekleme
- `CsvExporter`: veri dışa aktarma (ZIP içinde CSV)
- `Pkce`, `MealType`, `WeeklySummary`

## Dil ve tema

- Diller: İngilizce (`values`), Türkçe (`values-tr`), Yunanca (`values-el`). `LocaleHelper` dili uygulama içinden değiştirir. Bir metnin çevirisi eksikse lint derlemeyi durdurur; bu yüzden her yeni metni üç dosyaya da ekle.
- Açık, koyu ve sistem teması. Grafikler temaya uyan hafif özel view'lardır (`BarChartView`, `LineChartView`).

## Gizlilik

- Firebase Analytics ve Crashlytics **varsayılan olarak kapalı** (manifest'te). `ConsentManager` bunları yalnızca kullanıcı onay verirse açar; kullanıcı Ayarlar → Gizlilik'ten istediği zaman değiştirebilir.
- Kendi `google-services.json` dosyan yoksa Gradle bir yer tutucu üretir ve `BuildConfig.FIREBASE_CONFIGURED = false` olur. Uygulama o durumda FCM'i atlar, analitik ve hata raporlarını hiç açmaz.
- Kuruluma özel tüm değerler (`SUPABASE_URL`, `SUPABASE_API_KEY`, `PRIVACY_POLICY_URL`, release imzası) `local.properties` dosyasından ya da ortam değişkenlerinden gelir. Bkz. `app/build.gradle.kts`.
- Barkod sorguları ayrı bir HTTP istemcisi kullanır; Supabase token'ı Open Food Facts'e hiç gitmez.
- Release derlemesinde debug logları R8 tarafından silinir.

## Testler ve CI

| Tür | Konum | Komut |
|---|---|---|
| Birim testleri (domain, ayrıştırma, `MockWebServer` ile repository) | `app/src/test` | `./gradlew testDebugUnitTest` |
| Arayüz testleri (Espresso: giriş akışları, derin bağlantılar) | `app/src/androidTest` | `./gradlew connectedDebugAndroidTest` |
| Lint (eksik çeviriler dahil) | | `./gradlew lintDebug` |

GitHub Actions ([`.github/workflows/android.yml`](../../.github/workflows/android.yml)) `master`'a yapılan her push'ta ve her pull request'te birim testlerini, lint'i ve debug derlemesini çalıştırır. Yer tutucu ayarlar kullandığı için hiçbir gizli anahtar gerekmez.

## Katkıda bulunma

1. Repoyu fork'la ve bir dal oluştur.
2. İş mantığını mümkünse `domain/` içine koy ve bir birim testi ekle.
3. Yeni metinleri üç dilde de ekle.
4. Veritabanı değişikliklerini **yeni** bir migration dosyasına yaz. Çalışmış bir dosyayı asla değiştirme ve dosyayı idempotent tut (`if not exists`, `drop policy if exists`, …).
5. Pull request açmadan önce `./gradlew testDebugUnitTest lintDebug` çalıştır.
