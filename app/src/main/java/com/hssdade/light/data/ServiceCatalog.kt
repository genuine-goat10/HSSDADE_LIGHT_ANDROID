package com.hssdade.light.data

data class ServiceItem(val id:Int,val category:String,val nameEs:String,val nameEn:String,val hours:Double,val materials:Double,val defaultNotes:String)

object ServiceCatalog {
    val all = listOf(
        ServiceItem(1, "Receptáculos e interruptores", "Reemplazar receptáculo estándar", "Replace standard receptacle", 0.5, 8.0, "Reemplazo directo; visita mínima se cotiza aparte."),
        ServiceItem(2, "Receptáculos e interruptores", "Reemplazar receptáculo GFCI", "Replace GFCI receptacle", 0.75, 18.0, "Verificar protección, ubicación y requisitos aplicables."),
        ServiceItem(3, "Receptáculos e interruptores", "Reemplazar interruptor sencillo", "Replace single-pole switch", 0.5, 7.0, "Reemplazo directo."),
        ServiceItem(4, "Receptáculos e interruptores", "Instalar dimmer compatible", "Install compatible dimmer", 0.75, 25.0, "Confirmar compatibilidad con luminaria y carga."),
        ServiceItem(5, "Receptáculos e interruptores", "Receptáculo nuevo, recorrido cercano", "New receptacle, short run", 2.0, 25.0, "Supuesto de recorrido corto y acceso razonable."),
        ServiceItem(6, "Receptáculos e interruptores", "Receptáculo nuevo, recorrido medio", "New receptacle, medium run", 3.0, 75.0, "La distancia y el tipo de pared pueden cambiar el costo."),
        ServiceItem(7, "Receptáculos e interruptores", "Receptáculo exterior WR/GFCI", "Outdoor WR/GFCI receptacle", 2.5, 65.0, "Incluye caja/cubierta apta para exterior; condiciones especiales aparte."),
        ServiceItem(8, "Receptáculos e interruptores", "Receptáculo 240V en circuito existente", "240V receptacle on existing circuit", 1.75, 45.0, "Solo si el circuito existente tiene capacidad y configuración adecuadas."),
        ServiceItem(9, "Receptáculos e interruptores", "Instalar caja de conexiones accesible", "Install accessible junction box", 1.25, 20.0, "No incluye empalmes ocultos ni reparación de acabados."),
        ServiceItem(10, "Receptáculos e interruptores", "Reemplazar caja eléctrica existente", "Replace existing electrical box", 1.25, 18.0, "Supone acceso razonable y sin daño estructural."),
        ServiceItem(11, "Iluminación y ventilación", "Punto de iluminación nuevo", "New lighting point", 3.0, 65.0, "Recorrido estándar; acabado y acceso pueden variar."),
        ServiceItem(12, "Iluminación y ventilación", "Reemplazar luminaria sencilla", "Replace standard light fixture", 1.25, 15.0, "Luminaria suministrada por cliente; confirmar peso y soporte."),
        ServiceItem(13, "Iluminación y ventilación", "Instalar luz LED empotrada individual", "Install single recessed LED light", 1.0, 30.0, "Supone espacio y acceso adecuados; luminaria básica."),
        ServiceItem(14, "Iluminación y ventilación", "Paquete de 4 luces LED empotradas", "Package of 4 recessed LED lights", 3.5, 120.0, "Precio por paquete; luminarias básicas."),
        ServiceItem(15, "Iluminación y ventilación", "Instalar ventilador de techo", "Install ceiling fan", 2.0, 45.0, "Supone caja/soporte existente apto y ventilador suministrado por cliente."),
        ServiceItem(16, "Iluminación y ventilación", "Instalar soporte reforzado para ventilador", "Install reinforced fan support", 3.0, 75.0, "Solo estructura accesible; reparar techo no incluido."),
        ServiceItem(17, "Iluminación y ventilación", "Reemplazar extractor de baño", "Replace bathroom exhaust fan", 2.0, 55.0, "Supone ducto y alimentación existentes compatibles."),
        ServiceItem(18, "Iluminación y ventilación", "Instalar detector de humo cableado", "Install hardwired smoke detector", 1.5, 35.0, "Supone circuito/interconexión existente accesible."),
        ServiceItem(19, "Iluminación y ventilación", "Instalar detector de CO cableado", "Install hardwired CO detector", 1.5, 45.0, "Supone alimentación existente accesible."),
        ServiceItem(20, "Iluminación y ventilación", "Reemplazar interruptor de 3 vías", "Replace 3-way switch", 1.0, 12.0, "Reemplazo; diagnóstico de cableado se cotiza aparte."),
        ServiceItem(21, "Circuitos nuevos", "Circuito dedicado 20A, hasta 50 ft", "Dedicated 20A circuit, up to 50 ft", 3.0, 110.0, "Supone ruta accesible y panel con capacidad."),
        ServiceItem(22, "Circuitos nuevos", "Circuito dedicado 20A, 50–100 ft", "Dedicated 20A circuit, 50–100 ft", 5.0, 190.0, "Supone ruta accesible; no incluye acabado/reparación."),
        ServiceItem(23, "Circuitos nuevos", "Circuito 240V, 30A, hasta 50 ft", "240V, 30A circuit, up to 50 ft", 4.0, 160.0, "Calibre y protección sujetos a carga, equipo y método de instalación."),
        ServiceItem(24, "Circuitos nuevos", "Circuito 240V, 50A, hasta 50 ft", "240V, 50A circuit, up to 50 ft", 5.0, 230.0, "Calibre y protección sujetos a carga, equipo y método de instalación."),
        ServiceItem(25, "Circuitos nuevos", "Circuito dedicado para microondas", "Dedicated microwave circuit", 3.0, 110.0, "Supone recorrido accesible y panel compatible."),
        ServiceItem(26, "Circuitos nuevos", "Circuito dedicado para dishwasher", "Dedicated dishwasher circuit", 3.0, 100.0, "Supone recorrido accesible; coordinación con equipo."),
        ServiceItem(27, "Circuitos nuevos", "Circuito dedicado para triturador", "Dedicated garbage disposal circuit", 2.5, 75.0, "Supone recorrido accesible y ubicación existente."),
        ServiceItem(28, "Circuitos nuevos", "Circuito dedicado para laundry 20A", "Dedicated 20A laundry circuit", 4.0, 145.0, "Supone ruta accesible y requisitos del área de lavandería."),
        ServiceItem(29, "Circuitos nuevos", "Circuito para A/C sin desconectivo", "A/C circuit without disconnect", 5.0, 210.0, "Estimación preliminar; confirmar placa, MCA/MOCP y ruta."),
        ServiceItem(30, "Circuitos nuevos", "Instalar desconectivo para A/C", "Install A/C disconnect", 2.0, 85.0, "Supone circuito existente adecuado; verificar equipo y ubicación."),
        ServiceItem(31, "Paneles, protección y diagnóstico", "Reemplazar breaker estándar compatible", "Replace compatible standard breaker", 0.75, 25.0, "Solo breaker listado/compatible; verificar panel y condición."),
        ServiceItem(32, "Paneles, protección y diagnóstico", "Reemplazar breaker GFCI/AFCI compatible", "Replace compatible GFCI/AFCI breaker", 1.25, 95.0, "Verificar tipo de panel, compatibilidad y diagnóstico."),
        ServiceItem(33, "Paneles, protección y diagnóstico", "Instalar subpanel básico", "Install basic subpanel", 10.0, 450.0, "Estimación muy preliminar; requiere visita, cálculo y alcance definido."),
        ServiceItem(34, "Paneles, protección y diagnóstico", "Reemplazar panel residencial básico", "Replace basic residential panel", 16.0, 950.0, "No usar como precio fijo sin inspección, carga, permiso y alcance."),
        ServiceItem(35, "Paneles, protección y diagnóstico", "Instalar conductor de grounding/bonding", "Install grounding/bonding conductor", 5.0, 180.0, "Ruta, tamaño, electrodos y requisitos deben verificarse en sitio."),
        ServiceItem(36, "Paneles, protección y diagnóstico", "Diagnóstico eléctrico, primera hora", "Electrical troubleshooting, first hour", 1.0, 10.0, "Precio mínimo de diagnóstico; reparaciones y materiales adicionales aparte.")
    )
}
