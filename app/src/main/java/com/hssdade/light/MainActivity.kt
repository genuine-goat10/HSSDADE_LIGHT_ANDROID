package com.hssdade.light

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import com.hssdade.light.data.*
import com.hssdade.light.pdf.QuotePdf
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class MainActivity:ComponentActivity(){ override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState); val db=AppDatabase.get(this); setContent { MaterialTheme(colorScheme=lightColorScheme(primary=androidx.compose.ui.graphics.Color(0xFF145A72),secondary=androidx.compose.ui.graphics.Color(0xFF2D8A78))) { HssdadeApp(db) } } } }

@Composable fun HssdadeApp(db:AppDatabase){
 val context = androidx.compose.ui.platform.LocalContext.current
 var tab by remember { mutableStateOf(0) }; var settings by remember { mutableStateOf(EstimateSettings.load(context)) }; val clients by db.clientDao().observeAll().collectAsStateWithLifecycle(emptyList()); val quotes by db.quoteDao().observeAll().collectAsStateWithLifecycle(emptyList()); val scope= rememberCoroutineScope()
 Scaffold(bottomBar={ NavigationBar { listOf("Inicio","Clientes","Cotizar","Historial","Ajustes").forEachIndexed{index,label-> NavigationBarItem(selected=tab==index,onClick={tab=index},icon={},label={Text(label)}) } } }) { padding->
 Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
 Text("HSSDADE-LIGHT.CORP",style=MaterialTheme.typography.headlineSmall); Text("Cotizaciones eléctricas residenciales",style=MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(14.dp))
 when(tab){0->HomeScreen(clients.size,quotes.size){tab=2};1->ClientsScreen(clients,{name,phone,email,address->scope.launch{db.clientDao().insert(ClientEntity(name=name,phone=phone,email=email,address=address))}});2->QuoteScreen(clients,settings,{q->scope.launch{db.quoteDao().insert(q)} });3->HistoryScreen(quotes){q-> try { val file=QuotePdf.create(context,q); Toast.makeText(context,"PDF guardado en Descargas: $file",Toast.LENGTH_LONG).show() } catch(e:Exception) { Toast.makeText(context,"Error al crear PDF: ${e.message}",Toast.LENGTH_LONG).show() } };4->SettingsScreen(settings){ updated -> settings=updated; updated.save(context); Toast.makeText(context,"Configuración guardada",Toast.LENGTH_SHORT).show() }}
 }
 }
}

@Composable fun HomeScreen(clientCount:Int,quoteCount:Int,onNew:()->Unit){ Column(verticalArrangement=Arrangement.spacedBy(12.dp)){ Text("Panel principal",style=MaterialTheme.typography.titleLarge); Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){ StatCard("Clientes",clientCount.toString(),Modifier.weight(1f)); StatCard("Cotizaciones",quoteCount.toString(),Modifier.weight(1f)) }; Button(onClick=onNew,modifier=Modifier.fillMaxWidth()){Text("Crear cotización")} ; Text("Modo local activo. Los datos se guardan en este dispositivo.",style=MaterialTheme.typography.bodySmall) } }
@Composable fun StatCard(label:String,value:String,modifier:Modifier=Modifier){ Card(modifier){Column(Modifier.padding(16.dp)){Text(label);Text(value,style=MaterialTheme.typography.headlineMedium)}}}

