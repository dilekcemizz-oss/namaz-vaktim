package com.example.namazvakti

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------
// Renk paleti - ekran görüntüsündeki gün batımı temasına dayanır
// ------------------------------------------------------------------
object AppColors {
    val purpleDeep = Color(0xFF241238)
    val purpleMid = Color(0xFF3A2159)
    val magenta = Color(0xFF6D3564)
    val sunsetRed = Color(0xFFB5563F)
    val sunsetOrange = Color(0xFFE8895A)
    val sunsetGold = Color(0xFFF0A35C)
    val cardGlass = Color(0x14FFFFFF)
    val cardBorder = Color(0x24FFFFFF)
    val textSoft = Color(0xA6FFFFFF)
    val greenAccent = Color(0xFF6DE6A8)
    val activeGlow = Color(0xFFE0578A)
}

// ------------------------------------------------------------------
// Marka fontu - uygulama adı ve önemli başlıklarda kullanılan zarif
// serif font (Marcellus).
// ------------------------------------------------------------------
val BrandFont = FontFamily(Font(R.font.marcellus_regular))

// ------------------------------------------------------------------
// Namaz vakti veri modeli.
// NOT: Aşağıdaki değerler örnek/statik verilerdir. Gerçek bir uygulamada
// bu değerler Diyanet İşleri Başkanlığı API'si veya benzeri bir
// kaynaktan (örn. Aladhan API) şehir + tarihe göre çekilmelidir.
// ------------------------------------------------------------------
// NOT: Namaz vakitleri artık PrayerCalculator.kt içindeki astronomik
// hesaplama ile, kullanıcının Ayarlar ekranından seçtiği il için
// hesaplanır. iconKey, bu dosyadaki prayerIcon() ile gerçek Compose
// ikonuna çevrilir (Compose bağımlılığını hesaplama katmanından ayrı
// tutmak için).
// ------------------------------------------------------------------
data class PrayerTime(
    val key: String,
    val name: String,
    val time: String, // "HH:mm"
    val iconKey: String
)

fun prayerIcon(iconKey: String): androidx.compose.ui.graphics.vector.ImageVector = when (iconKey) {
    "imsak" -> Icons.Filled.NightsStay
    "gunes" -> Icons.Filled.WbSunny
    "ikindi" -> Icons.Filled.WbTwilight
    "aksam" -> Icons.Filled.Brightness3
    "yatsi" -> Icons.Filled.Brightness2
    else -> Icons.Filled.WbSunny
}

fun timeToMinutes(t: String): Int {
    val (h, m) = t.split(":").map { it.toInt() }
    return h * 60 + m
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                PrayerTimesScreen()
            }
        }
    }
}

