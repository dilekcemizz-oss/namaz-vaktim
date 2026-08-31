# Namaz Vakti - Android Studio Projesi

Bu proje Kotlin + Jetpack Compose ile yazılmıştır ve gönderdiğiniz ekran görüntüsündeki
gün batımı temasını (mor-turuncu gradyan gökyüzü, cami silüetleri, camlı vakit kartları,
dairesel geri sayım) birebir yeniden üretir.

## Açma / Çalıştırma

1. Android Studio'yu açın → **Open** → bu `android` klasörünü seçin.
2. Gradle senkronizasyonunun bitmesini bekleyin (ilk açılışta Gradle Wrapper'ı internetten indirir).
3. Bir emülatör veya gerçek cihaz seçip **Run ▶** tuşuna basın.

Minimum SDK: 26 (Android 8.0) · Hedef SDK: 34

## Proje Yapısı

```
android/
├── app/
│   ├── build.gradle.kts          # Bağımlılıklar (Compose, Material3)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/namazvakti/
│       │   └── MainActivity.kt   # Tüm ekran burada (tek dosya)
│       └── res/
│           ├── values/           # Renkler, string'ler, tema
│           └── mipmap.../        # Uygulama ikonu
├── build.gradle.kts               # Kök proje ayarları
└── settings.gradle.kts
```

## MainActivity.kt içinde neler var?

- `AppColors` — ekran görüntüsündeki gün batımı paletinin hex karşılıkları
- `samplePrayerTimes()` — **örnek/statik** vakit verileri (İmsak, Güneş, Öğle, İkindi,
  Akşam, Yatsı). Gerçek uygulamada bu veriler bir API'den çekilmelidir (aşağıya bakın).
- `CountdownRing` — `Canvas` ile çizilen dairesel geri sayım göstergesi, saniyede bir
  gerçek zamanlı güncellenir.
- `SkylineBackground` — Ayasofya/Sultanahmet esintili cami silüetleri ve yıldızlar,
  `Canvas` üzerine vektörel olarak çizilmiştir (harici görsel dosyası gerekmez).
- `PrayerCard` — her vakit için camsı (glassmorphism) kart; aktif/sıradaki vakit
  pembe-mor parıltıyla vurgulanır.
- `BottomNavBar` — Home / Kıble / Dua / Takvim / Ayarlar sekmeleri.

## Takvim artık ana sayfayla birebir tutarlı + ay otomatik güncelleniyor

Daha önce Takvim ekranı Aladhan'ın `calendar` endpoint'ini (tek istekte tüm
ay) kullanıyordu; bu endpoint bazı durumlarda ana sayfanın kullandığı
`timings` endpoint'inden farklı sonuçlar döndürebiliyordu. Artık **ikisi de
aynı tekniği** kullanıyor: `fetchMonthForCity()` ayın her günü için ayrı
ayrı `fetchPrayersForCity()` çağırır (istekler paralel gönderilir), böylece
takvimdeki her satır ana sayfadaki hesaplamayla birebir aynıdır.

Ayrıca Takvim ekranı artık **ayı otomatik takip eder**: `CalendarScreen`
içinde yıl/ay bir state olarak tutulur ve her dakika gerçek tarihle
karşılaştırılır; ay değiştiğinde (örn. gece yarısı Ağustos'tan Eylül'e
geçildiğinde) veri otomatik olarak yeni ayınkiyle yeniden çekilir. Aynı
şekilde ana sayfadaki vakitler de gün değiştiğinde (gece yarısı) otomatik
tazelenir.

## Uygulama adı ve marka

Uygulama adı **"Ezan Vaktim - Namaz & Kıble"** olarak güncellendi (launcher adı ve
uygulama içi başlık). Başlık, GitHub'daki Google Fonts deposundan indirilen
**Marcellus** (zarif, klasik bir serif font — `res/font/marcellus_regular.ttf`)
ile gösteriliyor; `MainActivity.kt` içinde `BrandFont` olarak tanımlı ve
`TopBar()` composable'ında kullanılıyor.

## Sabit alt menü ve tam ekran düzen

- **Alt menü artık gerçekten sabit**: Önceden `Column`'un bir parçasıydı, artık
  `Box` üzerinde ayrı bir katman (`Modifier.align(Alignment.BottomCenter)`)
  olarak duruyor. İçerik (Home'daki kart listesi, Kıble/Ayarlar/Takvim/Kur'an
  ekranları) ne kadar kaydırılırsa kaydırılsın menü asla hareket etmez.
- **Kenardan kenara (edge-to-edge) tam ekran**: `enableEdgeToEdge()` çağrısıyla
  arka plan fotoğrafı artık durum çubuğunun (status bar) ve gezinme çubuğunun
  (navigation bar) arkasına kadar uzanıyor; metin içeriği ise `WindowInsets`
  ile bu çubuklarla çakışmayacak şekilde otomatik olarak içeri kaydırılıyor.
- Kıble ekranına, küçük ekranlarda içerik taşmasın diye dikey kaydırma eklendi.

## Namaz vakitleri artık Diyanet'in resmi hesaplamasından geliyor

`awqatsalah.diyanet.gov.tr` (Diyanet'in kendi API'si) kayıt + kullanıcı adı/şifre +
JWT girişi gerektiriyor ve normal hesaplarda günde sadece 5 istek/endpoint gibi
çok sıkı bir sınırı var — bu, her kullanıcının cihazının doğrudan çağırması için
değil, bir sunucunun bir kez çekip önbelleğe alması için tasarlanmış.

Bunun yerine **Aladhan API**'nin `method=13` seçeneğini kullanıyoruz — bu,
Diyanet'in resmi açı parametreleriyle (Fecr 18°, Yatsı 17°, `school=0`) çalışan,
**kayıt/anahtar gerektirmeyen** açık bir servis (Kur'an mealinde kullandığımız
`api.alquran.cloud` ile aynı ekip/altyapı — `api.aladhan.com`).

- `PrayerCalculator.kt` içindeki `fetchPrayersForCity()` günlük vakitleri,
  `fetchMonthForCity()` ise takvim ekranı için tüm ayı **tek istekte** çeker.
- API'ye ulaşılamazsa (çevrimdışı, geçici kesinti vb.) **sessizce** daha önceki
  yerel astronomik hesaplamaya (`getPrayersForCityLocal`) geri döner — uygulama
  hiçbir zaman boş ekran göstermez.
- Hangi kaynağın kullanıldığı (Diyanet API / tahmini hesaplama) Takvim
  ekranının altında kullanıcıya bildirilir.

## Alt sekmeler artık gerçekten çalışıyor

- **Kıble** — Ayarlar'dan seçilen ile göre Kâbe açısını hesaplar (great-circle
  bearing), `SensorManager` (accelerometer + magnetometer) ile cihazın gerçek
  pusula yönünü okur ve altın renkli ibre/Kâbe rozetini canlı döndürür.
  Hizalanma durumunu ("Doğru Yöne Hizalandı" / "Hizalanmadı") gösterir.
