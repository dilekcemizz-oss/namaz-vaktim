package com.example.namazvakti

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ====================================================================
// KUR'AN-I KERİM MEALİ - canlı API entegrasyonu (api.alquran.cloud).
// Ayet metni ve meali uygulamaya gömülü DEĞİLDİR; kullanıcı bir sureyi
// açtığında gerçek zamanlı olarak bu API'den çekilir. Ses okuma da
// aynı şekilde Mishary Alafasy kıraatiyle canlı CDN üzerinden akar.
// ====================================================================

private const val QURAN_API = "https://api.alquran.cloud/v1"
private const val ARABIC_EDITION = "quran-uthmani"
private val TR_EDITION_CANDIDATES = listOf("tr.diyanet", "tr.yazir", "tr.ates")
const val AUDIO_EDITION = "ar.alafasy"
const val AUDIO_BITRATE = 128

data class SurahMeta(
    val number: Int,
    val name: String,          // Arapça isim
    val englishName: String,   // Türkçe/okunuş isim (API alan adı böyle)
    val numberOfAyahs: Int,
    val revelationType: String // "Meccan" / "Medinan"
)

data class Ayah(
    val number: Int,        // global ayet numarası (1-6236) - ses URL'i için gerekli
    val numberInSurah: Int,
    val text: String
)

data class SurahDetail(
    val number: Int,
    val englishName: String,
    val numberOfAyahs: Int,
    val revelationType: String,
    val ayahs: List<Ayah>
)

private fun httpGetJson(urlStr: String): JSONObject {
    val conn = URL(urlStr).openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 8000
    conn.readTimeout = 8000
    conn.inputStream.use { stream ->
        val text = stream.bufferedReader(Charsets.UTF_8).readText()
        return JSONObject(text)
    }
}

suspend fun fetchSurahList(): List<SurahMeta> = withContext(Dispatchers.IO) {
    val root = httpGetJson("$QURAN_API/surah")
    val arr = root.getJSONArray("data")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        SurahMeta(
            number = o.getInt("number"),
            name = o.getString("name"),
            englishName = o.getString("englishName"),
            numberOfAyahs = o.getInt("numberOfAyahs"),
            revelationType = o.getString("revelationType")
        )
    }
}

private fun parseAyahs(data: JSONObject): List<Ayah> {
    val arr = data.getJSONArray("ayahs")
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Ayah(
            number = o.getInt("number"),
            numberInSurah = o.getInt("numberInSurah"),
            text = o.getString("text")
        )
    }
}

/** Türkçe meal edisyonlarını sırayla dener, ilk çalışanı döndürür. */
suspend fun resolveWorkingTrEdition(): String = withContext(Dispatchers.IO) {
    for (ed in TR_EDITION_CANDIDATES) {
        try {
            httpGetJson("$QURAN_API/ayah/1/$ed")
            return@withContext ed
        } catch (e: Exception) { /* sıradakini dene */ }
    }
    TR_EDITION_CANDIDATES.first()
}

data class SurahWithMeal(val arabic: SurahDetail, val trAyahs: List<Ayah>)

suspend fun fetchSurahWithMeal(number: Int, trEdition: String): SurahWithMeal = withContext(Dispatchers.IO) {
    val arData = httpGetJson("$QURAN_API/surah/$number/$ARABIC_EDITION").getJSONObject("data")
    val trData = httpGetJson("$QURAN_API/surah/$number/$trEdition").getJSONObject("data")
    val arabic = SurahDetail(
        number = arData.getInt("number"),
        englishName = arData.getString("englishName"),
        numberOfAyahs = arData.getInt("numberOfAyahs"),
        revelationType = arData.getString("revelationType"),
        ayahs = parseAyahs(arData)
    )
    SurahWithMeal(arabic, parseAyahs(trData))
}

fun ayahAudioUrl(globalAyahNumber: Int): String =
    "https://cdn.islamic.network/quran/audio/$AUDIO_BITRATE/$AUDIO_EDITION/$globalAyahNumber.mp3"
