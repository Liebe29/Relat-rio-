package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.FilledReport
import com.example.data.model.JsonUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExportUtils {

    fun exportToCSV(context: Context, report: FilledReport) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(report.filledAt))

        val csvHeader = "Informação;Dado\n"
        val infoList = mutableListOf<String>()
        infoList.add("Relatório;${report.templateTitle}")
        infoList.add("Respondente;${report.respondentName}")
        infoList.add("Contato;${report.respondentPhone}")
        infoList.add("Data de Preenchimento;$dateStr")
        infoList.add(";") // Linha em branco

        val answers = JsonUtils.jsonToAnswers(report.answersJson)
        answers.forEach { (topic, answer) ->
            val escapedTopic = topic.replace(";", ", ").replace("\"", "'")
            val escapedAnswer = answer.replace(";", ", ").replace("\"", "'")
            infoList.add("$escapedTopic;$escapedAnswer")
        }

        val csvString = csvHeader + infoList.joinToString("\n")
        try {
            val fileName = "Relatorio_${report.respondentName.replace("[^a-zA-Z0-9]".toRegex(), "_")}.csv"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { out ->
                out.write(csvString.toByteArray(Charsets.UTF_8))
            }
            shareFile(context, file, "text/csv")
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao exportar Excel: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportAllToCSV(context: Context, reports: List<FilledReport>, templateTitle: String) {
        if (reports.isEmpty()) {
            Toast.makeText(context, "Sem dados para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        val allKeys = mutableSetOf<String>()
        val reportAnswersList = reports.map { report ->
            val answers = JsonUtils.jsonToAnswers(report.answersJson)
            answers.keys.forEach { allKeys.add(it) }
            report to answers
        }
        val sortedKeys = allKeys.toList()

        val headerRow = mutableListOf("Data", "Nome", "Contato")
        headerRow.addAll(sortedKeys)
        val csvHeader = headerRow.joinToString(";") + "\n"

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val csvRows = reportAnswersList.map { (report, answers) ->
            val row = mutableListOf<String>()
            row.add(dateFormat.format(Date(report.filledAt)))
            row.add(report.respondentName)
            row.add(report.respondentPhone)
            sortedKeys.forEach { key ->
                val ans = answers[key] ?: ""
                row.add(ans.replace(";", ", ").replace("\"", "'"))
            }
            row.joinToString(";")
        }

        val csvString = csvHeader + csvRows.joinToString("\n")
        try {
            val fileName = "Consolidado_${templateTitle.replace("[^a-zA-Z0-9]".toRegex(), "_")}.csv"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { out ->
                out.write(csvString.toByteArray(Charsets.UTF_8))
            }
            shareFile(context, file, "text/csv")
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao exportar Excel completo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportToPDF(context: Context, report: FilledReport) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4: 595 x 842 pt
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val headerPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var yPosition = 50f

        // Cabeçalho do PDF
        canvas.drawText("Relatório Comercial - Recebido", 40f, yPosition, titlePaint)
        yPosition += 35f

        canvas.drawText("Formulário: ${report.templateTitle}", 40f, yPosition, headerPaint)
        yPosition += 20f

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(report.filledAt))
        canvas.drawText("Respondente: ${report.respondentName}", 40f, yPosition, textPaint)
        yPosition += 18f
        canvas.drawText("WhatsApp: ${report.respondentPhone}", 40f, yPosition, textPaint)
        yPosition += 18f
        canvas.drawText("Preenchido em: $dateStr", 40f, yPosition, textPaint)
        yPosition += 15f

        canvas.drawLine(40f, yPosition, 555f, yPosition, linePaint)
        yPosition += 25f

        canvas.drawText("RESPOSTAS DETALHADAS", 40f, yPosition, headerPaint)
        yPosition += 22f

        val answers = JsonUtils.jsonToAnswers(report.answersJson)
        answers.forEach { (topic, answer) ->
            canvas.drawText("• $topic:", 45f, yPosition, headerPaint)
            yPosition += 18f
            canvas.drawText("  $answer", 45f, yPosition, textPaint)
            yPosition += 25f

            if (yPosition > 800f) {
                // Adicionamos margem de segurança
            }
        }

        pdfDocument.finishPage(page)

        try {
            val fileName = "Relatorio_${report.respondentName.replace("[^a-zA-Z0-9]".toRegex(), "_")}.pdf"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            shareFile(context, file, "application/pdf")
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    private fun shareFile(context: Context, file: File, mimeType: String) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar via..."))
    }
}
