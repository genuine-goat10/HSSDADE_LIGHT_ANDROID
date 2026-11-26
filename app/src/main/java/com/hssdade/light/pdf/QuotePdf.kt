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
        val margin = 42f
        val contentWidth = pageWidth - margin * 2

        val primaryColor = Color.parseColor("#143C4E")
        val accentColor = Color.parseColor("#2D8A78")
        val softBg = Color.parseColor("#F4F8FA")
        val borderColor = Color.parseColor("#D5E2E8")
        val textDark = Color.parseColor("#1E293B")
        val textMuted = Color.parseColor("#5A6B7C")

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9EEF3")
            textSize = 10.5f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textDark
            textSize = 10.5f
        }
        val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textDark
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 9.5f
        }
        val totalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val totalValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = softBg
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

        // 1. Encabezado corporativo
        val headerHeight = 96f
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), headerHeight, headerBgPaint)
        canvas.drawRect(0f, headerHeight, pageWidth.toFloat(), headerHeight + 5f, accentBarPaint)

        val logoBitmap = loadBrandLogo(context)
        var textStartX = margin
        if (logoBitmap != null) {
            val logoSize = 58f
            val logoTop = (headerHeight - logoSize) / 2f
            val dest = RectF(margin, logoTop, margin + logoSize, logoTop + logoSize)
            canvas.drawBitmap(logoBitmap, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            textStartX = margin + logoSize + 14f
        }

        canvas.drawText(cleanBrand, textStartX, 40f, titlePaint)
        canvas.drawText(
            t(languageCode, "Residential & Commercial Electrical Services", "Servicios Eléctricos Residenciales y Comerciales"),
            textStartX,
            58f,
            subtitlePaint
        )

        val metaParts = mutableListOf<String>()
        if (companyLicense.isNotBlank()) metaParts.add("Lic: $companyLicense")
        if (companyPhone.isNotBlank()) metaParts.add("Tel: $companyPhone")
        if (metaParts.isNotEmpty()) {
            canvas.drawText(metaParts.joinToString("  ·  "), textStartX, 75f, subtitlePaint)
        }

        val docTitle = if (isInvoice) {
            t(languageCode, "INVOICE", "FACTURA")
        } else {
            t(languageCode, "ESTIMATE", "COTIZACIÓN")
        }
        val docBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val docDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9EEF3")
            textSize = 10f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(docTitle, pageWidth - margin, 42f, docBadgePaint)
        canvas.drawText("#${quote.id.toString().padStart(4, '0')}", pageWidth - margin, 59f, docDatePaint)
        canvas.drawText("${t(languageCode, "Date", "Fecha")}: $dateStr", pageWidth - margin, 75f, docDatePaint)

        var y = headerHeight + 24f

        // 2. Tarjeta de Información del Cliente
        val clientLines = mutableListOf<Pair<String, String>>()
        clientLines.add(t(languageCode, "Customer", "Cliente") to quote.clientName)
        if (quote.clientPhone.isNotBlank()) clientLines.add(t(languageCode, "Phone", "Teléfono") to quote.clientPhone)
        if (quote.clientEmail.isNotBlank()) clientLines.add("Email" to quote.clientEmail)
        if (quote.clientAddress.isNotBlank()) clientLines.add(t(languageCode, "Job Address", "Dirección de Obra") to quote.clientAddress)

        val clientBoxHeight = 26f + clientLines.size * 16f
        val clientRect = RectF(margin, y, pageWidth - margin, y + clientBoxHeight)
        canvas.drawRoundRect(clientRect, 8f, 8f, boxFillPaint)
        canvas.drawRoundRect(clientRect, 8f, 8f, boxStrokePaint)

        canvas.drawText(t(languageCode, "CUSTOMER INFORMATION", "INFORMACIÓN DEL CLIENTE"), margin + 12f, y + 18f, sectionPaint)
        var cy = y + 35f
        clientLines.forEach { (label, value) ->
            canvas.drawText("$label:", margin + 12f, cy, boldBodyPaint)
            canvas.drawText(value, margin + 115f, cy, bodyPaint)
            cy += 16f
        }
        y += clientBoxHeight + 16f

        // 3. Servicios Incluidos
        canvas.drawText(t(languageCode, "INCLUDED ELECTRICAL SERVICES", "SERVICIOS ELÉCTRICOS INCLUIDOS"), margin, y, sectionPaint)
        y += 14f
        val serviceDisplay = formatServiceTitleForLang(quote.serviceName, languageCode)
        val serviceWrapped = wrapText(serviceDisplay, boldBodyPaint, contentWidth - 20f)
        val serviceBoxH = 14f + serviceWrapped.size * 14f
        val serviceRect = RectF(margin, y, pageWidth - margin, y + serviceBoxH)
        canvas.drawRoundRect(serviceRect, 6f, 6f, boxStrokePaint)
        var sy = y + 16f
        serviceWrapped.forEach { line ->
            canvas.drawText(line, margin + 10f, sy, boldBodyPaint)
            sy += 14f
        }
        y += serviceBoxH + 16f

        // 4. Descripción del Alcance (en el idioma seleccionado)
        val activeDescription = if (languageCode == "es") {
            quote.descriptionEs.ifBlank { quote.descriptionEn }
        } else {
            quote.descriptionEn.ifBlank { quote.descriptionEs }
        }
        if (activeDescription.isNotBlank()) {
            canvas.drawText(t(languageCode, "SCOPE OF WORK", "DESCRIPCIÓN DEL ALCANCE"), margin, y, sectionPaint)
            y += 14f
            val descLines = wrapText(activeDescription, bodyPaint, contentWidth)
            descLines.take(14).forEach { line ->
                canvas.drawText(line, margin, y, bodyPaint)
                y += 13.5f
            }
            y += 8f
        }

        // 5. Observaciones / Notas
        if (quote.observations.isNotBlank()) {
            canvas.drawText(t(languageCode, "NOTES & OBSERVATIONS", "OBSERVACIONES Y NOTAS"), margin, y, sectionPaint)
            y += 14f
            val noteLines = wrapText(quote.observations, mutedPaint, contentWidth)
            noteLines.take(4).forEach { line ->
                canvas.drawText(line, margin, y, mutedPaint)
                y += 13f
            }
            y += 8f
        }

        // 6. Evidencia Fotográfica (si cabe en la página)
        val photoPaths = quote.photoUris.split("|").filter { it.isNotBlank() && File(it).exists() }.take(3)
        if (photoPaths.isNotEmpty() && y < 565f) {
            canvas.drawText(t(languageCode, "JOB SITE PHOTOS", "EVIDENCIA FOTOGRÁFICA"), margin, y, sectionPaint)
            y += 10f
            val thumbW = 92f
            val thumbH = 68f
            photoPaths.forEachIndexed { idx, path ->
                val bmp = BitmapFactory.decodeFile(path)
                if (bmp != null) {
                    val left = margin + idx * (thumbW + 10f)
                    val dest = RectF(left, y, left + thumbW, y + thumbH)
                    canvas.drawBitmap(bmp, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    canvas.drawRect(dest, boxStrokePaint)
                }
            }
            y += thumbH + 14f
        }

        // 7. Caja de Total
        val totalBoxY = maxOf(y + 4f, 610f).coerceAtMost(645f)
        val totalBoxH = 54f
        val totalRect = RectF(margin, totalBoxY, pageWidth - margin, totalBoxY + totalBoxH)
        canvas.drawRoundRect(totalRect, 8f, 8f, boxFillPaint)
        canvas.drawRoundRect(totalRect, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        })

        val qtyFormatted = if (quote.quantity % 1.0 == 0.0) quote.quantity.toInt().toString() else quote.quantity.toString()
        canvas.drawText(
            "${t(languageCode, "Quantity", "Cantidad")}: $qtyFormatted",
            margin + 14f,
            totalBoxY + 22f,
            mutedPaint
        )
        canvas.drawText(
            if (isInvoice) t(languageCode, "TOTAL DUE", "TOTAL A PAGAR") else t(languageCode, "ESTIMATE TOTAL", "TOTAL COTIZACIÓN"),
            margin + 14f,
            totalBoxY + 41f,
            totalLabelPaint
        )

        val rightTotalPaint = Paint(totalValuePaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(money.format(quote.total), pageWidth - margin - 14f, totalBoxY + 37f, rightTotalPaint)

        // 8. Firma del Cliente (si está firmada)
        val sigY = totalBoxY + totalBoxH + 14f
        if (quote.signatureData.isNotBlank() && sigY < 735f) {
            val sigBox = RectF(margin, sigY, margin + 210f, sigY + 42f)
            canvas.drawRoundRect(sigBox, 6f, 6f, boxStrokePaint)
            drawSignatureVector(canvas, quote.signatureData, sigBox)
            val signedDate = if (quote.signedAt > 0L) {
                SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US).format(Date(quote.signedAt))
            } else ""
            canvas.drawText(
                "${t(languageCode, "Customer Signature", "Firma del Cliente")} ($signedDate)",
                margin + 222f,
                sigY + 25f,
                mutedPaint
            )
        }

        // 9. Pie de página
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
            pageHeight - 20f,
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

    private fun formatServiceTitleForLang(rawServiceName: String, lang: String): String {
        return rawServiceName.split(" / ").joinToString(" · ") { part ->
            val pieces = part.split("|").map { it.trim() }
            if (pieces.size >= 2) {
                if (lang == "es") pieces[1] else pieces[0]
            } else {
                part.trim()
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
            color = Color.parseColor("#143C4E")
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val pad = 6f
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
            if (trimmed.isEmpty()) {
                result.add("")
                return@forEach
            }
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