@Composable
fun PrayerTimesScreen() {
    val context = LocalContext.current
    var currentCity by remember { mutableStateOf(loadSelectedCity(context)) }
    var prayers by remember(currentCity) {
        mutableStateOf(getPrayersForCityLocal(currentCity, Calendar.getInstance()))
    }
    var prayersFromApi by remember { mutableStateOf<Boolean?>(null) } // null = yükleniyor
    var now by remember { mutableStateOf(Date()) }

    // İl değiştiğinde Diyanet API'sinden gerçek veriyi çek (başarısız olursa
    // yerel astronomik hesaplama zaten yukarıda gösterilmiş durumda kalır)
    LaunchedEffect(currentCity) {
        prayersFromApi = null
        val result = fetchPrayersForCity(currentCity, Calendar.getInstance())
        prayers = result.prayers
        prayersFromApi = result.fromApi
    }

    // Her saniye saati güncelle; gün değiştiyse (gece yarısı) vakitleri de tazele
    var lastKnownDay by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            val cal = Calendar.getInstance().apply { time = now }
            val today = cal.get(Calendar.DAY_OF_YEAR)
            if (today != lastKnownDay) {
                lastKnownDay = today
                prayersFromApi = null
                val result = fetchPrayersForCity(currentCity, cal)
                prayers = result.prayers
                prayersFromApi = result.fromApi
            }
            delay(1000)
        }
    }

    val cal = Calendar.getInstance().apply { time = now }
    val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 +
        cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60.0

    var nextIndex = prayers.indexOfFirst { timeToMinutes(it.time) > nowMinutes }
    var wraps = false
    if (nextIndex == -1) { nextIndex = 0; wraps = true }
    val next = prayers[nextIndex]
    val nextMinutes = timeToMinutes(next.time) + if (wraps) 24 * 60 else 0
    val prevMinutes = if (nextIndex == 0 && !wraps) 0 else {
        val prevIdx = if (nextIndex == 0) prayers.size - 1 else nextIndex - 1
        timeToMinutes(prayers[prevIdx].time) - if (nextIndex == 0) 24 * 60 else 0
    }

    val remainingSec = ((nextMinutes - nowMinutes) * 60).toInt().coerceAtLeast(0)
    val totalSpan = ((nextMinutes - prevMinutes) * 60).coerceAtLeast(1)
    val elapsed = totalSpan - remainingSec
    val ratio = (elapsed.toFloat() / totalSpan.toFloat()).coerceIn(0f, 1f)

    val hh = remainingSec / 3600
    val mm = (remainingSec % 3600) / 60
    val ss = remainingSec % 60
    val countdownText = "%02d:%02d:%02d".format(hh, mm, ss)

    val clockFormat = remember { SimpleDateFormat("HH:mm", Locale("tr")) }
    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy EEEE", Locale("tr")) }

    var currentScreen by remember { mutableStateOf("home") }

    Surface(color = AppColors.purpleDeep) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Arka plan fotoğrafı (İstanbul - Ayasofya & Sultanahmet gün batımı)
            // Kenardan kenara (edge-to-edge): status bar/nav bar arkasına kadar uzanır
            Image(
                painter = painterResource(id = R.drawable.bg_istanbul),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Okunabilirlik için karartma katmanı
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xC0140A23),
                                Color(0x38140A23),
                                Color(0x47140A23),
                                Color(0x8C0F0819),
                                Color(0xB80A0512)
                            )
                        )
                    )
            )

            // İçerik: sistem çubuklarından (status bar/gesture bar) korunur.
            // Alt tarafta sabit menüye yer açmak için ekstra padding bırakılır;
            // bu Column'un kendisi kaymaz, yalnızca weight(1f) alan ekran
            // içeriği (grid/liste) kendi içinde kayar — menü hep sabit kalır.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .padding(bottom = 84.dp)
            ) {

                TopBar(cityName = "${currentCity.name}, Türkiye")

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(now).replaceFirstChar { it.uppercase() },
                    color = AppColors.textSoft,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = clockFormat.format(now),
                    color = AppColors.textSoft,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(if (currentScreen == "home") 22.dp else 10.dp))

                when (currentScreen) {
                    "home" -> {
                        // Geri sayım halkası
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CountdownRing(
                                progress = ratio,
                                label = "${next.name}'A KALAN SÜRE",
                                timeText = countdownText
                            )
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // Vakit kartları - kalan tüm alanı doldurur, taşarsa kendi içinde kayar
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            items(prayers) { p ->
                                PrayerCard(prayer = p, isActive = p.key == next.key)
                            }
                        }
                    }
                    "kible" -> Box(modifier = Modifier.weight(1f)) { QiblaScreen(city = currentCity) }
                    "dua" -> Box(modifier = Modifier.weight(1f)) { QuranScreen() }
                    "takvim" -> Box(modifier = Modifier.weight(1f)) { CalendarScreen(city = currentCity) }
                    "ayarlar" -> Box(modifier = Modifier.weight(1f)) {
                        SettingsScreen(
                            selectedCity = currentCity,
                            onCitySelected = { city ->
                                currentCity = city
                                saveSelectedCity(context, city)
                            }
                        )
                    }
                }
            }

            // Alt navigasyon: Box'ta ayrı bir katman olarak en altta SABİT durur.
            // İçerik ne kadar kaydırılırsa kaydırılsın bu menü asla hareket etmez.
            BottomNavBar(
                selected = currentScreen,
                onSelect = { currentScreen = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
fun TopBar(cityName: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Ezan Vaktim",
            color = Color.White,
            fontSize = 26.sp,
            fontFamily = BrandFont,
            letterSpacing = 0.5.sp
        )
        Text(
            text = "N A M A Z   &   K I B L E",
            color = AppColors.sunsetGold,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 1.dp, bottom = 6.dp)
        )
        Text(
            text = cityName,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CountdownRing(progress: Float, label: String, timeText: String) {
    Box(modifier = Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 5.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            // Arka plan halkası
            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // İlerleme halkası
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = AppColors.textSoft,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = timeText,
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                listOf("saat", "dakika", "saniye").forEach {
                    Text(it, color = AppColors.textSoft, fontSize = 10.5.sp)
                }
            }
        }
    }
}

