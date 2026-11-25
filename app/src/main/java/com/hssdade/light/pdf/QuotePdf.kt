package com.hssdade.light.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.*
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

object QuotePdf {

    data class GeneratedPdf(
        val fileName: String,
        val uri: Uri?
    )

    private fun formatUsPhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6, 10)}"
            digits.length == 11 && digits.startsWith("1") -> "+1 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7, 11)}"
            else -> raw.trim()
        }
    }

    fun create(
        context: Context,
        quote: QuoteEntity,
        companyLicense: String = "",
        companyPhone: String = "",
        brandName: String = "ApexBid"
    ): GeneratedPdf {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create()
        val page = doc.startPage(pageInfo)
        val c = page.canvas

        val brand = brandName.ifBlank { "ApexBid" }
        val isInvoice = quote.status == "Facturada"
        val docTitle = if (isInvoice) "FACTURA / INVOICE" else "COTIZACIÓN / ESTIMATE"
        val formattedCompanyPhone = formatUsPhone(companyPhone)
        val formattedClientPhone = formatUsPhone(quote.clientPhone)

        val primaryColor = Color.rgb(15, 71, 97)
        val accentColor = Color.rgb(34, 136, 110)

        val headerBg = Paint().apply { color = primaryColor }
        val accentBar = Paint().apply { color = accentColor }
        val whiteBold = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val whiteSub = Paint().apply {
            color = Color.rgb(220, 238, 245)
            textSize = 10.5f
            isAntiAlias = true
        }
        val badgePaint = Paint().apply {
            color = Color.WHITE
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val sectionTitle = Paint().apply {
            color = primaryColor
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val text = Paint().apply {
            color = Color.rgb(35, 35, 35)
            textSize = 11f
            isAntiAlias = true
        }
        val mutedText = Paint().apply {
            color = Color.rgb(100, 100, 100)
            textSize = 9.5f
            isAntiAlias = true
        }
        val cardFill = Paint().apply { color = Color.rgb(245, 249, 251) }
        val linePaint = Paint().apply {
            color = Color.rgb(210, 224, 230)
            strokeWidth = 1f
        }
        val totalFill = Paint().apply { color = Color.rgb(232, 245, 242) }
        val totalPaint = Paint().apply {
            color = primaryColor
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        // Encabezado corporativo
        c.drawRect(0f, 0f, 612f, 94f, headerBg)
        c.drawRect(0f, 94f, 612f, 99f, accentBar)

        val customLogoFile = File(context.filesDir, "company_logo.png")
        val logoBitmap: Bitmap? = if (customLogoFile.exists()) {
            BitmapFactory.decodeFile(customLogoFile.absolutePath)
        } else {
            BitmapFactory.decodeResource(context.resources, R.drawable.apexbid_icon)
        }

        var textStartX = 40f
        if (logoBitmap != null) {
            val destRect = RectF(36f, 18f, 92f, 74f)
            c.drawBitmap(logoBitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            textStartX = 104f
        }

        c.drawText(brand, textStartX, 42f, whiteBold)
        c.drawText("Electrical Estimates & Invoices · Servicios Eléctricos", textStartX, 59f, whiteSub)

        val metaParts = mutableListOf<String>()
        if (companyLicense.isNotBlank()) metaParts.add("Lic: $companyLicense")
        if (formattedCompanyPhone.isNotBlank()) metaParts.add("Tel: $formattedCompanyPhone")
        if (metaParts.isNotEmpty()) {
            c.drawText(metaParts.joinToString("  |  "), textStartX, 75f, whiteSub)
        }

        val dateStr = SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(quote.createdAt))
        val docNumber = "#${quote.id.toString().padStart(4, '0')}"
        c.drawText(docTitle, 572f, 38f, badgePaint)
        c.drawText("No. $docNumber · $dateStr", 572f, 56f, whiteSub.apply { textAlign = Paint.Align.RIGHT })
        c.drawText("Estado: ${quote.status.uppercase(Locale.US)}", 572f, 73f, whiteSub)

        // Bloque de Cliente
        c.drawRoundRect(RectF(40f, 114f, 572f, 198f), 8f, 8f, cardFill)
        c.drawText("CLIENTE / CUSTOMER", 54f, 134f, sectionTitle)
        c.drawText(quote.clientName, 54f, 153f, Paint(text).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 12.5f
        })
        val contactLine = listOf(
            if (formattedClientPhone.isNotBlank()) "Tel: $formattedClientPhone" else "",
            if (quote.clientEmail.isNotBlank()) "Email: ${quote.clientEmail}" else ""
        ).filter { it.isNotBlank() }.joinToString("   |   ")
        if (contactLine.isNotBlank()) {
            c.drawText(contactLine, 54f, 170f, text)
        }
        if (quote.clientAddress.isNotBlank()) {
            c.drawText("Dirección / Address: ${quote.clientAddress}", 54f, 187f, text)
        }

        // Alcance de trabajos
        var y = 224f
        c.drawText("ALCANCE DE TRABAJOS / SCOPE OF WORK", 40f, y, sectionTitle)
        y += 18f
        y = drawWrapped(c, "Servicios / Services: ${quote.serviceName}", 40f, y, 532f, text) + 6f
        c.drawText("Cantidad / Quantity: ${quote.quantity}", 40f, y, text)
        y += 18f
        c.drawLine(40f, y, 572f, y, linePaint)
        y += 18f

        c.drawText("DESCRIPCIÓN (ESPAÑOL)", 40f, y, sectionTitle)
        y += 16f
        y = drawWrapped(c, quote.descriptionEs.ifBlank { "-" }, 40f, y, 532f, text) + 12f

        c.drawText("WORK DESCRIPTION (ENGLISH)", 40f, y, sectionTitle)
        y += 16f
        y = drawWrapped(c, quote.descriptionEn.ifBlank { "-" }, 40f, y, 532f, text) + 12f

        if (quote.observations.isNotBlank()) {
            c.drawText("OBSERVACIONES / NOTES", 40f, y, sectionTitle)
            y += 16f
            y = drawWrapped(c, quote.observations, 40f, y, 532f, text) + 12f
        }

        // Caja de Total
        val totalBoxTop = maxOf(y + 8f, 540f).coerceAtMost(605f)
        c.drawRoundRect(RectF(40f, totalBoxTop, 572f, totalBoxTop + 56f), 8f, 8f, totalFill)
        c.drawText(
            if (isInvoice) "TOTAL FACTURADO / INVOICE TOTAL" else "TOTAL ESTIMADO / ESTIMATE TOTAL",
            56f,
            totalBoxTop + 24f,
            sectionTitle
        )
        c.drawText("Incluye mano de obra y materiales / Includes labor & materials", 56f, totalBoxTop + 42f, mutedText)
        val money = NumberFormat.getCurrencyInstance(Locale.US).format(quote.total)
        c.drawText(money, 556f, totalBoxTop + 37f, totalPaint)

        // Firma digital
        val sigTop = totalBoxTop + 72f
        c.drawText("AUTORIZACIÓN DEL CLIENTE / CUSTOMER AUTHORIZATION", 40f, sigTop, sectionTitle)
        val sigBox = RectF(40f, sigTop + 8f, 320f, sigTop + 76f)
        c.drawRoundRect(sigBox, 6f, 6f, cardFill)
        c.drawLine(52f, sigTop + 62f, 308f, sigTop + 62f, linePaint)

        if (quote.signatureData.isNotBlank()) {
            drawSignatureStrokes(c, quote.signatureData, RectF(48f, sigTop + 12f, 312f, sigTop + 60f))
            val signedDate = if (quote.signedAt > 0L) {
                SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.US).format(Date(quote.signedAt))
            } else dateStr
            c.drawText("Firmado digitalmente / Signed: $signedDate", 336f, sigTop + 34f, text)
            c.drawText("Cliente / Customer: ${quote.clientName}", 336f, sigTop + 52f, mutedText)
        } else {
            c.drawText("Firma del cliente / Customer signature", 54f, sigTop + 72f, mutedText)
            c.drawText("Fecha / Date: ____/____/20____", 336f, sigTop + 46f, text)
        }

        c.drawText(
            "Documento generado con $brand · Válido por 30 días / Valid for 30 days",
            40f,
            774f,
            mutedText
        )

        doc.finishPage(page)

        // Página 2 opcional: Evidencia fotográfica adjunta
        val photoPaths = quote.photoUris.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (photoPaths.isNotEmpty()) {
            val photoPageInfo = PdfDocument.PageInfo.Builder(612, 792, 2).create()
            val photoPage = doc.startPage(photoPageInfo)
            val pc = photoPage.canvas

            pc.drawRect(0f, 0f, 612f, 62f, headerBg)
            pc.drawRect(0f, 62f, 612f, 66f, accentBar)
            pc.drawText("EVIDENCIA FOTOGRÁFICA / JOB SITE PHOTOS", 40f, 38f, Paint(whiteBold).apply { textSize = 15f })
            pc.drawText("$brand · ${quote.clientName} · $dateStr", 572f, 38f, Paint(whiteSub).apply { textAlign = Paint.Align.RIGHT })

            val cols = 2
            val cellW = 254f
            val cellH = 200f
            val startX = 40f
            var curY = 86f

            photoPaths.take(6).forEachIndexed { index, path ->
                val col = index % cols
                if (index > 0 && col == 0) curY += cellH + 20f
                val left = startX + col * (cellW + 24f)
                val box = RectF(left, curY, left + cellW, curY + cellH)
                pc.drawRoundRect(box, 6f, 6f, cardFill)

                val bmp = decodeSampledBitmap(path, 600, 600)
                if (bmp != null) {
                    val inner = RectF(left + 6f, curY + 6f, left + cellW - 6f, curY + cellH - 22f)
                    pc.drawBitmap(bmp, null, inner, Paint(Paint.FILTER_BITMAP_FLAG))
                }
                pc.drawText("Foto #${index + 1}", left + 10f, curY + cellH - 7f, mutedText)
            }

            pc.drawText("Documento generado con $brand", 40f, 774f, mutedText)
            doc.finishPage(photoPage)
        }

        val safeClient = quote.clientName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(25)
        val prefix = if (isInvoice) "Invoice" else "Estimate"
        val fileName = "${brand}_${prefix}_${safeClient}_${System.currentTimeMillis()}.pdf"
        var exportedUri: Uri? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ApexBid")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out -> doc.writeTo(out) }
                exportedUri = uri
            }
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ApexBid").apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { out -> doc.writeTo(out) }
            exportedUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

        doc.close()
        return GeneratedPdf(fileName, exportedUri)
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfH = options.outHeight / 2
                val halfW = options.outWidth / 2
                while ((halfH / inSampleSize) >= reqHeight && (halfW / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (_: Exception) {
            null
        }
    }

    private fun drawSignatureStrokes(canvas: Canvas, serialized: String, bounds: RectF) {
        val paint = Paint().apply {
            color = Color.rgb(20, 60, 78)
            strokeWidth = 2.4f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        val strokes = serialized.split(";")
        for (stroke in strokes) {
            val pts = stroke.split("|")
            if (pts.size < 2) continue
            val path = Path()
            var started = false
            for (pt in pts) {
                val xy = pt.split(",")
                if (xy.size == 2) {
                    val nx = xy[0].toFloatOrNull() ?: continue
                    val ny = xy[1].toFloatOrNull() ?: continue
                    val px = bounds.left + nx * bounds.width()
                    val py = bounds.top + ny * bounds.height()
                    if (!started) {
                        path.moveTo(px, py)
                        started = true
                    } else {
                        path.lineTo(px, py)
                    }
                }
            }
            if (started) canvas.drawPath(path, paint)
        }
    }

    private fun drawWrapped(canvas: Canvas, content: String, x: Float, startY: Float, maxWidth: Float, paint: Paint): Float {
        var y = startY
        for (paragraph in content.split("\n")) {
            var line = ""
            for (word in paragraph.split(" ")) {
                val test = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(test) <= maxWidth) {
                    line = test
                } else {
                    canvas.drawText(line, x, y, paint)
                    y += 15f
                    line = word
                }
            }
            if (line.isNotEmpty()) {
                canvas.drawText(line, x, y, paint)
                y += 15f
            }
        }
        return y
    }
}
