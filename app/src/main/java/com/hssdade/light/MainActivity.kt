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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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

@Composable
fun ApexBidLogo(size: Dp = 40.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color = Color(0xFF145A72),
            cornerRadius = CornerRadius(w * 0.22f, h * 0.22f)
        )
        // Hoja de cálculo / cotización blanca
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.24f, h * 0.18f),
            size = Size(w * 0.44f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.06f, h * 0.06f)
        )
        // Líneas de partidas y total
        drawRect(Color(0xFF145A72), Offset(w * 0.31f, h * 0.30f), Size(w * 0.24f, h * 0.05f))
        drawRect(Color(0xFF7BA4B3), Offset(w * 0.31f, h * 0.41f), Size(w * 0.30f, h * 0.045f))
        drawRect(Color(0xFF7BA4B3), Offset(w * 0.31f, h * 0.51f), Size(w * 0.20f, h * 0.045f))
        drawRect(Color(0xFF2D8A78), Offset(w * 0.31f, h * 0.63f), Size(w * 0.16f, h * 0.055f))

        // Sello circular verde con rayo dorado
        val cx = w * 0.68f
        val cy = h * 0.68f
        val r = w * 0.20f
        drawCircle(color = Color(0xFF2D8A78), radius = r, center = Offset(cx, cy))

        val bolt = Path().apply {
            moveTo(cx + r * 0.18f, cy - r * 0.58f)
            lineTo(cx - r * 0.42f, cy + r * 0.08f)
            lineTo(cx - r * 0.02f, cy + r * 0.08f)
            lineTo(cx - r * 0.20f, cy + r * 0.62f)
            lineTo(cx + r * 0.45f, cy - r * 0.08f)
            lineTo(cx + r * 0.04f, cy - r * 0.08f)
            close()
        }
        drawPath(path = bolt, color = Color(0xFFEBB328))
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
                            db.clientDao().insert(ClientEntity(name = name, phone = phone, email = email, address = address))
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
        Estimado/a ${quote.clientName} / Dear${quote.clientName},
        
        Adjuntamos su $docType eléctrica de $brand por un total de$money.
        Please find attached your electrical document from $brand for a total of$money.
        
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
    val message = "Hola ${quote.clientName}, le saludamos de$brand. Su cotización eléctrica (${quote.serviceName.take(55)}) es por $money. Estado: ${quote.status}."
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
            put("createdAt", q.createdAt)
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
                phone = obj.optString("phone"),
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
                clientPhone = obj.optString("clientPhone"),
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
                onValueChange = { phone = it },
                label = { Text("Teléfono (para SMS)") },
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

    val selected = ServiceCatalog.all.filter { it.id in selectedIds }
    val quantity = qty.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val additionalMaterials = extraMaterials.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    // Fórmulas económicas intactas
    val laborHours = selected.sumOf { it.hours }
    val materials = selected.sumOf { it.materials } + additionalMaterials
    val cost = (laborHours * settings.
