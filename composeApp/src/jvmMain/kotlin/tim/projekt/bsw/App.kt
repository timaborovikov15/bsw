package tim.projekt.bsw

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.sql.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.awt.Desktop
import java.net.URI

@Composable
fun App() {
    val scope = rememberCoroutineScope()

    val isProVersion: MutableState<Boolean> = remember { mutableStateOf(checkLocalLicense()) }
    val licenseKeyInput: MutableState<String> = remember { mutableStateOf("") }
    val activationStatus: MutableState<String> = remember { mutableStateOf("") }
    val isCheckingKey: MutableState<Boolean> = remember { mutableStateOf(false) }
    val showActivationDialog: MutableState<Boolean> = remember { mutableStateOf(false) }

    val query: MutableState<String> = remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<String>() }
    val isIndexing: MutableState<Boolean> = remember { mutableStateOf(false) }
    val selectedFilter: MutableState<String> = remember { mutableStateOf("Все") }

    val primaryColor = Color(0xFF6200EE)

    MaterialTheme {
        Surface(color = Color(0xFFF5F5F7), modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("BSW Search", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                        if (isProVersion.value) {
                            Text("Версия: PRO (Лицензия активна ✓)", color = Color(0xFF4CAF50), fontSize = 12.sp)
                        } else {
                            Text("Версия: FREE (Ограничено 5 результатов, без фильтров)", color = Color.Red, fontSize = 12.sp)
                        }
                    }

                    if (!isProVersion.value) {
                        Button(
                            onClick = { showActivationDialog.value = true },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF9800)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("Активировать PRO", color = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Card(elevation = 4.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                        TextField(
                            value = query.value,
                            onValueChange = { query.value = it },
                            placeholder = { Text("Введите имя файла...") },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.textFieldColors(
                                backgroundColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        Button(
                            onClick = {
                                results.clear()
                                results.addAll(searchInDb(query.value, selectedFilter.value, isProVersion.value))
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = primaryColor),
                            enabled = !isIndexing.value && query.value.isNotBlank()
                        ) {
                            Text("Найти", color = Color.White)
                        }
                    }
                }

                Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Все", "Картинки", "Документы").forEach { filter ->
                        FilterChipCustom(
                            text = filter,
                            isSelected = selectedFilter.value == filter,
                            enabled = isProVersion.value || filter == "Все",
                            onClick = { if (isProVersion.value || filter == "Все") selectedFilter.value = filter }
                        )
                    }
                    if (!isProVersion.value) {
                        Text("🔒 Фильтры доступны только в PRO", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                    }

                    Spacer(Modifier.weight(1f))

                    OutlinedButton(onClick = {
                        isIndexing.value = true
                        scope.launch(Dispatchers.IO) { rebuildIndex(); isIndexing.value = false }
                    }, enabled = !isIndexing.value, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Text(if (isIndexing.value) " Индексация..." else " Обновить базу")
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(results) { path -> FileResultItem(path) }
                    }
                }

                if (!isProVersion.value && results.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        backgroundColor = Color(0xFFFFF3E0),
                        modifier = Modifier.fillMaxWidth().clickable { openUrl("https://gumroad.com") }
                    ) {
                        Text(
                            "Показано только 5 результатов. Купите PRO версию, чтобы снять ограничения! Кликните для покупки.",
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFFE65100),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (showActivationDialog.value) {
                AlertDialog(
                    onDismissRequest = { showActivationDialog.value = false },
                    title = { Text("Активация BSW Search PRO") },
                    text = {
                        Column {
                            Text("Введите лицензионный ключ, полученный после оплаты:")
                            OutlinedTextField(
                                value = licenseKeyInput.value,
                                onValueChange = { licenseKeyInput.value = it },
                                label = { Text("Лицензионный ключ") },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                singleLine = true
                            )
                            if (activationStatus.value.isNotBlank()) {
                                Text(activationStatus.value, color = Color.Red, modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                isCheckingKey.value = true
                                activationStatus.value = "Проверка..."
                                scope.launch(Dispatchers.IO) {
                                    val success = verifyLicenseOnline(licenseKeyInput.value)
                                    withContext(Dispatchers.Main) {
                                        isCheckingKey.value = false
                                        if (success) {
                                            saveLicenseLocally(licenseKeyInput.value)
                                            isProVersion.value = true
                                            showActivationDialog.value = false
                                        } else {
                                            activationStatus.value = "Неверный ключ активации!"
                                        }
                                    }
                                }
                            },
                            enabled = !isCheckingKey.value && licenseKeyInput.value.isNotBlank()
                        ) { Text("Активировать") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showActivationDialog.value = false }) { Text("Отмена") }
                    }
                )
            }
        }
    }
}
@Composable
fun FilterChipCustom(text: String, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(end = 8.dp).clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFF6200EE) else if (enabled) Color.White else Color(0xFFE0E0E0),
        border = if (isSelected || !enabled) null else BorderStroke(1.dp, Color.LightGray)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isSelected) Color.White else if (enabled) Color.Black else Color.Gray
        )
    }
}

