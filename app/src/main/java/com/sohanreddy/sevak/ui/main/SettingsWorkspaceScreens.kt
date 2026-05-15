package com.sohanreddy.sevak.ui.main

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohanreddy.sevak.data.HealthReport
import com.sohanreddy.sevak.data.LocalWorkspaceRepository
import com.sohanreddy.sevak.data.SaathiDocument
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PageBlue = Color(0xFF10233F)
private val BodyBlue = Color(0xFF566983)
private val MutedBlue = Color(0xFF8190A6)
private val CardWhite = Color(0xFFFBFDFF)
private val StrokeLight = Color(0xFFE1E9F4)
private val AccentPink = Color(0xFFF65987)
private val AccentBlue = Color(0xFF5D8DFF)
private val AccentGreen = Color(0xFF20B79B)
private val Danger = Color(0xFFE85A6A)

@Composable
fun DocumentManagerScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { LocalWorkspaceRepository(context) }
    var documents by remember { mutableStateOf(repository.getDocuments()) }
    var renameTarget by remember { mutableStateOf<SaathiDocument?>(null) }
    var renameText by remember { mutableStateOf("") }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val imported = repository.importDocument(uri)
            documents = repository.getDocuments()
            Toast.makeText(
                context,
                if (imported != null) "Document saved locally" else "Could not import document",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    WorkspacePage(
        title = "Documents",
        subtitle = "Local files Saathi can use later",
        contentPadding = contentPadding,
        onBack = onBack,
        action = {
            PrimaryActionButton(
                label = "Upload",
                color = AccentBlue,
                icon = { Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    uploadLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "text/*",
                            "image/*",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                    )
                }
            )
        }
    ) {
        if (documents.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Folder,
                    title = "No documents yet",
                    body = "Upload prescriptions, lab reports, or notes. They will stay on this device."
                )
            }
        } else {
            items(documents, key = { it.id }) { document ->
                DocumentCard(
                    document = document,
                    onToggleAi = { enabled ->
                        repository.setDocumentAiEnabled(document.id, enabled)
                        documents = repository.getDocuments()
                    },
                    onRename = {
                        renameTarget = document
                        renameText = document.name
                    },
                    onDelete = {
                        repository.deleteDocument(document.id)
                        documents = repository.getDocuments()
                    }
                )
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename document") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Document name") },
                    colors = workspaceTextFieldColors()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameTarget?.let { repository.renameDocument(it.id, renameText.ifBlank { it.name }) }
                        documents = repository.getDocuments()
                        renameTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ReportManagerScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onCreateReport: () -> Unit,
    onEditReport: (String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { LocalWorkspaceRepository(context) }
    var reports by remember { mutableStateOf(repository.getReports()) }

    WorkspacePage(
        title = "Reports",
        subtitle = "General health summaries stored locally",
        contentPadding = contentPadding,
        onBack = onBack,
        action = {
            PrimaryActionButton(
                label = "New",
                color = AccentPink,
                icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = onCreateReport
            )
        }
    ) {
        item {
            ReportTemplatePreview()
        }

        items(reports, key = { it.id }) { report ->
            ReportCard(
                report = report,
                onEdit = { onEditReport(report.id) },
                onDelete = {
                    repository.deleteReport(report.id)
                    reports = repository.getReports()
                }
            )
        }
    }
}

