package com.hssdade.light.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.hssdade.light.R
import com.hssdade.light.data.AppointmentEntity
import com.hssdade.light.data.QuoteEntity
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BusinessReportPdf {

    private fun t(lang: String, en: String, es: String): String = if (lang == "es") es else en

    fun create(
        context: Context,
        periodLabel: String,
        rangeSubtitle: String,
        quotes: List<QuoteEntity>,
        appointments: List<AppointmentEntity>,
        brandName: String = "ApexBid",
        companyLicense: String = "",
        companyPhone: String = "",
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
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9EEF3")
            textSize = 10f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textSize = 11.5f
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
            textSize = 9f
        }
        val kpiValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textSize = 14f
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

        val cleanBrand = brandName.ifBlank { "ApexBid" }
        val money = NumberFormat.getCurrencyInstance(Locale.US)
        val dateFmt = SimpleDateFormat("MM/dd/yyyy", Locale.US)
        val generatedDate = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US).format(Date())

        // 1. Encabezado corporativo
        val headerHeight = 90f
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), headerHeight, headerBgPaint)
        canvas.drawRect(0f, headerHeight, pageWidth.toFloat(), headerHeight + 5f, accentBarPaint)

        val logoBitmap = loadBrandLogo(context)
        var textStartX = margin
        if (logoBitmap != null) {
            val logoSize = 52f
            val logoTop = (headerHeight - logoSize) / 2f
            val dest = RectF(margin, logoTop, margin + logoSize, logoTop + logoSize)
            canvas.drawBitmap(logoBitmap, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            textStartX = margin + logoSize + 12f
        }

        canvas.drawText(cleanBrand, textStartX, 38f, titlePaint)
        canvas.drawText(
            t(languageCode, "Financial & Operations Statement", "Estado Financiero y Operativo"),
            textStartX,
            55f,
            subtitlePaint
        )
        val metaParts = mutableListOf<String>()
        if (companyLicense.isNotBlank()) metaParts.add("Lic: $companyLicense")
        if (companyPhone.isNotBlank()) metaParts.add("Tel: $companyPhone")
        if (metaParts.isNotEmpty()) {
            canvas.drawText(metaParts.joinToString("  ·  "), textStartX, 71f, subtitlePaint)
        }

        val rightHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val rightSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9EEF3")
            textSize = 9.5f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(periodLabel.uppercase(), pageWidth - margin, 40f, rightHeaderPaint)
        canvas.drawText(rangeSubtitle, pageWidth - margin, 56f, rightSubPaint)
        canvas.drawText("${t(languageCode, "Printed", "Impreso")}: $generatedDate", pageWidth - margin, 72f, rightSubPaint)

        var y = headerHeight + 22f

        // 2. Resumen KPI (4 tarjetas)
        val totalInvoiced = quotes.filter { it.status == "Facturada" }.sumOf { it.total }
        val totalApproved = quotes.filter { it.status == "Aprobada" }.sumOf { it.total }
        val totalPending = quotes.filter { it.status == "Pendiente" }.sumOf { it.total }
        val completedJobs = appointments.count { it.status == "Completada" }

        canvas.drawText(t(languageCode, "PERIOD SUMMARY", "RESUMEN DEL PERÍODO"), margin, y, sectionPaint)
        y += 10f

        val cardGap = 8f
        val cardW = (contentWidth - cardGap * 3) / 4f
        val cardH = 52f
        val kpis = listOf(
            Triple(t(languageCode, "Invoiced", "Facturado"), money.format(totalInvoiced), accentColor),
            Triple(t(languageCode, "Approved", "Aprobado"), money.format(totalApproved), primaryColor),
            Triple(t(languageCode, "Pending", "Pendiente"), money.format(totalPending), textDark),
            Triple(t(languageCode, "Completed Jobs", "Completadas"), "$completedJobs / ${appointments.size}", primaryColor)
        )

        kpis.forEachIndexed { idx, (label, valText, valColor) ->
            val left = margin + idx * (cardW + cardGap)
            val rect = RectF(left, y, left + cardW, y + cardH)
            canvas.drawRoundRect(rect, 6f, 6f, boxFillPaint)
            canvas.drawRoundRect(rect, 6f, 6f, boxStrokePaint)
            canvas.drawText(label, left + 8f, y + 18f, mutedPaint)
            val vp = Paint(kpiValuePaint).apply {
                color = valColor
                textSize = if (valText.length > 10) 11.5f else 13.5f
            }
            canvas.drawText(valText, left + 8f, y + 39f, vp)
        }
        y += cardH + 20f

        // 3. Tabla detallada de cotizaciones y facturas del período
        canvas.drawText(
            "${t(languageCode, "QUOTES & INVOICES BREAKDOWN", "DETALLE DE COTIZACIONES Y FACTURAS")} (${quotes.size})",
            margin,
            y,
            sectionPaint
        )
        y += 10f

        val tableHeaderH = 22f
        val headerRect = RectF(margin, y, pageWidth - margin, y + tableHeaderH)
        canvas.drawRect(headerRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryColor })

        val colDateX = margin + 8f
        val colClientX = margin + 74f
        val colServiceX = margin + 195f
        val colStatusX = margin + 390f
        val colTotalRightX = pageWidth - margin - 8f

        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val thRightPaint = Paint(thPaint).apply { textAlign = Paint.Align.RIGHT }

        canvas.drawText(t(languageCode, "Date", "Fecha"), colDateX, y + 14.5f, thPaint)
        canvas.drawText(t(languageCode, "Customer", "Cliente"), colClientX, y + 14.5f, thPaint)
        canvas.drawText(t(languageCode, "Service", "Servicio"), colServiceX, y + 14.5f, thPaint)
        canvas.drawText(t(languageCode, "Status", "Estado"), colStatusX, y + 14.5f, thPaint)
        canvas.drawText(t(languageCode, "Amount", "Monto"), colTotalRightX, y + 14.5f, thRightPaint)
        y += tableHeaderH

        val rowRightPaint = Paint(boldBodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val maxRows = 22
        val visibleQuotes = quotes.take(maxRows)

        if (visibleQuotes.isEmpty()) {
            val emptyRect = RectF(margin, y, pageWidth - margin, y + 28f)
            canvas.drawRect(emptyRect, boxStrokePaint)
            canvas.drawText(
                t(languageCode, "No records registered in this period.", "No hay registros en este período."),
                margin + 10f,
                y + 18f,
                mutedPaint
            )
            y += 28f
        } else {
            visibleQuotes.forEachIndexed { idx, q ->
                val rowH = 21f
                val rowRect = RectF(margin, y, pageWidth - margin, y + rowH)
                if (idx % 2 == 1) {
                    canvas.drawRect(rowRect, boxFillPaint)
                }
                canvas.drawRect(rowRect, boxStrokePaint)

                val statusLabel = when (q.status) {
                    "Facturada" -> t(languageCode, "Invoiced", "Facturada")
                    "Aprobada" -> t(languageCode, "Approved", "Aprobada")
                    else -> t(languageCode, "Pending", "Pendiente")
                }
                val cleanService = formatServiceShort(q.serviceName, languageCode)

                canvas.drawText(dateFmt.format(Date(q.createdAt)), colDateX, y + 14f, bodyPaint)
                canvas.drawText(q.clientName.take(20), colClientX, y + 14f, boldBodyPaint)
                canvas.drawText(cleanService.take(34), colServiceX, y + 14f, bodyPaint)
                canvas.drawText(statusLabel, colStatusX, y + 14f, bodyPaint)
                canvas.drawText(money.format(q.total), colTotalRightX, y + 14f, rowRightPaint)
                y += rowH
            }
            if (quotes.size > maxRows) {
                y += 14f
                canvas.drawText(
                    t(languageCode, "+ ${quotes.size - maxRows} additional records...", "+ ${quotes.size - maxRows} registros adicionales..."),
                    margin,
                    y,
                    mutedPaint
                )
            }
        }

        // 4. Pie de página
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "$cleanBrand · ${t(languageCode, "Official Financial Statement", "Informe Financiero Oficial")}",
            pageWidth / 2f,
            pageHeight - 20f,
            footerPaint
        )

        doc.finishPage(page)

        val safePeriod = periodLabel.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val fileName = "Report_${safePeriod}_${System.currentTimeMillis() % 100000}.pdf"
        val savedUri = savePdfDocument(context, doc, fileName)
        doc.close()
        return GeneratedPdf(fileName = fileName, uri = savedUri)
    }

    private fun formatServiceShort(rawServiceName: String, lang: String): String {
        return rawServiceName.split(" / ").joinToString(", ") { part ->
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