@Composable
fun FileResultItem(path: String) {
    Card(elevation = 2.dp, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().clickable { openInExplorer(path) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.InsertDriveFile, null, tint = Color(0xFF6200EE), modifier = Modifier.size(32.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(File(path).name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(path, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
            }
        }
    }
}

val licenseFile = File(System.getProperty("user.home") + File.separator + ".bsw_license")

fun checkLocalLicense(): Boolean {
    return try {
        licenseFile.exists() && licenseFile.readText().trim().isNotBlank()
    } catch (e: Exception) {
        false
    }
}

fun saveLicenseLocally(key: String) {
    try { licenseFile.writeText(key) } catch (e: Exception) {}
}

suspend fun verifyLicenseOnline(key: String): Boolean {
    val cleanKey = key.trim().uppercase()

    // 1. Быстрый пропуск для теста (оставляем старый ключ)
    if (cleanKey == "TEST-KEY") return true

    // 2. Проверяем формат через регулярное выражение BSW-XXXX-XXXX-XXXX (где X - цифры)
    val regex = Regex("^BSW-\\d{4}-\\d{4}-\\d{4}$")
    if (!regex.matches(cleanKey)) return false

    // 3. Разделяем ключ по дефисам
    val parts = cleanKey.split("-")
    if (parts.size != 4) return false

    // Вытаскиваем блоки цифр
    val part1 = parts[1].toIntOrNull() ?: return false
    val part2 = parts[2].toIntOrNull() ?: return false
    val part3 = parts[3].toIntOrNull() ?: return false

    // Секретный математический паттерн проверки (чек-сумма)
    val check1 = (part1 * 7) % 9999 == part2
    val check2 = (part1 + part2) % 8888 == part3

    return check1 && check2
}


fun searchInDb(name: String, filter: String, isPro: Boolean): List<String> {
    val list = mutableListOf<String>()
    val currentFilter = if (isPro) filter else "Все"
    val limit = if (isPro) 500 else 5

    val extFilter = when (currentFilter) {
        "Картинки" -> " AND (path LIKE '%.jpg' OR path LIKE '%.png' OR path LIKE '%.jpeg')"
        "Документы" -> " AND (path LIKE '%.pdf' OR path LIKE '%.docx' OR path LIKE '%.txt')"
        else -> ""
    }
    try {
        val conn = connect()
        val pstmt = conn.prepareStatement("SELECT path FROM files WHERE path LIKE ? $extFilter LIMIT $limit")
        pstmt.setString(1, "%$name%")
        val rs = pstmt.executeQuery()
        while (rs.next()) { list.add(rs.getString("path")) }
        conn.close()
    } catch (e: Exception) { e.printStackTrace() }
    return list
}

fun connect(): Connection = DriverManager.getConnection("jdbc:sqlite:files_index.db")

fun rebuildIndex() {
    try {
        val conn = connect()
        conn.createStatement().use { it.execute("CREATE TABLE IF NOT EXISTS files (path TEXT)"); it.execute("DELETE FROM files") }
        conn.autoCommit = false
        val pstmt = conn.prepareStatement("INSERT INTO files VALUES (?)")
        Files.walkFileTree(Paths.get("C:\\Users"), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                pstmt.setString(1, file.toAbsolutePath().toString()); pstmt.addBatch(); return FileVisitResult.CONTINUE
            }
            override fun visitFileFailed(p: Path, e: IOException) = FileVisitResult.SKIP_SUBTREE
        })
        pstmt.executeBatch(); conn.commit(); conn.close()
    } catch (e: Exception) { e.printStackTrace() }
}

fun openInExplorer(path: String) {
    try { Runtime.getRuntime().exec("explorer.exe /select,\"$path\"") } catch (e: Exception) {}
}

fun openUrl(url: String) {
    try { Desktop.getDesktop().browse(URI(url)) } catch (e: Exception) { e.printStackTrace() }
}
