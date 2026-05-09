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

    MaterialTheme {
        Surface(color = Color(0xFFF5F5F7), modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp)) {
                Text("BSW Search", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                Text("Мгновенный поиск по всей системе", color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))

                // Панель поиска
                Card(elevation = 4.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                        TextField(
                            value = query,
                            onValueChange = { query = it },
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
                                results.addAll(searchInDb(query, selectedFilter))
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = primaryColor),
                            enabled = !isIndexing && query.isNotBlank()
                        ) {
                            Text("Найти", color = Color.White)
                        }
                    }
                }

                // Фильтры
                Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Все", "Картинки", "Документы").forEach { filter ->
                        FilterChip(filter, selectedFilter == filter) { selectedFilter = filter }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        isIndexing = true
                        scope.launch(Dispatchers.IO) {
                            rebuildIndex()
                            isIndexing = false
                        }
                    }, enabled = !isIndexing, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Text(if (isIndexing) " Индексация..." else " Обновить базу")
                    }
                }

                // Список
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results) { path -> FileResultItem(path) }
                }
            }
        }
    }
}

@Composable
fun FilterChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(end = 8.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFF6200EE) else Color.White,
        border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray)
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = if (isSelected) Color.White else Color.Black)
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

// --- Логика БД и Системы ---

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
        Files.walkFileTree(Paths.get("C:\\Users"), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                pstmt.setString(1, file.toAbsolutePath().toString())
                pstmt.addBatch()
                return FileVisitResult.CONTINUE
            }
            override fun visitFileFailed(p: Path, e: IOException) = FileVisitResult.SKIP_SUBTREE
        })
        pstmt.executeBatch()
        conn.commit()
        conn.close()
    } catch (e: Exception) { e.printStackTrace() }
}

fun searchInDb(name: String, filter: String): List<String> {
    val list = mutableListOf<String>()
    val extFilter = when (filter) {
        "Картинки" -> " AND (path LIKE '%.jpg' OR path LIKE '%.png' OR path LIKE '%.jpeg')"
        "Документы" -> " AND (path LIKE '%.pdf' OR path LIKE '%.docx' OR path LIKE '%.txt')"
        else -> ""
    }
    try {
        val conn = connect()
        val pstmt = conn.prepareStatement("SELECT path FROM files WHERE path LIKE ?$extFilter LIMIT 1000")
        pstmt.setString(1, "%$name%")
        val rs = pstmt.executeQuery()
        while (rs.next()) { list.add(rs.getString("path")) }
        conn.close()
    } catch (e: Exception) { e.printStackTrace() }
    return list
}

fun openInExplorer(path: String) {
    try { Runtime.getRuntime().exec("explorer.exe /select,\"$path\"") } catch (e: Exception) {}
}
