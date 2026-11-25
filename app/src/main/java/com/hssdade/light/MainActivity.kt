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
        digits.length <= 6 -> "(${digits.substring(0, 3)}) ${digits.substring(3)}"
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
    selectedServices: List<ElectricalService>,
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
        val esList = selectedServices.joinToString("\n") { "• ${it.nameEs}." }
        val enList = selectedServices.joinToString("\n") { "• ${it.nameEn}." }
        val noteEs = if (notes.isNotBlank()) "\nNota técnica: ${notes.trim()}." else ""
        val noteEn = if (notes.isNotBlank()) "\nTechnical note: ${notes.trim()}." else ""

        val fullEs = "Suministro e instalación profesional de:\n$esList\nIncluye materiales certificados UL, pruebas de voltaje, continuidad y verificación conforme al código eléctrico vigente (NEC).$noteEs"
        val fullEn = "Professional supply and installation of:\n$enList\nIncludes UL-listed materials, voltage and continuity testing, and code-compliant verification per NEC standards.$noteEn"
        return fullEs to fullEn
    }

    if (currentEs.isNotBlank() && currentEn.isBlank()) {
        var translated = currentEs
        dictionary.forEach { (en, es) ->
            translated = translated.replace(es, en, ignoreCase = true)
        }
        val polishedEs = "Trabajo eléctrico profesional: ${currentEs.trim()}. Ejecutado conforme a normas NEC con pruebas de seguridad."
        val polishedEn = "Professional electrical work: $translated. Performed in compliance with NEC safety standards and testing."
        return polishedEs to polishedEn
    }

    if (currentEn.isNotBlank() && currentEs.isBlank()) {
        var translated = currentEn
        dictionary.forEach { (en, es) ->
            translated = translated.replace(en, es, ignoreCase = true)
        }
        val polishedEn = "Professional electrical scope: ${currentEn.trim()}. Completed per NEC code with full safety verification."
        val polishedEs = "Alcance eléctrico profesional: $translated. Realizado bajo normativa NEC con verificación completa de seguridad."
        return polishedEs to polishedEn
    }

    val defaultEs = "Diagnóstico, suministro de materiales certificados y ejecución de trabajos eléctricos residenciales conforme al código NEC, incluyendo pruebas finales de funcionamiento."
    val defaultEn = "Troubleshooting, supply of UL-listed materials, and execution of residential electrical work per NEC standards, including final operational testing."
    return defaultEs to defaultEn
}

