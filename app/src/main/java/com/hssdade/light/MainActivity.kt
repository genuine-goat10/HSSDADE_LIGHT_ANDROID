package com.hssdade.light

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hssdade.light.data.*
import com.hssdade.light.pdf.QuotePdf
import kotlinx.coroutines.Dispatchers
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.get(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF145A72),
                    secondary = Color(0xFF2D8A78)
                )
            ) {
                ApexBidApp(db)
            }
        }
    }
}

fun formatUsPhoneInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(10)
    return when {
        digits.isEmpty() -> ""
        digits.length <= 3 -> "(${digits}"
        digits.length <= 6 -> "(${digits.substring(0, 3)})${digits.substring(3)}"
        else -> "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
    }
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
fun ApexBidApp(db: AppDatabase) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTabletLandscape = configuration.screenWidthDp >= 600

    var tab by remember { mutableStateOf(0) }
    var settings by remember { mutableStateOf(EstimateSettings.load(context)) }
    val clients by db.clientDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val quotes by db.quoteDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    val navItems = listOf(
        Triple("Inicio", Icons.Filled.Home, 0),
        Triple("Business", Icons.Filled.BarChart, 1),
        Triple("Clientes", Icons.Filled.People, 2),
        Triple("Cotizar", Icons.Filled.Calculate, 3),
        Triple("Historial", Icons.Filled.History, 4),
        Triple("Ajustes", Icons.Filled.Settings, 5)
    )

    val mainContent: @Composable (Modifier) -> Unit = { contentModifier ->
        Column(
            contentModifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        text = "Electrical Estimates & Invoices · Servicios Eléctricos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when (tab) {
                0 -> HomeScreen(clients.size, quotes, isTabletLandscape) { tab = 3 }
                1 -> BusinessOverviewScreen(quotes, isTabletLandscape)
                2 -> ClientsScreen(
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
                        }
                    },
                    onDelete = { client ->
                        scope.launch { db.clientDao().delete(client) }
                    }
                )
                3 -> QuoteScreen(clients, settings, isTabletLandscape) { q ->
                    scope.launch {
                        db.quoteDao().insert(q)
                        Toast.makeText(context, "Cotización guardada", Toast.LENGTH_SHORT).show()
                        tab = 4
                    }
                }
                4 -> HistoryScreen(
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
                            Toast.makeText(context, "PDF guardado: ${generated.fileName}", Toast.LENGTH_SHORT).show()
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
                5 -> SettingsScreen(
                    settings = settings,
                    onSave = { updated ->
                        settings = updated
                        updated.save(context)
                        Toast.makeText(context, "Configuración guardada", Toast.LENGTH_SHORT).show()
                    },
                    onExportBackup = { uri ->
                        scope.launch {
                            try {
                                exportDatabaseJson(context, db, uri)
                                Toast.makeText(context, "Respaldo exportado correctamente", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al exportar respaldo: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onImportBackup = { uri ->
                        scope.launch {
                            try {
                                val count = importDatabaseJson(context, db, uri)
                                Toast.makeText(context, "Respaldo importado ($count registros)", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al importar respaldo: ${e.message}", Toast.LENGTH_LONG).show()
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
        Toast.makeText(context, "Escribe el material o pieza en español o inglés", Toast.LENGTH_SHORT).show()
        return
    }
    val searchTerm = "site:homedepot.com $clean"
    val url = "https://www.google.com/search?q=${Uri.encode(searchTerm)}"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo abrir el navegador", Toast.LENGTH_SHORT).show()
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
                "Identificar pieza con Google Lens"
            )
            context.startActivity(chooser)
        }
    }
}

private fun buildSmartDescriptions(
    selectedServices: List<Pair<String, String>>,
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
        val esList = selectedServices.
