# Kendi FitLog'unu kur

[🇬🇧 English](../en/setup.md) · 🇹🇷 Türkçe

Bu rehber, projeyi indirdiğin andan FitLog'un **kendi** Supabase sunucunla telefonunda çalışmasına kadar her adımı anlatır. Yaklaşık 30 dakika sürer. Kullanılan tüm servislerin ücretsiz planı var.

- [Gerekenler](#gerekenler)
- [1. Kodu indir](#1-kodu-indir)
- [2. Supabase projesi oluştur](#2-supabase-projesi-oluştur)
- [3. Veritabanını kur](#3-veritabanını-kur)
- [4. Giriş ayarları](#4-giriş-ayarları)
- [5. AI fonksiyonunu yükle](#5-ai-fonksiyonunu-yükle)
- [6. Firebase (isteğe bağlı)](#6-firebase-isteğe-bağlı)
- [7. Uygulamayı bağla](#7-uygulamayı-bağla)
- [8. Çalıştır](#8-çalıştır)
- [9. Gizlilik politikası](#9-gizlilik-politikası)
- [Google ile giriş (isteğe bağlı)](#google-ile-giriş-isteğe-bağlı)
- [Kendi paket adın (isteğe bağlı)](#kendi-paket-adın-isteğe-bağlı)
- [Yönetici işlemleri](#yönetici-işlemleri)
- [Sorun giderme](#sorun-giderme)

## Gerekenler

| | Ne için | Zorunlu |
|---|---|---|
| [Android Studio](https://developer.android.com/studio) | Uygulamayı derleyip çalıştırmak (doğru JDK içinde gelir) | Evet |
| Android 13+ telefon veya emülatör | `minSdk` 33 | Evet |
| [Supabase](https://supabase.com) hesabı | Veritabanı, giriş, AI proxy | Evet |
| [Gemini API anahtarı](https://aistudio.google.com/apikey) | Öğün analizi, AI koç, alışveriş listesi | AI özellikleri için |
| [Supabase CLI](https://supabase.com/docs/guides/cli) | Tek komutla veritabanı kurulumu ve fonksiyon yükleme | Önerilir |
| [Firebase](https://console.firebase.google.com) projesi | Anlık bildirim, hata raporu, analitik | Hayır |

## 1. Kodu indir

```bash
git clone https://github.com/KaanKaya17/FitLog.git
```

`FitLog` klasörünü Android Studio'da aç ve Gradle'ın senkronize olmasını bekle. Senkronizasyon hemen çalışır; derlemek için [7. adımdaki](#7-uygulamayı-bağla) değerler gerekir.

## 2. Supabase projesi oluştur

1. [Supabase panelinde](https://supabase.com/dashboard) **New project**'e tıkla. Kullanıcılarına yakın bir bölge seç ve veritabanı şifresini güvenli bir yere kaydet.
2. Proje hazır olunca **Project Settings → API Keys** sayfasından şunları not al:
   - **Project URL** (`https://<proje-ref>.supabase.co`)
   - **anon** (ya da **publishable**) anahtar

> ⚠️ Uygulamada **asla** `service_role` / secret anahtarı kullanma. APK'ya giren her şeyi, APK'yı eline geçiren herkes okuyabilir.

## 3. Veritabanını kur

Şema [`supabase/migrations/`](../../supabase/migrations) klasöründe. Dosyalar ad sırasıyla çalışır:

| Dosya | Ne yapar |
|---|---|
| `20261118000000_base_schema.sql` | Ana tablolar: `users`, `meal_log`, `water_log`, `ai_meal_plans`, `admin_notifications` |
| `20261119000000_auth_and_rls.sql` | `users` tablosunu Supabase Auth'a bağlar, kayıt trigger'ını ve Row Level Security'yi ekler |
| `20261119000100_migrate_existing_users.sql` | Yalnızca orijinal FitLog veritabanı için; yeni projede hiçbir şey yapmaz |
| `20261120000000_features_v2.sql` | Kilo kaydı, favoriler, öğün türleri, profil hedefleri, AI kotası, hesap silme |
| `20261120000100_harden_function_grants.sql` | Fonksiyon izinlerini sıkılaştırır |

Her dosya idempotent: iki kez çalıştırmak zarar vermez.

**Yöntem A: Supabase CLI (önerilen)**

```bash
supabase login
supabase link --project-ref <proje-ref>
supabase db push
```

**Yöntem B: yalnızca panel**

**SQL Editor → New query** sayfasını aç. Her dosyanın içeriğini **yukarıdaki sırayla** yapıştır ve her birinden sonra **Run**'a bas.

Bitince **Table Editor**'da sekiz tablo görmelisin, hepsinde RLS açık: `users`, `meal_log`, `water_log`, `weight_log`, `favorite_meals`, `ai_meal_plans`, `ai_usage`, `admin_notifications`.

## 4. Giriş ayarları

**Authentication → URL Configuration → Redirect URLs** bölümünde **Add URL**'ye tıklayıp şunu ekle:

```
fitlog://auth-callback
```

Şifre sıfırlama, e-posta onayı ve Google girişi uygulamaya bu adresle döner. Bu adres eklenmezse bağlantılar uygulamayı değil tarayıcıda bir sayfayı açar.

**Authentication → Sign In / Providers → Email** altında şunlara da göz at:

- **Confirm email**: Açıksa yeni kullanıcı giriş yapmadan önce e-postadaki bağlantıya tıklamalı. Uygulama iki durumu da destekler.
- **Minimum password length**: Uygulama 8–72 karakter ister. Supabase'deki alt sınırı 8 ya da daha düşük tut; yoksa uygulamanın kabul ettiği bazı şifreleri sunucu reddeder.

> 📧 Supabase'in hazır e-posta servisi saatte yalnızca birkaç e-posta gönderir ve test içindir. Gerçek kullanıcılar kaydolmadan önce **Authentication → Emails → SMTP Settings** altından kendi SMTP sunucunu tanımla.

## 5. AI fonksiyonunu yükle

Gemini anahtarı uygulamanın içinde hiç yer almaz. Uygulama, kullanıcının oturumuyla `gemini-proxy` Edge Function'ını çağırır. Fonksiyon kullanıcıyı doğrular, günlük kotayı uygular ve isteği Gemini'ye iletir.

**Yöntem A: Supabase CLI**

```bash
supabase secrets set GEMINI_API_KEY=<gemini-anahtarın>
supabase secrets set AI_DAILY_LIMIT=60        # isteğe bağlı: kullanıcı başına günlük istek (varsayılan 60)
supabase functions deploy gemini-proxy
```

**Yöntem B: yalnızca panel**

1. **Edge Functions → Deploy a new function → Via Editor** yolunu izle. Adı `gemini-proxy` olsun. Örnek kodu [`supabase/functions/gemini-proxy/index.ts`](../../supabase/functions/gemini-proxy/index.ts) ile değiştirip yükle. **Verify JWT** açık kalsın.
2. **Edge Functions → Secrets** sayfasında anahtarını `GEMINI_API_KEY` adıyla ekle. Farklı bir günlük limit istiyorsan `AI_DAILY_LIMIT`'i de ekle.

Secret eklenmezse uygulama çalışır ama AI özellikleri sunucu hatası gösterir.

## 6. Firebase (isteğe bağlı)

Firebase anlık bildirim (FCM), Crashlytics ve Analytics için kullanılır. Analitik ve hata raporları yalnızca kullanıcı uygulamadaki onay penceresini kabul ederse açılır.

**Firebase ile**

1. [Firebase konsolunda](https://console.firebase.google.com) bir proje oluştur.
2. **Add app → Android** seçeneğine git. Paket adı `com.example.fitlog` olsun; kendi paket adını kullanacaksan [aşağıya](#kendi-paket-adın-isteğe-bağlı) bak.
3. `google-services.json` dosyasını indirip `app/` klasörüne koy. Bu dosya zaten git'e eklenmez.

**Firebase olmadan**

Hiçbir şey yapma. `app/google-services.json` yoksa derleme senin için bir yer tutucu oluşturur ve `Firebase kapalı` uyarısı yazar. Geri kalan her şey çalışır; yalnızca anlık bildirimler, hata raporları ve analitik kapalı kalır. Onay penceresi yine görünür, ama kullanıcı kabul etse bile hiçbir veri toplanmaz.

Firebase'i sonradan açmak için yer tutucuyu kendi `google-services.json` dosyanla değiştirip yeniden derle.

## 7. Uygulamayı bağla

Proje kökündeki `local.properties` dosyasını oluştur ya da düzenle. Bu dosya git'e eklenmez ve genellikle Android Studio `sdk.dir` satırıyla kendisi oluşturur. Şunları ekle:

```properties
SUPABASE_URL=https://<proje-ref>.supabase.co
SUPABASE_API_KEY=<anon-ya-da-publishable-anahtar>
# isteğe bağlı, 9. adıma bak
PRIVACY_POLICY_URL=https://example.com/gizlilik
```

Sonra **File → Sync Project with Gradle Files**'a tıkla. Supabase değerleri eksikse derleme neyi eklemen gerektiğini söyleyen bir mesajla durur. Aynı adlar ortam değişkeni olarak da çalışır; bu CI'da işe yarar.

## 8. Çalıştır

Android Studio'da bir cihaz seçip **Run ▶**'a bas ya da terminalden:

```bash
./gradlew installDebug
```

Yeni bir hesap aç; `users` tablosunda kendiliğinden bir satır oluşur. Her şeyin bağlı olduğunu görmek için bir öğün, biraz su ve bir kilo kaydı ekle.

Testleri çalıştırmak için:

```bash
./gradlew testDebugUnitTest lintDebug
```

```bash
./gradlew connectedDebugAndroidTest
```

İkinci komut bağlı bir cihaz ya da emülatör ister ve o cihazdaki yerel oturumu sıfırlar.

## 9. Gizlilik politikası

Uygulama onay penceresinden ve **Ayarlar → Gizlilik** bölümünden bir gizlilik politikasına bağlanır. Derlediğin uygulamayı başkalarıyla paylaşacaksan, özellikle Google Play'de, kendi politikan gerekir:

1. [`PRIVACY.md`](../../PRIVACY.md) dosyasını kopyalayıp köşeli parantezli alanları doldur: iletişim e-postan ve tarih. Kullanmadığın şeyleri (örneğin Firebase) çıkar.
2. Herkese açık bir yerde yayınla: GitHub Pages, bir gist ya da kendi siten.
3. `local.properties` içindeki `PRIVACY_POLICY_URL` değerini o adres yap ve yeniden derle.

İpucu: doldurduğun kopyayı git'e ekleme. `.local.md` ile biten dosyalar (örneğin `PRIVACY.local.md`) zaten git'e girmez.

`PRIVACY_POLICY_URL` tanımlı değilse bağlantı bu repodaki şablonu açar.

## Google ile giriş (isteğe bağlı)

1. [Google Cloud Console](https://console.cloud.google.com/apis/credentials)'da **Create credentials → OAuth client ID → Web application** yolunu izle.
2. **Authorized redirect URIs** alanına `https://<proje-ref>.supabase.co/auth/v1/callback` ekle.
3. Supabase'de **Authentication → Sign In / Providers → Google** sayfasını aç. Özelliği etkinleştir, **Client ID** ve **Client Secret**'ı yapıştırıp kaydet.

Uygulama Google girişini bir Custom Tab'de açar ve `fitlog://auth-callback` (PKCE) ile geri döner. Bu yüzden Android istemci kimliği ya da SHA-1 gerekmez.

## Kendi paket adın (isteğe bağlı)

Google Play `com.example.*` paket adlarını kabul etmez. Kendi sürümünü yayınlamak için:

1. `app/build.gradle.kts` içindeki `applicationId` değerini değiştir. `namespace` aynı kalabilir.
2. Firebase kullanıyorsan yeni paket adını orada kaydedip yeni bir `google-services.json` indir.
3. README'deki release imzası adımlarını uygula ve `./gradlew bundleRelease` çalıştır.

## Yönetici işlemleri

Bir yönetim paneli yok. Tam yetkiyle çalışan **SQL Editor**'ı kullan.

```sql
-- Kullanıcıyı engelle: artık hiçbir veri okuyup yazamaz
update public.users set is_blocked = true where email = 'birisi@example.com';

-- Uygulama içi bildirim gönder (ana ekranı bir sonraki açışında pencere olarak çıkar)
insert into public.admin_notifications (user_id, title, message)
select user_id, 'Merhaba', 'FitLog''a hoş geldin!' from public.users where email = 'birisi@example.com';

-- Bugünkü AI kullanımı
select u.email, a.count from public.ai_usage a join public.users u using (user_id)
where a.day = current_date order by a.count desc;
```

## Sorun giderme

| Belirti | Çözüm |
|---|---|
| Derleme `FitLog: SUPABASE_URL / SUPABASE_API_KEY tanımlı değil (not set)` mesajıyla duruyor | [7. adımdaki](#7-uygulamayı-bağla) değerleri `local.properties` dosyasına ekle |
| Anlık bildirimler hiç gelmiyor | Firebase yer tutucusu kullanılıyor. Kendi `google-services.json` dosyanı ekle ([6. adım](#6-firebase-isteğe-bağlı)) |
| Giriş "Invalid API key" hatası veriyor | Yanlış anahtar kullanılmış ya da anahtarın etrafında tırnak veya boşluk var |
| Sıfırlama ya da onay bağlantısı tarayıcıda bir sayfa açıyor | Redirect URLs listesinde `fitlog://auth-callback` yok ([4. adım](#4-giriş-ayarları)) |
| AI "sunucu hatası" veriyor | `GEMINI_API_KEY` secret'ı eksik ya da fonksiyon yüklenmemiş ([5. adım](#5-ai-fonksiyonunu-yükle)) |
| AI "günlük limit doldu" diyor | `AI_DAILY_LIMIT` değerini artır |
| Onay e-postası gelmiyor | Hazır e-posta servisinin saatlik sınırı dolmuştur. Kendi SMTP sunucunu tanımla |
| Uygulama birden sunucuya bağlanamıyor | Ücretsiz Supabase projeleri bir hafta hareketsiz kalınca duraklatılır. Panelden yeniden başlat |
| Gradle alışılmadık bir Java sürümüyle hata veriyor | Android Studio'nun kendi JDK'sını kullan (**Settings → Build Tools → Gradle → Gradle JDK**) |
