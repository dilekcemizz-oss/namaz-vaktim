package com.example.namazvakti

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

// ====================================================================
// TÜRKİYE 81 İL - il merkezi enlem/boylam koordinatları
// ====================================================================
data class City(val name: String, val lat: Double, val lon: Double)

val TR_CITIES: List<City> = listOf(
    City("Adana", 37.0000, 35.3213), City("Adıyaman", 37.7648, 38.2786),
    City("Afyonkarahisar", 38.7507, 30.5567), City("Ağrı", 39.7191, 43.0503),
    City("Amasya", 40.6499, 35.8353), City("Ankara", 39.9334, 32.8597),
    City("Antalya", 36.8969, 30.7133), City("Artvin", 41.1828, 41.8183),
    City("Aydın", 37.8560, 27.8416), City("Balıkesir", 39.6484, 27.8826),
    City("Bilecik", 40.1451, 29.9791), City("Bingöl", 38.8855, 40.4966),
    City("Bitlis", 38.4006, 42.1095), City("Bolu", 40.5760, 31.5788),
    City("Burdur", 37.4613, 30.1975), City("Bursa", 40.1826, 29.0665),
    City("Çanakkale", 40.1553, 26.4142), City("Çankırı", 40.6013, 33.6134),
    City("Çorum", 40.5506, 34.9556), City("Denizli", 37.7765, 29.0864),
    City("Diyarbakır", 37.9144, 40.2306), City("Edirne", 41.6771, 26.5557),
    City("Elazığ", 38.6810, 39.2264), City("Erzincan", 39.7500, 39.5000),
    City("Erzurum", 39.9000, 41.2700), City("Eskişehir", 39.7767, 30.5206),
    City("Gaziantep", 37.0662, 37.3833), City("Giresun", 40.9128, 38.3895),
    City("Gümüşhane", 40.4386, 39.5086), City("Hakkari", 37.5744, 43.7408),
    City("Hatay", 36.4018, 36.3498), City("Isparta", 37.7648, 30.5566),
    City("Mersin", 36.8000, 34.6333), City("İstanbul", 41.0082, 28.9784),
    City("İzmir", 38.4237, 27.1428), City("Kars", 40.6167, 43.1000),
    City("Kastamonu", 41.3887, 33.7827), City("Kayseri", 38.7312, 35.4787),
    City("Kırklareli", 41.7333, 27.2167), City("Kırşehir", 39.1425, 34.1709),
    City("Kocaeli", 40.8533, 29.8815), City("Konya", 37.8746, 32.4932),
    City("Kütahya", 39.4242, 29.9833), City("Malatya", 38.3552, 38.3095),
    City("Manisa", 38.6191, 27.4289), City("Kahramanmaraş", 37.5858, 36.9371),
    City("Mardin", 37.3212, 40.7245), City("Muğla", 37.2153, 28.3636),
    City("Muş", 38.9462, 41.7539), City("Nevşehir", 38.6939, 34.6857),
    City("Niğde", 37.9667, 34.6833), City("Ordu", 40.9839, 37.8764),
    City("Rize", 41.0201, 40.5234), City("Sakarya", 40.6940, 30.4358),
    City("Samsun", 41.2867, 36.3300), City("Siirt", 37.9333, 41.9500),
    City("Sinop", 42.0231, 35.1531), City("Sivas", 39.7477, 37.0179),
    City("Tekirdağ", 40.9833, 27.5167), City("Tokat", 40.3167, 36.5500),
    City("Trabzon", 41.0027, 39.7168), City("Tunceli", 39.3074, 39.4388),
    City("Şanlıurfa", 37.1591, 38.7969), City("Uşak", 38.6823, 29.4082),
    City("Van", 38.4891, 43.4089), City("Yozgat", 39.8181, 34.8147),
    City("Zonguldak", 41.4564, 31.7987), City("Aksaray", 38.3687, 34.0360),
    City("Bayburt", 40.2552, 40.2249), City("Karaman", 37.1759, 33.2287),
    City("Kırıkkale", 39.8468, 33.5153), City("Batman", 37.8812, 41.1351),
    City("Şırnak", 37.4187, 42.4918), City("Bartın", 41.5811, 32.4610),
    City("Ardahan", 41.1105, 42.7022), City("Iğdır", 39.9167, 44.0333),
    City("Yalova", 40.6500, 29.2667), City("Karabük", 41.2061, 32.6204),
    City("Kilis", 36.7184, 37.1212), City("Osmaniye", 37.0742, 36.2478),
    City("Düzce", 40.8438, 31.1565),
)

fun defaultCity(): City = TR_CITIES.first { it.name == "İstanbul" }

