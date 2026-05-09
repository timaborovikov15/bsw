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

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<String>() }
    var isIndexing by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("Все") }

    val primaryColor = Color(0xFF6200EE)
    val backgroundColor = Color(0xFFF5F5F7)

    MaterialTheme {
        Surface(color = backgroundColor, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp)) {
                // Заголовок
                Text(
                    "BSW Search",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
                Text("Мгновенный поиск файлов по всем пользователям", color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))

                // Панель поиска
                Card(elevation = 4.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Введите название файла...") },
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
                                results.addAll(searchInDb(query, selectedFilter))
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = primaryColor),
                            enabled = !isIndexing
                        ) {
                            Text("Найти", color = Color.White)
                        }
                    }
                }

                // Фильтры и управление базой
                Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Все", "Картинки", "Документы").forEach { filter ->
                        FilterChip(filter, selectedFilter == filter) { selectedFilter = filter }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            isIndexing = true
                            scope.launch(Dispatchers.IO) {
                                rebuildIndex()
                                isIndexing = false
                            }
                        },
                        enabled = !isIndexing,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isIndexing) "Индексация..." else "Обновить базу")
                    }
                }

                // Список результатов
                if (results.isEmpty() && !isIndexing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Ничего не найдено или база пуста", color = Color.LightGray)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(results) { path ->
                            FileResultItem(path)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(end = 8.dp).clickable { onClick() },
        elevation = if (isSelected) 2.dp else 0.dp,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFF6200EE) else Color.White,
        border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isSelected) Color.White else Color.Black,
            fontSize = 14.sp
        )
    }
}

@Composable
fun FileResultItem(path: String) {
    val file = File(path)
    val icon = when {
        path.lowercase().endsWith(".jpg") || path.lowercase().endsWith(".png") || path.lowercase().endsWith(".jpeg") -> Icons.Default.Image
        path.lowercase().endsWith(".pdf") || path.lowercase().endsWith(".docx") || path.lowercase().endsWith(".txt") -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }

    Card(
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable { openInExplorer(path) }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color(0xFF6200EE), modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(file.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Text(path, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
            }
        }
    }
}

// --- Логика базы данных и системы ---

fun connect(): Connection = DriverManager.getConnection("jdbc:sqlite:files_index.db")

fun rebuildIndex() {
    try {
        val conn = connect()
        conn.createStatement().use {
            it.execute("CREATE TABLE IF NOT EXISTS files (path TEXT)")
            it.execute("DELETE FROM files")
        }
        conn.autoCommit = false
        val pstmt = conn.prepareStatement("INSERT INTO files VALUES (?)")

        // Сканируем папку Users
        val root = Paths.get("C:\\Users")
        if (Files.exists(root)) {
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    pstmt.setString(1, file.toAbsolutePath().toString())
                    pstmt.addBatch()
                    return FileVisitResult.CONTINUE
                }
                override fun visitFileFailed(p: Path, e: IOException) = FileVisitResult.SKIP_SUBTREE
            })
        }

        pstmt.executeBatch()
        conn.commit()
        conn.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun searchInDb(name: String, filter: String): List<String> {
    val list = mutableListOf<String>()

    val extensionFilter = when (filter) {
        "Картинки" -> " AND (path LIKE '%.jpg' OR path LIKE '%.png' OR path LIKE '%.jpeg' OR path LIKE '%.gif' OR path LIKE '%.webp')"
        "Документы" -> " AND (path LIKE '%.pdf' OR path LIKE '%.docx' OR path LIKE '%.txt' OR path LIKE '%.xlsx' OR path LIKE '%.pptx' OR path LIKE '%.zip' OR path LIKE '%.rar')"
        else -> ""
    }

    try {
        val conn = connect()
        val sql = "SELECT path FROM files WHERE path LIKE ?$extensionFilter LIMIT 500"
        val pstmt: PreparedStatement = conn.prepareStatement(sql)
        pstmt.setString(1, "%$name%")
        val rs = pstmt.executeQuery()
        while (rs.next()) {
            list.add(rs.getString("path"))
        }
        conn.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun openInExplorer(path: String) {
    try {
        val file = File(path)
        if (file.exists()) {
            Runtime.getRuntime().exec("explorer.exe /select,${file.absolutePath}")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
