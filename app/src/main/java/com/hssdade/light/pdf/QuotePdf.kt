package com.hssdade.light.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import com.hssdade.light.data.QuoteEntity
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Creates a customer-facing bilingual PDF. Internal costs and margin are never included. */
object QuotePdf {
    data class GeneratedPdf(val fileName: String, val uri: Uri?)

    fun create(
        context: Context,
        quote: QuoteEntity,
        companyLicense: String = "",
        companyPhone: String = ""
    ): GeneratedPdf {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val navy = android.graphics.Color.rgb(20, 60, 78)
        val teal = android.graphics.Color.rgb(45, 138, 120)
        val black = android.graphics.Color.rgb(35, 42, 48)
        val muted = android.graphics.Color.rgb(95, 105, 112)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy; textSize = 20f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy; textSize = 12f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = black; textSize = 10.5f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 8.8f }
        val price = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy; textSize = 20f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = teal; textSize = 10f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }

        var headerLeftX = 42f
        val logoFile = File(context.filesDir, "company_logo.png")
        if (logoFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
            if (bitmap != null) {
                val maxH = 48f
                val scale = maxH / bitmap.height.toFloat()
                val drawW = (bitmap.width * scale).coerceAtMost(110f)
                val destRect = RectF(42f, 38f, 42f + drawW, 38f + maxH)
                canvas.drawBitmap(bitmap, null, destRect, null)
                headerLeftX = 42f + drawW + 12f
            }
        }

        var y = 56f
        canvas.drawText("HSSDADE-LIGHT.CORP", headerLeftX, y, title); y += 16f
        canvas.drawText("RESIDENTIAL ELECTRICAL SERVICES | SERVICIOS ELÉCTRICOS RESIDENCIALES", headerLeftX, y, small); y += 13f
        val subCompany = listOfNotNull(
            companyLicense.takeIf { it.isNotBlank() }?.let { "Lic: $it" },
            companyPhone.takeIf { it.isNotBlank() }?.let { "Tel: $it" }
        ).joinToString("  ·  ")
        if (subCompany.isNotBlank()) {
            canvas.drawText(subCompany, headerLeftX, y, small)
            y += 14f
        } else {
            y += 8f
        }

        canvas.drawLine(42f, y, 553f, y, Paint().apply { color = navy; strokeWidth = 1.5f }); y += 24f

        val statusEn = when (quote.status) {
            "Aprobada" -> "APPROVED / APROBADA"
            "Facturada" -> "INVOICED / FACTURADA"
            else -> "PENDING / PENDIENTE"
        }
        canvas.drawText("QUOTATION / COTIZACIÓN", 42f, y, heading)
        canvas.drawText("STATUS: $statusEn", 360f, y, badgePaint); y += 20f

        canvas.drawText("Date / Fecha: ${SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(quote.createdAt))}", 42f, y, body); y += 18f
        canvas.drawText("Customer / Cliente: ${quote.clientName.take(70)}", 42f, y, body); y += 18f
        val contactLine = listOfNotNull(
            quote.clientPhone.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
            quote.clientEmail.takeIf { it.isNotBlank() }?.let { "Email: $it" }
        ).joinToString("  |  ")
        if (contactLine.isNotBlank()) {
            canvas.drawText(contactLine.take(80), 42f, y, small); y += 20f
        } else {
            y += 8f
        }

        canvas.drawText("SERVICE / SERVICIO", 42f, y, heading); y += 18f
        y = drawWrapped(canvas, quote.serviceName, 42f, y, body, 505f) + 10f

        canvas.drawText("SCOPE OF WORK / DESCRIPCIÓN DEL TRABAJO", 42f, y, heading); y += 18f
        if (quote.descriptionEs.isNotBlank()) {
            y = drawWrapped(canvas, "ESPAÑOL: ${quote.descriptionEs}", 42f, y, body, 505f) + 6f
        }
        if (quote.descriptionEn.isNotBlank()) {
            y = drawWrapped(canvas, "ENGLISH: ${quote.descriptionEn}", 42f, y, body, 505f) + 6f
        }
        if (quote.observations.isNotBlank()) {
            y += 6f; canvas.drawText("NOTES / OBSERVACIONES", 42f, y, heading); y += 18f
            y = drawWrapped(canvas, quote.observations, 42f, y, body, 505f) + 10f
        }

        y += 8f
        canvas.drawLine(42f, y, 553f, y, Paint().apply { color = navy; strokeWidth = 1f }); y += 24f
        canvas.drawText("Quantity / Cantidad: ${formatQty(quote.quantity)}", 42f, y, body)
        canvas.drawText("TOTAL / TOTAL", 360f, y, heading); y += 24f
        canvas.drawText(NumberFormat.getCurrencyInstance(Locale.US).format(quote.total), 360f, y, price)
        y += 28f

        // Bloque de firma digital del cliente
        val sigBoxTop = y.coerceAtMost(665f)
        val sigBoxBottom = sigBoxTop + 68f
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(210, 220, 226)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRect(42f, sigBoxTop, 310f, sigBoxBottom, boxPaint)
        canvas.drawText("CUSTOMER SIGNATURE / FIRMA DEL CLIENTE", 48f, sigBoxTop + 12f, small)

        if (quote.signatureData.isNotBlank()) {
            drawSignatureStrokes(canvas, quote.signatureData, 48f, sigBoxTop + 16f, 254f, 46f, navy)
            if (quote.signedAt > 0L) {
                val signedDate = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.US).format(Date(quote.signedAt))
                canvas.drawText("Signed / Firmado: $signedDate", 325f, sigBoxTop + 36f, small)
            }
        } else {
            canvas.drawLine(54f, sigBoxBottom - 14f, 296f, sigBoxBottom - 14f, boxPaint)
        }

        val footerY = (sigBoxBottom + 22f).coerceAtMost(795f)
        canvas.drawText("Thank you for the opportunity. / Gracias por la oportunidad.", 42f, footerY, small)
        canvas.drawText("This quotation reflects the scope described above. Additional work requires approval.", 42f, footerY + 13f, small)
        canvas.drawText("Esta cotización corresponde al alcance descrito. Todo trabajo adicional requiere aprobación.", 42f, footerY + 26f, small)
        document.finishPage(page)

        val fileName = "HSSDADE_Quote_${quote.id}_${System.currentTimeMillis()}.pdf"
        val resolver = context.contentResolver
        var insertedUri: Uri? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/HSSDADE-LIGHT.CORP")
                }
                insertedUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("No se pudo crear el archivo PDF")
                resolver.openOutputStream(insertedUri)?.use { document.writeTo(it) }
                    ?: throw IllegalStateException("No se pudo guardar el PDF")
            } else {
                val folder = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: throw IllegalStateException("Carpeta de documentos no disponible")
                val file = File(folder, fileName)
                FileOutputStream(file).use { document.writeTo(it) }
                insertedUri = file.toUri()
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                insertedUri?.let { resolver.delete(it, null, null) }
            }
            throw e
        } finally {
            document.close()
        }
        return GeneratedPdf(fileName, insertedUri)
    }

    private fun drawSignatureStrokes(
        canvas: Canvas,
        serialized: String,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        strokeColor: Int
    ) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        serialized.split(";").forEach { stroke ->
            val pts = stroke.split("|").mapNotNull { pair ->
                val parts = pair.split(",")
                if (parts.size == 2) {
                    val nx = parts[0].toFloatOrNull()
                    val ny = parts[1].toFloatOrNull()
                    if (nx != null && ny != null) (left + nx * width) to (top + ny * height) else null
                } else null
            }
            if (pts.size >= 2) {
                val path = Path()
                path.moveTo(pts[0].first, pts[0].second)
                for (i in 1 until pts.size) {
                    path.lineTo(pts[i].first, pts[i].second)
                }
                canvas.drawPath(path, strokePaint)
            }
        }
    }

    private fun drawWrapped(canvas: Canvas, text: String, x: Float, startY: Float, paint: Paint, maxWidth: Float): Float {
        var y = startY
        val words = text.replace("\n", " ").split(Regex("\\s+"))
        var line = StringBuilder()
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line.toString(), x, y, paint); y += 15f; line = StringBuilder(word)
            } else line = StringBuilder(candidate)
        }
        if (line.isNotEmpty()) { canvas.drawText(line.toString(), x, y, paint); y += 15f }
        return y
    }

    private fun formatQty(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
