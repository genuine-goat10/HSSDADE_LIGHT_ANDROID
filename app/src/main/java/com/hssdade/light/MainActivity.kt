package com.hssdade.light

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hssdade.light.data.*
import com.hssdade.light.pdf.QuotePdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var sharedTextState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.get(this)
        sharedTextState.value = extractIncomingText(intent)
        setContent {
            ApexBidApp(
                db = db,
                initialSharedMessage = sharedTextState.value,
                onClearSharedMessage = { sharedTextState.value = "" }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incoming = extractIncomingText(intent)
        if (incoming.isNotBlank()) {
            sharedTextState.value = incoming
        }
    }

    private fun extractIncomingText(intent: Intent?): String {
        if (intent == null) return ""
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    ?: intent.getStringExtra(Intent.EXTRA_TITLE)
                    ?: ""
                val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                listOf(subject.trim(), body.trim()).filter { it.isNotEmpty() }.joinToString("\n")
            }
            Intent.ACTION_PROCESS_TEXT -> {
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
            }
            else -> ""
        }
    }
}

fun tr(lang: String, en: String, es: String): String = if (lang == "es") es else en

fun isNightTimeNow(): Boolean {
    val cal = Calendar.getInstance()
    val minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    // Modo claro de 06:30 (390 min) a 18:30 (1110 min); modo oscuro fuera de ese rango
    return minutesOfDay < 390 || minutesOfDay >= 1110
}

fun formatUsPhoneInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(10)
    return when {
        digits.isEmpty() -> ""
        digits.length <= 3 -> "(${digits}"
        digits.length <= 6 -> "(${digits.substring(0, 3)}) ${digits.substring(3)}"
        else -> "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
    }
}

class UsPhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(10)
        val formatted = buildString {
            for (i in digits.indices) {
                if (i == 0) append('(')
                if (i == 3) append(") ")
                if (i == 6) append('-')
                append(digits[i])
            }
        }

        val offsetTranslator = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val cleanOffset = offset.coerceIn(0, digits.length)
                return when {
                    digits.isEmpty() -> 0
                    cleanOffset <= 3 -> cleanOffset + 1
                    cleanOffset <= 6 -> cleanOffset + 3
                    else -> cleanOffset + 4
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                return when {
                    offset <= 1 -> 0
                    offset <= 4 -> (offset - 1).coerceAtMost(digits.length)
                    offset <= 6 -> 3.coerceAtMost(digits.length)
                    offset <= 9 -> (offset - 3).coerceAtMost(digits.length)
                    offset <= 10 -> 6.coerceAtMost(digits.length)
                    else -> (offset - 4).coerceAtMost(digits.length)
                }
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetTranslator)
    }
}

data class ParsedClientMessage(
    val name: String,
    val phoneDigits: String,
    val address: String,
    val workSummary: String,
    val scheduledAtMillis: Long,
    val notes: String
)

