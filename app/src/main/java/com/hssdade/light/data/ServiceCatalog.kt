package com.hssdade.light.data

data class ServiceItem(
    val id: Int,
    val category: String,
    val nameEs: String,
    val nameEn: String,
    val hours: Double,
    val materials: Double
)

object ServiceCatalog {
    val all = listOf(
        // === 1. Receptáculos e interruptores (Originales + Nuevos) ===
        ServiceItem(1, "Receptáculos e interruptores", "Instalación de tomacorriente estándar 120V", "Standard 120V Duplex Outlet Installation", 1.25, 28.0),
        ServiceItem(2, "Receptáculos e interruptores", "Reemplazo de tomacorriente por GFCI", "GFCI Receptacle Upgrade", 1.0, 38.0),
        ServiceItem(3, "Receptáculos e interruptores", "Instalación de tomacorriente exterior resistente a la intemperie", "Weatherproof Exterior Outlet Installation", 1.5, 55.0),
        ServiceItem(4, "Receptáculos e interruptores", "Instalación de interruptor sencillo (Single-Pole)", "Single-Pole Light Switch Installation", 1.0, 22.0),
        ServiceItem(5, "Receptáculos e interruptores", "Instalación de interruptor de 3 vías (3-Way Switch)", "3-Way Light Switch Installation", 1.75, 42.0),
        ServiceItem(6, "Receptáculos e interruptores", "Instalación de atenuador de luz (Dimmer Switch)", "Dimmer Switch Installation", 1.0, 45.0),
        ServiceItem(7, "Receptáculos e interruptores", "Instalación de tomacorriente USB / USB-C", "USB / USB-C Charger Outlet Installation", 0.75, 38.0),
        ServiceItem(8, "Receptáculos e interruptores", "Reemplazo de placa y mecanismos dañados (pack x3)", "Damaged Outlet/Switch Replacement (3-Pack)", 1.5, 32.0),
        ServiceItem(9, "Receptáculos e interruptores", "Instalación de tomacorriente dedicado para TV en pared", "Recessed Behind-TV Outlet & Cable Kit", 1.5, 65.0),
        ServiceItem(37, "Receptáculos e interruptores", "Tomacorriente de piso empotrado (Floor Box Outlet)", "Recessed Floor Box Receptacle Installation", 2.5, 95.0),
        ServiceItem(38, "Receptáculos e interruptores", "Interruptor inteligente Wi-Fi / Smart Dimmer", "Wi-Fi Smart Switch or Smart Dimmer Installation", 1.0, 52.0),
        ServiceItem(39, "Receptáculos e interruptores", "Sensor de ocupación / movimiento en pared", "Wall Occupancy / Motion Sensor Switch Installation", 1.0, 42.0),
        ServiceItem(40, "Receptáculos e interruptores", "Timbre con cámara (Ring / Nest) y transformador", "Smart Video Doorbell & Transformer Upgrade", 1.5, 45.0),
        ServiceItem(41, "Receptáculos e interruptores", "Reemplazo de detector de humo / CO interconectado", "Hardwired Interconnected Smoke & CO Detector", 1.0, 48.0),
        ServiceItem(42, "Receptáculos e interruptores", "Tomacorriente de servicio GFCI exterior para A/C (requisito NEC 25 pies)", "Exterior HVAC Service GFCI Receptacle (NEC 25-ft Requirement)", 1.5, 55.0),

        // === 2. Iluminación y ventilación (Originales + Nuevos) ===
        ServiceItem(10, "Iluminación y ventilación", "Instalación de ventilador de techo (con caja existente)", "Ceiling Fan Replacement (Existing Box)", 1.25, 20.0),
        ServiceItem(11, "Iluminación y ventilación", "Instalación de ventilador de techo nuevo con soporte estructural", "New Ceiling Fan Installation with Fan-Rated Box", 2.25, 55.0),
        ServiceItem(12, "Iluminación y ventilación", "Instalación de luces LED empotradas (pack de 4)", "Recessed LED Canless Lights Installation (4-Pack)", 2.5, 95.0),
        ServiceItem(13, "Iluminación y ventilación", "Instalación de luces LED empotradas (pack de 8)", "Recessed LED Canless Lights Installation (8-Pack)", 4.5, 180.0),
        ServiceItem(14, "Iluminación y ventilación", "Instalación de lámpara colgante o candelabro estándar", "Chandelier / Pendant Light Fixture Installation", 1.75, 35.0),
        ServiceItem(15, "Iluminación y ventilación", "Instalación de candelabro en techo alto (+12 pies)", "High-Ceiling Chandelier Installation (Over 12 ft)", 3.5, 65.0),
        ServiceItem(16, "Iluminación y ventilación", "Instalación de reflector exterior con sensor de movimiento", "Exterior Security Floodlight with Motion Sensor", 1.5, 75.0),
        ServiceItem(17, "Iluminación y ventilación", "Instalación de iluminación LED bajo gabinetes de cocina", "Under-Cabinet LED Kitchen Lighting", 2.5, 90.0),
        ServiceItem(18, "Iluminación y ventilación", "Reemplazo o instalación de extractor de baño con luz", "Bathroom Exhaust Fan & Light Installation", 2.0, 115.0),
        ServiceItem(43, "Iluminación y ventilación", "Tira LED indirecta en techo (Cove / Tray Ceiling)", "Tray Ceiling Indirect LED Strip & Driver Setup", 3.0, 115.0),
        ServiceItem(44, "Iluminación y ventilación", "Iluminación exterior en aleros / fachada (Soffit Lights x4)", "Exterior Soffit Recessed LED Lights (4-Pack)", 3.0, 110.0),
        ServiceItem(45, "Iluminación y ventilación", "Iluminación de paisaje de bajo voltaje (Transformador + 6 luces)", "Low-Voltage Landscape Lighting (Transformer + 6 Lights)", 3.5, 210.0),

        // === 3. Circuitos nuevos, Mini-Splits y A/C (Originales + Nuevos) ===
        ServiceItem(19, "Circuitos nuevos", "Circuito dedicado 120V/20A para electrodoméstico o garaje", "Dedicated 120V/20A Appliance or Garage Circuit", 2.25, 90.0),
        ServiceItem(20, "Circuitos nuevos", "Circuito dedicado 240V/30A para secadora o calentador de agua", "Dedicated 240V/30A Dryer or Water Heater Circuit", 2.75, 150.0),
        ServiceItem(21, "Circuitos nuevos", "Circuito dedicado 240V/50A para estufa o horno eléctrico", "Dedicated 240V/50A Range or Oven Circuit", 3.0, 175.0),
        ServiceItem(22, "Circuitos nuevos", "Instalación de cargador Nivel 2 para vehículo eléctrico (EV 240V/50A)", "Level 2 EV Charger Circuit & NEMA 14-50 / Hardwire", 3.5, 240.0),
        ServiceItem(23, "Circuitos nuevos", "Circuito dedicado 240V/20A–30A y desconectador para Mini-Split", "Dedicated 240V/20A–30A Mini-Split Circuit & Disconnect", 3.0, 165.0),
        ServiceItem(24, "Circuitos nuevos", "Circuito dedicado para bomba de piscina / spa con protección GFCI", "Pool / Spa Pump Dedicated GFCI Circuit", 3.5, 195.0),
        ServiceItem(25, "Circuitos nuevos", "Circuito dedicado para triturador de basura (Garbage Disposal)", "Garbage Disposal Dedicated Circuit & Switch", 2.0, 75.0),
        ServiceItem(26, "Circuitos nuevos", "Canalización exterior en tubería EMT / PVC (hasta 50 pies)", "Exterior EMT / PVC Conduit Run (up to 50 ft)", 3.0, 120.0),
        ServiceItem(27, "Circuitos nuevos", "Circuito dedicado para jacuzzi / hot tub con desconectador GFCI", "Hot Tub / Spa 240V GFCI Disconnect & Circuit", 4.5, 290.0),
        ServiceItem(46, "Circuitos nuevos", "Circuito dedicado 120V/20A y desconectador para Mini-Split (9k–12k BTU)", "Dedicated 120V/20A Mini-Split Circuit & Disconnect (9k–12k BTU)", 2.5, 135.0),
        ServiceItem(47, "Circuitos nuevos", "Instalación o reemplazo de caja desconectadora (Disconnect) y whip para Mini-Split / A/C", "AC / Mini-Split Disconnect Box & Liquidtight Whip Replacement", 1.5, 75.0),
        ServiceItem(48, "Circuitos nuevos", "Alimentación eléctrica para sistema Mini-Split Multi-Zona (2–4 zonas)", "Multi-Zone Mini-Split Condenser Power Supply & Disconnect", 3.5, 210.0),
        ServiceItem(49, "Circuitos nuevos", "Circuito dedicado para manejadora de aire (Air Handler) y heat strip", "Air Handler & Heat Strip Dedicated Circuit(s)", 3.5, 220.0),
        ServiceItem(50, "Circuitos nuevos", "Circuito dedicado 120V/240V para aire acondicionado de ventana / pared (PTAC)", "Window / Wall AC (PTAC) Dedicated Receptacle & Circuit", 2.0, 90.0),
        ServiceItem(51, "Circuitos nuevos", "Circuito dedicado 240V para calentador sin tanque (Tankless Water Heater)", "Dedicated 240V Tankless Water Heater Circuit(s)", 4.0, 230.0),
        ServiceItem(52, "Circuitos nuevos", "Circuito dedicado para motor de portón eléctrico (Gate Motor)", "Automatic Driveway Gate Motor Power Circuit", 3.5, 185.0),
        ServiceItem(53, "Circuitos nuevos", "Zanja y cableado subterráneo UF-B / PVC (hasta 50 pies)", "Underground Trenching & UF-B / PVC Feeder (up to 50 ft)", 4.5, 190.0),

        // === 4. Paneles, protección y diagnóstico (Originales + Nuevos) ===
        ServiceItem(28, "Paneles, protección y diagnóstico", "Actualización de panel eléctrico principal a 200A", "200A Main Service Panel Upgrade", 6.5, 480.0),
        ServiceItem(29, "Paneles, protección y diagnóstico", "Instalación de subpanel eléctrico de 60A–100A", "Install 60A–100A Subpanel", 4.0, 260.0),
        ServiceItem(30, "Paneles, protección y diagnóstico", "Reemplazo de tablero antiguo (Federal Pacific / Zinsco)", "Obsolete Panel Replacement (FPE / Zinsco)", 7.0, 520.0),
        ServiceItem(31, "Paneles, protección y diagnóstico", "Reemplazo de breaker estándar o doble polo", "Standard Single/Double-Pole Breaker Replacement", 1.0, 35.0),
        ServiceItem(32, "Paneles, protección y diagnóstico", "Reemplazo de breaker combinado AFCI / GFCI", "Dual-Function AFCI/GFCI Breaker Replacement", 1.0, 68.0),
        ServiceItem(33, "Paneles, protección y diagnóstico", "Instalación de supresor de sobretensiones para toda la casa", "Whole-House Surge Protector Installation", 1.5, 135.0),
        ServiceItem(34, "Paneles, protección y diagnóstico", "Instalación de transfer switch / interlock para generador", "Generator Interlock Kit & 50A Power Inlet", 3.5, 240.0),
        ServiceItem(35, "Paneles, protección y diagnóstico", "Actualización de sistema de puesta a tierra (Grounding & Bonding)", "Grounding Electrode & Bonding System Upgrade", 2.5, 110.0),
        ServiceItem(36, "Paneles, protección y diagnóstico", "Diagnóstico y reparación de falla eléctrica / cortocircuito", "Electrical Troubleshooting & Short-Circuit Repair", 1.5, 25.0),
        ServiceItem(54, "Paneles, protección y diagnóstico", "Protector contra sobretensiones (Surge Protector) para condensadora A/C o Mini-Split", "HVAC / Mini-Split Surge Protective Device (SPD) Installation", 1.0, 85.0),
        ServiceItem(55, "Paneles, protección y diagnóstico", "Reemplazo de breaker dedicado y cableado para condensadora A/C", "AC Condenser Dedicated Breaker & Feeder Wire Replacement", 2.0, 95.0),
        ServiceItem(56, "Paneles, protección y diagnóstico", "Diagnóstico eléctrico de Mini-Split / A/C (voltaje, desconectador, breaker)", "AC / Mini-Split Electrical Troubleshooting (Voltage, Disconnect, Breaker)", 1.5, 25.0),
        ServiceItem(57, "Paneles, protección y diagnóstico", "Actualización de servicio pesado a 400A (Doble panel)", "400A Heavy-Up Service Upgrade (Dual Panel)", 10.0, 950.0),
        ServiceItem(58, "Paneles, protección y diagnóstico", "Reemplazo de interruptor principal (Main Breaker)", "Main Breaker Replacement", 2.0, 145.0),
        ServiceItem(59, "Paneles, protección y diagnóstico", "Transfer switch manual completo y caja de entrada para generador", "Manual Generator Transfer Switch & Inlet Box", 4.5, 340.0),
        ServiceItem(60, "Paneles, protección y diagnóstico", "Reemplazo de base de medidor (Meter Can) y acometida", "Meter Base & Service Entrance Riser Replacement", 5.0, 380.0),
        ServiceItem(61, "Paneles, protección y diagnóstico", "Inspección eléctrica de 4 puntos para seguro (4-Point Inspection)", "4-Point Electrical Safety & Insurance Inspection", 1.5, 15.0)
    )
}