@Composable fun ClientsScreen(clients:List<ClientEntity>,onSave:(String,String,String,String)->Unit){ var name by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var address by remember{mutableStateOf("")}; Column(Modifier.fillMaxSize()){ Text("Clientes",style=MaterialTheme.typography.titleLarge); OutlinedTextField(name,{name=it},label={Text("Nombre del cliente")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(phone,{phone=it},label={Text("Teléfono")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(email,{email=it},label={Text("Email")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(address,{address=it},label={Text("Dirección del trabajo")},modifier=Modifier.fillMaxWidth()); Button(onClick={if(name.isNotBlank()){onSave(name,phone,email,address);name="";phone="";email="";address=""}},modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("Guardar cliente")}; Spacer(Modifier.height(12.dp)); Text("Clientes guardados",style=MaterialTheme.typography.titleMedium); LazyColumn {items(clients){c->ListItem(headlineContent={Text(c.name)},supportingContent={Text(listOf(c.phone,c.email,c.address).filter{it.isNotBlank()}.joinToString(" · "))})}} } }

@Composable fun QuoteScreen(clients:List<ClientEntity>,settings:EstimateSettings,onSave:(QuoteEntity)->Unit){
    var client by remember { mutableStateOf("") }
    var clientExpanded by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Int>() }
    var servicesExpanded by remember { mutableStateOf(false) }
    var descEs by remember { mutableStateOf("") }
    var descEn by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var extraMaterials by remember { mutableStateOf("0") }
    val selected = ServiceCatalog.all.filter { it.id in selectedIds }
    val quantity = qty.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val additionalMaterials = extraMaterials.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val laborHours = selected.sumOf { it.hours }
    val materials = selected.sumOf { it.materials } + additionalMaterials
    val cost = (laborHours * settings.hourlyOperatingCost + materials) * quantity
    val margin = settings.marginPercent / 100.0
    val total = if (cost > 0 && margin < 1) cost / (1 - margin) else 0.0
    val money = NumberFormat.getCurrencyInstance(Locale.US)
    val serviceNamesEs = selected.joinToString("\n") { "• ${it.nameEs}" }
    val serviceNamesEn = selected.joinToString("\n") { "• ${it.nameEn}" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("Nueva cotización / New quotation", style=MaterialTheme.typography.titleLarge)
        Text("Cliente / Customer", style=MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick={clientExpanded=true}, modifier=Modifier.fillMaxWidth()) {
                Text(if(client.isBlank()) "Seleccionar cliente / Select customer" else client)
            }
            DropdownMenu(expanded=clientExpanded, onDismissRequest={clientExpanded=false}) {
                clients.forEach { c ->
                    DropdownMenuItem(text={Text(c.name)}, onClick={client=c.name;clientExpanded=false})
                }
            }
        }
        OutlinedTextField(client,{client=it},label={Text("Nombre del cliente / Customer name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Text("Servicios del catálogo / Service catalog (36)", style=MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick={servicesExpanded=true}, modifier=Modifier.fillMaxWidth()) {
            Text(if(selected.isEmpty()) "Agregar servicios / Add services" else "${selected.size} servicio(s) seleccionado(s) / selected")
        }
        DropdownMenu(expanded=servicesExpanded,onDismissRequest={servicesExpanded=false},modifier=Modifier.heightIn(max=420.dp)) {
            ServiceCatalog.all.forEach { service ->
                DropdownMenuItem(
                    text={Column { Text(service.nameEs); Text(service.nameEn,style=MaterialTheme.typography.bodySmall) }},
                    leadingIcon={Checkbox(checked=service.id in selectedIds,onCheckedChange={checked->if(checked)selectedIds.add(service.id) else selectedIds.remove(service.id)})},
                    onClick={if(service.id in selectedIds)selectedIds.remove(service.id) else selectedIds.add(service.id)}
                )
            }
        }
        if(selected.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("Servicios incluidos / Included services",style=MaterialTheme.typography.titleSmall)
                    selected.forEach { service ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(service.nameEs)
                                Text(service.nameEn,style=MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick={selectedIds.remove(service.id)}) { Text("Quitar / Remove") }
                        }
                    }
                }
            }
        }
        OutlinedTextField(descEs,{descEs=it},label={Text("Descripción del trabajo (español)")},modifier=Modifier.fillMaxWidth(),minLines=3)
        OutlinedTextField(descEn,{descEn=it},label={Text("Work description (English)")},modifier=Modifier.fillMaxWidth(),minLines=3)
        OutlinedTextField(notes,{notes=it},label={Text("Observaciones / Job notes")},modifier=Modifier.fillMaxWidth(),minLines=3)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(qty,{qty=it},label={Text("Cantidad / Quantity")},modifier=Modifier.weight(1f))
            OutlinedTextField(extraMaterials,{extraMaterials=it},label={Text("Material adicional $ / Extra materials $")},modifier=Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("Resumen / Estimate summary",style=MaterialTheme.typography.titleMedium)
                Text("Horas estimadas / Estimated hours: ${"%.2f".format(Locale.US,laborHours*quantity)}")
                Text("Materiales base + adicionales / Base + extra materials: ${money.format(materials*quantity)}")
                Text("TOTAL / TOTAL",style=MaterialTheme.typography.labelLarge)
                Text(money.format(total),style=MaterialTheme.typography.headlineMedium)
                Text("Cálculo interno: no se incluyen costos ni margen en el PDF del cliente. / Internal calculation: costs and margin are excluded from the customer PDF.",style=MaterialTheme.typography.bodySmall)
            }
        }
        Button(
            onClick={
                if(client.isNotBlank() && selected.isNotEmpty() && quantity>0) {
                    val names = selected.joinToString(" / ") { "${it.nameEs} | ${it.nameEn}" }
                    onSave(QuoteEntity(
                        clientName=client.trim(),
                        serviceName=names,
                        descriptionEs=descEs.ifBlank { serviceNamesEs },
                        descriptionEn=descEn.ifBlank { serviceNamesEn },
                        observations=notes,
                        quantity=quantity,
                        total=total
                    ))
                    client="";selectedIds.clear();descEs="";descEn="";notes="";qty="1";extraMaterials="0"
                }
            },
            enabled=client.isNotBlank() && selected.isNotEmpty() && quantity>0,
            modifier=Modifier.fillMaxWidth()
        ) { Text("Guardar cotización / Save quotation") }
        Text("Los datos se guardan localmente en este dispositivo. / Data is saved locally on this device.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable fun HistoryScreen(quotes:List<QuoteEntity>,onExport:(QuoteEntity)->Unit){ val money=NumberFormat.getCurrencyInstance(Locale.US); Column {Text("Historial de cotizaciones",style=MaterialTheme.typography.titleLarge); if(quotes.isEmpty()) Text("Todavía no hay cotizaciones guardadas.") else LazyColumn {items(quotes){q->Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(q.clientName,style=MaterialTheme.typography.titleMedium);Text(q.serviceName);Text(money.format(q.total),style=MaterialTheme.typography.titleLarge);if(q.observations.isNotBlank())Text(q.observations,style=MaterialTheme.typography.bodySmall);Text("Guardada en este dispositivo",style=MaterialTheme.typography.labelSmall);OutlinedButton(onClick={onExport(q)},modifier=Modifier.fillMaxWidth()){Text("Generar PDF bilingüe")}}}}} } }


data class EstimateSettings(
    val electricianHourly: Double = 40.0,
    val helperHourly: Double = 25.0,
    val reservePercent: Double = 20.0,
    val overheadHourly: Double = 12.50,
    val marginPercent: Double = 30.0,
    val minimumVisit: Double = 125.0
) {
    val hourlyOperatingCost: Double get() = (electricianHourly + helperHourly) * (1.0 + reservePercent / 100.0) + overheadHourly
    fun save(context: android.content.Context) {
        context.getSharedPreferences("estimate_settings", android.content.Context.MODE_PRIVATE).edit()
            .putString("electrician", electricianHourly.toString()).putString("helper", helperHourly.toString())
            .putString("reserve", reservePercent.toString()).putString("overhead", overheadHourly.toString())
            .putString("margin", marginPercent.toString()).putString("minimum", minimumVisit.toString()).apply()
    }
    companion object {
        fun load(context: android.content.Context): EstimateSettings {
            val p = context.getSharedPreferences("estimate_settings", android.content.Context.MODE_PRIVATE)
            fun number(key: String, default: Double) = p.getString(key, null)?.toDoubleOrNull() ?: default
            return EstimateSettings(number("electrician",40.0), number("helper",25.0), number("reserve",20.0), number("overhead",12.50), number("margin",30.0), number("minimum",125.0))
        }
    }
}

@Composable fun SettingsScreen(settings: EstimateSettings, onSave: (EstimateSettings) -> Unit) {
    var electrician by remember(settings) { mutableStateOf(settings.electricianHourly.toString()) }
    var helper by remember(settings) { mutableStateOf(settings.helperHourly.toString()) }
    var reserve by remember(settings) { mutableStateOf(settings.reservePercent.toString()) }
    var overhead by remember(settings) { mutableStateOf(settings.overheadHourly.toString()) }
    var margin by remember(settings) { mutableStateOf(settings.marginPercent.toString()) }
    var minimum by remember(settings) { mutableStateOf(settings.minimumVisit.toString()) }
    val e = electrician.toDoubleOrNull() ?: 0.0; val h = helper.toDoubleOrNull() ?: 0.0
    val r = reserve.toDoubleOrNull() ?: 0.0; val o = overhead.toDoubleOrNull() ?: 0.0
    val m = margin.toDoubleOrNull() ?: 0.0; val min = minimum.toDoubleOrNull() ?: 0.0
    val preview = (e+h)*(1+r/100.0)+o
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("Configuración de precios", style=MaterialTheme.typography.titleLarge)
        Text("Estos valores son internos y nunca aparecen en el PDF del cliente.", style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(electrician,{electrician=it},label={Text("Electricista principal $/hora")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(helper,{helper=it},label={Text("Ayudante $/hora")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(reserve,{reserve=it},label={Text("Provisión adicional (%)")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(overhead,{overhead=it},label={Text("Gastos indirectos $/hora")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(margin,{margin=it},label={Text("Margen objetivo (%)")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(minimum,{minimum=it},label={Text("Cargo mínimo por visita ($)")},modifier=Modifier.fillMaxWidth())
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Vista previa del cálculo", style=MaterialTheme.typography.titleMedium)
            Text("Costo operativo del equipo: ${NumberFormat.getCurrencyInstance(Locale.US).format(preview)}/hora", style=MaterialTheme.typography.titleLarge)
            Text("Precio = costo total ÷ (1 − margen)")
            Text("El cargo mínimo se guarda como referencia y todavía no se aplica automáticamente a las cotizaciones.", style=MaterialTheme.typography.bodySmall)
        } }
        Button(onClick={ if(e>=0 && h>=0 && r>=0 && o>=0 && m in 0.0..90.0 && min>=0) onSave(EstimateSettings(e,h,r,o,m,min)) }, modifier=Modifier.fillMaxWidth()) { Text("Guardar configuración") }
        Text("Nota: la provisión es un porcentaje configurable para estimación. No representa automáticamente impuestos patronales para trabajadores 1099.", style=MaterialTheme.typography.bodySmall)
    }
}