fun looksLikeClientMessageOrAddress(text: String): Boolean {
    val clean = text.trim()
    if (clean.length !in 6..450) return false
    val hasPhone = Regex("""\b\d{3}[\s.-]?\d{3}[\s.-]?\d{4}\b""").containsMatchIn(clean)
    val hasStreet = Regex(
        """\b\d{1,6}\s+[A-Za-z0-9\s.#-]{2,40}?\b(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Drive|Dr|Lane|Ln|Court|Ct|Way|Terrace|Ter|Circle|Cir|Place|Pl|Highway|Hwy|Parkway|Pkwy|NW|NE|SW|SE|Calle|Avenida)\b""",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(clean)
    val hasServiceKeyword = Regex(
        """\b(?:breaker|panel|outlet|gfci|afci|switch|fan|charger|ev|light|recessed|wire|wiring|troubleshoot|estimate|cotizaci[oó]n|electric|el[eé]ctric|ma[nñ]ana|tomorrow|hoy|today)\b""",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(clean)
    return hasPhone || hasStreet || hasServiceKeyword
}

fun parseClientMessageToAppointment(
    rawText: String,
    baseDayMillis: Long = System.currentTimeMillis(),
    existingClients: List<ClientEntity> = emptyList()
): ParsedClientMessage {
    val clean = rawText.trim()
    val lines = clean.lines().map { it.trim() }.filter { it.isNotEmpty() }

    val phoneRegex = Regex("""(?:\+?1[\s.-]?)?\(?(\d{3})\)?[\s.-]?(\d{3})[\s.-]?(\d{4})""")
    val phoneMatch = phoneRegex.find(clean)
    var phoneDigits = if (phoneMatch != null) {
        phoneMatch.groupValues[1] + phoneMatch.groupValues[2] + phoneMatch.groupValues[3]
    } else {
        ""
    }

    val addressRegex = Regex(
        """\b\d{1,6}\s+[A-Za-z0-9\s.#-]{2,45}?\b(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Drive|Dr|Lane|Ln|Court|Ct|Way|Terrace|Ter|Circle|Cir|Place|Pl|Highway|Hwy|Parkway|Pkwy|Calle|Avenida)\b\.?(?:[,\s]+[A-Za-z\s]{2,25})?(?:[,\s]+(?:FL|Florida|TX|CA|NY|NJ|GA|NC|[A-Z]{2}))?(?:\s+\d{5})?""",
        RegexOption.IGNORE_CASE
    )
    val labeledAddrRegex = Regex("""(?:direcci[oó]n|address|ubicaci[oó]n|location)\s*[:=-]\s*(.+)""", RegexOption.IGNORE_CASE)
    var extractedAddress = labeledAddrRegex.find(clean)?.groupValues?.get(1)?.trim()
        ?: addressRegex.find(clean)?.value?.trim()
        ?: ""

    if (extractedAddress.isBlank() && lines.size == 1 && clean.any { it.isDigit() } && clean.length in 6..90) {
        extractedAddress = clean
    }

    val labeledNameRegex = Regex("""(?:nombre|name|cliente|client|soy|me llamo|my name is|this is|i'm|i am)\s*[:=-]?\s*([A-ZÁÉÍÓÚÑa-záéíóúñ]+(?:\s+[A-ZÁÉÍÓÚÑa-záéíóúñ]+){0,2})""", RegexOption.IGNORE_CASE)
    val nameMatch = labeledNameRegex.find(clean)
    var extractedName = when {
        nameMatch != null -> nameMatch.groupValues[1].trim()
        lines.isNotEmpty() && lines[0].length <= 35 && !lines[0].any { it.isDigit() } -> lines[0]
        else -> ""
    }

    if (existingClients.isNotEmpty()) {
        val matchedByPhone = if (phoneDigits.length == 10) {
            existingClients.find { it.phone.filter { d -> d.isDigit() }.endsWith(phoneDigits) }
        } else null

        val matchedByAddress = if (extractedAddress.isNotBlank()) {
            existingClients.find {
                it.address.isNotBlank() && (
                    it.address.contains(extractedAddress, ignoreCase = true) ||
                    extractedAddress.contains(it.address, ignoreCase = true)
                )
            }
        } else null

        val matchedByName = if (extractedName.isNotBlank()) {
            existingClients.find { it.name.equals(extractedName, ignoreCase = true) }
        } else null

        val matched = matchedByPhone ?: matchedByAddress ?: matchedByName
        if (matched != null) {
            if (extractedName.isBlank()) extractedName = matched.name
            if (phoneDigits.isBlank()) phoneDigits = matched.phone.filter { it.isDigit() }.take(10)
            if (extractedAddress.isBlank()) extractedAddress = matched.address
        }
    }

    val cal = Calendar.getInstance().apply {
        timeInMillis = baseDayMillis
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val lower = clean.lowercase()
    when {
        lower.contains("pasado mañana") || lower.contains("day after tomorrow") -> cal.add(Calendar.DAY_OF_YEAR, 2)
        lower.contains("mañana") || lower.contains("tomorrow") -> cal.add(Calendar.DAY_OF_YEAR, 1)
        lower.contains("hoy") || lower.contains("today") -> { }
        else -> {
            val dayMap = listOf(
                listOf("lunes", "monday") to Calendar.MONDAY,
                listOf("martes", "tuesday") to Calendar.TUESDAY,
                listOf("miércoles", "miercoles", "wednesday") to Calendar.WEDNESDAY,
                listOf("jueves", "thursday") to Calendar.THURSDAY,
                listOf("viernes", "friday") to Calendar.FRIDAY,
                listOf("sábado", "sabado", "saturday") to Calendar.SATURDAY,
                listOf("domingo", "sunday") to Calendar.SUNDAY
            )
            for ((keywords, targetDow) in dayMap) {
                if (keywords.any { lower.contains(it) }) {
                    val currentDow = cal.get(Calendar.DAY_OF_WEEK)
                    var diff = targetDow - currentDow
                    if (diff <= 0) diff += 7
                    cal.add(Calendar.DAY_OF_YEAR, diff)
                    break
                }
            }
        }
    }

    val timeRegex = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)\b""", RegexOption.IGNORE_CASE)
    val time24Regex = Regex("""(?:a las|at|hora|time)\s*[:=-]?\s*(\d{1,2}):(\d{2})""", RegexOption.IGNORE_CASE)
    val tMatch = timeRegex.find(clean)
    if (tMatch != null) {
        var h = tMatch.groupValues[1].toIntOrNull() ?: 9
        val m = tMatch.groupValues[2].toIntOrNull() ?: 0
        val ampm = tMatch.groupValues[3].lowercase()
        if (ampm.startsWith("p") && h < 12) h += 12
        if (ampm.startsWith("a") && h == 12) h = 0
        cal.set(Calendar.HOUR_OF_DAY, h.coerceIn(0, 23))
        cal.set(Calendar.MINUTE, m.coerceIn(0, 59))
    } else {
        val t24 = time24Regex.find(clean)
        if (t24 != null) {
            val h = t24.groupValues[1].toIntOrNull() ?: 9
            val m = t24.groupValues[2].toIntOrNull() ?: 0
            cal.set(Calendar.HOUR_OF_DAY, h.coerceIn(0, 23))
            cal.set(Calendar.MINUTE, m.coerceIn(0, 59))
        }
    }

    var summary = clean
    if (phoneMatch != null) summary = summary.replace(phoneMatch.value, "")
    if (extractedAddress.isNotBlank()) summary = summary.replace(extractedAddress, "")
    summary = summary
        .replace(Regex("""\s+"""), " ")
        .trim(' ', ',', '.', '-', ':', ';')

    return ParsedClientMessage(
        name = extractedName,
        phoneDigits = phoneDigits,
        address = extractedAddress,
        workSummary = summary.ifBlank { "Electrical service visit" },
        scheduledAtMillis = cal.timeInMillis,
        notes = "Imported from message"
    )
}

private fun requiredRuntimePermissions(): Array<String> {
    val perms = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CONTACTS
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        perms.add(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    return perms.toTypedArray()
}

private fun openAppSystemSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Composable
fun ApexBidLogo(size: Dp = 40.dp) {
    val logoShape = RoundedCornerShape(size * 0.22f)
    Box(
        modifier = Modifier
            .size(size)
            .background(Color(0xFF0F4761), logoShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.apexbid_icon),
            contentDescription = "ApexBid Logo",
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun ApexBidApp(
    db: AppDatabase,
    initialSharedMessage: String = "",
    onClearSharedMessage: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTabletLandscape = configuration.screenWidthDp >= 600

    var settings by remember { mutableStateOf(EstimateSettings.load(context)) }
    val lang = settings.languageCode

    val useDarkTheme = remember(settings.themeMode) {
        when (settings.themeMode) {
            "dark" -> true
            "light" -> false
            else -> isNightTimeNow()
        }
    }

    val colorScheme = if (useDarkTheme) {
        darkColorScheme(
            primary = Color(0xFF56B4D3),
            secondary = Color(0xFF4DB6AC),
            background = Color(0xFF0F171C),
            surface = Color(0xFF162127),
            surfaceVariant = Color(0xFF22313A)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF145A72),
            secondary = Color(0xFF2D8A78)
        )
    }

    var tab by remember { mutableStateOf(if (initialSharedMessage.isNotBlank()) 1 else 0) }
    var incomingMessageToParse by remember { mutableStateOf(initialSharedMessage) }

    LaunchedEffect(initialSharedMessage) {
        if (initialSharedMessage.isNotBlank()) {
            incomingMessageToParse = initialSharedMessage
            tab = 1
            onClearSharedMessage()
        }
    }

    val clients by db.clientDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val quotes by db.quoteDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val appointments by db.appointmentDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    val navItems = listOf(
        Triple(tr(lang, "Home", "Inicio"), Icons.Filled.Home, 0),
        Triple(tr(lang, "Schedule", "Agenda"), Icons.Filled.EventNote, 1),
        Triple(tr(lang, "Business", "Negocio"), Icons.Filled.BarChart, 2),
        Triple(tr(lang, "Clients", "Clientes"), Icons.Filled.People, 3),
        Triple(tr(lang, "Estimate", "Cotizar"), Icons.Filled.Calculate, 4),
        Triple(tr(lang, "History", "Historial"), Icons.Filled.History, 5),
        Triple(tr(lang, "Settings", "Ajustes"), Icons.Filled.Settings, 6)
    )

    MaterialTheme(colorScheme = colorScheme) {
        val mainContent: @Composable (Modifier) -> Unit = { contentModifier ->
            Column(
                contentModifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!isTabletLandscape) {
                            ApexBidLogo(size = 38.dp)
                        }
                        Column {
                            Text(
                                text = settings.brandName.ifBlank { "ApexBid" },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = tr(lang, "Electrical Estimates & Invoices", "Cotizaciones y Facturas Eléctricas"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Botón rápido de Tema (Auto / Claro / Oscuro)
                        FilledTonalIconButton(
                            onClick = {
                                val nextTheme = when (settings.themeMode) {
                                    "auto" -> "dark"
                                    "dark" -> "light"
                                    else -> "auto"
                                }
                                val updated = settings.copy(themeMode = nextTheme)
                                settings = updated
                                updated.save(context)
                                val msg = when (nextTheme) {
                                    "dark" -> tr(lang, "Theme: Dark", "Tema: Oscuro")
                                    "light" -> tr(lang, "Theme: Light", "Tema: Claro")
                                    else -> tr(lang, "Theme: Auto (Time of day)", "Tema: Automático (Hora del día)")
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = when (settings.themeMode) {
                                    "dark" -> Icons.Filled.DarkMode
                                    "light" -> Icons.Filled.LightMode
                                    else -> Icons.Filled.BrightnessAuto
                                },
                                contentDescription = "Theme",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        // Selector rápido de idioma EN / ES
                        FilledTonalButton(
                            onClick = {
                                val nextLang = if (lang == "en") "es" else "en"
                                val updated = settings.copy(languageCode = nextLang)
                                settings = updated
                                updated.save(context)
                                Toast.makeText(
                                    context,
                                    if (nextLang == "en") "Language: English" else "Idioma: Español",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (lang == "en") "EN" else "ES", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                when (tab) {
                    0 -> HomeScreen(
                        lang = lang,
                        clientCount = clients.size,
                        quotes = quotes,
                        appointments = appointments,
                        isTablet = isTabletLandscape,
                        onNew = { tab = 4 },
                        onOpenAgenda = { tab = 1 }
                    )
                    1 -> ScheduleAndRoutesScreen(
                        lang = lang,
                        appointments = appointments,
                        clients = clients,
                        quotes = quotes,
                        settings = settings,
                        isTablet = isTabletLandscape,
                        initialMessageText = incomingMessageToParse,
                        onConsumeInitialMessage = { incomingMessageToParse = "" },
                        onSaveAppointment = { appt, alsoSaveClient ->
                            scope.launch {
                                db.appointmentDao().insert(appt)
                                if (alsoSaveClient && appt.clientName.isNotBlank()) {
                                    val exists = clients.any { it.name.equals(appt.clientName.trim(), ignoreCase = true) }
                                    if (!exists) {
                                        db.clientDao().insert(
                                            ClientEntity(
                                                name = appt.clientName.trim(),
                                                phone = formatUsPhoneInput(appt.clientPhone),
                                                address = appt.clientAddress.trim()
                                            )
                                        )
                                    }
                                }
                                Toast.makeText(context, tr(lang, "Appointment saved", "Cita organizada y guardada"), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUpdateAppointment = { appt ->
                            scope.launch { db.appointmentDao().update(appt) }
                        },
                        onDeleteAppointment = { appt ->
                            scope.launch { db.appointmentDao().delete(appt) }
                        }
                    )
                    2 -> BusinessOverviewScreen(lang, quotes, isTabletLandscape)
                    3 -> ClientsScreen(
                        lang = lang,
                        clients = clients,
                        isTablet = isTabletLandscape,
                        onSave = { name, phone, email, address ->
                            scope.launch {
                                db.clientDao().insert(
                                    ClientEntity(
                                        name = name,
                                        phone = formatUsPhoneInput(phone),
                                        email = email,
                                        address = address
                                    )
                                )
                                Toast.makeText(context, tr(lang, "Client saved", "Cliente guardado"), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUpdate = { updatedClient ->
                            scope.launch {
                                db.clientDao().update(updatedClient.copy(phone = formatUsPhoneInput(updatedClient.phone)))
                                Toast.makeText(context, tr(lang, "Client updated", "Cliente actualizado"), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = { client ->
                            scope.launch { db.clientDao().delete(client) }
                        }
                    )
                    4 -> QuoteScreen(lang, clients, settings, isTabletLandscape) { q ->
                        scope.launch {
                            db.quoteDao().insert(q)
                            Toast.makeText(context, tr(lang, "Estimate saved", "Cotización guardada"), Toast.LENGTH_SHORT).show()
                            tab = 5
                        }
                    }
                    5 -> HistoryScreen(
                        lang = lang,
                        quotes = quotes,
                        onUpdateQuote = { updated ->
                            scope.launch { db.quoteDao().update(updated) }
                        },
                        onExportPdf = { q, mode ->
                            try {
                                val generated = QuotePdf.create(
                                    context = context,
                                    quote = q,
                                    companyLicense = settings.companyLicense,
                                    companyPhone = settings.companyPhone,
                                    brandName = settings.brandName
                                )
                                Toast.makeText(context, "PDF: ${generated.fileName}", Toast.LENGTH_SHORT).show()
                                when (mode) {
                                    "share" -> if (generated.uri != null) sharePdfGeneral(context, generated.uri, q, settings.brandName)
                                    "email" -> sendQuoteByEmail(context, q, generated.uri, settings.brandName)
                                    "sms" -> sendQuoteBySms(context, q, settings.brandName)
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    6 -> SettingsScreen(
                        settings = settings,
                        onSave = { updated ->
                            settings = updated
                            updated.save(context)
                            Toast.makeText(context, tr(updated.languageCode, "Settings saved", "Configuración guardada"), Toast.LENGTH_SHORT).show()
                        },
                        onExportBackup = { uri ->
                            scope.launch {
                                try {
                                    exportDatabaseJson(context, db, uri)
                                    Toast.makeText(context, tr(lang, "Backup exported", "Respaldo exportado correctamente"), Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onImportBackup = { uri ->
                            scope.launch {
                                try {
                                    val count = importDatabaseJson(context, db, uri)
                                    Toast.makeText(context, tr(lang, "Backup imported ($count records)", "Respaldo importado ($count registros)"), Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }

        if (isTabletLandscape) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        header = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                ApexBidLogo(size = 42.dp)
                                Spacer(Modifier.height(4.dp))
                                Text("ApexBid", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    ) {
                        navItems.forEach { (label, icon, index) ->
                            NavigationRailItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                    VerticalDivider()
                    mainContent(Modifier.weight(1f))
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        navItems.forEach { (label, icon, index) ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            ) { padding ->
                mainContent(Modifier.padding(padding))
            }
        }
    }
}

private fun saveUriToInternalFile(context: Context, uri: Uri, prefix: String): String? {
    return try {
        val dir = File(context.filesDir, "quote_photos").apply { mkdirs() }
        val dest = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bmp = BitmapFactory.decodeStream(input) ?: return null
            FileOutputStream(dest).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        }
        dest.absolutePath
    } catch (_: Exception) {
        null
    }
}

private fun createTempCameraUri(context: Context): Pair<Uri, File> {
    val dir = File(context.filesDir, "quote_photos").apply { mkdirs() }
    val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return uri to file
}

private fun searchHomeDepotMaterial(context: Context, query: String) {
    val clean = query.trim()
    if (clean.isEmpty()) {
        Toast.makeText(context, "Enter material or part name", Toast.LENGTH_SHORT).show()
        return
    }
    val searchTerm = "site:homedepot.com $clean"
    val url = "https://www.google.com/search?q=${Uri.encode(searchTerm)}"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, "Browser not available", Toast.LENGTH_SHORT).show()
    }
}

private fun launchVisualIdentifier(context: Context, imageUri: Uri) {
    val lensIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        setPackage("com.google.ar.lens")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(lensIntent)
    } catch (_: Exception) {
        val googleAppIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            setPackage("com.google.android.googlequicksearchbox")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(googleAppIntent)
        } catch (_: Exception) {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Identify part with Google Lens"
            )
            context.startActivity(chooser)
        }
    }
}

private fun buildSmartDescriptions(
    selectedServices: List<ServiceItem>,
    currentEs: String,
    currentEn: String,
    notes: String
): Pair<String, String> {
    val dictionary = listOf(
        "breaker" to "interruptor termomagnético (breaker)",
        "panel" to "panel eléctrico principal",
        "subpanel" to "subpanel de distribución",
        "outlet" to "tomacorriente (receptáculo)",
        "gfci" to "protección diferencial GFCI",
        "afci" to "protección contra falla de arco AFCI",
        "switch" to "interruptor de pared",
        "dimmer" to "atenuador de luz (dimmer)",
        "recessed" to "luminaria empotrada LED",
        "ceiling fan" to "ventilador de techo",
        "charger" to "cargador para vehículo eléctrico (EV)",
        "surge" to "supresor de sobretensiones",
        "ground" to "sistema de puesta a tierra",
        "conduit" to "canalización eléctrica (conduit)",
        "wire" to "cableado eléctrico THHN/Romex",
        "troubleshooting" to "diagnóstico y localización de falla eléctrica",
        "install" to "instalación",
        "replace" to "reemplazo",
        "upgrade" to "actualización de capacidad"
    )

    if (selectedServices.isNotEmpty()) {
        val categories = selectedServices.map { it.category }.distinct()
        val esItems = selectedServices.joinToString("\n") { "• ${it.nameEs}." }
        val enItems = selectedServices.joinToString("\n") { "• ${it.nameEn}." }

        val technicalDetailsEn = mutableListOf<String>()
        val technicalDetailsEs = mutableListOf<String>()

        if (categories.any { it.contains("Paneles", ignoreCase = true) }) {
            technicalDetailsEn.add("Includes load balancing, proper torque verification on busbars/lugs, circuit labeling, and grounding/bonding inspection.")
            technicalDetailsEs.add("Incluye balanceo de cargas, verificación de torque en barras y terminales, rotulación de circuitos e inspección de puesta a tierra.")
        }
        if (categories.any { it.contains("Circuitos", ignoreCase = true) }) {
            technicalDetailsEn.add("Includes dedicated home-run wiring sizing per amperage load, proper conduit/box mounting, and overcurrent protection.")
            technicalDetailsEs.add("Incluye calibre de conductor dedicado según amperaje de carga, fijación de canalización/cajas y protección contra sobrecorriente.")
        }
        if (categories.any { it.contains("Iluminación", ignoreCase = true) }) {
            technicalDetailsEn.add("Includes structural box support check, clean ceiling/wall cutouts, switch control integration, and operational dimming/balance test.")
            technicalDetailsEs.add("Incluye revisión de soporte estructural en caja, cortes limpios, integración con interruptores y prueba de funcionamiento.")
        }
        if (categories.any { it.contains("Receptáculos", ignoreCase = true) }) {
            technicalDetailsEn.add("Includes polarity check, GFCI/AFCI trip testing where required by code, and secure device trim alignment.")
            technicalDetailsEs.add("Incluye verificación de polaridad, prueba de disparo GFCI/AFCI donde exige el código y nivelación de placas.")
        }

        val detailBlockEn = if (technicalDetailsEn.isNotEmpty()) "\n" + technicalDetailsEn.joinToString(" ") else ""
        val detailBlockEs = if (technicalDetailsEs.isNotEmpty()) "\n" + technicalDetailsEs.joinToString(" ") else ""
        val noteEn = if (notes.isNotBlank()) "\nJob Note: ${notes.trim()}." else ""
        val noteEs = if (notes.isNotBlank()) "\nNota técnica: ${notes.trim()}." else ""

        val fullEn = "General Scope of Electrical Work:\n" +
            "Supply of UL-listed electrical materials and professional installation/service for:\n$enItems$detailBlockEn\n" +
            "All work will be performed in strict compliance with the National Electrical Code (NEC), including final voltage, continuity, and safety verification.$noteEn"

        val fullEs = "Descripción General del Trabajo Eléctrico:\n" +
            "Suministro de materiales certificados UL e instalación/servicio profesional de:\n$esItems$detailBlockEs\n" +
            "Todo el trabajo se ejecutará conforme al Código Eléctrico Nacional (NEC), incluyendo pruebas finales de voltaje, continuidad y seguridad.$noteEs"

        return fullEs to fullEn
    }

    if (currentEs.isNotBlank() && currentEn.isBlank()) {
        var translated = currentEs
        dictionary.forEach { (en, es) ->
            translated = translated.replace(es, en, ignoreCase = true)
        }
        val polishedEs = "Alcance general del trabajo eléctrico: ${currentEs.trim()}. Incluye materiales certificados UL y pruebas de seguridad conforme al código NEC."
        val polishedEn = "General scope of electrical work: $translated. Includes UL-listed materials and NEC code-compliant safety testing."
        return polishedEs to polishedEn
    }

    if (currentEn.isNotBlank() && currentEs.isBlank()) {
        var translated = currentEn
        dictionary.forEach { (en, es) ->
            translated = translated.replace(en, es, ignoreCase = true)
        }
        val polishedEn = "General scope of electrical work: ${currentEn.trim()}. Includes UL-listed materials and NEC code-compliant safety testing."
        val polishedEs = "Alcance general del trabajo eléctrico: $translated. Incluye materiales certificados UL y pruebas de seguridad conforme al código NEC."
        return polishedEs to polishedEn
    }

    val defaultEn = "General scope of electrical work: Troubleshooting, supply of UL-listed materials, and execution of residential/commercial electrical work per NEC standards, including final operational and safety testing."
    val defaultEs = "Alcance general del trabajo eléctrico: Diagnóstico, suministro de materiales certificados UL y ejecución de trabajos eléctricos conforme al código NEC, incluyendo pruebas finales de funcionamiento y seguridad."
    return defaultEs to defaultEn
}

private fun openInGoogleMaps(context: Context, address: String) {
    val clean = address.trim()
    if (clean.isEmpty()) {
        Toast.makeText(context, "Enter an address first", Toast.LENGTH_SHORT).show()
        return
    }
    val mapUri = Uri.parse("geo:0,0?q=${Uri.encode(clean)}")
    val mapIntent = Intent(Intent.ACTION_VIEW, mapUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(mapIntent)
    } catch (e: Exception) {
        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(clean)}")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
}

private fun callPhoneNumber(context: Context, phone: String) {
    val digits = phone.filter { it.isDigit() }
    if (digits.isEmpty()) {
        Toast.makeText(context, "No phone number saved", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
    context.startActivity(intent)
}

@Suppress("DEPRECATION")
private suspend fun estimateTravelMinutesToAddress(
    context: Context,
    destinationAddress: String,
    fallbackOriginAddress: String
): Int? = withContext(Dispatchers.IO) {
    if (destinationAddress.isBlank() || !Geocoder.isPresent()) return@withContext null
    return@withContext try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val destList = geocoder.getFromLocationName(destinationAddress, 1)
        val dest = destList?.firstOrNull() ?: return@withContext null

        val originCoords = getCurrentDeviceLatLng(context) ?: run {
            if (fallbackOriginAddress.isNotBlank()) {
                val orig = geocoder.getFromLocationName(fallbackOriginAddress, 1)?.firstOrNull()
                if (orig != null) orig.latitude to orig.longitude else null
            } else null
        } ?: return@withContext null

        val results = FloatArray(1)
        Location.distanceBetween(originCoords.first, originCoords.second, dest.latitude, dest.longitude, results)
        val straightMeters = results[0].toDouble()
        // Factor vial urbano (~1.3x) a velocidad promedio de 45 km/h (750 m/min)
        val roadMeters = straightMeters * 1.30
        val mins = (roadMeters / 750.0).roundToInt().coerceAtLeast(5)
        mins
    } catch (_: Exception) {
        null
    }
}

private fun sendEnRouteSmsToClient(
    context: Context,
    lang: String,
    appt: AppointmentEntity,
    brandName: String,
    etaMinutes: Int?
) {
    val brand = brandName.ifBlank { "ApexBid" }
    val digits = appt.clientPhone.filter { it.isDigit() }
    if (digits.isEmpty()) {
        Toast.makeText(context, tr(lang, "No phone number saved for this client", "Este cliente no tiene teléfono guardado"), Toast.LENGTH_SHORT).show()
        return
    }
    val message = if (lang == "es") {
        buildString {
            append("Hola ${appt.clientName}, le saludamos de $brand. Ya vamos en camino")
            if (appt.clientAddress.isNotBlank()) append(" hacia ${appt.clientAddress}")
            if (etaMinutes != null) append(". Tiempo estimado de llegada: ~$etaMinutes min.")
            else append(".")
        }
    } else {
        buildString {
            append("Hello ${appt.clientName}, this is $brand. We are on our way")
            if (appt.clientAddress.isNotBlank()) append(" to ${appt.clientAddress}")
            if (etaMinutes != null) append(". Estimated arrival time: ~$etaMinutes min.")
            else append(".")
        }
    }
    val smsUri = Uri.parse("smsto:${Uri.encode(appt.clientPhone)}")
    val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
        putExtra("sms_body", message)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, tr(lang, "SMS app not found", "No se encontró app de mensajes"), Toast.LENGTH_SHORT).show()
    }
}

private fun addAppointmentToSystemCalendar(context: Context, appt: AppointmentEntity, brandName: String) {
    val brand = brandName.ifBlank { "ApexBid" }
    val endMillis = appt.scheduledAt + appt.durationMinutes * 60_000L
    val desc = buildString {
        append("Scope: ${appt.workSummary}\n")
        if (appt.clientPhone.isNotBlank()) append("Phone: ${appt.clientPhone}\n")
        if (appt.notes.isNotBlank()) append("Notes: ${appt.notes}\n")
        append("Scheduled with $brand")
    }
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, "[$brand] ${appt.clientName}")
        putExtra(CalendarContract.Events.EVENT_LOCATION, appt.clientAddress)
        putExtra(CalendarContract.Events.DESCRIPTION, desc)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, appt.scheduledAt)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Calendar app not found", Toast.LENGTH_SHORT).show()
    }
}

@SuppressLint("MissingPermission")
private fun getCurrentDeviceLatLng(context: Context): Pair<Double, Double>? {
    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fineGranted && !coarseGranted) return null

    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        var best: Location? = null
        for (p in providers) {
            val loc = try { lm.getLastKnownLocation(p) } catch (_: Exception) { null }
            if (loc != null && (best == null || loc.accuracy < best.accuracy)) {
                best = loc
            }
        }
        if (best != null) best.latitude to best.longitude else null
    } catch (_: Exception) {
        null
    }
}

@Suppress("DEPRECATION")
private suspend fun optimizeAppointmentsRoute(
    context: Context,
    dayAppointments: List<AppointmentEntity>,
    useCurrentGps: Boolean,
    customOriginAddress: String
): List<AppointmentEntity> = withContext(Dispatchers.IO) {
    val withAddress = dayAppointments.filter { it.clientAddress.isNotBlank() }
    if (withAddress.isEmpty()) return@withContext emptyList()

    val geocoder = if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()) else null

    fun geocodeAddress(addr: String): Pair<Double, Double>? {
        if (geocoder == null || addr.isBlank()) return null
        return try {
            val list = geocoder.getFromLocationName(addr, 1)
            val first = list?.firstOrNull()
            if (first != null) first.latitude to first.longitude else null
        } catch (_: Exception) {
            null
        }
    }

    val startCoords: Pair<Double, Double>? = if (useCurrentGps) {
        getCurrentDeviceLatLng(context) ?: geocodeAddress(customOriginAddress)
    } else {
        geocodeAddress(customOriginAddress) ?: getCurrentDeviceLatLng(context)
    }

    if (startCoords == null || geocoder == null) {
        return@withContext withAddress.sortedBy { it.scheduledAt }
    }

    data class GeoNode(val appt: AppointmentEntity, val coords: Pair<Double, Double>?)

    val nodes = withAddress.map { GeoNode(it, geocodeAddress(it.clientAddress)) }
    val unvisited = nodes.toMutableList()
    val ordered = mutableListOf<AppointmentEntity>()
    var currentLat = startCoords.first
    var currentLng = startCoords.second

    while (unvisited.isNotEmpty()) {
        val next = unvisited.minByOrNull { node ->
            val c = node.coords
            if (c == null) {
                Double.MAX_VALUE / 2.0
            } else {
                val results = FloatArray(1)
                Location.distanceBetween(currentLat, currentLng, c.first, c.second, results)
                results[0].toDouble()
            }
        } ?: break

        unvisited.remove(next)
        ordered.add(next.appt)
        if (next.coords != null) {
            currentLat = next.coords.first
            currentLng = next.coords.second
        }
    }

    ordered
}

private fun launchMultiStopGoogleMapsRoute(
    context: Context,
    orderedStops: List<AppointmentEntity>,
    useCurrentGps: Boolean,
    customOriginAddress: String
) {
    if (orderedStops.isEmpty()) {
        Toast.makeText(context, "No appointments with addresses for this day", Toast.LENGTH_SHORT).show()
        return
    }

    val addresses = orderedStops.map { it.clientAddress.trim() }.filter { it.isNotEmpty() }
    if (addresses.isEmpty()) return

    val destination = Uri.encode(addresses.last())
    val waypoints = if (addresses.size > 1) {
        addresses.dropLast(1).joinToString("|") { Uri.encode(it) }
    } else {
        ""
    }

    val url = buildString {
        append("https://www.google.com/maps/dir/?api=1")
        if (!useCurrentGps && customOriginAddress.isNotBlank()) {
            append("&origin=${Uri.encode(customOriginAddress.trim())}")
        }
        append("&destination=$destination")
        if (waypoints.isNotEmpty()) {
            append("&waypoints=$waypoints")
        }
        append("&travelmode=driving")
    }

    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(mapIntent)
    } catch (_: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun sharePdfGeneral(context: Context, uri: Uri, quote: QuoteEntity, brandName: String) {
    val brand = brandName.ifBlank { "ApexBid" }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Estimate / Cotización - $brand (${quote.clientName})")
        if (quote.clientEmail.isNotBlank()) {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(quote.clientEmail))
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share PDF / Compartir PDF"))
}

private fun sendQuoteByEmail(context: Context, quote: QuoteEntity, pdfUri: Uri?, brandName: String) {
    val brand = brandName.ifBlank { "ApexBid" }
    val money = NumberFormat.getCurrencyInstance(Locale.US).format(quote.total)
    val docType = if (quote.status == "Facturada") "Invoice / Factura" else "Estimate / Cotización"
    val body = """
        Dear ${quote.clientName} / Estimado/a ${quote.clientName},
        
        Please find attached your electrical $docType from $brand for a total of $money.
        Adjuntamos su $docType eléctrica de $brand por un total de $money.
        
        Services / Servicios:
        ${quote.serviceName}
        
        Thank you for your business / Gracias por su confianza.
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        if (quote.clientEmail.isNotBlank()) {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(quote.clientEmail))
        }
        putExtra(Intent.EXTRA_SUBJECT, "Electrical $docType - $brand")
        putExtra(Intent.EXTRA_TEXT, body)
        if (pdfUri != null) {
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(intent, "Send Email / Enviar Correo"))
}

private fun sendQuoteBySms(context: Context, quote: QuoteEntity, brandName: String) {
    val brand = brandName.ifBlank { "ApexBid" }
    val money = NumberFormat.getCurrencyInstance(Locale.US).format(quote.total)
    val message = "Hello ${quote.clientName}, greetings from $brand. Your electrical estimate (${quote.serviceName.take(55)}) is $money. Status: ${quote.status}."
    val smsUri = Uri.parse("smsto:${Uri.encode(quote.clientPhone)}")
    val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
        putExtra("sms_body", message)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "SMS app not found", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun exportDatabaseJson(context: Context, db: AppDatabase, uri: Uri) = withContext(Dispatchers.IO) {
    val clients = db.clientDao().getAllOnce()
    val quotes = db.quoteDao().getAllOnce()
    val appointments = db.appointmentDao().getAllOnce()

    val root = JSONObject()
    root.put("app", "ApexBid")
    root.put("exportedAt", System.currentTimeMillis())

    val clientsArr = JSONArray()
    clients.forEach { c ->
        clientsArr.put(JSONObject().apply {
            put("name", c.name); put("phone", c.phone); put("email", c.email); put("address", c.address)
        })
    }
    root.put("clients", clientsArr)

    val quotesArr = JSONArray()
    quotes.forEach { q ->
        quotesArr.put(JSONObject().apply {
            put("clientName", q.clientName); put("clientPhone", q.clientPhone); put("clientEmail", q.clientEmail)
            put("clientAddress", q.clientAddress); put("serviceName", q.serviceName)
            put("descriptionEs", q.descriptionEs); put("descriptionEn", q.descriptionEn)
            put("observations", q.observations); put("quantity", q.quantity); put("total", q.total)
            put("status", q.status); put("signatureData", q.signatureData); put("signedAt", q.signedAt)
            put("photoUris", q.photoUris); put("createdAt", q.createdAt)
        })
    }
    root.put("quotes", quotesArr)

    val apptsArr = JSONArray()
    appointments.forEach { a ->
        apptsArr.put(JSONObject().apply {
            put("clientName", a.clientName); put("clientPhone", a.clientPhone); put("clientAddress", a.clientAddress)
            put("workSummary", a.workSummary); put("notes", a.notes); put("scheduledAt", a.scheduledAt)
            put("durationMinutes", a.durationMinutes); put("routeOrder", a.routeOrder); put("status", a.status)
        })
    }
    root.put("appointments", apptsArr)

    context.contentResolver.openOutputStream(uri)?.use { out ->
        out.write(root.toString(2).toByteArray(Charsets.UTF_8))
    } ?: throw IllegalStateException("Could not open destination file")
}

private suspend fun importDatabaseJson(context: Context, db: AppDatabase, uri: Uri): Int = withContext(Dispatchers.IO) {
    val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: throw IllegalStateException("Could not read file")
    val root = JSONObject(text)
    var imported = 0

    val clientsArr = root.optJSONArray("clients") ?: JSONArray()
    for (i in 0 until clientsArr.length()) {
        val obj = clientsArr.getJSONObject(i)
        db.clientDao().insert(
            ClientEntity(
                name = obj.optString("name"),
                phone = formatUsPhoneInput(obj.optString("phone")),
                email = obj.optString("email"),
                address = obj.optString("address")
            )
        )
        imported++
    }

    val quotesArr = root.optJSONArray("quotes") ?: JSONArray()
    for (i in 0 until quotesArr.length()) {
        val obj = quotesArr.getJSONObject(i)
        db.quoteDao().insert(
            QuoteEntity(
                clientName = obj.optString("clientName"),
                clientPhone = formatUsPhoneInput(obj.optString("clientPhone")),
                clientEmail = obj.optString("clientEmail"),
                clientAddress = obj.optString("clientAddress"),
                serviceName = obj.optString("serviceName"),
                descriptionEs = obj.optString("descriptionEs"),
                descriptionEn = obj.optString("descriptionEn"),
                observations = obj.optString("observations"),
                quantity = obj.optDouble("quantity", 1.0),
                total = obj.optDouble("total", 0.0),
                status = obj.optString("status", "Pendiente"),
                signatureData = obj.optString("signatureData"),
                signedAt = obj.optLong("signedAt", 0L),
                photoUris = obj.optString("photoUris"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        )
        imported++
    }

    val apptsArr = root.optJSONArray("appointments") ?: JSONArray()
    for (i in 0 until apptsArr.length()) {
        val obj = apptsArr.getJSONObject(i)
        db.appointmentDao().insert(
            AppointmentEntity(
                clientName = obj.optString("clientName"),
                clientPhone = formatUsPhoneInput(obj.optString("clientPhone")),
                clientAddress = obj.optString("clientAddress"),
                workSummary = obj.optString("workSummary"),
                notes = obj.optString("notes"),
                scheduledAt = obj.optLong("scheduledAt", System.currentTimeMillis()),
                durationMinutes = obj.optInt("durationMinutes", 60),
                routeOrder = obj.optInt("routeOrder", 0),
                status = obj.optString("status", "Programada")
            )
        )
        imported++
    }

    imported
}

private fun isSameCalendarDay(ts1: Long, ts2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = ts1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = ts2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
        c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun HomeScreen(
    lang: String,
    clientCount: Int,
    quotes: List<QuoteEntity>,
    appointments: List<AppointmentEntity>,
    isTablet: Boolean,
    onNew: () -> Unit,
    onOpenAgenda: () -> Unit
) {
    val money = NumberFormat.getCurrencyInstance(Locale.US)
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.US) }
    val pending = quotes.count { it.status == "Pendiente" }
    val approved = quotes.count { it.status == "Aprobada" }
    val invoiced = quotes.count { it.status == "Facturada" }
    val invoicedTotal = quotes.filter { it.status == "Facturada" }.sumOf { it.total }
    val todayAppts = remember(appointments) {
        val now = System.currentTimeMillis()
        appointments.filter { isSameCalendarDay(it.scheduledAt, now) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr(lang, "Dashboard", "Panel principal"), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(tr(lang, "Clients", "Clientes"), clientCount.toString(), Modifier.weight(1f))
            StatCard(tr(lang, "Today's Visits", "Citas Hoy"), todayAppts.size.toString(), Modifier.weight(1f))
            if (isTablet) {
                StatCard(tr(lang, "Total Invoiced", "Total Facturado"), money.format(invoicedTotal), Modifier.weight(1.3f))
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$pending", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(tr(lang, "Pending", "Pendientes"), style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$approved", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Text(tr(lang, "Approved", "Aprobadas"), style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$invoiced", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(tr(lang, "Invoiced", "Facturadas"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (todayAppts.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr(lang, "Today's Upcoming Visits", "Próximas visitas de hoy"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    todayAppts.take(3).forEach { a ->
                        Text(
                            "• ${timeFmt.format(Date(a.scheduledAt))} — ${a.clientName} (${a.workSummary})",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNew, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(tr(lang, "New Estimate", "Cotizar"), maxLines = 1)
            }
            OutlinedButton(onClick = onOpenAgenda, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Route, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(tr(lang, "Schedule & Route", "Agenda y Ruta"), maxLines = 1)
            }
        }
        Text(
            tr(lang, "Local mode active. All data is stored securely on this device.", "Modo local activo. Datos almacenados de forma segura en este dispositivo."),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleAndRoutesScreen(
    lang: String,
    appointments: List<AppointmentEntity>,
    clients: List<ClientEntity>,
    quotes: List<QuoteEntity>,
    settings: EstimateSettings,
    isTablet: Boolean,
    initialMessageText: String = "",
    onConsumeInitialMessage: () -> Unit = {},
    onSaveAppointment: (AppointmentEntity, Boolean) -> Unit,
    onUpdateAppointment: (AppointmentEntity) -> Unit,
    onDeleteAppointment: (AppointmentEntity) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val locale = if (lang == "es") Locale("es", "ES") else Locale.US
    val dayNameFmt = remember(lang) { SimpleDateFormat("EEE", locale) }
    val dayNumFmt = remember { SimpleDateFormat("dd", Locale.US) }
    val fullDateFmt = remember(lang) { SimpleDateFormat("EEEE dd MMM, yyyy", locale) }
    val rangeFmt = remember(lang) { SimpleDateFormat("dd MMM", locale) }
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.US) }

    var weekOffset by remember { mutableStateOf(0) }
    var selectedDayMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showNewDialog by remember { mutableStateOf(initialMessageText.isNotBlank()) }
    var prefillMessage by remember { mutableStateOf(initialMessageText) }
    var editingAppointment by remember { mutableStateOf<AppointmentEntity?>(null) }
    var lastAutoClipHash by remember {
        mutableStateOf(
            context.getSharedPreferences("apexbid_clip", Context.MODE_PRIVATE).getInt("last_hash", 0)
        )
    }

    fun checkClipboardForNewClientMessage() {
        if (showNewDialog || editingAppointment != null || prefillMessage.isNotBlank()) return
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            if (!cm.hasPrimaryClip()) return
            val clipText = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
            val hash = clipText.hashCode()
            if (clipText.isNotBlank() && hash != lastAutoClipHash && looksLikeClientMessageOrAddress(clipText)) {
                lastAutoClipHash = hash
                context.getSharedPreferences("apexbid_clip", Context.MODE_PRIVATE)
                    .edit().putInt("last_hash", hash).apply()
                prefillMessage = clipText
                showNewDialog = true
                Toast.makeText(context, tr(lang, "Message detected from clipboard", "Mensaje detectado del portapapeles"), Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) { }
    }

    LaunchedEffect(Unit) {
        delay(250)
        checkClipboardForNewClientMessage()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkClipboardForNewClientMessage()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(initialMessageText) {
        if (initialMessageText.isNotBlank()) {
            prefillMessage = initialMessageText
            showNewDialog = true
            onConsumeInitialMessage()
        }
    }

    var useCurrentGps by remember { mutableStateOf(true) }
    var customStartAddress by remember { mutableStateOf(settings.defaultStartAddress) }
    var isOptimizingRoute by remember { mutableStateOf(false) }

    LaunchedEffect(settings.defaultStartAddress) {
        if (customStartAddress.isBlank() && settings.defaultStartAddress.isNotBlank()) {
            customStartAddress = settings.defaultStartAddress
        }
    }

    // Vista de 2 semanas completas (14 días: semana actual + próxima semana)
    val twoWeeksDays = remember(weekOffset) {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            add(Calendar.WEEK_OF_YEAR, weekOffset)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        (0..13).map { offset ->
            val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            c.timeInMillis
        }
    }

    val dayAppointments = remember(appointments, selectedDayMillis) {
        appointments
            .filter { isSameCalendarDay(it.scheduledAt, selectedDayMillis) }
            .sortedWith(compareBy({ if (it.routeOrder > 0) it.routeOrder else Int.MAX_VALUE }, { it.scheduledAt }))
    }

    if (showNewDialog) {
        AppointmentEditorDialog(
            lang = lang,
            initialDateMillis = selectedDayMillis,
            initialRawMessage = prefillMessage,
            existingAppointment = null,
            clients = clients,
            quotes = quotes,
            onDismiss = {
                showNewDialog = false
                prefillMessage = ""
            },
            onSave = { newAppt, alsoSaveClient ->
                onSaveAppointment(newAppt, alsoSaveClient)
                selectedDayMillis = newAppt.scheduledAt
                showNewDialog = false
                prefillMessage = ""
            }
        )
    }

    if (editingAppointment != null) {
        AppointmentEditorDialog(
            lang = lang,
            initialDateMillis = editingAppointment!!.scheduledAt,
            initialRawMessage = "",
            existingAppointment = editingAppointment,
            clients = clients,
            quotes = quotes,
            onDismiss = { editingAppointment = null },
            onSave = { updatedAppt, _ ->
                onUpdateAppointment(updatedAppt)
                selectedDayMillis = updatedAppt.scheduledAt
                editingAppointment = null
                Toast.makeText(context, tr(lang, "Appointment updated", "Cita actualizada"), Toast.LENGTH_SHORT).show()
            }
        )
    }

    val agendaHeaderAndRoutePanel: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tr(lang, "2-Week Schedule & Routes", "Agenda 2 Semanas y Rutas"), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            prefillMessage = ""
                            showNewDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.ContentPasteGo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr(lang, "From SMS", "De Mensaje"), maxLines = 1)
                    }
                    Button(
                        onClick = {
                            prefillMessage = ""
                            showNewDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr(lang, "Appt", "Cita"), maxLines = 1)
                    }
                }
            }

            // Navegación de 2 semanas
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { weekOffset -= 2 }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous 2 weeks")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${rangeFmt.format(Date(twoWeeksDays.first()))} – ${rangeFmt.format(Date(twoWeeksDays.last()))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = fullDateFmt.format(Date(selectedDayMillis)).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { weekOffset += 2 }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next 2 weeks")
                }
            }

            // Cuadrícula visual de 2 semanas (Semana 1 y Semana 2)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                twoWeeksDays.chunked(7).forEach { weekChunk ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        weekChunk.forEach { dayMs ->
                            val isSelected = isSameCalendarDay(dayMs, selectedDayMillis)
                            val isToday = isSameCalendarDay(dayMs, System.currentTimeMillis())
                            val count = appointments.count { isSameCalendarDay(it.scheduledAt, dayMs) }
                            val bgColor = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isToday -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            }
                            val textColor = when {
                                isSelected -> Color.White
                                isToday -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(bgColor, RoundedCornerShape(9.dp))
                                    .clickable { selectedDayMillis = dayMs }
                                    .padding(vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = dayNameFmt.format(Date(dayMs)).take(3).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor
                                )
                                Text(
                                    text = dayNumFmt.format(Date(dayMs)),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                if (count > 0) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 1.dp)
                                            .background(
                                                if (isSelected) Color.White else MaterialTheme.colorScheme.secondary,
                                                RoundedCornerShape(5.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 0.5.dp)
                                    ) {
                                        Text(
                                            text = "$count",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.height(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Asistente de Ruta Automática del Día
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(tr(lang, "Smart Daily Route Assistant", "Asistente de Ruta Automática del Día"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = useCurrentGps,
                            onClick = { useCurrentGps = true },
                            label = { Text(tr(lang, "From My GPS", "Desde mi GPS")) },
                            leadingIcon = { Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = !useCurrentGps,
                            onClick = { useCurrentGps = false },
                            label = { Text(tr(lang, "Starting Address", "Dirección salida")) },
                            leadingIcon = { Icon(Icons.Filled.HomeWork, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    if (!useCurrentGps) {
                        OutlinedTextField(
                            value = customStartAddress,
                            onValueChange = { customStartAddress = it },
                            label = { Text(tr(lang, "Starting Point (Home / Shop)", "Punto de partida (Casa / Taller)")) },
                            placeholder = { Text("1234 W 49th St, Hialeah, FL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    Button(
                        onClick = {
                            if (dayAppointments.none { it.clientAddress.isNotBlank() }) {
                                Toast.makeText(context, tr(lang, "Add at least one appointment with an address", "Agrega al menos una cita con dirección"), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!useCurrentGps && customStartAddress.isBlank()) {
                                Toast.makeText(context, tr(lang, "Enter starting address", "Escribe la dirección de salida"), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isOptimizingRoute = true
                            scope.launch {
                                val optimized = optimizeAppointmentsRoute(
                                    context = context,
                                    dayAppointments = dayAppointments,
                                    useCurrentGps = useCurrentGps,
                                    customOriginAddress = customStartAddress
                                )
                                optimized.forEachIndexed { idx, appt ->
                                    onUpdateAppointment(appt.copy(routeOrder = idx + 1))
                                }
                                isOptimizingRoute = false
                                Toast.makeText(context, tr(lang, "Route optimized (${optimized.size} stops)", "Ruta optimizada (${optimized.size} paradas)"), Toast.LENGTH_SHORT).show()
                                launchMultiStopGoogleMapsRoute(
                                    context = context,
                                    orderedStops = optimized,
                                    useCurrentGps = useCurrentGps,
                                    customOriginAddress = customStartAddress
                                )
                            }
                        },
                        enabled = !isOptimizingRoute && dayAppointments.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Directions, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        val stopsCount = dayAppointments.count { it.clientAddress.isNotBlank() }
                        Text(
                            if (isOptimizingRoute) tr(lang, "Calculating optimal route...", "Calculando ruta óptima...")
                            else tr(lang, "Build Auto Route ($stopsCount stops)", "Crear Ruta Automática ($stopsCount paradas)")
                        )
                    }
                }
            }
        }
    }

    val dayAppointmentsList: @Composable () -> Unit = {
        if (dayAppointments.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Text(tr(lang, "No appointments scheduled for this day", "Sin citas programadas este día"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        tr(lang, "Copy any message in Google Messages and open Schedule to auto-fill a new appointment.", "Copia cualquier mensaje en Google Mensajes y abre Agenda para crear la cita automáticamente."),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dayAppointments, key = { it.id }) { appt ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (appt.routeOrder > 0) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = tr(lang, "Stop #${appt.routeOrder}", "Parada #${appt.routeOrder}"),
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${timeFmt.format(Date(appt.scheduledAt))} (${appt.durationMinutes} min)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = { editingAppointment = appt },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = tr(lang, "Edit appointment", "Editar cita"), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                                    }
                                    IconButton(
                                        onClick = { onDeleteAppointment(appt) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Text(appt.clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(appt.workSummary, style = MaterialTheme.typography.bodyMedium)

                            if (appt.clientAddress.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { openInGoogleMaps(context, appt.clientAddress) }
                                ) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text(appt.clientAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            if (appt.notes.isNotBlank()) {
                                Text("${tr(lang, "Note", "Nota")}: ${appt.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val statuses = listOf(
                                    "Programada" to tr(lang, "Scheduled", "Programada"),
                                    "En camino" to tr(lang, "En Route", "En camino"),
                                    "Completada" to tr(lang, "Completed", "Completada")
                                )
                                statuses.forEach { (dbVal, label) ->
                                    FilterChip(
                                        selected = appt.status == dbVal,
                                        onClick = {
                                            onUpdateAppointment(appt.copy(status = dbVal))
                                            // Al seleccionar "En camino", calcula ETA y envía SMS automático al contacto
                                            if (dbVal == "En camino") {
                                                scope.launch {
                                                    val etaMins = estimateTravelMinutesToAddress(
                                                        context = context,
                                                        destinationAddress = appt.clientAddress,
                                                        fallbackOriginAddress = settings.defaultStartAddress
                                                    )
                                                    sendEnRouteSmsToClient(
                                                        context = context,
                                                        lang = lang,
                                                        appt = appt,
                                                        brandName = settings.brandName,
                                                        etaMinutes = etaMins
                                                    )
                                                }
                                            }
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { callPhoneNumber(context, appt.clientPhone) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(tr(lang, "Call", "Llamar"), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { openInGoogleMaps(context, appt.clientAddress) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(tr(lang, "Map", "Mapa"), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                                }
                                FilledTonalButton(
                                    onClick = { addAppointmentToSystemCalendar(context, appt, settings.brandName) },
                                    modifier = Modifier.weight(1.2f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(tr(lang, "Calendar", "Calendario"), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isTablet) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { agendaHeaderAndRoutePanel() }
            VerticalDivider()
            Box(Modifier.weight(1.2f)) { dayAppointmentsList() }
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            agendaHeaderAndRoutePanel()
            Box(Modifier.weight(1f)) { dayAppointmentsList() }
        }
    }
}

@Composable
fun AppointmentEditorDialog(
    lang: String,
    initialDateMillis: Long,
    initialRawMessage: String = "",
    existingAppointment: AppointmentEntity? = null,
    clients: List<ClientEntity>,
    quotes: List<QuoteEntity>,
    onDismiss: () -> Unit,
    onSave: (AppointmentEntity, Boolean) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val initialParsed = remember(initialRawMessage, clients, existingAppointment) {
        if (existingAppointment == null && initialRawMessage.isNotBlank()) {
            parseClientMessageToAppointment(initialRawMessage, initialDateMillis, clients)
        } else null
    }

    val startCal = remember(initialDateMillis, initialParsed, existingAppointment) {
        Calendar.getInstance().apply {
            timeInMillis = existingAppointment?.scheduledAt
                ?: initialParsed?.scheduledAtMillis
                ?: initialDateMillis
            if (existingAppointment == null && initialParsed == null && get(Calendar.HOUR_OF_DAY) < 7) {
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
            }
        }
    }

    var rawMessageInput by remember { mutableStateOf(initialRawMessage) }
    var year by remember { mutableStateOf(startCal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(startCal.get(Calendar.MONTH)) }
    var day by remember { mutableStateOf(startCal.get(Calendar.DAY_OF_MONTH)) }
    var hour by remember { mutableStateOf(startCal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(startCal.get(Calendar.MINUTE)) }

    var clientName by remember { mutableStateOf(existingAppointment?.clientName ?: initialParsed?.name.orEmpty()) }
    var clientPhone by remember {
        mutableStateOf(
            (existingAppointment?.clientPhone?.filter { it.isDigit() }?.take(10))
                ?: initialParsed?.phoneDigits.orEmpty()
        )
    }
    var clientAddress by remember { mutableStateOf(existingAppointment?.clientAddress ?: initialParsed?.address.orEmpty()) }
    var workSummary by remember { mutableStateOf(existingAppointment?.workSummary ?: initialParsed?.workSummary.orEmpty()) }
    var notes by remember { mutableStateOf(existingAppointment?.notes ?: initialParsed?.notes.orEmpty()) }
    var durationStr by remember { mutableStateOf((existingAppointment?.durationMinutes ?: 60).toString()) }
    var alsoSaveClient by remember { mutableStateOf(existingAppointment == null) }

    var clientMenuExpanded by remember { mutableStateOf(false) }
    var quoteMenuExpanded by remember { mutableStateOf(false) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { contactUri ->
        if (contactUri != null) {
            try {
                context.contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                        val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                        val contactId = if (idIdx >= 0) cursor.getString(idIdx) else ""
                        val displayName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                        val hasPhone = if (hasPhoneIdx >= 0) cursor.getInt(hasPhoneIdx) > 0 else false

                        if (displayName.isNotBlank()) clientName = displayName

                        if (hasPhone && contactId.isNotBlank()) {
                            context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(contactId),
                                null
                            )?.use { pCursor ->
                                if (pCursor.moveToFirst()) {
                                    val numIdx = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    if (numIdx >= 0) {
                                        val rawNum = pCursor.getString(numIdx).orEmpty()
                                        val digits = rawNum.filter { it.isDigit() }
                                        clientPhone = if (digits.length == 11 && digits.startsWith("1")) {
                                            digits.drop(1)
                                        } else {
                                            digits.take(10)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            contactPickerLauncher.launch(null)
        } else {
            Toast.makeText(context, tr(lang, "Enable Contacts permission to import sender", "Activa el permiso de Contactos para importar remitentes"), Toast.LENGTH_LONG).show()
        }
    }

    fun applyParsedText(textToParse: String) {
        if (textToParse.isBlank()) {
            Toast.makeText(context, tr(lang, "Paste client message first", "Pega primero el mensaje del cliente"), Toast.LENGTH_SHORT).show()
            return
        }
        val parsed = parseClientMessageToAppointment(textToParse, initialDateMillis, clients)
        if (parsed.name.isNotBlank()) clientName = parsed.name
        if (parsed.phoneDigits.isNotBlank()) clientPhone = parsed.phoneDigits
        if (parsed.address.isNotBlank()) clientAddress = parsed.address
        if (parsed.workSummary.isNotBlank()) workSummary = parsed.workSummary
        if (parsed.notes.isNotBlank() && notes.isBlank()) notes = parsed.notes

        val c = Calendar.getInstance().apply { timeInMillis = parsed.scheduledAtMillis }
        year = c.get(Calendar.YEAR)
        month = c.get(Calendar.MONTH)
        day = c.get(Calendar.DAY_OF_MONTH)
        hour = c.get(Calendar.HOUR_OF_DAY)
        minute = c.get(Calendar.MINUTE)
        Toast.makeText(context, tr(lang, "Data extracted automatically", "Datos extraídos automáticamente"), Toast.LENGTH_SHORT).show()
    }

    val dateLabel = remember(year, month, day) {
        val c = Calendar.getInstance().apply { set(year, month, day) }
        SimpleDateFormat("MM/dd/yyyy", Locale.US).format(c.time)
    }
    val timeLabel = remember(hour, minute) {
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }
        SimpleDateFormat("hh:mm a", Locale.US).format(c.time)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existingAppointment != null) tr(lang, "Edit Appointment", "Editar Cita")
                else tr(lang, "Schedule Appointment", "Programar Cita")
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (existingAppointment == null) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(tr(lang, "Smart Message Extractor", "Asistente de Extracción desde Mensaje"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = rawMessageInput,
                                onValueChange = { rawMessageInput = it },
                                label = { Text(tr(lang, "Message text (SMS / WhatsApp)", "Texto del mensaje (Google Mensajes / WhatsApp)")) },
                                placeholder = { Text("1200 W 49th St Hialeah FL 33012") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val clip = clipboard.getText()?.text.orEmpty()
                                        if (clip.isNotBlank()) {
                                            rawMessageInput = clip
                                            applyParsedText(clip)
                                        } else {
                                            Toast.makeText(context, tr(lang, "Clipboard is empty", "El portapapeles está vacío"), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(tr(lang, "Paste & Extract", "Pegar y Extraer"), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                                }
                                FilledTonalButton(
                                    onClick = { applyParsedText(rawMessageInput) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(tr(lang, "Extract Data", "Extraer Datos"), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        val hasContactsPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasContactsPerm) {
                            contactPickerLauncher.launch(null)
                        } else {
                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.ContactPhone, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr(lang, "Pick from Phone Contacts", "Seleccionar de Contactos del Teléfono"))
                }

                if (clients.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { clientMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(tr(lang, "Link Saved Client (${clients.size})", "Vincular cliente guardado (${clients.size})"))
                        }
                        DropdownMenu(expanded = clientMenuExpanded, onDismissRequest = { clientMenuExpanded = false }) {
                            clients.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.name} · ${c.address}") },
                                    onClick = {
                                        clientName = c.name
                                        clientPhone = c.phone.filter { it.isDigit() }.take(10)
                                        if (clientAddress.isBlank()) clientAddress = c.address
                                        clientMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (quotes.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { quoteMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(tr(lang, "Link from Existing Estimate", "Vincular desde cotización existente"))
                        }
                        DropdownMenu(expanded = quoteMenuExpanded, onDismissRequest = { quoteMenuExpanded = false }) {
                            quotes.take(15).forEach { q ->
                                DropdownMenuItem(
                                    text = { Text("${q.clientName} — ${q.serviceName.take(35)}") },
                                    onClick = {
                                        clientName = q.clientName
                                        clientPhone = q.clientPhone.filter { it.isDigit() }.take(10)
                                        if (clientAddress.isBlank()) clientAddress = q.clientAddress
                                        workSummary = q.serviceName
                                        quoteMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(context, { _, y, m, d ->
                                year = y; month = m; day = d
                            }, year, month, day).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(dateLabel)
                    }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(context, { _, h, min ->
                                hour = h; minute = min
                            }, hour, minute, false).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(timeLabel)
                    }
                }

                OutlinedTextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    label = { Text(tr(lang, "Customer Name", "Nombre del cliente")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = clientPhone,
                    onValueChange = { clientPhone = it.filter { c -> c.isDigit() }.take(10) },
                    label = { Text(tr(lang, "Phone (XXX) XXX-XXXX", "Teléfono (XXX) XXX-XXXX")) },
                    visualTransformation = UsPhoneVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
                OutlinedTextField(
                    value = clientAddress,
                    onValueChange = { clientAddress = it },
                    label = { Text(tr(lang, "Job Address (for GPS route)", "Dirección exacta (para ruta GPS)")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = workSummary,
                    onValueChange = { workSummary = it },
                    label = { Text(tr(lang, "Scope of Work", "Trabajo a realizar")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = durationStr,
                        onValueChange = { durationStr = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text(tr(lang, "Duration (min)", "Duración (min)")) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(tr(lang, "Notes / Gate Code", "Notas / Gate code")) },
                        modifier = Modifier.weight(1.4f),
                        singleLine = true
                    )
                }

                if (existingAppointment == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alsoSaveClient, onCheckedChange = { alsoSaveClient = it })
                        Text(tr(lang, "Also save to Clients directory", "Guardar también en directorio de Clientes"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val scheduledCal = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val apptEntity = (existingAppointment ?: AppointmentEntity(
                        clientName = "",
                        workSummary = "",
                        scheduledAt = scheduledCal.timeInMillis
                    )).copy(
                        clientName = clientName.trim(),
                        clientPhone = formatUsPhoneInput(clientPhone),
                        clientAddress = clientAddress.trim(),
                        workSummary = workSummary.trim().ifBlank { "Electrical service visit" },
                        notes = notes.trim(),
                        scheduledAt = scheduledCal.timeInMillis,
                        durationMinutes = durationStr.toIntOrNull()?.coerceAtLeast(15) ?: 60
                    )
                    onSave(apptEntity, alsoSaveClient)
                },
                enabled = clientName.isNotBlank()
            ) {
                Text(
                    if (existingAppointment != null) tr(lang, "Save Changes", "Guardar Cambios")
                    else tr(lang, "Save Appointment", "Guardar Cita")
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(lang, "Cancel", "Cancelar")) }
        }
    )
}

@Composable
fun BusinessOverviewScreen(lang: String, quotes: List<QuoteEntity>, isTablet: Boolean) {
    val money = NumberFormat.getCurrencyInstance(Locale.US)
    val totalInvoiced = quotes.filter { it.status == "Facturada" }.sumOf { it.total }
    val totalApproved = quotes.filter { it.status == "Aprobada" }.sumOf { it.total }
    val totalPending = quotes.filter { it.status == "Pendiente" }.sumOf { it.total }
    val avgTicket = if (quotes.isNotEmpty()) quotes.sumOf { it.total } / quotes.size else 0.0

    val monthlyData = remember(quotes) {
        val fmt = SimpleDateFormat("MMM", Locale.US)
        val result = mutableListOf<Triple<String, Double, Double>>()
        for (i in 5 downTo 0) {
            val c = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -i)
            }
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val label = fmt.format(c.time)

            val inMonth = quotes.filter { q ->
                val qc = Calendar.getInstance().apply { timeInMillis = q.createdAt }
                qc.get(Calendar.YEAR) == year && qc.get(Calendar.MONTH) == month
            }
            val quotedSum = inMonth.sumOf { it.total }
            val paidSum = inMonth.filter { it.status == "Facturada" }.sumOf { it.total }
            result.add(Triple(label, quotedSum, paidSum))
        }
        result
    }

    val categoryStats = remember(quotes) {
        val categories = listOf(
            "Receptáculos e interruptores" to tr(lang, "Outlets & Switches", "Receptáculos e interruptores"),
            "Iluminación y ventilación" to tr(lang, "Lighting & Fans", "Iluminación y ventilación"),
            "Circuitos nuevos" to tr(lang, "New Circuits", "Circuitos nuevos"),
            "Paneles, protección y diagnóstico" to tr(lang, "Panels, Protection & Diagnostics", "Paneles, protección y diagnóstico")
        )
        categories.map { (catKey, displayCat) ->
            val servicesInCat = ServiceCatalog.all.filter { it.category == catKey }
            val matchedTotal = quotes.filter { q ->
                servicesInCat.any { s -> q.serviceName.contains(s.nameEs, ignoreCase = true) || q.serviceName.contains(s.nameEn, ignoreCase = true) }
            }.sumOf { it.total }
            displayCat to matchedTotal
        }
    }

    val kpiGrid: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(tr(lang, "Invoiced", "Facturado"), style = MaterialTheme.typography.labelMedium)
                        Text(money.format(totalInvoiced), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Card(
                    Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(tr(lang, "Approved", "Aprobado"), style = MaterialTheme.typography.labelMedium)
                        Text(money.format(totalApproved), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(tr(lang, "Pending", "Pendiente"), style = MaterialTheme.typography.labelMedium)
                        Text(money.format(totalPending), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(tr(lang, "Average Ticket", "Ticket Promedio"), style = MaterialTheme.typography.labelMedium)
                        Text(money.format(avgTicket), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    val chartsSection: @Composable () -> Unit = {
        val barColorQuoted = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        val barColorPaid = MaterialTheme.colorScheme.primary
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(lang, "Monthly Trend (Last 6 Months)", "Evolución Mensual (Últimos 6 meses)"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(10.dp).background(barColorQuoted, RoundedCornerShape(2.dp)))
                            Text(tr(lang, "Quoted", "Cotizado"), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(10.dp).background(barColorPaid, RoundedCornerShape(2.dp)))
                            Text(tr(lang, "Invoiced / Paid", "Facturado / Pagado"), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    val maxVal = (monthlyData.maxOfOrNull { maxOf(it.second, it.third) } ?: 1.0).coerceAtLeast(1.0)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(top = 8.dp)
                    ) {
                        val groupWidth = size.width / monthlyData.size
                        val barWidth = (groupWidth * 0.28f).coerceAtMost(28f)
                        val chartH = size.height - 12f

                        monthlyData.forEachIndexed { idx, (_, quoted, paid) ->
                            val centerX = idx * groupWidth + groupWidth / 2f
                            val qHeight = ((quoted / maxVal) * chartH).toFloat()
                            val pHeight = ((paid / maxVal) * chartH).toFloat()

                            drawRoundRect(
                                color = barColorQuoted,
                                topLeft = Offset(centerX - barWidth - 2f, chartH - qHeight),
                                size = Size(barWidth, qHeight.coerceAtLeast(4f)),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                            drawRoundRect(
                                color = barColorPaid,
                                topLeft = Offset(centerX + 2f, chartH - pHeight),
                                size = Size(barWidth, pHeight.coerceAtLeast(4f)),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        monthlyData.forEach { (month, _, _) ->
                            Text(month, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr(lang, "Revenue by Electrical Specialty", "Ingresos por Especialidad Eléctrica"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val maxCat = (categoryStats.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
                    categoryStats.forEach { (catName, amount) ->
                        val ratio = (amount / maxCat).toFloat().coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(catName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text(money.format(amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr(lang, "Business Overview", "Resumen Financiero del Negocio"), style = MaterialTheme.typography.titleLarge)
        if (isTablet) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) { kpiGrid() }
                Box(Modifier.weight(1.3f)) { chartsSection() }
            }
        } else {
            kpiGrid()
            chartsSection()
        }
    }
}

@Composable
fun ClientsScreen(
    lang: String,
    clients: List<ClientEntity>,
    isTablet: Boolean,
    onSave: (String, String, String, String) -> Unit,
    onUpdate: (ClientEntity) -> Unit,
    onDelete: (ClientEntity) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var editingClient by remember { mutableStateOf<ClientEntity?>(null) }

    if (editingClient != null) {
        EditClientDialog(
            lang = lang,
            client = editingClient!!,
            onDismiss = { editingClient = null },
            onConfirm = { updated ->
                onUpdate(updated)
                editingClient = null
            }
        )
    }

    val formContent: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(lang, "Add New Client", "Registrar cliente"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(tr(lang, "Client Name", "Nombre del cliente")) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true
            )
            OutlinedTextField(
                value = phone,
