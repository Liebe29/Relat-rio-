package com.example

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FilledReport
import com.example.data.model.JsonUtils
import com.example.data.model.ReportField
import com.example.data.model.ReportTemplate
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ResponseDto
import com.example.ui.viewmodel.ReportViewModel
import com.example.ui.viewmodel.TemplateDto
import com.example.util.ExportUtils
import java.text.SimpleDateFormat
import java.util.*

// Navegação simples por estado
sealed class Screen {
    object Dashboard : Screen()
    object CreateTemplate : Screen()
    data class FillReport(val templateTitle: String, val fields: List<ReportField>, val templateId: Int) : Screen()
    data class ViewReportDetail(val report: FilledReport) : Screen()
}

class MainActivity : ComponentActivity() {
    private val viewModel: ReportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Trata deep link inicial se houver
        intent?.data?.toString()?.let {
            viewModel.handleDeepLink(it)
        }

        setContent {
            MyApplicationTheme {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.toString()?.let {
            viewModel.handleDeepLink(it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(viewModel: ReportViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    
    // Estados reativos do ViewModel
    val templates by viewModel.allTemplates.collectAsStateWithLifecycle(initialValue = emptyList())
    val filledReports by viewModel.allFilledReports.collectAsStateWithLifecycle(initialValue = emptyList())
    
    val incomingTemplate by viewModel.incomingTemplate.collectAsStateWithLifecycle()
    val incomingResponse by viewModel.incomingResponse.collectAsStateWithLifecycle()

    // Dialogs temporários para importação via Link
    if (incomingTemplate != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearIncomingTemplate() },
            title = { Text("Preencher Relatório") },
            text = { Text("Deseja abrir o formulário para preencher o relatório '${incomingTemplate?.title}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        incomingTemplate?.let {
                            currentScreen = Screen.FillReport(
                                templateTitle = it.title,
                                fields = it.fields,
                                templateId = 0 // Link externo não possui ID local salvo
                            )
                        }
                        viewModel.clearIncomingTemplate()
                    }
                ) {
                    Text("Preencher")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearIncomingTemplate() }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (incomingResponse != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearIncomingResponse() },
            title = { Text("Importar Resposta") },
            text = { 
                Text("Deseja importar a resposta de '${incomingResponse?.respondentName}' para o formulário '${incomingResponse?.templateTitle}'?") 
            },
            confirmButton = {
                Button(
                    onClick = {
                        incomingResponse?.let {
                            viewModel.saveFilledReport(
                                templateId = 0,
                                templateTitle = it.templateTitle,
                                respondentName = it.respondentName,
                                respondentPhone = it.respondentPhone,
                                answers = it.answers,
                                filledAt = it.filledAt ?: System.currentTimeMillis()
                            )
                            Toast.makeText(context, "Resposta de ${it.respondentName} importada com sucesso!", Toast.LENGTH_SHORT).show()
                        }
                        viewModel.clearIncomingResponse()
                    }
                ) {
                    Text("Importar")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearIncomingResponse() }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    is Screen.Dashboard -> {
                        DashboardScreen(
                            templates = templates,
                            filledReports = filledReports,
                            viewModel = viewModel,
                            onCreateTemplateClick = { currentScreen = Screen.CreateTemplate },
                            onFillTemplateClick = { t ->
                                val fields = JsonUtils.jsonToFields(t.fieldsJson)
                                currentScreen = Screen.FillReport(t.title, fields, t.id)
                            },
                            onViewReportDetail = { r ->
                                currentScreen = Screen.ViewReportDetail(r)
                            }
                        )
                    }
                    is Screen.CreateTemplate -> {
                        CreateTemplateScreen(
                            onBackClick = { currentScreen = Screen.Dashboard },
                            onSaveClick = { title, fields ->
                                viewModel.saveTemplate(title, fields)
                                currentScreen = Screen.Dashboard
                                Toast.makeText(context, "Modelo criado com sucesso!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    is Screen.FillReport -> {
                        FillReportScreen(
                            templateTitle = targetScreen.templateTitle,
                            fields = targetScreen.fields,
                            onBackClick = { currentScreen = Screen.Dashboard },
                            onShareAndSave = { name, phone, answers, dateMillis ->
                                // Salva localmente se tiver um template associado
                                viewModel.saveFilledReport(
                                    templateId = targetScreen.templateId,
                                    templateTitle = targetScreen.templateTitle,
                                    respondentName = name,
                                    respondentPhone = phone,
                                    answers = answers,
                                    filledAt = dateMillis
                                )
                                currentScreen = Screen.Dashboard
                            },
                            viewModel = viewModel
                        )
                    }
                    is Screen.ViewReportDetail -> {
                        ViewReportDetailScreen(
                            report = targetScreen.report,
                            onBackClick = { currentScreen = Screen.Dashboard }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 1: DASHBOARD
// ==========================================
@Composable
fun DashboardScreen(
    templates: List<ReportTemplate>,
    filledReports: List<FilledReport>,
    viewModel: ReportViewModel,
    onCreateTemplateClick: () -> Unit,
    onFillTemplateClick: (ReportTemplate) -> Unit,
    onViewReportDetail: (FilledReport) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    
    // States for custom search and date filters
    var searchText by remember { mutableStateOf("") }
    var selectedDateFilter by remember { mutableStateOf("TODOS") } // "TODOS", "HOJE", "SEMANA", "MES", "CUSTOM"
    var customFilterDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Custom Bar with gradient and Portuguese actions
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Relatórios FJU",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Gerenciamento inteligente e dinâmico",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
                
                Button(
                    onClick = { showImportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.testTag("btn_importar_rapido")
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Importar", fontSize = 13.sp)
                }
            }
        }

        // Tabs
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Modelos (${templates.size})") },
                icon = { Icon(Icons.Filled.Description, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Respostas (${filledReports.size})") },
                icon = { Icon(Icons.Filled.Send, contentDescription = null) }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                // LIST OF TEMPLATES
                if (templates.isEmpty()) {
                    EmptyStateLayout(
                        title = "Nenhum modelo de relatório",
                        subtitle = "Crie tópicos que você deseja perguntar para as pessoas e customize se o tipo de resposta é livre ou de seleção simples.",
                        icon = Icons.Filled.Create,
                        ctaText = "Criar Modelo",
                        onCtaClick = onCreateTemplateClick
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(templates) { template ->
                                TemplateListItemCard(
                                    template = template,
                                    onFillClick = { onFillTemplateClick(template) },
                                    onShareClick = {
                                        val link = viewModel.generateTemplateLink(template)
                                        val fields = JsonUtils.jsonToFields(template.fieldsJson)
                                        val formattedTopics = fields.mapIndexed { idx, f ->
                                            val choiceInfo = if (f.type == "CHOICE") " (Opções: ${f.options.joinToString(", ")})" else " (Responder por extenso)"
                                            "${idx + 1}. *${f.label}*$choiceInfo"
                                        }.joinToString("\n")

                                        val shareText = buildString {
                                            append("📋 *SOLICITAÇÃO DE RELATÓRIO: ${template.title.uppercase()}*\n\n")
                                            append("Olá! Por favor, preencha as informações solicitadas.\n\n")
                                            append("Você pode preencher de duas formas:\n\n")
                                            append("👉 *Opção 1 (Rápido):* Se você tem o app instalado, abra o link para responder com 1 toque:\n")
                                            append("$link\n\n")
                                            append("👉 *Opção 2 (Texto):* Caso não tenha o app, responda por favor diretamente a esta mensagem com as informações destes tópicos:\n")
                                            append(formattedTopics)
                                            append("\n\n_Obrigado pelo preenchimento!_")
                                        }

                                        clipboardManager.setText(AnnotatedString(shareText))
                                        Toast.makeText(context, "Link e instruções copiados para a área de transferência!", Toast.LENGTH_LONG).show()

                                        // Compartilha direto no WhatsApp / Apps
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Enviar Solicitação via..."))
                                    },
                                    onDeleteClick = {
                                        viewModel.deleteTemplate(template.id)
                                        Toast.makeText(context, "Modelo removido", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }

                        // Floating action button to generate template
                        LargeFloatingActionButton(
                            onClick = onCreateTemplateClick,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(24.dp)
                                .testTag("fab_create_template"),
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Criar Modelo")
                        }
                    }
                }
            } else {
                // LIST OF RECEIVED REPORTS WITH SEARCH & DATE FILTER CHIPS
                val filteredReports = remember(filledReports, searchText, selectedDateFilter, customFilterDateMillis) {
                    filledReports.filter { r ->
                        val matchesText = r.respondentName.contains(searchText, ignoreCase = true) ||
                                r.templateTitle.contains(searchText, ignoreCase = true)
                        
                        if (!matchesText) return@filter false
                        
                        // Date filter
                        when (selectedDateFilter) {
                            "TODOS" -> true
                            "HOJE" -> {
                                val reportCal = Calendar.getInstance().apply { timeInMillis = r.filledAt }
                                val todayCal = Calendar.getInstance()
                                reportCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                        reportCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
                            }
                            "SEMANA" -> {
                                val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                                r.filledAt >= oneWeekAgo
                            }
                            "MES" -> {
                                val reportCal = Calendar.getInstance().apply { timeInMillis = r.filledAt }
                                val thisMonthCal = Calendar.getInstance()
                                reportCal.get(Calendar.YEAR) == thisMonthCal.get(Calendar.YEAR) &&
                                        reportCal.get(Calendar.MONTH) == thisMonthCal.get(Calendar.MONTH)
                            }
                            "CUSTOM" -> {
                                val reportCal = Calendar.getInstance().apply { timeInMillis = r.filledAt }
                                val customCal = Calendar.getInstance().apply { timeInMillis = customFilterDateMillis }
                                reportCal.get(Calendar.YEAR) == customCal.get(Calendar.YEAR) &&
                                        reportCal.get(Calendar.DAY_OF_YEAR) == customCal.get(Calendar.DAY_OF_YEAR)
                            }
                            else -> true
                        }
                    }
                }

                if (filledReports.isEmpty()) {
                    EmptyStateLayout(
                        title = "Nenhuma resposta recebida",
                        subtitle = "Ao enviar o link que o aplicativo gera para a pessoa preencher, ela responderá e te enviará de volta. Use o botão Importar para colar as respostas!",
                        icon = Icons.Filled.Send,
                        ctaText = "Importar Copiado",
                        onCtaClick = { showImportDialog = true }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Opção de exportação geral
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Relatórios Respondidos",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                
                                // Exporta todos os consolidados para CSV
                                if (filledReports.isNotEmpty()) {
                                    Button(
                                        onClick = {
                                            ExportUtils.exportAllToCSV(context, filledReports, "Relatorios_Consolidados")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Exportar Excel Completo", fontSize = 12.sp)
                                    }
                                }
                            }

                            // Dynamic Search Bar
                            OutlinedTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                placeholder = { Text("Pesquisar por nome ou relatório...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchText.isNotEmpty()) {
                                        IconButton(onClick = { searchText = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Limpar")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .testTag("search_reports_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                )
                            )

                            // Scrollable filter chips row
                            val chipLabels = listOf(
                                "TODOS" to "Todos",
                                "HOJE" to "Hoje",
                                "SEMANA" to "Últimos 7 dias",
                                "MES" to "Este Mês",
                                "CUSTOM" to "Escolher Data..."
                            )
                            
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(chipLabels) { (key, label) ->
                                    val isSelected = selectedDateFilter == key
                                    val formattedLabel = if (key == "CUSTOM" && selectedDateFilter == "CUSTOM") {
                                        val df = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                                        "📅 " + df.format(Date(customFilterDateMillis))
                                    } else {
                                        label
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable {
                                                if (key == "CUSTOM") {
                                                    val cal = Calendar.getInstance().apply { timeInMillis = customFilterDateMillis }
                                                    android.app.DatePickerDialog(
                                                        context,
                                                        { _, year, month, dayOfMonth ->
                                                            val selectedCal = Calendar.getInstance().apply {
                                                                set(Calendar.YEAR, year)
                                                                set(Calendar.MONTH, month)
                                                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                            }
                                                            customFilterDateMillis = selectedCal.timeInMillis
                                                            selectedDateFilter = "CUSTOM"
                                                        },
                                                        cal.get(Calendar.YEAR),
                                                        cal.get(Calendar.MONTH),
                                                        cal.get(Calendar.DAY_OF_MONTH)
                                                    ).show()
                                                } else {
                                                    selectedDateFilter = key
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                            .testTag("filter_chip_$key")
                                    ) {
                                        Text(
                                            text = formattedLabel,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            if (filteredReports.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search, 
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = "Nenhum resultado",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Tente alterar os termos da busca ou limpar o filtro temporal.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    TextButton(onClick = {
                                        searchText = ""
                                        selectedDateFilter = "TODOS"
                                    }) {
                                        Text("Limpar Filtros")
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 80.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(filteredReports) { report ->
                                        ReportListItemCard(
                                            report = report,
                                            onCardClick = { onViewReportDetail(report) },
                                            onWhatsappClick = {
                                                val cleanPhone = report.respondentPhone.replace("[^0-9]".toRegex(), "")
                                                if (cleanPhone.isNotEmpty()) {
                                                    val uri = Uri.parse("https://wa.me/$cleanPhone")
                                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                                    context.startActivity(intent)
                                                } else {
                                                    Toast.makeText(context, "Telefone inválido ou não informado", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onPdfClick = {
                                                ExportUtils.exportToPDF(context, report)
                                            },
                                            onCsvClick = {
                                                ExportUtils.exportToCSV(context, report)
                                            },
                                            onDeleteClick = {
                                                viewModel.deleteFilledReport(report.id)
                                                Toast.makeText(context, "Resposta excluída", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // CUSTOM TEXT/LINK PASTE IMPORT DIALOG
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false; pasteText = "" },
            title = { Text("Importar via Link / Texto Copiado") },
            text = {
                Column {
                    Text(
                        text = "Cole o link 'relatoriofacil://' que você recebeu, ou simplesmente copie e cole todo o texto da resposta que a pessoa te enviou pelo WhatsApp.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .testTag("paste_area_input"),
                        placeholder = { Text("Cole aqui o link ou relatório recebido no WhatsApp...") },
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pasteText.trim().isNotEmpty()) {
                            val success = viewModel.parseAndImportText(pasteText)
                            if (success) {
                                Toast.makeText(context, "Código lido com sucesso!", Toast.LENGTH_SHORT).show()
                                showImportDialog = false
                                pasteText = ""
                            } else {
                                Toast.makeText(context, "O formato de texto ou link colado é inválido", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Por favor cole alguma informação", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Analisar & Importar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false; pasteText = "" }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// EMPTY STATE REUSABLE DESIGN
@Composable
fun EmptyStateLayout(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    ctaText: String,
    onCtaClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCtaClick) {
            Text(ctaText)
        }
    }
}

// TEMPLATE ITEM UI CARD
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateListItemCard(
    template: ReportTemplate,
    onFillClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val fields = JsonUtils.jsonToFields(template.fieldsJson)
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val dateStr = dateFormat.format(Date(template.createdAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("template_card_${template.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Criado em: $dateStr • ${fields.size} perguntas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("btn_delete_template_${template.id}")
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Deletar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Topics pill view (Horizontal/Flow)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                fields.forEach { f ->
                    val typeText = if (f.type == "CHOICE") "Seleção" else "Livre"
                    val bcolor = if (f.type == "CHOICE") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                    val tcolor = if (f.type == "CHOICE") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(bcolor)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("${f.label} ($typeText)", fontSize = 11.sp, color = tcolor, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onFillClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.FormatAlignLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Preencher", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onShareClick,
                    modifier = Modifier.weight(1.1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Compartilhar Link/Zap", fontSize = 12.sp)
                }
            }
        }
    }
}

// REPORT RESPONDENT CARD DESIGN
@Composable
fun ReportListItemCard(
    report: FilledReport,
    onCardClick: () -> Unit,
    onWhatsappClick: () -> Unit,
    onPdfClick: () -> Unit,
    onCsvClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd 'de' MMM, yyyy", Locale.getDefault())
    val dateStr = dateFormat.format(Date(report.filledAt))
    val answers = JsonUtils.jsonToAnswers(report.answersJson)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("report_card_${report.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // First Row: Header & Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = report.respondentName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ref: ${report.templateTitle}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Botão WhatsApp rápido
                    IconButton(
                        onClick = onWhatsappClick,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Filled.Phone, contentDescription = "Conversar WhatsApp")
                    }

                    // Botão de Excluir
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Filled.Close, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(10.dp))

            // Simplified quick answer display
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                answers.entries.take(2).forEach { (topic, ans) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = topic,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = ans,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.End
                        )
                    }
                }
                if (answers.size > 2) {
                    Text(
                        text = "+ ${answers.size - 2} respostas...",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Export Actions footer triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPdfClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onCsvClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Excel", fontSize = 11.sp)
                }

                Button(
                    onClick = onCardClick,
                    modifier = Modifier.weight(1.3f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("Ver Detalhes", fontSize = 11.sp)
                }
            }
        }
    }
}

// ==========================================
// SCREEN 2: TEMPLATE CREATOR
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateTemplateScreen(
    onBackClick: () -> Unit,
    onSaveClick: (String, List<ReportField>) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    
    // Lista de campos temporária
    var fieldsList = remember { mutableStateListOf<ReportField>() }
    
    // Diálogo aberto para novo campo
    var showAddFieldDialog by remember { mutableStateOf(false) }
    var fieldLabel by remember { mutableStateOf("") }
    var fieldType by remember { mutableStateOf("TEXT") } // "TEXT" or "CHOICE"

    // Variáveis temporárias para opções múltiplas
    var currentOptionInput by remember { mutableStateOf("") }
    var optionsList = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                text = "Criar Modelo de Relatório",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Template Name Input
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Nome / Título do Relatório") },
            placeholder = { Text("Ex: Fechamento de Caixa, Diário de Bordo") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_project_title")
        )

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tópicos do Relatório (${fieldsList.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Button(
                onClick = { 
                    showAddFieldDialog = true 
                    fieldLabel = ""
                    fieldType = "TEXT"
                    optionsList.clear()
                    currentOptionInput = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Novo Tópico")
            }
        }

        Spacer(Modifier.height(12.dp))

        // Added Topics List
        if (fieldsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhum tópico adicionado ainda.\nClique em '+ Novo Tópico' acima para configurar o que o relatório deve conter.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(fieldsList.toList()) { field ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = field.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (field.type == "CHOICE") "Resposta: Seleção Única (${field.options.joinToString(", ")})" else "Resposta: Escrita Livre",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = { fieldsList.remove(field) }) {
                                Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remover", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Save Template Button
        Button(
            onClick = {
                if (title.trim().isEmpty()) {
                    Toast.makeText(context, "Insira um título para o relatório", Toast.LENGTH_SHORT).show()
                } else if (fieldsList.isEmpty()) {
                    Toast.makeText(context, "Adicione pelo menos um tópico de resposta!", Toast.LENGTH_SHORT).show()
                } else {
                    onSaveClick(title.trim(), fieldsList.toList())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("btn_save_template"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Salvar Modelo de Relatório", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }

    // DIAOLOG TO CONFIGURE ADDING TOPICS
    if (showAddFieldDialog) {
        AlertDialog(
            onDismissRequest = { showAddFieldDialog = false },
            title = { Text("Adicionar Tópico") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = fieldLabel,
                        onValueChange = { fieldLabel = it },
                        label = { Text("Nome do Tópico / Pergunta") },
                        placeholder = { Text("Ex: Fez o check-in? Qual o valor?") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("topic_label_input")
                    )

                    Text("Como o usuário deve responder?", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { fieldType = "TEXT" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (fieldType == "TEXT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Escrever")
                        }

                        Button(
                            onClick = { fieldType = "CHOICE" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (fieldType == "CHOICE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Selecionar")
                        }
                    }

                    // Se for de Seleção, monta opções
                    if (fieldType == "CHOICE") {
                        Spacer(Modifier.height(8.dp))
                        Text("Opções de Seleção:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = currentOptionInput,
                                onValueChange = { currentOptionInput = it },
                                label = { Text("Digite uma opção") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_option_variant"),
                                singleLine = true
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (currentOptionInput.trim().isNotEmpty()) {
                                        optionsList.add(currentOptionInput.trim())
                                        currentOptionInput = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add")
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        
                        // Lista as opções cadastradas
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            optionsList.forEach { option ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(option, fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Remover",
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable { optionsList.remove(option) },
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fieldLabel.trim().isEmpty()) {
                            Toast.makeText(context, "Escreva a pergunta ou label do tópico", Toast.LENGTH_SHORT).show()
                        } else if (fieldType == "CHOICE" && optionsList.isEmpty()) {
                            Toast.makeText(context, "Insira ao menos uma opção para a escolha única!", Toast.LENGTH_SHORT).show()
                        } else {
                            fieldsList.add(
                                ReportField(
                                    label = fieldLabel.trim(),
                                    type = fieldType,
                                    options = optionsList.toList()
                                )
                            )
                            showAddFieldDialog = false
                        }
                    }
                ) {
                    Text("Adicionar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFieldDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// ==========================================
// SCREEN 3: FILL REPORT
// ==========================================
@Composable
fun FillReportScreen(
    templateTitle: String,
    fields: List<ReportField>,
    onBackClick: () -> Unit,
    onShareAndSave: (String, String, Map<String, String>, Long) -> Unit,
    viewModel: ReportViewModel
) {
    val context = LocalContext.current
    var respondentName by remember { mutableStateOf("") }
    var respondentPhone by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // Armazena as respostas associando Label -> Valor
    var answersMap = remember { mutableStateMapOf<String, String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                text = "Preencher Relatório",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Formulário Ativo:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(templateTitle, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Form Fields Container
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Seções Obrigatórias: Nome, Telefone e Data
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Suas Informações de Identificação:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    
                    OutlinedTextField(
                        value = respondentName,
                        onValueChange = { respondentName = it },
                        label = { Text("Nome Completo") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fill_respondent_name"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = respondentPhone,
                        onValueChange = { respondentPhone = it },
                        label = { Text("Número de WhatsApp / Celular") },
                        placeholder = { Text("Ex: 5511999999999") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fill_respondent_phone"),
                        singleLine = true
                    )

                    Spacer(Modifier.height(4.dp))

                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val dateFormatted = dateFormat.format(Date(selectedDateMillis))
                    
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val calendar = Calendar.getInstance().apply {
                                    timeInMillis = selectedDateMillis
                                }
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val selectedCal = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, year)
                                            set(Calendar.MONTH, month)
                                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                        }
                                        selectedDateMillis = selectedCal.timeInMillis
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Selecionar Data",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Data de Referência",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = dateFormatted,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = "Alterar",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Tópicos Customizados configurados pelo Criador
            item {
                Divider()
                Spacer(Modifier.height(8.dp))
                Text("Responda aos Tópicos Abaixo:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            items(fields) { field ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = field.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    if (field.type == "TEXT") {
                        OutlinedTextField(
                            value = answersMap[field.label] ?: "",
                            onValueChange = { answersMap[field.label] = it },
                            placeholder = { Text("Digite sua resposta...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("answer_${field.label}"),
                            maxLines = 4
                        )
                    } else {
                        // Choice type: Beautiful layout of options inside a row/column
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            field.options.forEach { option ->
                                val isSelected = answersMap[field.label] == option
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(
                                                alpha = 0.3f
                                            )
                                        )
                                        .clickable { answersMap[field.label] = option }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("opt_${field.label}_$option"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { answersMap[field.label] = option }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = option,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Send responses trigger Button
        Button(
            onClick = {
                if (respondentName.trim().isEmpty()) {
                    Toast.makeText(context, "Preencha o seu nome!", Toast.LENGTH_SHORT).show()
                } else if (respondentPhone.trim().isEmpty()) {
                    Toast.makeText(context, "Preencha o seu telefone/WhatsApp!", Toast.LENGTH_SHORT).show()
                } else if (answersMap.size < fields.size) {
                    // Alert that some fields are missing answers
                    Toast.makeText(context, "Responda a todos os tópicos!", Toast.LENGTH_SHORT).show()
                } else {
                    val answers = answersMap.toMap()
                    val dl = viewModel.generateResponseLink(templateTitle, respondentName, respondentPhone, answers, selectedDateMillis)
                    val dateFormattedStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(selectedDateMillis))
                    
                    // Formata a mensagem bonita para o WhatsApp
                    val formattedMsg = buildString {
                        append("📋 *RESPOSTAS DE RELATÓRIO*\n\n")
                        append("*Relatório:* $templateTitle\n")
                        append("👤 *Respondente:* $respondentName\n")
                        append("📞 *Contato:* $respondentPhone\n")
                        append("📅 *Data de Referência:* $dateFormattedStr\n")
                        append("\n--- RESPOSTAS DETALHADAS ---\n")
                        answers.forEach { (topic, ans) ->
                            append("• *${topic}:* $ans\n")
                        }
                        append("-----------------------------\n\n")
                        append("Abra este link no app para importar minhas respostas automaticamente:\n")
                        append(dl)
                    }

                    // Copia para a área de transferência
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = android.content.ClipData.newPlainText("RelatorioRespondido", formattedMsg)
                    clipboard.setPrimaryClip(clip)
                    
                    Toast.makeText(context, "Respostas formatadas copiadas!", Toast.LENGTH_SHORT).show()

                    // Compartilha diretamente
                    try {
                        val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, formattedMsg)
                        }
                        context.startActivity(Intent.createChooser(whatsappIntent, "Enviar Relatório para..."))
                    } catch (e: Exception) {
                        Toast.makeText(context, "App de compartilhamento não encontrado", Toast.LENGTH_SHORT).show()
                    }

                    // Chama callback para finalizar e salvar localmente se o criador estiver preenchendo
                    onShareAndSave(respondentName.trim(), respondentPhone.trim(), answers, selectedDateMillis)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("btn_enviar_relatorio"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Enviar Relatório via WhatsApp", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// SCREEN 4: VIEW DETAILED REPORT
// ==========================================
@Composable
fun ViewReportDetailScreen(
    report: FilledReport,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val answers = JsonUtils.jsonToAnswers(report.answersJson)
    val dateFormat = SimpleDateFormat("dd 'de' MMMM, yyyy - HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(report.filledAt))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                text = "Detalhes do Relatório",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Main Report Content displaying answers
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Respondent Metadata Block
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = report.templateTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Respondente: ${report.respondentName}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "WhatsApp/Contato: ${report.respondentPhone}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Preenchido em: $dateStr",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Answers Headers
            item {
                Text(
                    text = "Respostas dos Tópicos",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Iterates answers list
            items(answers.entries.toList()) { (topic, ans) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = topic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = ans,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Exporter Buttons Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { ExportUtils.exportToPDF(context, report) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Exportar PDF", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = { ExportUtils.exportToCSV(context, report) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Icon(Icons.Filled.TableChart, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Exportar Excel/CSV", fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                // Quick Copy Report text
                val textBuilder = buildString {
                    append("📋 *RELATÓRIO: ${report.templateTitle.uppercase()}*\n")
                    append("Respondente: ${report.respondentName}\n")
                    append("WhatsApp: ${report.respondentPhone}\n")
                    append("Data: $dateStr\n\n")
                    append("--- RESPOSTAS ---\n")
                    answers.forEach { (t, valAns) ->
                        append("• *${t}:* $valAns\n")
                    }
                    append("----------------")
                }
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("RelatorioCopiado", textBuilder)
                clipboard.setPrimaryClip(clip)

                Toast.makeText(context, "Texto copiado para a área de transferência!", Toast.LENGTH_SHORT).show()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, textBuilder)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartilhar via..."))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Compartilhar Resposta", fontWeight = FontWeight.Bold)
        }
    }
}