- **Kur'an** — 114 surenin listesi + her surenin ayetleri (Arapça metin ve
  Türkçe meal) **canlı olarak api.alquran.cloud üzerinden** çekilir; metin
  uygulamaya gömülü değildir, telif nedeniyle her açılışta gerçek zamanlı
  sağlanır. Her ayetin yanındaki oynat düğmesi Mishary Alafasy kıraatiyle
  gerçek ses akışını (CDN) başlatır. Bu ekran internet bağlantısı gerektirir.
- **Takvim** — Seçili ile göre aylık namaz vakti tablosu, her gün için ayrı
  astronomik hesaplama yapılır, bugünün satırı vurgulanır.
- **Ayarlar** — Türkiye'nin 81 ili arasından arama yaparak konum seçilir.
  Seçim `SharedPreferences` ile cihazda kalıcı olarak saklanır; uygulama
  her açıldığında en son seçilen il otomatik yüklenir, farklı bir il
  seçildiğinde tüm ekranlar (Home, Kıble, Takvim) o ilin vakitlerine göre
  anında güncellenir.

## Kur'an ekranı nasıl çalışıyor?

`QuranApi.kt` dosyası `api.alquran.cloud` REST API'sine `HttpURLConnection`
ile bağlanır (ek kütüphane bağımlılığı yok):
- `fetchSurahList()` → 114 surenin meta verisini (isim, ayet sayısı vb.) çeker
- `fetchSurahWithMeal(number, edition)` → seçilen surenin Arapça metnini
  (`quran-uthmani` edisyonu) ve Türkçe mealini (`tr.diyanet` / yedek olarak
  `tr.yazir`, `tr.ates`) paralel çeker
