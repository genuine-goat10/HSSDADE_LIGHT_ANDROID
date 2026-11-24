package com.hssdade.light.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
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

    fun create(context: Context, quote: QuoteEntity): GeneratedPdf {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val navy = android.graphics.Color.rgb(20, 60, 78)
        val black = android.graphics.Color.rgb(35, 42, 48)
        val muted = android.graphics.Color.rgb(95, 105, 112)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy; textSize = 22f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy; textSize = 13f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = black; textSize = 11f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 9f }
        val price = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy; textSize = 20f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }

        var y = 58f
        canvas.drawText("HSSDADE-LIGHT.CORP", 42f, y, title); y += 24f
        canvas.drawText("RESIDENTIAL ELECTRICAL SERVICES  |  SERVICIOS ELÉCTRICOS RESIDENCIALES", 42f, y, small); y += 28f
        canvas.drawLine(42f, y, 553f, y, Paint().apply { color = navy; strokeWidth = 1.5f }); y += 30f
        canvas.drawText("QUOTATION / COTIZACIÓN", 42f, y, heading); y += 24f
        canvas.drawText("Date / Fecha: ${SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(quote.createdAt))}", 42f, y, body); y += 22f
        canvas.drawText("Customer / Cliente: ${quote.clientName.take(75)}", 42f, y, body); y += 30f
        canvas.drawText("SERVICE / SERVICIO", 42f, y, heading); y += 20f
        y = drawWrapped(canvas, quote.serviceName, 42f, y, body, 500f) + 12f
        canvas.drawText("SCOPE OF WORK / DESCRIPCIÓN DEL TRABAJO", 42f, y, heading); y += 20f
        if (quote.descriptionEs.isNotBlank()) {
            y = drawWrapped(canvas, "ESPAÑOL: ${quote.descriptionEs}", 42f, y, body, 500f) + 8f
        }
        if (quote.descriptionEn.isNotBlank()) {
            y = drawWrapped(canvas, "ENGLISH: ${quote.descriptionEn}", 42f, y, body, 500f) + 8f
        }
        if (quote.observations.isNotBlank()) {
            y += 8f; canvas.drawText("NOTES / OBSERVACIONES", 42f, y, heading); y += 20f
            y = drawWrapped(canvas, quote.observations, 42f, y, body, 500f) + 14f
        }
        y += 10f
        canvas.drawLine(42f, y, 553f, y, Paint().apply { color = navy; strokeWidth = 1f }); y += 28f
        canvas.drawText("Quantity / Cantidad: ${formatQty(quote.quantity)}", 42f, y, body)
        canvas.drawText("TOTAL / TOTAL", 360f, y, heading); y += 28f
        canvas.drawText(NumberFormat.getCurrencyInstance(Locale.US).format(quote.total), 360f, y, price)
        y += 55f
        canvas.drawText("Thank you for the opportunity. / Gracias por la oportunidad.", 42f, y, small)
        canvas.drawText("This quotation reflects the scope described above. Additional work requires approval.", 42f, y + 15f, small)
        canvas.drawText("Esta cotización corresponde al alcance descrito. Todo trabajo adicional requiere aprobación.", 42f, y + 29f, small)
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

    private fun drawWrapped(canvas: android.graphics.Canvas, text: String, x: Float, startY: Float, paint: Paint, maxWidth: Float): Float {
        var y = startY
        val words = text.replace("\n", " ").split(Regex("\\s+"))
        var line = StringBuilder()
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line.toString(), x, y, paint); y += 16f; line = StringBuilder(word)
            } else line = StringBuilder(candidate)
        }
        if (line.isNotEmpty()) { canvas.drawText(line.toString(), x, y, paint); y += 16f }
        return y
    }

    private fun formatQty(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
