package com.hssdade.light.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.hssdade.light.R
import com.hssdade.light.data.QuoteEntity
import com.hssdade.light.data.ServiceCatalog
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GeneratedPdf(
    val fileName: String,
    val uri: Uri?
)

private data class PdfLineItem(
    val index: Int,
    val title: String,
    val subtitle: String,
    val qty: Int,
    val unitPrice: Double,
    val subtotal: Double
)

object QuotePdf {

    private fun t(lang: String, en: String, es: String): String = if (lang == "es") es else en

    fun create(
        context: Context,
        quote: QuoteEntity,
        companyLicense: String = "",
        companyPhone: String = "",
        brandName: String = "ApexBid",
        languageCode: String = "en"
    ): GeneratedPdf {
        val doc = PdfDocument()
        val pageWidth = 612
        val pageHeight = 792
        val margin = 38f
        val contentWidth = pageWidth - margin * 2

        val primaryColor = Color.parseColor("#12394B")
        val accentColor = Color.parseColor("#2A8573")
        val softBg = Color.parseColor("#F4F8FA")
        val zebraBg = Color.parseColor("#F9FBFD")
        val borderColor = Color.parseColor("#D4E1E8")
        val textDark = Color.parseColor("#1E293B")
        val textMuted = Color.parseColor("#5A6B7C")

        val brandTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val brandSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D7ECF2")
            textSize = 9.5f
        }
        val heroBannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textDark
            textSize = 9.5f
        }
        val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textDark
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 8.5f
        }
        val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = softBg
            style = Paint.Style.FILL
        }
        val zebraFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = zebraBg
            style = Paint.Style.FILL
        }
        val boxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        val accentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.FILL
        }

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val isInvoice = quote.status == "Facturada"
        val cleanBrand = brandName.ifBlank { "ApexBid" }
        val dateStr = SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(quote.createdAt))
        val money = NumberFormat.getCurrencyInstance(Locale.US)
        val docTitle = if (isInvoice) t(languageCode, "INVOICE", "FACTURA") else t(languageCode, "ESTIMATE", "COTIZACIÓN")

        // 1. Encabezado Corporativo Superior
        val headerHeight = 88f
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), headerHeight, headerBgPaint)
        canvas.drawRect(0f, headerHeight, pageWidth.toFloat(), headerHeight + 5f, accentBarPaint)

        val logoBitmap = loadBrandLogo(context)
        var leftX = margin
        if (logoBitmap != null) {
            val logoSize = 48f
            val logoTop = (headerHeight - logoSize) / 2f
            val dest = RectF(margin, logoTop, margin + logoSize, logoTop + logoSize)
            canvas.drawBitmap(logoBitmap, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            leftX = margin + logoSize + 12f
        }

        canvas.drawText(docTitle, leftX, 52f, heroBannerPaint)

        val rightBrandPaint = Paint(brandTitlePaint).apply { textAlign = Paint.Align.RIGHT }
        val rightMetaPaint = Paint(brandSubPaint).apply { textAlign = Paint.Align.RIGHT }

        canvas.drawText(cleanBrand, pageWidth - margin, 34f, rightBrandPaint)
        canvas.drawText(
            t(languageCode, "Residential & Commercial Electrical Services", "Servicios Eléctricos Residenciales y Comerciales"),
            pageWidth - margin,
            50f,
            rightMetaPaint
        )
        val metaParts = mutableListOf<String>()
        if (companyLicense.isNotBlank()) metaParts.add("Lic: $companyLicense")
        if (companyPhone.isNotBlank()) metaParts.add("Tel: $companyPhone")
        if (metaParts.isNotEmpty()) {
            canvas.drawText(metaParts.joinToString("  ·  "), pageWidth - margin, 66f, rightMetaPaint)
        }

        var y = headerHeight + 20f

        // 2. Bloque Superior: Información del Cliente (Izquierda) + Metadatos del Documento (Derecha)
        val formattedAddress = formatSmartAddress(quote.clientAddress)
        val clientRows = mutableListOf<Pair<String, String>>()
        clientRows.add(t(languageCode, "Customer", "Cliente") to quote.clientName)
        if (quote.clientPhone.isNotBlank()) clientRows.add(t(languageCode, "Phone", "Teléfono") to quote.clientPhone)
        if (quote.clientEmail.isNotBlank()) clientRows.add("Email" to quote.clientEmail)
        if (formattedAddress.isNotBlank()) clientRows.add(t(languageCode, "Job Address", "Dirección de Obra") to formattedAddress)

        val infoBoxH = 24f + clientRows.size * 15f
        val leftCardW = contentWidth * 0.65f
        val rightCardW = contentWidth * 0.32f
        val rightCardX = pageWidth - margin - rightCardW

        val leftRect = RectF(margin, y, margin + leftCardW, y + infoBoxH)
        canvas.drawRoundRect(leftRect, 6f, 6f, boxFillPaint)
        canvas.drawRoundRect(leftRect, 6f, 6f, boxStrokePaint)

        canvas.drawText(t(languageCode, "CUSTOMER INFORMATION", "INFORMACIÓN DEL CLIENTE"), margin + 10f, y + 16f, sectionPaint)
        var cy = y + 32f
        clientRows.forEach { (lbl, valStr) ->
            canvas.drawText("$lbl:", margin + 10f, cy, boldBodyPaint)
            canvas.drawText(valStr.take(36), margin + 98f, cy, bodyPaint)
            cy += 15f
        }

        val rightRect = RectF(rightCardX, y, pageWidth - margin, y + infoBoxH)
        canvas.drawRoundRect(rightRect, 6f, 6f, boxFillPaint)
        canvas.drawRoundRect(rightRect, 6f, 6f, boxStrokePaint)

        val rightAlignBold = Paint(sectionPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightAlignText = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightAlignAccent = Paint(boldBodyPaint).apply {
            color = accentColor
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawText(docTitle, pageWidth - margin - 10f, y + 18f, rightAlignBold)
        canvas.drawText("#${quote.id.toString().padStart(4, '0')}", pageWidth - margin - 10f, y + 34f, rightAlignAccent)
        canvas.drawText("${t(languageCode, "Date", "Fecha")}: $dateStr", pageWidth - margin - 10f, y + 50f, rightAlignText)
        if (infoBoxH >= 75f) {
            canvas.drawText(
                t(languageCode, "Valid: 15 Days", "Validez: 15 Días"),
                pageWidth - margin - 10f,
                y + 66f,
                Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT }
            )
        }

        y += infoBoxH + 16f

        // 3. Tabla Profesional de Partidas (Servicio / Descripción | Cant. | Precio Unit. | Subtotal)
        val lineItems = buildBreakdownItems(quote, languageCode)
        val tableHeaderH = 24f
        val thRect = RectF(margin, y, pageWidth - margin, y + tableHeaderH)
        canvas.drawRoundRect(thRect, 4f, 4f, headerBgPaint)

        val colNumX = margin + 8f
        val colDescX = margin + 28f
        val colQtyCenterX = pageWidth - margin - 165f
        val colUnitRightX = pageWidth - margin - 82f
        val colSubRightX = pageWidth - margin - 10f

        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val thCenter = Paint(thPaint).apply { textAlign = Paint.Align.CENTER }
        val thRight = Paint(thPaint).apply { textAlign = Paint.Align.RIGHT }

        canvas.drawText("#", colNumX, y + 15.5f, thPaint)
        canvas.drawText(t(languageCode, "SERVICE / DESCRIPTION", "SERVICIO / DESCRIPCIÓN"), colDescX, y + 15.5f, thPaint)
        canvas.drawText(t(languageCode, "QTY", "CANT."), colQtyCenterX, y + 15.5f, thCenter)
        canvas.drawText(t(languageCode, "UNIT PRICE", "PRECIO UNIT."), colUnitRightX, y + 15.5f, thRight)
        canvas.drawText(t(languageCode, "SUBTOTAL", "SUBTOTAL"), colSubRightX, y + 15.5f, thRight)
        y += tableHeaderH

        val tdCenter = Paint(bodyPaint).apply { textAlign = Paint.Align.CENTER }
        val tdRight = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val tdRightBold = Paint(boldBodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val maxDescWidth = (colQtyCenterX - colDescX - 24f).coerceAtLeast(160f)

        lineItems.forEachIndexed { idx, item ->
            val titleLines = wrapText(item.title, boldBodyPaint, maxDescWidth).take(2)
            val subLines = if (item.subtitle.isNotBlank()) wrapText(item.subtitle, mutedPaint, maxDescWidth).take(2) else emptyList()
            val rowH = (14f + titleLines.size * 12.5f + subLines.size * 11f).coerceAtLeast(30f)

            val rowRect = RectF(margin, y, pageWidth - margin, y + rowH)
            if (idx % 2 == 1) {
                canvas.drawRect(rowRect, zebraFillPaint)
            }
            canvas.drawRect(rowRect, boxStrokePaint)

            val midY = y + (rowH / 2f) + 3.5f
            canvas.drawText(item.index.toString(), colNumX, midY, boldBodyPaint)

            var textY = y + 13.5f
            titleLines.forEach { line ->
                canvas.drawText(line, colDescX, textY, boldBodyPaint)
                textY += 12.5f
            }
            subLines.forEach { sLine ->
                canvas.drawText(sLine, colDescX, textY, mutedPaint)
                textY += 11f
            }

            canvas.drawText(item.qty.toString(), colQtyCenterX, midY, tdCenter)
            canvas.drawText(money.format(item.unitPrice), colUnitRightX, midY, tdRight)
            canvas.drawText(money.format(item.subtotal), colSubRightX, midY, tdRightBold)

            y += rowH
        }

        y += 14f

        // 4. Alcance del Trabajo y Garantía NEC
        val activeDescription = if (languageCode == "es") {
            quote.descriptionEs.ifBlank { quote.descriptionEn }
        } else {
            quote.descriptionEn.ifBlank { quote.descriptionEs }
        }
        val cleanScope = activeDescription
            .replace("Descripción General del Trabajo Eléctrico:", "")
            .replace("General Scope of Electrical Work:", "")
            .trim()

        if (cleanScope.isNotBlank() && y < 530f) {
            canvas.drawText(t(languageCode, "SCOPE OF WORK & WARRANTY", "ALCANCE DEL TRABAJO Y GARANTÍA"), margin, y, sectionPaint)
            y += 13f
            val scopeLines = wrapText(cleanScope, bodyPaint, contentWidth).take(8)
            scopeLines.forEach { line ->
                canvas.drawText(line, margin, y, bodyPaint)
                y += 12.5f
            }
            y += 6f
        }

        // 5. Evidencia Fotográfica (si existe y hay espacio disponible)
        val photoPaths = quote.photoUris.split("|").filter { it.isNotBlank() && File(it).exists() }.take(3)
        if (photoPaths.isNotEmpty() && y < 525f) {
            canvas.drawText(t(languageCode, "JOB SITE PHOTOS", "EVIDENCIA FOTOGRÁFICA"), margin, y, sectionPaint)
            y += 8f
            val thumbW = 84f
            val thumbH = 60f
            photoPaths.forEachIndexed { idx, path ->
                val bmp = BitmapFactory.decodeFile(path)
                if (bmp != null) {
                    val left = margin + idx * (thumbW + 10f)
                    val dest = RectF(left, y, left + thumbW, y + thumbH)
                    canvas.drawBitmap(bmp, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    canvas.drawRect(dest, boxStrokePaint)
                }
            }
            y += thumbH + 12f
        }

        // 6. Bloque Inferior Dividido: Términos y Notas (Izquierda) + Caja de Totales (Derecha)
        val summaryY = maxOf(y + 4f, 575f).coerceAtMost(615f)
        val leftNotesW = contentWidth * 0.53f
        val totalsBoxX = margin + leftNotesW + 14f
        val totalsBoxW = pageWidth - margin - totalsBoxX

        // Columna Izquierda: Notas y Condiciones Comerciales
        var ny = summaryY + 12f
        canvas.drawText(t(languageCode, "NOTES & TERMS", "NOTAS Y CONDICIONES"), margin, ny, sectionPaint)
        ny += 14f

        val termsList = mutableListOf<String>()
        if (quote.observations.isNotBlank()) {
            termsList.add("${t(languageCode, "Note", "Nota")}: ${quote.observations.trim()}")
        }
        termsList.add(t(languageCode, "• Estimate valid for 15 calendar days.", "• Validez de la cotización: 15 días calendario."))
        termsList.add(t(languageCode, "• Payment: 50% deposit / 50% upon completion.", "• Forma de pago: 50% anticipo / 50% al finalizar."))
        termsList.add(t(languageCode, "• 1-Year workmanship warranty per NEC code.", "• 1 año de garantía en mano de obra según normas NEC."))

        termsList.forEach { term ->
            wrapText(term, mutedPaint, leftNotesW).take(2).forEach { tLine ->
                canvas.drawText(tLine, margin, ny, mutedPaint)
                ny += 11.5f
            }
        }

        // Columna Derecha: Subtotal, Impuestos/Otros y Barra Total Verde
        val totalsCardH = 78f
        val totalsRect = RectF(totalsBoxX, summaryY, pageWidth - margin, summaryY + totalsCardH)
        canvas.drawRoundRect(totalsRect, 6f, 6f, boxFillPaint)
        canvas.drawRoundRect(totalsRect, 6f, 6f, boxStrokePaint)

        val tLabelPaint = Paint(boldBodyPaint).apply { textSize = 9.5f }
        val tValRight = Paint(boldBodyPaint).apply { textSize = 9.5f; textAlign = Paint.Align.RIGHT }

        canvas.drawText(t(languageCode, "SUBTOTAL:", "SUBTOTAL:"), totalsBoxX + 10f, summaryY + 19f, tLabelPaint)
        canvas.drawText(money.format(quote.total), pageWidth - margin - 10f, summaryY + 19f, tValRight)

        canvas.drawText(t(languageCode, "TAX / OTHER:", "IMPUESTOS / OTROS:"), totalsBoxX + 10f, summaryY + 36f, mutedPaint)
        canvas.drawText(money.format(0.0), pageWidth - margin - 10f, summaryY + 36f, Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT })

        val totalBannerRect = RectF(totalsBoxX, summaryY + 46f, pageWidth - margin, summaryY + totalsCardH)
        canvas.drawRoundRect(totalBannerRect, 6f, 6f, accentBarPaint)

        val totalWhiteLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val totalWhiteVal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            if (isInvoice) t(languageCode, "TOTAL DUE:", "TOTAL FACTURA:") else t(languageCode, "TOTAL:", "TOTAL:"),
            totalsBoxX + 10f,
            summaryY + 67f,
            totalWhiteLabel
        )
        canvas.drawText(money.format(quote.total), pageWidth - margin - 10f, summaryY + 68f, totalWhiteVal)

        // 7. Línea de Aceptación / Firma del Cliente (siempre visible, con firma digital si existe)
        val sigLineY = 742f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            strokeWidth = 1f
        }
        canvas.drawText(
            t(languageCode, "Accepted by Customer:", "Aceptado por el Cliente:"),
            margin,
            sigLineY,
            boldBodyPaint
        )
        val sigStartX = margin + 118f
        val sigEndX = margin + 320f
        canvas.drawLine(sigStartX, sigLineY, sigEndX, sigLineY, linePaint)

        if (quote.signatureData.isNotBlank()) {
            val sigBounds = RectF(sigStartX + 6f, sigLineY - 34f, sigEndX - 6f, sigLineY - 2f)
            drawSignatureVector(canvas, quote.signatureData, sigBounds)
        }

        val dateLabelX = sigEndX + 22f
        canvas.drawText(t(languageCode, "Date:", "Fecha:"), dateLabelX, sigLineY, boldBodyPaint)
        canvas.drawLine(dateLabelX + 36f, sigLineY, pageWidth - margin, sigLineY, linePaint)
        if (quote.signedAt > 0L) {
            val signedDateStr = SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(quote.signedAt))
            canvas.drawText(signedDateStr, dateLabelX + 42f, sigLineY - 3f, bodyPaint)
        }

        // 8. Pie de página
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            t(
                languageCode,
                "All electrical work complies with NEC standards · Thank you for choosing $cleanBrand",
                "Todos los trabajos cumplen con las normas NEC · Gracias por confiar en $cleanBrand"
            ),
            pageWidth / 2f,
            pageHeight - 18f,
            footerPaint
        )

        doc.finishPage(page)

        val prefix = if (isInvoice) {
            t(languageCode, "Invoice", "Factura")
        } else {
            t(languageCode, "Estimate", "Cotizacion")
        }
        val safeClient = quote.clientName.replace(Regex("[^A-Za-z0-9_-]"), "_").take(22)
        val fileName = "${prefix}_${safeClient}_${System.currentTimeMillis() % 100000}.pdf"

        val savedUri = savePdfDocument(context, doc, fileName)
        doc.close()
        return GeneratedPdf(fileName = fileName, uri = savedUri)
    }

    private fun buildBreakdownItems(quote: QuoteEntity, lang: String): List<PdfLineItem> {
        val rawParts = quote.serviceName.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }
        if (rawParts.isEmpty()) {
            return listOf(
                PdfLineItem(
                    index = 1,
                    title = t(lang, "Electrical Installation & Service", "Servicio e Instalación Eléctrica"),
                    subtitle = "",
                    qty = quote.quantity.toInt().coerceAtLeast(1),
                    unitPrice = quote.total / quote.quantity.coerceAtLeast(1.0),
                    subtotal = quote.total
                )
            )
        }

        data class ParsedPart(
            val title: String,
            val subtitle: String,
            val qty: Int,
            val weight: Double
        )

        val parsedList = rawParts.map { part ->
            val qtyMatch = Regex("""^\(x(\d+)\)\s*(.+)$""").find(part)
            val itemQty = qtyMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val rawNames = (qtyMatch?.groupValues?.get(2) ?: part).split("|").map { it.trim() }
            val displayTitle = if (rawNames.size >= 2) {
                if (lang == "es") rawNames[1] else rawNames[0]
            } else {
                rawNames.firstOrNull().orEmpty()
            }

            val catalogMatch = ServiceCatalog.all.find { svc ->
                rawNames.any { n ->
                    svc.nameEn.equals(n, ignoreCase = true) || svc.nameEs.equals(n, ignoreCase = true)
                }
            }
            val baseWeight = if (catalogMatch != null) {
                (catalogMatch.hours * 90.5 + catalogMatch.materials) * itemQty
            } else {
                100.0 * itemQty
            }
            val sub = if (catalogMatch != null) {
                t(lang, "NEC code-compliant installation & UL materials", "Instalación conforme al código NEC y materiales UL")
            } else ""

            ParsedPart(title = displayTitle, subtitle = sub, qty = itemQty, weight = baseWeight.coerceAtLeast(1.0))
        }

        val totalWeight = parsedList.sumOf { it.weight }.coerceAtLeast(1.0)
        var accumulated = 0.0

        return parsedList.mapIndexed { idx, p ->
            val lineTotal = if (idx == parsedList.lastIndex) {
                (quote.total - accumulated).coerceAtLeast(0.0)
            } else {
                val share = Math.round((quote.total * (p.weight / totalWeight)) * 100.0) / 100.0
                accumulated += share
                share
            }
            val unitPrice = if (p.qty > 0) lineTotal / p.qty else lineTotal
            PdfLineItem(
                index = idx + 1,
                title = p.title,
                subtitle = p.subtitle,
                qty = p.qty,
                unitPrice = unitPrice,
                subtotal = lineTotal
            )
        }
    }

    private fun formatSmartAddress(raw: String): String {
        val clean = raw.trim()
        if (clean.isEmpty()) return ""
        val directions = setOf("nw", "ne", "sw", "se", "n", "s", "e", "w", "fl", "us", "apt", "ste")
        return clean.split(Regex("\\s+")).joinToString(" ") { word ->
            val lower = word.lowercase()
            when {
                lower in directions -> word.uppercase()
                word.any { it.isDigit() } -> word.uppercase()
                else -> lower.replaceFirstChar { it.uppercase() }
            }
        }
    }

    private fun loadBrandLogo(context: Context): Bitmap? {
        return try {
            val customFile = File(context.filesDir, "company_logo.png")
            if (customFile.exists()) {
                BitmapFactory.decodeFile(customFile.absolutePath)
            } else {
                BitmapFactory.decodeResource(context.resources, R.drawable.apexbid_icon)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun drawSignatureVector(canvas: Canvas, serialized: String, bounds: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#12394B")
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val pad = 4f
        val drawW = (bounds.width() - pad * 2).coerceAtLeast(1f)
        val drawH = (bounds.height() - pad * 2).coerceAtLeast(1f)

        serialized.split(";").forEach { strokeStr ->
            val pts = strokeStr.split("|").mapNotNull { pairStr ->
                val xy = pairStr.split(",")
                if (xy.size == 2) {
                    val nx = xy[0].toFloatOrNull()
                    val ny = xy[1].toFloatOrNull()
                    if (nx != null && ny != null) {
                        (bounds.left + pad + nx * drawW) to (bounds.top + pad + ny * drawH)
                    } else null
                } else null
            }
            if (pts.size >= 2) {
                val path = Path().apply {
                    moveTo(pts[0].first, pts[0].second)
                    for (i in 1 until pts.size) lineTo(pts[i].first, pts[i].second)
                }
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        text.lines().forEach { paragraph ->
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) return@forEach
            val words = trimmed.split(Regex("\\s+"))
            var currentLine = StringBuilder()
            for (word in words) {
                val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    currentLine = StringBuilder(candidate)
                } else {
                    if (currentLine.isNotEmpty()) result.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                }
            }
            if (currentLine.isNotEmpty()) result.add(currentLine.toString())
        }
        return result
    }

    private fun savePdfDocument(context: Context, doc: PdfDocument, fileName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ApexBid")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out -> doc.writeTo(out) }
                }
                uri
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ApexBid").apply { mkdirs() }
                val file = File(dir, fileName)
                FileOutputStream(file).use { out -> doc.writeTo(out) }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        } catch (_: Exception) {
            val cacheFile = File(context.cacheDir, fileName)
            FileOutputStream(cacheFile).use { out -> doc.writeTo(out) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
        }
    }
}