// ====================================================================
// NAMAZ VAKTİ HESAPLAMA (astronomik güneş konumu tabanlı yaklaşık hesap)
// NOT: NOAA güneş doğuş/batış denklemlerine dayanır, birkaç dakikalık
// sapma içerebilir. Kesin vakitler için Diyanet İşleri Başkanlığı gibi
// resmi bir kaynak kullanılmalıdır.
// ====================================================================
private const val TR_TIMEZONE_OFFSET = 3.0 // Türkiye sabit UTC+3

data class DayPrayerMinutes(
    val imsak: Double, val gunes: Double, val ogle: Double,
    val ikindi: Double, val aksam: Double, val yatsi: Double
)

private fun deg2rad(d: Double) = d * Math.PI / 180.0
private fun rad2deg(r: Double) = r * 180.0 / Math.PI

fun calcSunTimesMinutes(lat: Double, lon: Double, cal: Calendar): DayPrayerMinutes {
    val yearStart = Calendar.getInstance().apply {
        set(cal.get(Calendar.YEAR), 0, 0, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val dayOfYear = ((cal.timeInMillis - yearStart.timeInMillis) / 86400000L).toInt()

    val gamma = 2 * Math.PI / 365.0 * (dayOfYear - 1)
    val eqtime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma)
        - 0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)) // dakika
    val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
        0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
        0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma) // radyan
    val declDeg = rad2deg(decl)

    val solarNoonUTCmin = 720.0 - 4.0 * lon - eqtime
    val solarNoonLocal = solarNoonUTCmin + TR_TIMEZONE_OFFSET * 60.0

    fun hourAngleForZenith(zenithDeg: Double): Double? {
        val zenith = deg2rad(zenithDeg)
        val latRad = deg2rad(lat)
        val cosH = (cos(zenith) - sin(latRad) * sin(decl)) / (cos(latRad) * cos(decl))
        if (cosH > 1.0 || cosH < -1.0) return null
        return rad2deg(Math.acos(cosH))
    }

    fun timeFromH(h: Double?, isMorning: Boolean): Double? {
        if (h == null) return null
        return if (isMorning) solarNoonLocal - 4.0 * h else solarNoonLocal + 4.0 * h
    }

    fun asrMinutes(shadowFactor: Double): Double? {
        val altitude = rad2deg(atan2(1.0, shadowFactor + tan(deg2rad(abs(lat - declDeg)))))
        val zenith = 90.0 - altitude
        val h = hourAngleForZenith(zenith)
        return timeFromH(h, false)
    }

    val h90 = hourAngleForZenith(90.833)
    val sunrise = timeFromH(h90, true)
    val sunset = timeFromH(h90, false)
    val h108 = hourAngleForZenith(108.0) // fecr-i sadık ~18°
    val h107 = hourAngleForZenith(107.0) // yatsı ~17°

    val imsak = timeFromH(h108, true) ?: ((sunrise ?: 330.0) - 90.0)
    val gunes = sunrise ?: 330.0
    val ogle = solarNoonLocal + 4.0
    val ikindi = asrMinutes(1.0) ?: (ogle + 210.0) // Diyanet hesaplama yöntemine yakın gölge oranı
    val aksam = (sunset ?: 1170.0) + 3.0
    val yatsi = timeFromH(h107, false) ?: ((sunset ?: 1170.0) + 90.0)

    return DayPrayerMinutes(imsak, gunes, ogle, ikindi, aksam, yatsi)
}

fun minutesToHHMM(minsIn: Double): String {
    var mins = Math.round(minsIn).toInt() % 1440
    if (mins < 0) mins += 1440
    val h = mins / 60
    val m = mins % 60
    return "%02d:%02d".format(h, m)
}

// getPrayersForCityLocal: yalnızca kendi astronomik hesaplamamızı kullanır
// (API'ye ulaşılamazsa yedek olarak devreye girer)
fun getPrayersForCityLocal(city: City, cal: Calendar): List<PrayerTime> {
    val t = calcSunTimesMinutes(city.lat, city.lon, cal)
    return listOf(
        PrayerTime("imsak1", "İMSAK", minutesToHHMM(t.imsak), "imsak"),
        PrayerTime("gunes", "GÜNEŞ", minutesToHHMM(t.gunes), "gunes"),
        PrayerTime("ogle", "ÖĞLE", minutesToHHMM(t.ogle), "gunes"),
        PrayerTime("ikindi", "İKİNDİ", minutesToHHMM(t.ikindi), "ikindi"),
        PrayerTime("aksam", "AKŞAM", minutesToHHMM(t.aksam), "aksam"),
        PrayerTime("yatsi", "YATSI", minutesToHHMM(t.yatsi), "yatsi"),
    )
}

