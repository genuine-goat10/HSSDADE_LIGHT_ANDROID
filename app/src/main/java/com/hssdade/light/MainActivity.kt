package com.hssdade.light

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hssdade.light.data.*
import com.hssdade.light.pdf.QuotePdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
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
                HssdadeApp(db)
            }
        }
    }
}

@Composable
fun HssdadeApp(db: AppDatabase) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var settings by remember { mutableStateOf(EstimateSettings.load(context)) }
    val clients by db.clientDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val quotes by db.quoteDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    val navItems = listOf(
        Triple("Inicio", Icons.Filled.Home, 0),
        Triple("Clientes", Icons.Filled.People, 1),
        Triple("Cotizar", Icons.Filled.Calculate, 2),
        Triple("Historial", Icons.Filled.History, 3),
        Triple("Ajustes", Icons.Filled.Settings, 4)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("HSSDADE-LIGHT.CORP", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Cotizaciones eléctricas residenciales", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))

            when (tab) {
                0 -> HomeScreen(clients.size, quotes.size) { tab = 2 }
                1 -> ClientsScreen(
                    clients = clients,
                    onSave = { name, phone, email, address ->
                        scope.launch {
                            db.clientDao().insert(ClientEntity(name = name, phone = phone, email = email, address = address))
                        }
                    },
                    onDelete = { client ->
                        scope.launch { db.clientDao().delete(client) }
                    }
                )
                2 -> QuoteScreen(clients, settings) { q ->
                    scope.launch {
                        db.quoteDao().insert(q)
                        Toast.makeText(context, "Cotización guardada", Toast.LENGTH_SHORT).show()
                        tab = 3
                    }
                }
                3 -> HistoryScreen(
                    quotes = quotes,
                    onExport = { q, shareAfter ->
                        try {
                            val generated = QuotePdf.create(context, q)
                            Toast.makeText(context, "PDF guardado en Descargas: ${generated.fileName}", Toast.LENGTH_LONG).show()
                            if (shareAfter && generated.uri != null) {
                                sharePdf(context, generated.uri, q.clientName)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error al crear PDF: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
                4 -> SettingsScreen(
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
}

private fun sharePdf(context: Context, uri: Uri, clientName: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Cotización / Quotation - HSSDADE-LIGHT.CORP ($clientName)")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir cotización PDF / Share PDF"))
}

private suspend fun exportDatabaseJson(context: Context, db: AppDatabase, uri: Uri) = withContext(Dispatchers.IO) {
    val clients = db.clientDao().getAllOnce()
    val quotes = db.quoteDao().getAllOnce()

    val root = JSONObject()
    root.put("app", "HSSDADE-LIGHT.CORP")
    root.put("exportedAt", System.currentTimeMillis())

    val clientsArr = JSONArray()
    clients.forEach { c ->
        clientsArr.put(JSONObject().apply {
            put("name", c.name)
            put("phone", c.phone)
            put("email", c.email)
            put("address", c.address)
        })
    }
    root.put("clients", clientsArr)

    val quotesArr = JSONArray()
    quotes.forEach { q ->
        quotesArr.put(JSONObject().apply {
            put("clientName", q.clientName)
            put("serviceName", q.serviceName)
            put("descriptionEs", q.descriptionEs)
            put("descriptionEn", q.descriptionEn)
            put("observations", q.observations)
            put("quantity", q.quantity)
            put("total", q.total)
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
                serviceName = obj.optString("serviceName"),
                descriptionEs = obj.optString("descriptionEs"),
                descriptionEn = obj.optString("descriptionEn"),
                observations = obj.optString("observations"),
                quantity = obj.optDouble("quantity", 1.0),
                total = obj.optDouble("total", 0.0),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        )
        imported++
    }
    imported
}

@Composable
fun HomeScreen(clientCount: Int, quoteCount: Int, onNew: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Panel principal", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Clientes", clientCount.toString(), Modifier.weight(1f))
            StatCard("Cotizaciones", quoteCount.toString(), Modifier.weight(1f))
        }
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Crear cotización")
        }
        Text("Modo local activo. Los datos se guardan en este dispositivo.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ClientsScreen(
    clients: List<ClientEntity>,
    onSave: (String, String, String, String) -> Unit,
    onDelete: (ClientEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Text("Clientes", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name, { name = it }, label = { Text("Nombre del cliente") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(address, { address = it }, label = { Text("Dirección del trabajo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(
            onClick = {
                if (name.isNotBlank()) {
                    onSave(name.trim(), phone.trim(), email.trim(), address.trim())
                    name = ""; phone = ""; email = ""; address = ""
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) { Text("Guardar cliente") }
        Spacer(Modifier.height(12.dp))
        Text("Clientes guardados (${clients.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(clients) { c ->
                ListItem(
                    headlineContent = { Text(c.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(listOf(c.phone, c.email, c.address).filter { it.isNotBlank() }.joinToString(" · "))
                    },
                    trailingContent = {
                        IconButton(onClick = { onDelete(c) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteScreen(clients: List<ClientEntity>, settings: EstimateSettings, onSave: (QuoteEntity) -> Unit) {
    var client by remember { mutableStateOf("") }
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

    // Cálculos económicos idénticos a la versión original
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("Nueva cotización / New quotation", style = MaterialTheme.typography.titleLarge)
        Text("Cliente / Customer", style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { clientExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (client.isBlank()) "Seleccionar cliente guardado / Select customer" else client)
            }
            DropdownMenu(expanded = clientExpanded, onDismissRequest = { clientExpanded = false }) {
                clients.forEach { c ->
                    DropdownMenuItem(text = { Text(c.name) }, onClick = { client = c.name; clientExpanded = false })
                }
            }
        }
        OutlinedTextField(client, { client = it }, label = { Text("Nombre del cliente / Customer name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Text("Servicios del catálogo / Service catalog (36)", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { showCatalogSheet = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (selected.isEmpty()) "Buscar y agregar servicios / Add services" else "${selected.size} servicio(s) seleccionado(s)")
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

        OutlinedTextField(descEs, { descEs = it }, label = { Text("Descripción del trabajo (español)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        OutlinedTextField(descEn, { descEn = it }, label = { Text("Work description (English)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones / Job notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

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
                            serviceName = names,
                            descriptionEs = descEs.ifBlank { serviceNamesEs },
                            descriptionEn = descEn.ifBlank { serviceNamesEn },
                            observations = notes.trim(),
                            quantity = quantity,
                            total = total
                        )
                    )
                    client = ""; selectedIds.clear(); descEs = ""; descEn = ""; notes = ""; qty = "1"; extraMaterials = "0"
                }
            },
            enabled = client.isNotBlank() && selected.isNotEmpty() && quantity > 0,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Guardar cotización / Save quotation") }
    }
}

@Composable
fun HistoryScreen(quotes: List<QuoteEntity>, onExport: (QuoteEntity, Boolean) -> Unit) {
    val money = NumberFormat.getCurrencyInstance(Locale.US)
    val dateFmt = remember { SimpleDateFormat("MM/dd/yyyy", Locale.US) }

    Column {
        Text("Historial de cotizaciones", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (quotes.isEmpty()) {
            Text("Todavía no hay cotizaciones guardadas.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quotes) { q ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(q.clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(dateFmt.format(Date(q.createdAt)), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(q.serviceName, style = MaterialTheme.typography.bodyMedium)
                            Text(money.format(q.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            if (q.observations.isNotBlank()) {
                                Text(q.observations, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onExport(q, false) }, modifier = Modifier.weight(1f)) {
                                    Text("Guardar PDF")
                                }
                                Button(onClick = { onExport(q, true) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Compartir PDF")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class EstimateSettings(
    val electricianHourly: Double = 40.0,
    val helperHourly: Double = 25.0,
    val reservePercent: Double = 20.0,
    val overheadHourly: Double = 12.50,
    val marginPercent: Double = 30.0,
    val minimumVisit: Double = 125.0,
    val applyMinimumVisit: Boolean = false
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
                applyMinimumVisit = p.getBoolean("apply_minimum", false)
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
    var electrician by remember(settings) { mutableStateOf(settings.electricianHourly.toString()) }
    var helper by remember(settings) { mutableStateOf(settings.helperHourly.toString()) }
    var reserve by remember(settings) { mutableStateOf(settings.reservePercent.toString()) }
    var overhead by remember(settings) { mutableStateOf(settings.overheadHourly.toString()) }
    var margin by remember(settings) { mutableStateOf(settings.marginPercent.toString()) }
    var minimum by remember(settings) { mutableStateOf(settings.minimumVisit.toString()) }
    var applyMin by remember(settings) { mutableStateOf(settings.applyMinimumVisit) }

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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
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
                    onSave(EstimateSettings(e, h, r, o, m, min, applyMin))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Guardar configuración") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Respaldo de datos locales", style = MaterialTheme.typography.titleMedium)
        Text("Exporta o importa tus clientes y cotizaciones en formato JSON.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { exportLauncher.launch("HSSDADE_Backup_${System.currentTimeMillis()}.json") },
                modifier = Modifier.weight(1f)
            ) { Text("Exportar JSON") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.weight(1f)
            ) { Text("Importar JSON") }
        }
    }
}
