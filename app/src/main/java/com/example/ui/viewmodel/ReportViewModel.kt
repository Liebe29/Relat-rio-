package com.example.ui.viewmodel

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.FilledReport
import com.example.data.model.JsonUtils
import com.example.data.model.ReportField
import com.example.data.model.ReportTemplate
import com.example.data.repository.ReportRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

data class TemplateDto(
    val title: String,
    val fields: List<ReportField>
)

data class ResponseDto(
    val templateTitle: String,
    val respondentName: String,
    val respondentPhone: String,
    val answers: Map<String, String>,
    val filledAt: Long? = null
)

class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ReportRepository
    val allTemplates: StateFlow<List<ReportTemplate>>
    val allFilledReports: StateFlow<List<FilledReport>>

    // Active navigation states or dynamic payloads
    private val _incomingTemplate = MutableStateFlow<TemplateDto?>(null)
    val incomingTemplate = _incomingTemplate.asStateFlow()

    private val _incomingResponse = MutableStateFlow<ResponseDto?>(null)
    val incomingResponse = _incomingResponse.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ReportRepository(database.reportDao())
        
        allTemplates = repository.allTemplates.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
        allFilledReports = repository.allFilledReports.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // --- TEMPLATE CRUD ---
    fun saveTemplate(title: String, fields: List<ReportField>) {
        viewModelScope.launch {
            val json = JsonUtils.fieldsToJson(fields)
            val template = ReportTemplate(title = title, fieldsJson = json)
            repository.insertTemplate(template)
        }
    }

    fun deleteTemplate(id: Int) {
        viewModelScope.launch {
            repository.deleteTemplateById(id)
        }
    }

    // --- FILLED REPORT CRUD ---
    fun saveFilledReport(
        templateId: Int,
        templateTitle: String,
        respondentName: String,
        respondentPhone: String,
        answers: Map<String, String>,
        filledAt: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val json = JsonUtils.answersToJson(answers)
            val filled = FilledReport(
                templateId = templateId,
                templateTitle = templateTitle,
                respondentName = respondentName,
                respondentPhone = respondentPhone,
                answersJson = json,
                filledAt = filledAt
            )
            repository.insertFilledReport(filled)
        }
    }

    fun deleteFilledReport(id: Int) {
        viewModelScope.launch {
            repository.deleteFilledReportById(id)
        }
    }

    // --- DEEP LINK DECODING & ENCODING ---
    private val moshiLocal = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    fun generateTemplateLink(template: ReportTemplate): String {
        return try {
            val fields = JsonUtils.jsonToFields(template.fieldsJson)
            val dto = TemplateDto(title = template.title, fields = fields)
            val json = moshiLocal.adapter(TemplateDto::class.java).toJson(dto)
            val base64 = Base64.encodeToString(json.toByteArray(StandardCharsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
            "relatoriofacil://preencher?t=$base64"
        } catch (e: Exception) {
            ""
        }
    }

    fun generateTemplateWebLink(template: ReportTemplate): String {
        return try {
            val fields = JsonUtils.jsonToFields(template.fieldsJson)
            val dto = TemplateDto(title = template.title, fields = fields)
            val json = moshiLocal.adapter(TemplateDto::class.java).toJson(dto)
            val base64 = Base64.encodeToString(json.toByteArray(StandardCharsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
            "https://relatoriofacil.page.link/fill?t=$base64"
        } catch (e: Exception) {
            ""
        }
    }

    fun generateResponseLink(
        templateTitle: String,
        respondentName: String,
        respondentPhone: String,
        answers: Map<String, String>,
        filledAt: Long = System.currentTimeMillis()
    ): String {
        return try {
            val dto = ResponseDto(
                templateTitle = templateTitle,
                respondentName = respondentName,
                respondentPhone = respondentPhone,
                answers = answers,
                filledAt = filledAt
            )
            val json = moshiLocal.adapter(ResponseDto::class.java).toJson(dto)
            val base64 = Base64.encodeToString(json.toByteArray(StandardCharsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
            "relatoriofacil://importar?r=$base64"
        } catch (e: Exception) {
            ""
        }
    }

    fun handleDeepLink(urlStr: String?): Boolean {
        if (urlStr == null) return false
        try {
            if (urlStr.contains("t=")) {
                val base64 = urlStr.substringAfter("t=")
                val json = String(Base64.decode(base64, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
                val dto = moshiLocal.adapter(TemplateDto::class.java).fromJson(json)
                if (dto != null) {
                    _incomingTemplate.value = dto
                    return true
                }
            } else if (urlStr.contains("r=")) {
                val base64 = urlStr.substringAfter("r=")
                val json = String(Base64.decode(base64, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
                val dto = moshiLocal.adapter(ResponseDto::class.java).fromJson(json)
                if (dto != null) {
                    _incomingResponse.value = dto
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun clearIncomingTemplate() {
        _incomingTemplate.value = null
    }

    fun clearIncomingResponse() {
        _incomingResponse.value = null
    }

    // --- PASTE TEXT ANALYSIS AND MANUAL IMPORT ---
    fun parseAndImportText(text: String): Boolean {
        // Checks if text contains t=... or r=... parameters, or even parses simple formats.
        if (text.contains("t=")) {
            val segment = text.substringAfter("t=").substringBefore(" ").substringBefore("\n").trim()
            try {
                val json = String(Base64.decode(segment, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
                val dto = moshiLocal.adapter(TemplateDto::class.java).fromJson(json)
                if (dto != null) {
                    _incomingTemplate.value = dto
                    return true
                }
            } catch (e: Exception) {
                // fall through
            }
        }
        if (text.contains("r=")) {
            val segment = text.substringAfter("r=").substringBefore(" ").substringBefore("\n").trim()
            try {
                val json = String(Base64.decode(segment, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
                val dto = moshiLocal.adapter(ResponseDto::class.java).fromJson(json)
                if (dto != null) {
                    _incomingResponse.value = dto
                    return true
                }
            } catch (e: Exception) {
                // fall through
            }
        }
        
        // Also support parsing a raw copy-pasted text report!
        // Format:
        // *RESPOSTAS DO RELATÓRIO* (or similar strings)
        // Relatório: XYZ
        // Nome: ABC
        // WhatsApp/Contato: 123
        // or a simpler structured paste! Let's make a fallback human-friendly text parser.
        try {
            val lines = text.lines().map { it.trim() }
            var reportTitle = ""
            var respName = ""
            var respPhone = ""
            val answersMap = mutableMapOf<String, String>()

            lines.forEach { line ->
                when {
                    line.startsWith("Relatório:", ignoreCase = true) || line.startsWith("📋 Relatório:", ignoreCase = true) -> {
                        reportTitle = line.substringAfter(":").trim()
                    }
                    line.startsWith("Nome:", ignoreCase = true) || line.startsWith("👤 Nome:", ignoreCase = true) -> {
                        respName = line.substringAfter(":").trim()
                    }
                    line.startsWith("WhatsApp:", ignoreCase = true) || line.startsWith("Contato:", ignoreCase = true) || line.startsWith("📞 WhatsApp:", ignoreCase = true) -> {
                        respPhone = line.substringAfter(":").trim()
                    }
                    line.contains(":") && !line.startsWith("http", ignoreCase = true) && !line.startsWith("relatoriofacil", ignoreCase = true) -> {
                        val key = line.substringBefore(":").trim().removePrefix("•").removePrefix("-").trim()
                        val value = line.substringAfter(":").trim()
                        if (key.isNotEmpty() && value.isNotEmpty()) {
                            answersMap[key] = value
                        }
                    }
                }
            }

            if (reportTitle.isNotEmpty() && respName.isNotEmpty() && answersMap.isNotEmpty()) {
                val dto = ResponseDto(
                    templateTitle = reportTitle,
                    respondentName = respName,
                    respondentPhone = respPhone,
                    answers = answersMap
                )
                _incomingResponse.value = dto
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