// ====================================================================
// DİYANET RESMİ HESAPLAMASI (Aladhan API, method=13 = "Diyanet İşleri
// Başkanlığı, Turkey"). Kayıt/anahtar gerektirmez. API'ye ulaşılamazsa
// (çevrimdışı vb.) sessizce yerel astronomik hesaplamaya geri döner.
// ====================================================================
private const val PRAYER_API_BASE = "https://api.aladhan.com/v1"

data class PrayersResult(val prayers: List<PrayerTime>, val fromApi: Boolean)

private fun cleanTime(raw: String): String = raw.split(" ")[0] // "04:42 (+03)" -> "04:42"

suspend fun fetchPrayersForCity(city: City, cal: Calendar): PrayersResult = withContext(Dispatchers.IO) {
    try {
        val dd = "%02d".format(cal.get(Calendar.DAY_OF_MONTH))
        val mm = "%02d".format(cal.get(Calendar.MONTH) + 1)
        val yyyy = cal.get(Calendar.YEAR)
        val url = "$PRAYER_API_BASE/timings/$dd-$mm-$yyyy?latitude=${city.lat}&longitude=${city.lon}&method=13&school=0"

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val text = conn.inputStream.use { it.bufferedReader(Charsets.UTF_8).readText() }
        val root = JSONObject(text)
        if (root.getInt("code") != 200) throw Exception("API error")

        val t = root.getJSONObject("data").getJSONObject("timings")
        val fajr = cleanTime(t.getString("Fajr"))

        val list = listOf(
            PrayerTime("imsak1", "İMSAK", fajr, "imsak"),
            PrayerTime("gunes", "GÜNEŞ", cleanTime(t.getString("Sunrise")), "gunes"),
            PrayerTime("ogle", "ÖĞLE", cleanTime(t.getString("Dhuhr")), "gunes"),
            PrayerTime("ikindi", "İKİNDİ", cleanTime(t.getString("Asr")), "ikindi"),
            PrayerTime("aksam", "AKŞAM", cleanTime(t.getString("Maghrib")), "aksam"),
            PrayerTime("yatsi", "YATSI", cleanTime(t.getString("Isha")), "yatsi"),
        )
        PrayersResult(list, fromApi = true)
    } catch (e: Exception) {
        PrayersResult(getPrayersForCityLocal(city, cal), fromApi = false)
    }
}

data class MonthDay(
    val day: Int,
    val imsak: String, val gunes: String, val ogle: String,
    val ikindi: String, val aksam: String, val yatsi: String
)

// fetchMonthForCity: ana sayfa (fetchPrayersForCity) ile AYNI teknik
// kullanılır - ayın her günü için ayrı ayrı /timings isteği paralel
// gönderilir. Böylece takvimdeki saatler ana sayfadaki ile birebir
// tutarlı olur (calendar endpoint'i farklı sonuç verebildiği için
// artık kullanılmıyor).
suspend fun fetchMonthForCity(city: City, year: Int, month: Int): Pair<List<MonthDay>, Boolean> = withContext(Dispatchers.IO) {
    val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val dayCalendars = (1..daysInMonth).map { day ->
        Calendar.getInstance().apply { set(year, month - 1, day, 12, 0, 0) }
    }

    val results = dayCalendars.map { dayCal ->
        async { fetchPrayersForCity(city, dayCal) }
    }.awaitAll()

    val allFromApi = results.all { it.fromApi }
    val days = results.mapIndexed { idx, result ->
        val byKey = result.prayers.associateBy { it.key }
        MonthDay(
            day = idx + 1,
            imsak = byKey["imsak1"]?.time ?: "--:--",
            gunes = byKey["gunes"]?.time ?: "--:--",
            ogle = byKey["ogle"]?.time ?: "--:--",
            ikindi = byKey["ikindi"]?.time ?: "--:--",
            aksam = byKey["aksam"]?.time ?: "--:--",
            yatsi = byKey["yatsi"]?.time ?: "--:--",
        )
    }
    days to allFromApi
}

// ====================================================================
// SEÇİLİ KONUM (SharedPreferences ile kalıcı)
// ====================================================================
private const val PREFS_NAME = "namaz_vakti_prefs"
private const val KEY_SELECTED_CITY = "selected_city"

fun loadSelectedCity(context: Context): City {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val savedName = prefs.getString(KEY_SELECTED_CITY, null)
    return TR_CITIES.firstOrNull { it.name == savedName } ?: defaultCity()
}

fun saveSelectedCity(context: Context, city: City) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_SELECTED_CITY, city.name).apply()
}

fun bearingToTurkishDirection(deg: Float): String {
    val dirs = listOf("Kuzey", "Kuzeydoğu", "Doğu", "Güneydoğu", "Güney", "Güneybatı", "Batı", "Kuzeybatı")
    val idx = (Math.round(deg / 45f)) % 8
    return dirs[if (idx < 0) idx + 8 else idx]
}