@Composable
fun PrayerCard(prayer: PrayerTime, isActive: Boolean) {
    val bg = if (isActive) {
        Brush.linearGradient(
            listOf(AppColors.activeGlow.copy(alpha = 0.35f), AppColors.magenta.copy(alpha = 0.45f))
        )
    } else {
        Brush.linearGradient(listOf(AppColors.cardGlass, AppColors.cardGlass))
    }
    val iconTint = when {
        isActive -> Color.White
        prayer.key.startsWith("imsak") -> AppColors.greenAccent
        prayer.key == "ikindi" -> AppColors.sunsetOrange
        else -> Color(0xFFE9E9F2)
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .then(
                if (isActive) Modifier.background(Color.Transparent)
                else Modifier
            )
            .padding(vertical = 16.dp, horizontal = 8.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = prayer.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = AppColors.textSoft,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ON", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        Icon(prayerIcon(prayer.iconKey), contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = prayer.name,
            color = if (isActive) Color.White else AppColors.textSoft,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = prayer.time,
            color = Color.White,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BottomNavBar(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        Triple("home", "Home", Icons.Filled.Home),
        Triple("kible", "Kıble", Icons.Filled.Explore),
        Triple("dua", "Kur'an", Icons.Filled.MenuBook),
        Triple("takvim", "Takvim", Icons.Filled.CalendarMonth),
        Triple("ayarlar", "Ayarlar", Icons.Filled.Settings)
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x8C140A23))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { (key, label, icon) ->
            val isSel = key == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(key) }
                    .background(if (isSel) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (isSel) AppColors.activeGlow else AppColors.textSoft,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    label,
                    color = if (isSel) AppColors.activeGlow else AppColors.textSoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ====================================================================
// KIBLE EKRANI - seçili ile göre Kâbe açısı hesaplanır, cihazın gerçek
// pusula yönü (SensorManager: accelerometer + magnetometer) ile
// birleştirilerek görsel pusula üzerinde canlı gösterilir.
// ====================================================================
private const val KAABA_LAT = 21.4225
private const val KAABA_LON = 39.8262

fun qiblaBearing(lat: Double, lon: Double): Float {
    val lat1 = Math.toRadians(lat)
    val lon1 = Math.toRadians(lon)
    val lat2 = Math.toRadians(KAABA_LAT)
    val lon2 = Math.toRadians(KAABA_LON)
    val dLon = lon2 - lon1
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val brng = Math.toDegrees(atan2(y, x)).toFloat()
    return (brng + 360f) % 360f
}

@Composable
fun QiblaScreen(city: City) {
    val context = LocalContext.current

    var azimuth by remember { mutableStateOf(0f) } // cihazın baktığı yön (0=Kuzey)
    var sensorAvailable by remember { mutableStateOf(false) }
    var deviceHeadingActive by remember { mutableStateOf(false) }

    // Pusula sensörü (accelerometer + magnetometer -> azimuth)
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        sensorAvailable = accelerometer != null && magnetometer != null

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, gravity, 0, 3); hasGravity = true
                }
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, geomagnetic, 0, 3); hasGeomagnetic = true
                }
                if (hasGravity && hasGeomagnetic) {
                    val r = FloatArray(9)
                    val i = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(r, orientation)
                        val degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        azimuth = (degrees + 360f) % 360f
                        deviceHeadingActive = true
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        if (accelerometer != null) sm.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        if (magnetometer != null) sm.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)

        onDispose { sm.unregisterListener(listener) }
    }

    val qiblaAngle = remember(city) { qiblaBearing(city.lat, city.lon) }
    val needleRotation = qiblaAngle - azimuth
    val normalizedDist = run {
        val n = ((needleRotation % 360f) + 360f) % 360f
        minOf(n, 360f - n)
    }
    val isAligned = deviceHeadingActive && normalizedDist < 6f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Kıble Yönü:", color = AppColors.textSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "${qiblaAngle.toInt()}° (${bearingToTurkishDirection(qiblaAngle)})",
            color = AppColors.sunsetGold, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
        )

        Box(
            modifier = Modifier
                .padding(top = 18.dp, bottom = 6.dp)
                .size(280.dp),
            contentAlignment = Alignment.Center
        ) {
            // Cam efektli dış disk
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.03f))
                        )
                    )
            )

            // Dekoratif 8 köşeli altın yıldız + iç çemberler
            Canvas(modifier = Modifier.fillMaxSize(0.8f)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerR = size.minDimension / 2f
                val innerR = outerR * 0.42f
                val starPath = Path()
                for (k in 0 until 8) {
                    val angleOuter = Math.toRadians((k * 45).toDouble())
                    val angleInner = Math.toRadians((k * 45 + 22.5).toDouble())
                    val ox = cx + outerR * kotlin.math.cos(angleOuter).toFloat()
                    val oy = cy + outerR * kotlin.math.sin(angleOuter).toFloat()
                    val ix = cx + innerR * kotlin.math.cos(angleInner).toFloat()
                    val iy = cy + innerR * kotlin.math.sin(angleInner).toFloat()
                    if (k == 0) starPath.moveTo(ox, oy) else starPath.lineTo(ox, oy)
                    starPath.lineTo(ix, iy)
                }
                starPath.close()
                drawPath(starPath, color = Color(0x1FE8B84A))
                drawPath(starPath, color = Color(0x59E8B84A), style = Stroke(width = 1.dp.toPx()))
                drawCircle(color = Color.White.copy(alpha = 0.10f), radius = outerR * 0.75f, style = Stroke(width = 1.dp.toPx()))
                drawCircle(color = Color.White.copy(alpha = 0.07f), radius = outerR * 0.48f, style = Stroke(width = 1.dp.toPx()))
            }

            // Yön harfleri (K/D/G/B) - sabit, dönmez
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp)
            ) {
                Text("K", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("(Kuzey)", color = AppColors.textSoft, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
            ) {
                Text("G", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("(Güney)", color = AppColors.textSoft, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)
            ) {
                Text("D", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("(Doğu)", color = AppColors.textSoft, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp)
            ) {
                Text("B", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("(Batı)", color = AppColors.textSoft, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
            }

            // Dönen grup: ibre çizgisi + Kâbe rozeti (qiblaAngle - azimuth kadar döner)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(needleRotation),
                contentAlignment = Alignment.TopCenter
            ) {
                // İbre çizgisi (merkezden yukarı doğru)
                Box(
                    modifier = Modifier
                        .padding(top = 60.dp)
                        .width(3.dp)
                        .height(70.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(AppColors.sunsetGold, Color.White)
                            ),
                            RoundedCornerShape(3.dp)
                        )
                )
                // Kâbe rozeti
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xE6FFDC8C), Color(0x26FFBE50), Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(34.dp)) {
                        val s = size.width / 44f // 44x44 tasarım ızgarasına göre ölçek
                        fun p(x: Float, y: Float) = Offset(x * s, y * s)

                        // üst yüz
                        drawPath(
                            Path().apply {
                                moveTo(p(22f, 5f).x, p(22f, 5f).y)
                                lineTo(p(38f, 14f).x, p(38f, 14f).y)
                                lineTo(p(22f, 23f).x, p(22f, 23f).y)
                                lineTo(p(6f, 14f).x, p(6f, 14f).y)
                                close()
                            },
                            color = Color(0xFF2A2A2A)
                        )
                        // sol yüz
                        drawPath(
                            Path().apply {
                                moveTo(p(6f, 14f).x, p(6f, 14f).y)
                                lineTo(p(22f, 23f).x, p(22f, 23f).y)
                                lineTo(p(22f, 39f).x, p(22f, 39f).y)
                                lineTo(p(6f, 30f).x, p(6f, 30f).y)
                                close()
                            },
                            color = Color(0xFF141414)
                        )
                        // sağ yüz (gölgede)
                        drawPath(
                            Path().apply {
                                moveTo(p(22f, 23f).x, p(22f, 23f).y)
                                lineTo(p(38f, 14f).x, p(38f, 14f).y)
                                lineTo(p(38f, 30f).x, p(38f, 30f).y)
                                lineTo(p(22f, 39f).x, p(22f, 39f).y)
                                close()
                            },
                            color = Color(0xFF050505)
                        )
                        // altın kuşak (hizam) - sol yüz
                        drawPath(
                            Path().apply {
                                moveTo(p(6f, 20f).x, p(6f, 20f).y)
                                lineTo(p(22f, 29f).x, p(22f, 29f).y)
                                lineTo(p(22f, 32f).x, p(22f, 32f).y)
                                lineTo(p(6f, 23f).x, p(6f, 23f).y)
                                close()
                            },
                            color = Color(0xFFF0C869)
                        )
                        // altın kuşak - sağ yüz
                        drawPath(
                            Path().apply {
                                moveTo(p(22f, 29f).x, p(22f, 29f).y)
                                lineTo(p(38f, 20f).x, p(38f, 20f).y)
                                lineTo(p(38f, 23f).x, p(38f, 23f).y)
                                lineTo(p(22f, 32f).x, p(22f, 32f).y)
                                close()
                            },
                            color = Color(0xFFC99A3A)
                        )
                        // Kâbe kapısı
                        drawRoundRect(
                            color = Color(0xFFF0C869),
                            topLeft = p(10.5f, 31f),
                            size = androidx.compose.ui.geometry.Size(6f * s, 7f * s),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.6f * s, 0.6f * s)
                        )
                    }
                }
            }

            // Merkez nokta
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            if (isAligned) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AppColors.greenAccent, modifier = Modifier.size(16.dp))
                Text("Doğru Yöne Hizalandı", color = AppColors.greenAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Filled.Explore, contentDescription = null, tint = AppColors.textSoft, modifier = Modifier.size(16.dp))
                Text(
                    if (deviceHeadingActive) "Hizalanmadı" else "Sabit Açı Gösteriliyor",
                    color = AppColors.textSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
        Text(
            "Lütfen cihazınızı yatay tutun ve döndürün",
            color = AppColors.textSoft, fontSize = 11.5.sp,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (sensorAvailable)
                "${city.name} konumuna göre hesaplandı. Ayarlar sekmesinden farklı bir il seçebilirsiniz."
            else
                "Cihazınızda pusula sensörü bulunamadı; sabit açı gösteriliyor. ${city.name} konumuna göre hesaplandı.",
            color = AppColors.textSoft,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 30.dp)
        )
    }
}

