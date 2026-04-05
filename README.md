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
2. Uygulama veritabanı için Supabase Proje URL'i ve API Key'i
3. Yapay Zeka için Google Gemini API Key
4. Firebase konfigürasyon dosyası (`google-services.json`) 

### Yapılandırma
Aşağıdaki gibi proje ana dizininde bulunan özel **`local.properties`** dosyanıza kendi anahtarlarınızı yerleştirmelisiniz:

```properties
SUPABASE_URL="SİZİN_SUPABASE_URL_ADRESINIZ"
SUPABASE_API_KEY="SİZİN_SUPABASE_ANON_KEY_ŞİFRENİZ"
GEMINI_API_KEY="SİZİN_GEMINI_KEY_ŞİFRENİZ"
```
Ayrıca `app/src/main/assets/config.properties` içerisine AI servislerinin API keyler'i tanımlanarak çalışma mantığı aktifleştirilmelidir.

## 📱 Ekran Görüntüleri ve Arayüz
*(Arayüz resimlerinizi veya projenizin demolarını daha sonra bu alana ekleyebilirsiniz.)*

## 💡 Geliştirici Notu
FitLog, modern yazılım mimarisi, güvenli ağ çağrıları ve yüksek çözünürlüklü asenkron işlemleri (Multi-threading + Retrofit) baz alarak ölçeklenebilir ve profesyonel bir tasarımla geliştirilmiştir. Hem sağlıklı kalmanız hem de harika bir teknoloji deneyimi yaşamanız dileğiyle!