private fun openInGoogleMaps(context: Context, address: String) {
    val clean = address.trim()
    if (clean.isEmpty()) {
        Toast.makeText(context, "Escribe una dirección primero", Toast.LENGTH_SHORT).show()
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

private fun sharePdfGeneral(context: Context, uri: Uri, quote: QuoteEntity, brandName: String) {
    val brand = brandName.ifBlank { "ApexBid" }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Cotización / Estimate - $brand (${quote.clientName})")
        if (quote.clientEmail.isNotBlank()) {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(quote.clientEmail))
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir PDF / Share PDF"))
}

private fun sendQuoteByEmail(context: Context, quote: QuoteEntity, pdfUri: Uri?, brandName: String) {
    val brand = brandName.ifBlank { "ApexBid" }
    val money = NumberFormat.getCurrencyInstance(Locale.US).format(quote.total)
    val docType = if (quote.status == "Facturada") "Factura / Invoice" else "Cotización / Estimate"
    val body = """
        Estimado/a ${quote.clientName} / Dear ${quote.clientName},
        
        Adjuntamos su $docType eléctrica de $brand por un total de $money.
        Please find attached your electrical document from $brand for a total of $money.
        
        Servicios / Services:
        ${quote.serviceName}
        
        Gracias por su confianza / Thank you for your business.
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        if (quote.clientEmail.isNotBlank()) {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(quote.clientEmail))
        }
        putExtra(Intent.EXTRA_SUBJECT, "$docType Eléctrica - $brand")
        putExtra(Intent.EXTRA_TEXT, body)
        if (pdfUri != null) {
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(intent, "Enviar por correo / Send email"))
}

private fun sendQuoteBySms(context: Context, quote: QuoteEntity, brandName: String) {
    val brand = brandName.ifBlank { "ApexBid" }
    val money = NumberFormat.getCurrencyInstance(Locale.US).format(quote.total)
    val message = "Hola ${quote.clientName}, le saludamos de $brand. Su cotización eléctrica (${quote.serviceName.take(55)}) es por $money. Estado: ${quote.status}."
    val smsUri = Uri.parse("smsto:${Uri.encode(quote.clientPhone)}")
    val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
        putExtra("sms_body", message)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No se encontró aplicación de mensajes SMS", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun exportDatabaseJson(context: Context, db: AppDatabase, uri: Uri) = withContext(Dispatchers.IO) {
    val clients = db.clientDao().getAllOnce()
    val quotes = db.quoteDao().getAllOnce()

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

    context.contentResolver.openOutputStream(uri)?.use { out ->
        out.write(root.toString(2).toByteArray(Charsets.UTF_8))
    } ?: throw IllegalStateException("No se pudo abrir el archivo de destino")
}

private suspend fun importDatabaseJson(context: Context, db: AppDatabase, uri: Uri): Int = withContext(Dispatchers.IO) {
    val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: throw IllegalStateException("No se pudo leer el archivo")
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
    imported
}

@Composable
fun HomeScreen(clientCount: Int, quotes: List<QuoteEntity>, isTablet: Boolean, onNew: () -> Unit) {
    val money = NumberFormat.getCurrencyInstance(Locale.US)
    val pending = quotes.count { it.status == "Pendiente" }
    val approved = quotes.count { it.status == "Aprobada" }
    val invoiced = quotes.count { it.status == "Facturada" }
    val invoicedTotal = quotes.filter { it.status == "Facturada" }.sumOf { it.total }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Panel principal", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Clientes", clientCount.toString(), Modifier.weight(1f))
            StatCard("Cotizaciones", quotes.size.toString(), Modifier.weight(1f))
            if (isTablet) {
                StatCard("Total Facturado", money.format(invoicedTotal), Modifier.weight(1.3f))
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
                    Text("Pendientes", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$approved", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2D8A78))
                    Text("Aprobadas", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$invoiced", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF145A72))
                    Text("Facturadas", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Crear cotización / New estimate")
        }
        Text("Modo local activo. Datos almacenados de forma segura en este dispositivo.", style = MaterialTheme.typography.bodySmall)
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

@Composable
fun BusinessOverviewScreen(quotes: List<QuoteEntity>, isTablet: Boolean) {
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
            "Receptáculos e interruptores",
            "Iluminación y ventilación",
            "Circuitos nuevos",
            "Paneles, protección y diagnóstico"
        )
        categories.map { cat ->
            val servicesInCat = ServiceCatalog.all.filter { it.category == cat }
            val matchedTotal = quotes.filter { q ->
                servicesInCat.any { s -> q.serviceName.contains(s.nameEs, ignoreCase = true) }
            }.sumOf { it.total }
            cat to matchedTotal
        }
    }

    val kpiGrid: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF145A72).copy(alpha = 0.12f))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Facturado / Invoiced", style = MaterialTheme.typography.labelMedium)
                        Text(money.format(totalInvoiced), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF145A72))
                    }
                }
                Card(
                    Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D8A78).copy(alpha = 0.12f))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Aprobado / Approved", style = MaterialTheme.typography.labelMedium)
                        Text(money.format(totalApproved), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2D8A78))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Pendiente / Pending", style = MaterialTheme.typography.labelMedium)
                        Text(money.format(totalPending), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Ticket Promedio", style = MaterialTheme.typography.labelMedium)
                        Text(money.format(avgTicket), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    val chartsSection: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Evolución Mensual (Últimos 6 meses)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(10.dp).background(Color(0xFF90B8C7), RoundedCornerShape(2.dp)))
                            Text("Cotizado", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(10.dp).background(Color(0xFF145A72), RoundedCornerShape(2.dp)))
                            Text("Facturado / Pagado", style = MaterialTheme.typography.bodySmall)
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
                                color = Color(0xFF90B8C7),
                                topLeft = Offset(centerX - barWidth - 2f, chartH - qHeight),
                                size = Size(barWidth, qHeight.coerceAtLeast(4f)),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                            drawRoundRect(
                                color = Color(0xFF145A72),
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
                    Text("Ingresos por Especialidad Eléctrica", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                                color = Color(0xFF2D8A78),
                                trackColor = Color(0xFFE0ECEF)
                            )
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Business Overview · Resumen Financiero", style = MaterialTheme.typography.titleLarge)
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
    clients: List<ClientEntity>,
    isTablet: Boolean,
    onSave: (String, String, String, String) -> Unit,
    onDelete: (ClientEntity) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    val formContent: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Registrar cliente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del cliente") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = formatUsPhoneInput(it) },
                label = { Text("Teléfono (XXX) XXX-XXXX") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email (para envío PDF)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección exacta del trabajo") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    singleLine = true
                )
                FilledTonalIconButton(
                    onClick = { openInGoogleMaps(context, address) },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = "Verificar en Google Maps")
                }
            }
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), phone.trim(), email.trim(), address.trim())
                        name = ""; phone = ""; email = ""; address = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar cliente") }
        }
    }

    val listContent: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            Text("Clientes guardados (${clients.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyColumn {
                items(clients) { c ->
                    ListItem(
                        headlineContent = { Text(c.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(listOf(c.phone, c.email, c.address).filter { it.isNotBlank() }.joinToString(" · "))
                        },
                        trailingContent = {
                            Row {
                                if (c.address.isNotBlank()) {
                                    IconButton(onClick = { openInGoogleMaps(context, c.address) }) {
                                        Icon(Icons.Filled.Map, contentDescription = "Abrir en Maps", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                IconButton(onClick = { onDelete(c) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (isTablet) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { formContent() }
            VerticalDivider()
            Box(Modifier.weight(1.2f)) { listContent() }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            formContent()
            Spacer(Modifier.height(12.dp))
            listContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteScreen(clients: List<ClientEntity>, settings: EstimateSettings, isTablet: Boolean, onSave: (QuoteEntity) -> Unit) {
    val context = LocalContext.current
    var client by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var clientEmail by remember { mutableStateOf("") }
    var clientAddress by remember { mutableStateOf("") }
    var clientExpanded by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Int>() }
    var showCatalogSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var descEs by remember { mutableStateOf("") }
    var descEn by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var extraMaterials by remember { mutableStateOf("0") }
    var materialSearchQuery by remember { mutableStateOf("") }
    val attachedPhotos = remember { mutableStateListOf<String>() }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingLensFile by remember { mutableStateOf<File?>(null) }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.take(6 - attachedPhotos.size).forEach { uri ->
            saveUriToInternalFile(context, uri, "gal")?.let { attachedPhotos.add(it) }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile
        if (success && file != null && file.exists() && attachedPhotos.size < 6) {
            attachedPhotos.add(file.absolutePath)
        }
    }

    val lensCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingLensFile
        if (success && file != null && file.exists()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            launchVisualIdentifier(context, uri)
        }
    }

    val lensGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            launchVisualIdentifier(context, uri)
        }
    }

    val selected = ServiceCatalog.all.filter { it.id in selectedIds }
    val quantity = qty.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val additionalMaterials = extraMaterials.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    val laborHours = selected.sumOf { it.hours }
    val materials = selected.sumOf { it.materials } + additionalMaterials
    val cost = (laborHours * settings.hourlyOperatingCost + materials) * quantity
    val margin = settings.marginPercent / 100.0
    val rawTotal = if (cost > 0 && margin < 1) cost / (1 - margin) else 0.0
    val total = if (settings.applyMinimumVisit && rawTotal > 0 && rawTotal < settings.minimumVisit) {
        settings.minimumVisit
    } else {
        rawTotal
    }

    val money = NumberFormat.getCurrencyInstance(Locale.US)
    val serviceNamesEs = selected.joinToString("\n") { "• ${it.nameEs}" }
    val serviceNamesEn = selected.joinToString("\n") { "• ${it.nameEn}" }

    if (showCatalogSheet) {
        val filtered = remember(searchQuery) {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) ServiceCatalog.all
            else ServiceCatalog.all.filter {
                it.nameEs.lowercase().contains(q) ||
                it.nameEn.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
            }
        }
        val grouped = remember(filtered) { filtered.groupBy { it.category } }

        ModalBottomSheet(onDismissRequest = { showCatalogSheet = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Catálogo de servicios (${selectedIds.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showCatalogSheet = false }) { Text("Listo") }
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar por nombre o categoría...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Limpiar")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    grouped.forEach { (category, itemsInCat) ->
                        item(key = "cat_$category") {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        items(itemsInCat, key = { it.id }) { service ->
                            val isChecked = service.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) selectedIds.remove(service.id) else selectedIds.add(service.id)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedIds.add(service.id) else selectedIds.remove(service.id)
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(service.nameEs, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${service.nameEn} · ${service.hours}h · Mat: ${money.format(service.materials)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val leftColumn: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Nueva cotización / New estimate", style = MaterialTheme.typography.titleLarge)
            Box {
                OutlinedButton(onClick = { clientExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (client.isBlank()) "Vincular cliente guardado / Link customer" else "Cliente: $client")
                }
                DropdownMenu(expanded = clientExpanded, onDismissRequest = { clientExpanded = false }) {
                    clients.forEach { c ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(c.name, fontWeight = FontWeight.SemiBold)
                                    val sub = listOf(c.phone, c.address).filter { it.isNotBlank() }.joinToString(" · ")
                                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {
                                client = c.name
                                clientPhone = formatUsPhoneInput(c.phone)
                                clientEmail = c.email
                                clientAddress = c.address
                                clientExpanded = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(client, { client = it }, label = { Text("Nombre del cliente / Customer name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = clientPhone,
                    onValueChange = { clientPhone = formatUsPhoneInput(it) },
                    label = { Text("Teléfono (XXX) XXX-XXXX") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
                OutlinedTextField(clientEmail, { clientEmail = it }, label = { Text("Email") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = clientAddress,
                    onValueChange = { clientAddress = it },
                    label = { Text("Dirección del trabajo / Job address") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                FilledTonalIconButton(
                    onClick = { openInGoogleMaps(context, clientAddress) },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = "Verificar dirección en Google Maps")
                }
            }

            OutlinedButton(onClick = { showCatalogSheet = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (selected.isEmpty()) "Buscar y agregar servicios (36)" else "${selected.size} servicio(s) seleccionado(s)")
            }

            if (selected.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Servicios incluidos / Included services", style = MaterialTheme.typography.titleSmall)
                        selected.forEach { service ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(service.nameEs)
                                    Text(service.nameEn, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { selectedIds.remove(service.id) }) { Text("Quitar") }
                            }
                        }
                    }
                }
            }

            // Asistente de precios en Home Depot + Identificador visual tipo Google Lens
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Asistente de Materiales y Piezas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = materialSearchQuery,
                            onValueChange = { materialSearchQuery = it },
                            label = { Text("Buscar precio en Home Depot (ES / EN)") },
                            placeholder = { Text("Ej: breaker 20A GFCI / 12-2 Romex") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        FilledTonalButton(
                            onClick = {
                                val q = materialSearchQuery.ifBlank {
                                    selected.firstOrNull()?.let { "${it.nameEn} ${it.nameEs}" } ?: ""
                                }
                                searchHomeDepotMaterial(context, q)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)
                        ) {
                            Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Precio")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val (uri, file) = createTempCameraUri(context)
                                pendingLensFile = file
                                lensCameraLauncher.launch(uri)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Lens Cámara", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { lensGalleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ImageSearch, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Lens Galería", maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    val rightColumn: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            // Asistente IA de redacción y traducción ES <-> EN
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Descripción técnica bilingüe", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FilledTonalButton(
                    onClick = {
                        val (genEs, genEn) = buildSmartDescriptions(selected, descEs, descEn, notes)
                        descEs = genEs
                        descEn = genEn
                        Toast.makeText(context, "Descripción profesional generada (ES / EN)", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Asistente IA ES/EN")
                }
            }

            OutlinedTextField(
                value = descEs,
                onValueChange = { descEs = it },
                label = { Text("Descripción del trabajo (español)") },
                trailingIcon = {
                    IconButton(onClick = {
                        val (genEs, genEn) = buildSmartDescriptions(selected, descEs, descEn, notes)
                        descEs = genEs
                        if (descEn.isBlank()) descEn = genEn
                    }) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Mejorar redacción en español", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = descEn,
                onValueChange = { descEn = it },
                label = { Text("Work description (English)") },
                trailingIcon = {
                    IconButton(onClick = {
                        val (genEs, genEn) = buildSmartDescriptions(selected, descEs, descEn, notes)
                        descEn = genEn
                        if (descEs.isBlank()) descEs = genEs
                    }) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Improve English description", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones / Job notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            // Fotos adjuntas al estimado y PDF
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Fotos del trabajo (${attachedPhotos.size}/6)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (attachedPhotos.size < 6) {
                                        val (uri, file) = createTempCameraUri(context)
                                        pendingCameraFile = file
                                        cameraLauncher.launch(uri)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cámara")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (attachedPhotos.size < 6) galleryPicker.launch("image/*")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Galería")
                            }
                        }
                    }
                    if (attachedPhotos.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            attachedPhotos.forEach { path ->
                                val bmp = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
                                Box(Modifier.size(72.dp)) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = "Foto adjunta",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                        )
                                    }
                                    IconButton(
                                        onClick = { attachedPhotos.remove(path) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(22.dp)
                                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(11.dp))
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Quitar foto", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(qty, { qty = it }, label = { Text("Cantidad / Qty") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(extraMaterials, { extraMaterials = it }, label = { Text("Material extra $") }, modifier = Modifier.weight(1f), singleLine = true)
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Resumen / Estimate summary", style = MaterialTheme.typography.titleMedium)
                    Text("Horas estimadas / Estimated hours: ${"%.2f".format(Locale.US, laborHours * quantity)}")
                    Text("Materiales base + adicionales: ${money.format(materials * quantity)}")
                    Text("TOTAL / TOTAL", style = MaterialTheme.typography.labelLarge)
                    Text(money.format(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Cálculo interno: no se incluyen costos ni margen en el PDF del cliente.", style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = {
                    if (client.isNotBlank() && selected.isNotEmpty() && quantity > 0) {
                        val names = selected.joinToString(" / ") { "${it.nameEs} | ${it.nameEn}" }
                        onSave(
                            QuoteEntity(
                                clientName = client.trim(),
                                clientPhone = formatUsPhoneInput(clientPhone),
                                clientEmail = clientEmail.trim(),
                                clientAddress = clientAddress.trim(),
                                serviceName = names,
                                descriptionEs = descEs.ifBlank { serviceNamesEs },
                                descriptionEn = descEn.ifBlank { serviceNamesEn },
                                observations = notes.trim(),
                                quantity = quantity,
                                total = total,
                                status = "Pendiente",
                                photoUris = attachedPhotos.joinToString("|")
                            )
                        )
                        client = ""; clientPhone = ""; clientEmail = ""; clientAddress = ""; selectedIds.clear()
                        descEs = ""; descEn = ""; notes = ""; qty = "1"; extraMaterials = "0"; attachedPhotos.clear()
                    }
                },
                enabled = client.isNotBlank() && selected.isNotEmpty() && quantity > 0,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar cotización / Save estimate") }
        }
    }

    if (isTablet) {
        Row(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.weight(1f)) { leftColumn() }
            VerticalDivider()
            Box(Modifier.weight(1f)) { rightColumn() }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            leftColumn()
            rightColumn()
        }
    }
}

@Composable
fun HistoryScreen(
    quotes: List<QuoteEntity>,
    onUpdateQuote: (QuoteEntity) -> Unit,
    onExportPdf: (QuoteEntity, String) -> Unit
) {
    val context = LocalContext.current
    val money = NumberFormat.getCurrencyInstance(Locale.US)
    val dateFmt = remember { SimpleDateFormat("MM/dd/yyyy", Locale.US) }
    var signingQuote by remember { mutableStateOf<QuoteEntity?>(null) }

    if (signingQuote != null) {
        SignatureDialog(
            quote = signingQuote!!,
            onDismiss = { signingQuote = null },
            onSaveSignature = { serialized ->
                val updated = signingQuote!!.copy(
                    signatureData = serialized,
                    signedAt = System.currentTimeMillis(),
                    status = if (signingQuote!!.status == "Pendiente") "Aprobada" else signingQuote!!.status
                )
                onUpdateQuote(updated)
                signingQuote = null
            }
        )
    }

    Column {
        Text("Historial de cotizaciones y facturas", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (quotes.isEmpty()) {
            Text("Todavía no hay registros guardados.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(quotes, key = { it.id }) { q ->
                    val photoCount = q.photoUris.split("|").count { it.isNotBlank() }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(q.clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    val contact = listOf(q.clientPhone, q.clientEmail).filter { it.isNotBlank() }.joinToString(" · ")
                                    if (contact.isNotBlank()) {
                                        Text(contact, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (q.clientAddress.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { openInGoogleMaps(context, q.clientAddress) }
                                        ) {
                                            Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(3.dp))
                                            Text(q.clientAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(dateFmt.format(Date(q.createdAt)), style = MaterialTheme.typography.bodySmall)
                                    if (photoCount > 0) {
                                        Text("📷 $photoCount foto(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Pendiente", "Aprobada", "Facturada").forEach { st ->
                                    FilterChip(
                                        selected = q.status == st,
                                        onClick = { onUpdateQuote(q.copy(status = st)) },
                                        label = { Text(st) }
                                    )
                                }
                            }

                            Text(q.serviceName, style = MaterialTheme.typography.bodyMedium)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(money.format(q.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                OutlinedButton(onClick = { signingQuote = q }) {
                                    Icon(Icons.Filled.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (q.signatureData.isNotBlank()) "Firmada ✓" else "Firmar")
                                }
                            }

                            if (q.observations.isNotBlank()) {
                                Text(q.observations, style = MaterialTheme.typography.bodySmall)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { onExportPdf(q, "sms") },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("SMS", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { onExportPdf(q, "email") },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Correo", maxLines = 1)
                                }
                                Button(
                                    onClick = { onExportPdf(q, "share") },
                                    modifier = Modifier.weight(1.2f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("PDF", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SignatureDialog(
    quote: QuoteEntity,
    onDismiss: () -> Unit,
    onSaveSignature: (String) -> Unit
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Firma del cliente / Customer Signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cliente: ${quote.clientName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Firme con el dedo dentro del recuadro:", style = MaterialTheme.typography.bodySmall)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.5.dp, Color(0xFF145A72)), RoundedCornerShape(8.dp))
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> currentStroke = listOf(offset) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.size > 1) strokes.add(currentStroke)
                                    currentStroke = emptyList()
                                }
                            )
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val allStrokes = strokes + listOf(currentStroke)
                        allStrokes.forEach { pts ->
                            if (pts.size >= 2) {
                                val path = Path().apply {
                                    moveTo(pts[0].x, pts[0].y)
                                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                                }
                                drawPath(
                                    path = path,
                                    color = Color(0xFF143C4E),
                                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { strokes.clear(); currentStroke = emptyList() }) {
                    Text("Limpiar firma / Clear")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = canvasSize.width.coerceAtLeast(1).toFloat()
                    val h = canvasSize.height.coerceAtLeast(1).toFloat()
                    val serialized = strokes.joinToString(";") { stroke ->
                        stroke.joinToString("|") { pt ->
                            val nx = (pt.x / w).coerceIn(0f, 1f)
                            val ny = (pt.y / h).coerceIn(0f, 1f)
                            "${"%.4f".format(Locale.US, nx)},${"%.4f".format(Locale.US, ny)}"
                        }
                    }
                    onSaveSignature(serialized)
                },
                enabled = strokes.isNotEmpty()
            ) { Text("Guardar firma") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

data class EstimateSettings(
    val electricianHourly: Double = 40.0,
    val helperHourly: Double = 25.0,
    val reservePercent: Double = 20.0,
    val overheadHourly: Double = 12.50,
    val marginPercent: Double = 30.0,
    val minimumVisit: Double = 125.0,
    val applyMinimumVisit: Boolean = false,
    val companyLicense: String = "",
    val companyPhone: String = "",
    val brandName: String = "ApexBid"
) {
    val hourlyOperatingCost: Double
        get() = (electricianHourly + helperHourly) * (1.0 + reservePercent / 100.0) + overheadHourly

    fun save(context: Context) {
        context.getSharedPreferences("estimate_settings", Context.MODE_PRIVATE).edit()
            .putString("electrician", electricianHourly.toString())
            .putString("helper", helperHourly.toString())
            .putString("reserve", reservePercent.toString())
            .putString("overhead", overheadHourly.toString())
            .putString("margin", marginPercent.toString())
            .putString("minimum", minimumVisit.toString())
            .putBoolean("apply_minimum", applyMinimumVisit)
            .putString("company_license", companyLicense)
            .putString("company_phone", formatUsPhoneInput(companyPhone))
            .putString("brand_name", brandName)
            .apply()
    }

    companion object {
        fun load(context: Context): EstimateSettings {
            val p = context.getSharedPreferences("estimate_settings", Context.MODE_PRIVATE)
            fun number(key: String, default: Double) = p.getString(key, null)?.toDoubleOrNull() ?: default
            return EstimateSettings(
                electricianHourly = number("electrician", 40.0),
                helperHourly = number("helper", 25.0),
                reservePercent = number("reserve", 20.0),
                overheadHourly = number("overhead", 12.50),
                marginPercent = number("margin", 30.0),
                minimumVisit = number("minimum", 125.0),
                applyMinimumVisit = p.getBoolean("apply_minimum", false),
                companyLicense = p.getString("company_license", "") ?: "",
                companyPhone = formatUsPhoneInput(p.getString("company_phone", "") ?: ""),
                brandName = p.getString("brand_name", "ApexBid") ?: "ApexBid"
            )
        }
    }
}

@Composable
fun SettingsScreen(
    settings: EstimateSettings,
    onSave: (EstimateSettings) -> Unit,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit
) {
    val context = LocalContext.current
    var electrician by remember(settings) { mutableStateOf(settings.electricianHourly.toString()) }
    var helper by remember(settings) { mutableStateOf(settings.helperHourly.toString()) }
    var reserve by remember(settings) { mutableStateOf(settings.reservePercent.toString()) }
    var overhead by remember(settings) { mutableStateOf(settings.overheadHourly.toString()) }
    var margin by remember(settings) { mutableStateOf(settings.marginPercent.toString()) }
    var minimum by remember(settings) { mutableStateOf(settings.minimumVisit.toString()) }
    var applyMin by remember(settings) { mutableStateOf(settings.applyMinimumVisit) }
    var license by remember(settings) { mutableStateOf(settings.companyLicense) }
    var compPhone by remember(settings) { mutableStateOf(formatUsPhoneInput(settings.companyPhone)) }
    var brandName by remember(settings) { mutableStateOf(settings.brandName) }

    val logoFile = remember { File(context.filesDir, "company_logo.png") }
    var logoVersion by remember { mutableStateOf(0) }
    val currentLogoBitmap = remember(logoVersion) {
        if (logoFile.exists()) BitmapFactory.decodeFile(logoFile.absolutePath)?.asImageBitmap() else null
    }

    val logoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        FileOutputStream(logoFile).use { out ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
                        }
                        logoVersion++
                        Toast.makeText(context, "Logo personalizado actualizado", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error cargando logo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExportBackup(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportBackup(uri) }

    val e = electrician.toDoubleOrNull() ?: 0.0
    val h = helper.toDoubleOrNull() ?: 0.0
    val r = reserve.toDoubleOrNull() ?: 0.0
    val o = overhead.toDoubleOrNull() ?: 0.0
    val m = margin.toDoubleOrNull() ?: 0.0
    val min = minimum.toDoubleOrNull() ?: 0.0
    val preview = (e + h) * (1 + r / 100.0) + o

    val versionLabel = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            "v${pInfo.versionName} (Build $code)"
        } catch (ex: Exception) {
            "v0.8.0"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("Identidad de Marca y Logo (PDF)", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (currentLogoBitmap != null) {
                        Image(bitmap = currentLogoBitmap, contentDescription = "Logo empresa", modifier = Modifier.size(56.dp))
                    } else {
                        ApexBidLogo(size = 56.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (currentLogoBitmap != null) "Logo personalizado activo" else "Logo vectorial ApexBid activo",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { logoLauncher.launch("image/*") }) {
                                Text("Cambiar imagen")
                            }
                            if (currentLogoBitmap != null) {
                                TextButton(onClick = {
                                    logoFile.delete()
                                    logoVersion++
                                }) { Text("Usar ApexBid") }
                            }
                        }
                    }
                }
                OutlinedTextField(brandName, { brandName = it }, label = { Text("Nombre comercial en app y PDF") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(license, { license = it }, label = { Text("Licencia eléctrica (opcional en PDF)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    value = compPhone,
                    onValueChange = { compPhone = formatUsPhoneInput(it) },
                    label = { Text("Teléfono de empresa (XXX) XXX-XXXX") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        Text("Configuración de precios", style = MaterialTheme.typography.titleLarge)
        Text("Estos valores son internos y nunca aparecen en el PDF del cliente.", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(electrician, { electrician = it }, label = { Text("Electricista principal $/hora") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(helper, { helper = it }, label = { Text("Ayudante $/hora") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(reserve, { reserve = it }, label = { Text("Provisión adicional (%)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(overhead, { overhead = it }, label = { Text("Gastos indirectos $/hora") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(margin, { margin = it }, label = { Text("Margen objetivo (%)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(minimum, { minimum = it }, label = { Text("Cargo mínimo por visita ($)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Aplicar cargo mínimo automáticamente", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = applyMin, onCheckedChange = { applyMin = it })
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Vista previa del cálculo", style = MaterialTheme.typography.titleMedium)
                Text("Costo operativo del equipo: ${NumberFormat.getCurrencyInstance(Locale.US).format(preview)}/hora", style = MaterialTheme.typography.titleLarge)
                Text("Precio = costo total ÷ (1 − margen)")
            }
        }

        Button(
            onClick = {
                if (e >= 0 && h >= 0 && r >= 0 && o >= 0 && m in 0.0..90.0 && min >= 0) {
                    onSave(
                        EstimateSettings(
                            e, h, r, o, m, min, applyMin,
                            license.trim(),
                            formatUsPhoneInput(compPhone),
                            brandName.trim().ifBlank { "ApexBid" }
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Guardar configuración") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Respaldo de datos locales", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch("ApexBid_Backup_${System.currentTimeMillis()}.json") },
                modifier = Modifier.weight(1f)
            ) { Text("Exportar JSON") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.weight(1f)
            ) { Text("Importar JSON") }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ApexBidLogo(size = 28.dp)
                    Column {
                        Text("ApexBid · Electrical Estimates & Invoices", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Versión instalada: $versionLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("• Base de datos local Room v4 con fotos adjuntas", style = MaterialTheme.typography.bodySmall)
                Text("• Asistente Home Depot (ES/EN), Google Lens y Redacción IA", style = MaterialTheme.typography.bodySmall)
                Text("• Exportación PDF bilingüe con firma digital y evidencia fotográfica", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