@Composable
fun ReportEditorScreen(
    reportId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { LocalWorkspaceRepository(context) }
    val initial = remember(reportId) {
        if (reportId == null || reportId == "new") repository.newBlankReport()
        else repository.getReport(reportId) ?: repository.newBlankReport()
    }

    var name by remember { mutableStateOf(initial.name) }
    var status by remember { mutableStateOf(initial.status) }
    var patientSummary by remember { mutableStateOf(initial.patientSummary) }
    var symptoms by remember { mutableStateOf(initial.symptoms) }
    var medications by remember { mutableStateOf(initial.medications) }
    var allergies by remember { mutableStateOf(initial.allergies) }
    var vitals by remember { mutableStateOf(initial.vitals) }
    var medicalHistory by remember { mutableStateOf(initial.medicalHistory) }
    var lifestyleNotes by remember { mutableStateOf(initial.lifestyleNotes) }
    var doctorNotes by remember { mutableStateOf(initial.doctorNotes) }
    var recommendations by remember { mutableStateOf(initial.recommendations) }
    var followUpPlan by remember { mutableStateOf(initial.followUpPlan) }

    WorkspacePage(
        title = if (reportId == "new") "Create report" else "Edit report",
        subtitle = "General health summary template",
        contentPadding = contentPadding,
        onBack = onBack,
        action = {
            PrimaryActionButton(
                label = "Save",
                color = AccentGreen,
                icon = { Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    repository.upsertReport(
                        initial.copy(
                            name = name.ifBlank { "Untitled health report" },
                            status = status.ifBlank { "Draft" },
                            patientSummary = patientSummary,
                            symptoms = symptoms,
                            medications = medications,
                            allergies = allergies,
                            vitals = vitals,
                            medicalHistory = medicalHistory,
                            lifestyleNotes = lifestyleNotes,
                            doctorNotes = doctorNotes,
                            recommendations = recommendations,
                            followUpPlan = followUpPlan
                        )
                    )
                    Toast.makeText(context, "Report saved locally", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            )
        }
    ) {
        item {
            FormField("Report name", name, { name = it }, singleLine = true)
            FormField("Status", status, { status = it }, singleLine = true)
            MetadataStrip(initial)
            FormField("Patient summary", patientSummary, { patientSummary = it })
            FormField("Symptoms", symptoms, { symptoms = it })
            FormField("Medications", medications, { medications = it })
            FormField("Allergies", allergies, { allergies = it })
            FormField("Vitals", vitals, { vitals = it })
            FormField("Medical history", medicalHistory, { medicalHistory = it })
            FormField("Lifestyle notes", lifestyleNotes, { lifestyleNotes = it })
            FormField("Doctor notes", doctorNotes, { doctorNotes = it })
            FormField("Recommendations", recommendations, { recommendations = it })
            FormField("Follow-up plan", followUpPlan, { followUpPlan = it })
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun WorkspacePage(
    title: String,
    subtitle: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    action: @Composable () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFDFEFF), Color(0xFFF7FAFF), Color(0xFFFDF7FB))
                )
            )
            .statusBarsPadding()
            .padding(contentPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, end = 20.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.92f))
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(46.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PageBlue)
                }
            }
            action()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        ) {
            Text(
                title,
                color = PageBlue,
                fontSize = 28.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = BodyBlue,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    color: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
        modifier = Modifier.heightIn(min = 46.dp)
    ) {
        icon()
        Spacer(Modifier.size(8.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun DocumentCard(
    document: SaathiDocument,
    onToggleAi: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    WorkspaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBubble(color = AccentBlue) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(document.name, color = PageBlue, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${document.mimeType} - ${formatBytes(document.sizeBytes)}", color = BodyBlue, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Added ${formatDate(document.createdAt)}", color = MutedBlue, fontSize = 12.sp, lineHeight = 17.sp)
                    Text("Updated ${formatDate(document.updatedAt)}", color = MutedBlue, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF2F6FF),
                border = BorderStroke(1.dp, StrokeLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Use in AI conversations",
                            color = PageBlue,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (document.aiEnabled) "AI can reference this file" else "AI will ignore this file",
                            color = BodyBlue,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = document.aiEnabled,
                        onCheckedChange = onToggleAi,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentBlue,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFBCC6D7)
                        )
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onRename,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PageBlue),
                    border = BorderStroke(1.dp, Color(0xFFB9C5D8)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Rename", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Danger)
                    Spacer(Modifier.size(6.dp))
                    Text("Delete", color = Danger, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ReportCard(
    report: HealthReport,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    WorkspaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBubble(color = AccentPink) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(report.name, color = PageBlue, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(report.templateName, color = AccentPink, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Created ${formatDate(report.createdAt)}", color = MutedBlue, fontSize = 12.sp, lineHeight = 17.sp)
                    Text("Updated ${formatDate(report.updatedAt)}", color = MutedBlue, fontSize = 12.sp, lineHeight = 17.sp)
                    StatusChip(report.status)
                }
            }
            Text(report.patientSummary.ifBlank { "No summary filled yet." }, color = BodyBlue, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F495F), contentColor = Color.White),
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Edit", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Danger)
                    Spacer(Modifier.size(6.dp))
                    Text("Delete", color = Danger, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = AccentGreen.copy(alpha = 0.13f)) {
        Text(
            status,
            color = AccentGreen,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ReportTemplatePreview() {
    WorkspaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Report template", color = PageBlue, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("General health summary", color = AccentBlue, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Ready for future AI-filled fields: patient summary, symptoms, medications, allergies, vitals, medical history, lifestyle notes, doctor notes, recommendations, and follow-up plan.",
                color = BodyBlue,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            HorizontalDivider(color = StrokeLight)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Metadata", "Clinical notes", "Care plan").forEach { label ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF0F5FF)
                    ) {
                        Text(
                            label,
                            color = BodyBlue,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    WorkspaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconBubble(color = AccentBlue) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Text(title, color = PageBlue, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(body, color = BodyBlue, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun WorkspaceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CardWhite,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.9f)),
        tonalElevation = 1.dp,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun IconBubble(color: Color, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(color, RoundedCornerShape(17.dp)),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
        singleLine = singleLine,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = PageBlue,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold
        ),
        colors = workspaceTextFieldColors(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .heightIn(min = if (singleLine) 58.dp else 112.dp),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun MetadataStrip(report: HealthReport) {
    WorkspaceCard {
        Text("Metadata", color = PageBlue, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text("Template: ${report.templateName}", color = BodyBlue, fontSize = 14.sp, lineHeight = 20.sp)
        Text("Created: ${formatDate(report.createdAt)}", color = BodyBlue, fontSize = 14.sp, lineHeight = 20.sp)
        Text("Last updated: ${formatDate(report.updatedAt)}", color = BodyBlue, fontSize = 14.sp, lineHeight = 20.sp)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun workspaceTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PageBlue,
    unfocusedTextColor = PageBlue,
    focusedLabelColor = AccentBlue,
    unfocusedLabelColor = MutedBlue,
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = Color(0xFFB4BDCA),
    cursorColor = AccentBlue,
    focusedContainerColor = Color.White.copy(alpha = 0.72f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.72f)
)

private fun formatDate(raw: String): String {
    return runCatching {
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
        Instant.parse(raw).atZone(ZoneId.systemDefault()).format(formatter)
    }.getOrDefault("Recently")
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
        bytes >= 1_000 -> "${bytes / 1_000} KB"
        else -> "$bytes B"
    }
}