// ====================================================================
// KUR'AN-I KERİM MEALİ EKRANI - sure listesi + ayet görünümü.
// Sure listesine tıklanınca ayetler (Arapça + Türkçe meal) canlı olarak
// çekilir; her ayetin yanındaki oynat düğmesi gerçek kıraat sesini
// (Mishary Alafasy) CDN üzerinden akıtır.
// ====================================================================
sealed class QuranUiState {
    object Loading : QuranUiState()
    data class Error(val message: String) : QuranUiState()
    data class SurahListLoaded(val list: List<SurahMeta>) : QuranUiState()
    data class SurahDetailLoaded(val detail: SurahWithMeal) : QuranUiState()
}

@Composable
fun QuranScreen() {
    val context = LocalContext.current
    var uiState by remember { mutableStateOf<QuranUiState>(QuranUiState.Loading) }
    var surahListCache by remember { mutableStateOf<List<SurahMeta>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var trEdition by remember { mutableStateOf<String?>(null) }

    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var playingAyahNumber by remember { mutableStateOf<Int?>(null) }

    fun stopAudio() {
        mediaPlayer?.let { runCatching { it.stop(); it.release() } }
        mediaPlayer = null
        playingAyahNumber = null
    }

    fun playAyahAudio(globalNumber: Int) {
        if (playingAyahNumber == globalNumber) { stopAudio(); return }
        stopAudio()
        try {
            val mp = android.media.MediaPlayer()
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(ayahAudioUrl(globalNumber))
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { playingAyahNumber = null; mediaPlayer = null }
            mp.prepareAsync()
            mediaPlayer = mp
            playingAyahNumber = globalNumber
        } catch (e: Exception) {
            stopAudio()
        }
    }

    DisposableEffect(Unit) { onDispose { stopAudio() } }

    // Sure listesini bir kez yükle
    LaunchedEffect(Unit) {
        if (surahListCache == null) {
            uiState = QuranUiState.Loading
            try {
                val list = fetchSurahList()
                surahListCache = list
                uiState = QuranUiState.SurahListLoaded(list)
            } catch (e: Exception) {
                uiState = QuranUiState.Error("Sureler yüklenemedi. İnternet bağlantınızı kontrol edip tekrar deneyin.")
            }
        } else {
            uiState = QuranUiState.SurahListLoaded(surahListCache!!)
        }
    }

    val scope = rememberCoroutineScope()

    fun openSurahAsync(number: Int) {
        stopAudio()
        uiState = QuranUiState.Loading
        scope.launch {
            try {
                val ed = trEdition ?: resolveWorkingTrEdition().also { trEdition = it }
                val detail = fetchSurahWithMeal(number, ed)
                uiState = QuranUiState.SurahDetailLoaded(detail)
            } catch (e: Exception) {
                uiState = QuranUiState.Error("Sure yüklenemedi. İnternet bağlantınızı kontrol edip tekrar deneyin.")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is QuranUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Yükleniyor...", color = AppColors.textSoft, fontSize = 13.sp)
                }
            }
            is QuranUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "⚠️ ${state.message}", color = Color(0xFFF0A3A3), fontSize = 13.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 30.dp)
                    )
                }
            }
            is QuranUiState.SurahListLoaded -> {
                Text("Kur'an-ı Kerim Meali", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("Sureyi seçin, ayetlere tıklayarak dinleyin", color = AppColors.textSoft, fontSize = 12.5.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp), textAlign = TextAlign.Center)

                androidx.compose.material3.OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Sure ara... (örn. Yasin, 36)", color = AppColors.textSoft, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedContainerColor = AppColors.cardGlass, unfocusedContainerColor = AppColors.cardGlass,
                        focusedBorderColor = Color.White.copy(alpha = 0.3f), unfocusedBorderColor = AppColors.cardBorder,
                        cursorColor = Color.White
                    )
                )

                val filtered = state.list.filter {
                    it.englishName.contains(searchQuery, ignoreCase = true) || it.number.toString().contains(searchQuery)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(filtered) { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppColors.cardGlass)
                                .clickable { openSurahAsync(s.number) }
                                .padding(horizontal = 14.dp, vertical = 11.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${s.number}", color = AppColors.sunsetGold, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.englishName, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${s.numberOfAyahs} ayet · ${if (s.revelationType == "Meccan") "Mekki" else "Medeni"}",
                                    color = AppColors.textSoft, fontSize = 10.5.sp
                                )
                            }
                            Text(s.name, color = AppColors.sunsetGold, fontSize = 16.sp)
                        }
                    }
                }
            }
            is QuranUiState.SurahDetailLoaded -> {
                val detail = state.detail
                val isBesmele = detail.arabic.number != 1 && detail.arabic.number != 9

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Box(
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                            .background(AppColors.cardGlass)
                            .clickable {
                                stopAudio()
                                surahListCache?.let { uiState = QuranUiState.SurahListLoaded(it) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(detail.arabic.englishName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "${detail.arabic.numberOfAyahs} ayet · ${if (detail.arabic.revelationType == "Meccan") "Mekki" else "Medeni"}",
                            color = AppColors.textSoft, fontSize = 11.sp
                        )
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    if (isBesmele) {
                        item {
                            Text(
                                "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                                color = AppColors.sunsetGold, fontSize = 19.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            )
                        }
                    }
                    itemsIndexed(detail.arabic.ayahs) { idx, ayah ->
                        val trText = detail.trAyahs.getOrNull(idx)?.text ?: ""
                        val isPlaying = playingAyahNumber == ayah.number
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isPlaying) Brush.linearGradient(
                                        listOf(AppColors.activeGlow.copy(alpha = 0.22f), AppColors.magenta.copy(alpha = 0.22f))
                                    ) else Brush.linearGradient(listOf(AppColors.cardGlass, AppColors.cardGlass))
                                )
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(8.dp))
                                        .background(if (isPlaying) AppColors.activeGlow else Color.White.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${ayah.numberInSurah}", color = if (isPlaying) Color.White else AppColors.textSoft, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Box(
                                    modifier = Modifier.size(28.dp).clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .clickable { playAyahAudio(ayah.number) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isPlaying) Icons.Filled.Close else Icons.Filled.PlayArrow,
                                        contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(ayah.text, color = Color.White, fontSize = 20.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(trText, color = AppColors.textSoft, fontSize = 12.5.sp, lineHeight = 19.sp)
                        }
                    }
                    item {
                        Text(
                            "Arapça metin ve meal api.alquran.cloud üzerinden, okuma ise Mishary Alafasy kıraatiyle gerçek zamanlı sağlanmaktadır.",
                            color = AppColors.textSoft, fontSize = 10.sp, lineHeight = 14.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ====================================================================
// TAKVİM EKRANI - örnek/tahmini aylık namaz vakti tablosu.
// Gerçek uygulamada Diyanet/Aladhan gibi bir kaynaktan doldurulmalıdır.
// ====================================================================
@Composable
fun CalendarScreen(city: City) {
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("tr")) }
    val headerIcons = listOf(
        null to "Gün",
        Icons.Filled.NightsStay to "İmsak",
        Icons.Filled.WbSunny to "Güneş",
        Icons.Filled.WbSunny to "Öğle",
        Icons.Filled.WbTwilight to "İkindi",
        Icons.Filled.Brightness3 to "Akşam",
        Icons.Filled.Brightness2 to "Yatsı",
    )

    // Ay/yıl ve bugünün günü state olarak tutulur; gerçek tarih değiştiğinde
    // (örn. gece yarısı Ağustos'tan Eylül'e geçildiğinde) otomatik güncellenir.
    var currentYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var currentMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1) }
    var todayDay by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }
    val today = todayDay

    var monthDays by remember { mutableStateOf<List<MonthDay>?>(null) }
    var fromApi by remember { mutableStateOf<Boolean?>(null) }

    // Şehir, yıl veya ay değiştiğinde ana sayfa ile AYNI teknikle veriyi yeniden çek
    LaunchedEffect(city, currentYear, currentMonth) {
        monthDays = null
        fromApi = null
        val (days, usedApi) = fetchMonthForCity(city, currentYear, currentMonth)
        monthDays = days
        fromApi = usedApi
    }

    // Her dakika gerçek tarihi kontrol et; ay değiştiyse (ör. Ağustos->Eylül)
    // currentYear/currentMonth güncellenir ve yukarıdaki LaunchedEffect otomatik
    // tetiklenerek yeni ayın verisini çeker.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            val cal = Calendar.getInstance()
            val realYear = cal.get(Calendar.YEAR)
            val realMonth = cal.get(Calendar.MONTH) + 1
            if (realYear != currentYear || realMonth != currentMonth) {
                currentYear = realYear
                currentMonth = realMonth
            }
            todayDay = cal.get(Calendar.DAY_OF_MONTH)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Aylık Takvim", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(
            "${monthFormat.format(Calendar.getInstance().apply { set(currentYear, currentMonth - 1, 1) }.time).replaceFirstChar { it.uppercase() }} — ${city.name}",
            color = AppColors.textSoft, fontSize = 12.5.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
            textAlign = TextAlign.Center
        )

        val days = monthDays
        if (days == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Yükleniyor...", color = AppColors.textSoft, fontSize = 13.sp)
            }
        } else {
            // Başlık satırı - ikonlu, sütun grid'i ile hizalı
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                headerIcons.forEach { (icon, label) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(if (icon == null) 0.55f else 1f)
                    ) {
                        if (icon != null) {
                            Icon(icon, contentDescription = null, tint = AppColors.textSoft, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Text(
                            label, color = AppColors.textSoft, fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp, textAlign = TextAlign.Center
                        )
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.weight(1f).padding(top = 6.dp)
            ) {
                items(days.size) { idx ->
                    val row = days[idx]
                    val isToday = row.day == today
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isToday) Brush.linearGradient(
                                    listOf(AppColors.activeGlow.copy(alpha = 0.32f), AppColors.magenta.copy(alpha = 0.32f))
                                ) else Brush.linearGradient(listOf(AppColors.cardGlass, AppColors.cardGlass))
                            )
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(0.55f)
                                .size(26.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (isToday) Brush.linearGradient(
                                        listOf(Color(0xFFF0578A), Color(0xFF9A5FC4))
                                    ) else Brush.linearGradient(
                                        listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${row.day}", color = Color.White,
                                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold
                            )
                        }
                        listOf(row.imsak, row.gunes, row.ogle, row.ikindi, row.aksam, row.yatsi).forEach { t ->
                            Text(
                                t,
                                color = if (isToday) Color.White else Color.White.copy(alpha = 0.85f),
                                fontSize = 11.5.sp,
                                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Text(
            if (fromApi == true)
                "Vakitler Diyanet İşleri Başkanlığı (resmi) verilerine göre gösterilmektedir."
            else if (fromApi == false)
                "Diyanet API'sine ulaşılamadı; vakitler tahmini astronomik hesaplamayla gösterilmektedir."
            else "",
            color = AppColors.textSoft, fontSize = 10.5.sp, lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
    }
}

// ====================================================================
// AYARLAR EKRANI - 81 il arasından konum seçimi. Seçim SharedPreferences
// ile kalıcı olarak saklanır; uygulama her açıldığında son seçilen il
// otomatik yüklenir.
// ====================================================================
@Composable
fun SettingsScreen(selectedCity: City, onCitySelected: (City) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filteredCities = remember(query) {
        TR_CITIES.filter { it.name.contains(query, ignoreCase = true) }
            .sortedBy { it.name }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Ayarlar", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(
            "Konumunuzu seçin, namaz vakitleri buna göre hesaplansın",
            color = AppColors.textSoft, fontSize = 12.5.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
            textAlign = TextAlign.Center
        )

        Text(
            "SEÇİLİ KONUM", color = AppColors.textSoft, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(AppColors.activeGlow.copy(alpha = 0.28f), AppColors.magenta.copy(alpha = 0.28f))
                    )
                )
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("ŞU ANKİ İL", color = AppColors.textSoft, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
                Text(selectedCity.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "ŞEHİR SEÇ (81 İL)", color = AppColors.textSoft, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("İl ara... (örn. Ankara)", color = AppColors.textSoft, fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = AppColors.cardGlass,
                unfocusedContainerColor = AppColors.cardGlass,
                focusedBorderColor = Color.White.copy(alpha = 0.3f),
                unfocusedBorderColor = AppColors.cardBorder,
                cursorColor = Color.White
            )
        )

        if (filteredCities.isEmpty()) {
            Text(
                "Sonuç bulunamadı", color = AppColors.textSoft, fontSize = 12.5.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredCities) { city ->
                    val isSel = city.name == selectedCity.name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSel) Brush.linearGradient(
                                    listOf(AppColors.activeGlow.copy(alpha = 0.4f), AppColors.magenta.copy(alpha = 0.4f))
                                ) else Brush.linearGradient(listOf(AppColors.cardGlass, AppColors.cardGlass))
                            )
                            .clickable { onCitySelected(city) }
                            .padding(horizontal = 12.dp, vertical = 11.dp)
                    ) {
                        Text(city.name, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        if (isSel) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}