- `ayahAudioUrl(globalAyahNumber)` → her ayetin gerçek kıraat sesinin CDN
  bağlantısını üretir (`cdn.islamic.network/quran/audio/...`)

`MainActivity.kt` içindeki `QuranScreen()` composable'ı bu verileri
gösterir; ses çalma için Android'in yerleşik `MediaPlayer` sınıfı kullanılır.
`AndroidManifest.xml`'e bu nedenle `INTERNET` izni eklendi.

**Not:** Bu ekran, mealikerim.com gibi belirli bir yorumcunun telif korumalı
tefsir tarzı mealini değil, açık bir API üzerinden sunulan standart Diyanet
mealini kullanır. Farklı bir meal/kıraat tercih ediyorsanız `QuranApi.kt`
içindeki `TR_EDITION_CANDIDATES` ve `AUDIO_EDITION` sabitlerini
değiştirmeniz yeterli.

## Namaz vakitleri nasıl hesaplanıyor?

`PrayerCalculator.kt` dosyasındaki `calcSunTimesMinutes()` fonksiyonu, NOAA
güneş doğuş/batış denklemlerine dayanan astronomik bir hesaplama yapar:
il'in enlem/boylamına ve seçilen tarihe göre güneşin konumunu (deklinasyon,
zaman denklemi) hesaplayıp imsak (~18° fecr), güneş doğuşu, öğle (gerçek
zeval), ikindi (Hanefi mezhebi gölge oranı = 2), akşam (gün batımı) ve
yatsı (~17°) vakitlerini üretir.

**Önemli:** Bu hesaplama birkaç dakikalık sapma içerebilir ve resmi bir
kaynak değildir. Kesin vakitler için Diyanet İşleri Başkanlığı'nın açık
verisi veya Aladhan API gibi resmi/üçüncü parti bir kaynakla entegrasyon
önerilir — bu durumda `getPrayersForCity()` fonksiyonu bir ağ isteğiyle
değiştirilebilir.

## Gerçek namaz vakitleri nasıl eklenir?

Şu an vakitler `samplePrayerTimes()` içinde sabit (statik) olarak tanımlı. Gerçek ve
güncel vakitler için önerilen seçenekler:

1. **Diyanet İşleri Başkanlığı** açık verisi / gayri resmi sarımsak API'leri
2. **Aladhan API** (`https://api.aladhan.com`) — şehir/enlem-boylama göre ücretsiz JSON döner
3. Telefon konumuna göre (FusedLocationProvider) enlem/boylam alıp yerel bir
   hesaplama kütüphanesi (örn. `Adhan` Kotlin/Java kütüphanesi) ile hesaplama

Bunlardan birini eklemek isterseniz `samplePrayerTimes()` fonksiyonunu bir
`ViewModel` + ağ isteği (Retrofit/Ktor) ile değiştirmemiz yeterli olur — isterseniz
bu entegrasyonu birlikte ekleyebiliriz.

## Notlar

- Uygulama ikonu basit bir vektör placeholder'dır; isterseniz kendi logonuzla
  değiştirebiliriz.
- Kıble, Dua, Takvim, Ayarlar sekmeleri şu an sadece görsel olarak seçilebilir;
  içerikleri henüz eklenmedi.
- `/mnt/user-data/outputs` içindeki `index.html` dosyası bu tasarımın tarayıcıda
  hızlıca test edebileceğiniz web/HTML versiyonudur — Android'e geçmeden önce
  tasarımı orada önizleyebilirsiniz.
