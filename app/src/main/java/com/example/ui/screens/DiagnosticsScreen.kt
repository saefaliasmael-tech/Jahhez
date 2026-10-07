package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.FirebaseBootstrapManager
import com.example.util.DiagnosticLogEntry
import com.example.util.DiagnosticsLogger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allLogs by DiagnosticsLogger.logsFlow.collectAsState()

    var selectedTagFilter by remember { mutableStateOf<String?>("ALL") }
    var isRunningSync by remember { mutableStateOf(false) }

    val filteredLogs = remember(allLogs, selectedTagFilter) {
        val targetedTags = setOf("JahezBootstrap", "JahezSync", "JahezRemote")
        val relevant = allLogs.filter { it.tag in targetedTags }
        if (selectedTagFilter == null || selectedTagFilter == "ALL") {
            relevant
        } else {
            relevant.filter { it.tag == selectedTagFilter }
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to latest log when logs update
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("سجل التشخيص (Diagnostics)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = "${filteredLogs.size} رسائل مسجلة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val formatted = filteredLogs.joinToString("\n") { it.toLogLine() }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Jahez Diagnostics", formatted))
                            Toast.makeText(context, "تم نسخ السجلات إلى الحافظة", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ السجلات")
                    }
                    IconButton(
                        onClick = {
                            DiagnosticsLogger.clear()
                            Toast.makeText(context, "تم مسح السجلات", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter chips row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf("ALL" to "الكل", "JahezBootstrap" to "Bootstrap", "JahezSync" to "Sync", "JahezRemote" to "Remote").forEach { (tag, label) ->
                    FilterChip(
                        selected = selectedTagFilter == tag,
                        onClick = { selectedTagFilter = tag },
                        label = { Text(label) }
                    )
                }
            }

            // Quick trigger button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اختبار Bootstrap & Sync فوري:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )

                    Button(
                        onClick = {
                            if (!isRunningSync) {
                                isRunningSync = true
                                coroutineScope.launch {
                                    try {
                                        val bootstrapManager = FirebaseBootstrapManager.getInstance(context)
                                        bootstrapManager.ensureBootstrappedAndSync()
                                    } catch (e: Exception) {
                                        DiagnosticsLogger.e("JahezSync", "Manual trigger error: ${e.message}", e)
                                    } finally {
                                        isRunningSync = false
                                    }
                                }
                            }
                        },
                        enabled = !isRunningSync,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        if (isRunningSync) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("جاري التشغيل...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تشغيل الآن", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Logs console display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
                    .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد رسائل مسجلة حالياً.\nاضغط 'تشغيل الآن' أو قم بإضافة/مزامنة منتج لرؤية الـLogs المباشرة.",
                            color = Color(0xFFAAAAAA),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { entry ->
                            LogItemView(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItemView(entry: DiagnosticLogEntry) {
    val tagColor = when (entry.tag) {
        "JahezBootstrap" -> Color(0xFF64B5F6) // Light Blue
        "JahezSync" -> Color(0xFF81C784)      // Light Green
        "JahezRemote" -> Color(0xFFFFB74D)    // Orange
        else -> Color(0xFFE0E0E0)
    }

    val levelColor = when (entry.level) {
        "E" -> Color(0xFFEF5350) // Red
        "W" -> Color(0xFFFFCA28) // Yellow
        "I" -> Color(0xFF81D4FA) // Cyan
        else -> Color(0xFFB0BEC5)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.formattedTime(),
                color = Color(0xFF757575),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = entry.level,
                color = levelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = entry.tag,
                color = tagColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = entry.message,
            color = if (entry.level == "E") Color(0xFFFF8A80) else Color(0xFFEEEEEE),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp, top = 1.dp)
        )
    }
}
