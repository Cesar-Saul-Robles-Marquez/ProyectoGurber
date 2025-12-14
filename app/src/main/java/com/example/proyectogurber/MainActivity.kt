package com.example.proyectogurber

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

// --- MODELOS DE DATOS ---

enum class LogType { HOME, STREET }

data class Place(
    val id: String,
    val name: String,
    val colorHex: Long, // Color en formato 0xFFRRGGBB
    val icon: String = "🍔"
)

data class BurgerDef(
    val id: String,
    val placeId: String,
    val name: String,
    var currentRating: Int
)

data class BurgerLog(
    val id: String,
    val date: LocalDate,
    val type: LogType,
    val quantity: Int, // Para caseras
    val burgerDefId: String?, // Para callejeras
    val note: String,
    val photoUri: Uri? = null
)

// --- DATOS INICIALES (MOCK) ---

val INITIAL_PLACES = listOf(
    Place("p1", "Carl's Jr.", 0xFFFFD700),
    Place("p2", "McDonalds", 0xFFDA291C),
    Place("p3", "La Burguesía", 0xFF2E4053)
)

val INITIAL_BURGER_DEFS = listOf(
    BurgerDef("b1", "p1", "Double Western", 5),
    BurgerDef("b2", "p2", "Big Mac", 3),
    BurgerDef("b3", "p3", "La Trufada", 4)
)

val INITIAL_LOGS = listOf(
    BurgerLog("l1", LocalDate.now().minusDays(15), LogType.STREET, 1, "b1", "Clásica e insuperable"),
    BurgerLog("l2", LocalDate.now().minusDays(10), LogType.HOME, 3, null, "Asado con los amigos"),
    BurgerLog("l3", LocalDate.now().minusDays(5), LogType.STREET, 1, "b2", "Rápida y barata"),
)

// --- MAIN ACTIVITY ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFFE65100), // Naranja Burger
                    secondary = Color(0xFF2E7D32), // Verde Casero
                    tertiary = Color(0xFF1565C0)
                )
            ) {
                BurgerTrackerApp()
            }
        }
    }
}

// --- APP COMPOSABLE PRINCIPAL ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BurgerTrackerApp() {
    // Estado Global
    var places by remember { mutableStateOf(INITIAL_PLACES) }
    var burgerDefs by remember { mutableStateOf(INITIAL_BURGER_DEFS) }
    var logs by remember { mutableStateOf(INITIAL_LOGS) }

    // Navegación simple por estado
    var currentScreen by remember { mutableStateOf("home") } // "home", "stats"
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🍔 BurgerLog",
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Home, contentDescription = "Bitácora") },
                    label = { Text("Bitácora") },
                    selected = currentScreen == "home",
                    onClick = { currentScreen = "home" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Rankeo") },
                    label = { Text("Rankeo") },
                    selected = currentScreen == "stats",
                    onClick = { currentScreen = "stats" }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (currentScreen == "home") {
                HomeScreen(logs, burgerDefs, places)
            } else {
                StatsScreen(logs, burgerDefs, places)
            }
        }

        if (showAddDialog) {
            AddBurgerDialog(
                places = places,
                burgerDefs = burgerDefs,
                onDismiss = { showAddDialog = false },
                onAddPlace = { name, color ->
                    val newPlace = Place(System.currentTimeMillis().toString() + "p", name, color)
                    places = places + newPlace
                    newPlace
                },
                onAddBurgerDef = { placeId, name, rating ->
                    val newDef = BurgerDef(System.currentTimeMillis().toString() + "b", placeId, name, rating)
                    burgerDefs = burgerDefs + newDef
                    newDef
                },
                onUpdateBurgerRating = { id, rating ->
                    burgerDefs = burgerDefs.map { if (it.id == id) it.copy(currentRating = rating) else it }
                },
                onSaveLog = { newLog ->
                    logs = listOf(newLog) + logs
                    showAddDialog = false
                }
            )
        }
    }
}

// --- PANTALLAS ---

@Composable
fun HomeScreen(
    logs: List<BurgerLog>,
    burgerDefs: List<BurgerDef>,
    places: List<Place>
) {
    var timeFilter by remember { mutableStateOf("year") } // day, month, year

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Filtros
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
            listOf("day" to "Día", "month" to "Mes", "year" to "Año").forEach { (key, label) ->
                FilterChip(
                    selected = timeFilter == key,
                    onClick = { timeFilter = key },
                    label = { Text(label) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        // Lista
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(logs) { log ->
                LogCard(log, burgerDefs, places)
            }
            item {
                Spacer(modifier = Modifier.height(80.dp)) // Espacio para el FAB
            }
        }
    }
}

@Composable
fun LogCard(log: BurgerLog, burgerDefs: List<BurgerDef>, places: List<Place>) {
    val isHome = log.type == LogType.HOME
    val def = if (!isHome) burgerDefs.find { it.id == log.burgerDefId } else null
    val place = if (!isHome) places.find { it.id == def?.placeId } else null

    val cardColor = if (isHome) Color(0xFFE8F5E9) else Color.White
    val accentColor = if (isHome) Color(0xFF2E7D32) else Color(place?.colorHex ?: 0xFF000000)

    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column {
            // Banda de color
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(accentColor))

            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // Icono
                Surface(
                    shape = CircleShape,
                    color = if (isHome) Color(0xFFC8E6C9) else Color(0xFFF5F5F5),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = if (isHome) "🏡" else place?.icon ?: "🍔", fontSize = 24.sp)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHome) "Caseras (${log.quantity})" else def?.name ?: "Desconocida",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                    Text(
                        text = if (isHome) "Hechas en casa" else place?.name ?: "Lugar desconocido",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = log.date.format(DateTimeFormatter.ofPattern("dd MMM")), fontSize = 12.sp, color = Color.Gray)
                    if (!isHome && def != null) {
                        Row {
                            repeat(def.currentRating) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Expandido
            if (expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    if (log.note.isNotEmpty()) {
                        Text(text = "\"${log.note}\"", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Color.DarkGray)
                    }
                    if (log.photoUri != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        // Nota: En una app real usarías Coil aquí.
                        // Text("📸 Foto adjunta (Implementar Coil para ver imagen real)")
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.LightGray), contentAlignment = Alignment.Center) {
                            Text("📸 Foto")
                        }
                    }
                }
            }
        }
    }
}

// --- PANTALLA DE ESTADÍSTICAS ---

@Composable
fun StatsScreen(logs: List<BurgerLog>, burgerDefs: List<BurgerDef>, places: List<Place>) {
    val totalBurgers = logs.sumOf { if (it.type == LogType.HOME) it.quantity else 1 }

    // Tier List Logic
    val tiers = mapOf(
        "S" to burgerDefs.filter { it.currentRating == 5 },
        "A" to burgerDefs.filter { it.currentRating == 4 },
        "B" to burgerDefs.filter { it.currentRating == 3 },
        "C" to burgerDefs.filter { it.currentRating <= 2 }
    )

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
        .verticalScroll(rememberScrollState())) {

        // Header
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total Consumido", style = MaterialTheme.typography.labelLarge)
                Text("$totalBurgers 🍔", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Distribución por Lugar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // Pie Chart Simple
        PieChartSimple(logs, places)

        Spacer(modifier = Modifier.height(24.dp))
        Text("Tier List", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        tiers.forEach { (tier, items) ->
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 4.dp)) {
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .fillMaxHeight()
                        .background(
                            when (tier) {
                                "S" -> Color(0xFFEF5350)
                                "A" -> Color(0xFFFFCA28)
                                "B" -> Color(0xFF9CCC65)
                                else -> Color(0xFFBDBDBD)
                            }, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tier, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp)
                }

                Column(modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .padding(8.dp)) {
                    if (items.isEmpty()) {
                        Text("-", color = Color.Gray, fontSize = 12.sp)
                    } else {
                        items.forEach { def ->
                            val place = places.find { it.id == def.placeId }
                            Text("• ${def.name} (${place?.name})", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun PieChartSimple(logs: List<BurgerLog>, places: List<Place>) {
    // Calculando datos
    val homeCount = logs.filter { it.type == LogType.HOME }.sumOf { it.quantity }
    val streetCounts = logs.filter { it.type == LogType.STREET }
        .groupingBy {
            val def = INITIAL_BURGER_DEFS.find { d -> d.id == it.burgerDefId } // Simplificación: usar lista global para buscar placeId rápido
            def?.placeId
        }
        .eachCount()

    val total = homeCount + streetCounts.values.sum()
    if (total == 0) return

    val slices = mutableListOf<Triple<Float, Color, String>>()

    // Home Slice
    if (homeCount > 0) {
        val sweep = (homeCount.toFloat() / total) * 360f
        slices.add(Triple(sweep, Color(0xFF2E7D32), "Caseras"))
    }

    // Street Slices
    streetCounts.forEach { (placeId, count) ->
        if (placeId != null) {
            val place = places.find { it.id == placeId }
            val sweep = (count.toFloat() / total) * 360f
            slices.add(Triple(sweep, Color(place?.colorHex ?: 0xFF000000), place?.name ?: "?"))
        }
    }

    Row(modifier = Modifier.fillMaxWidth().height(200.dp), verticalAlignment = Alignment.CenterVertically) {
        // Grafica
        Box(modifier = Modifier.size(160.dp).weight(1f), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(160.dp)) {
                var startAngle = -90f
                slices.forEach { (sweep, color, _) ->
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true
                    )
                    startAngle += sweep
                }
            }
        }
        // Leyenda
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            slices.forEach { (_, color, name) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(name, fontSize = 12.sp)
                }
            }
        }
    }
}

// --- DIALOGO DE REGISTRO ---

@Composable
fun AddBurgerDialog(
    places: List<Place>,
    burgerDefs: List<BurgerDef>,
    onDismiss: () -> Unit,
    onAddPlace: (String, Long) -> Place,
    onAddBurgerDef: (String, String, Int) -> BurgerDef,
    onUpdateBurgerRating: (String, Int) -> Unit,
    onSaveLog: (BurgerLog) -> Unit
) {
    var selectedTab by remember { mutableStateOf(LogType.STREET) }
    var quantity by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    var selectedPlaceId by remember { mutableStateOf("") }
    var selectedBurgerDefId by remember { mutableStateOf("") }

    // New Entry States
    var isNewPlace by remember { mutableStateOf(false) }
    var newPlaceName by remember { mutableStateOf("") }
    var isNewBurger by remember { mutableStateOf(false) }
    var newBurgerName by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Nuevo Registro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                // Tabs
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFEEEEEE))) {
                    TabButton("De la Calle", selectedTab == LogType.STREET) { selectedTab = LogType.STREET }
                    TabButton("Casera", selectedTab == LogType.HOME) { selectedTab = LogType.HOME }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == LogType.HOME) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Cantidad") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Street Logic
                    Text("Lugar", style = MaterialTheme.typography.labelLarge)
                    if (!isNewPlace) {
                        PlaceSelector(places, selectedPlaceId) { id ->
                            if (id == "NEW") isNewPlace = true else { selectedPlaceId = id; selectedBurgerDefId = "" }
                        }
                    } else {
                        OutlinedTextField(
                            value = newPlaceName,
                            onValueChange = { newPlaceName = it },
                            label = { Text("Nombre del Restaurante") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = { isNewPlace = false }, modifier = Modifier.align(Alignment.End)) { Text("Cancelar") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedPlaceId.isNotEmpty() || isNewPlace) {
                        Text("Hamburguesa", style = MaterialTheme.typography.labelLarge)
                        if (!isNewBurger && !isNewPlace) {
                            BurgerSelector(burgerDefs.filter { it.placeId == selectedPlaceId }, selectedBurgerDefId) { id ->
                                if (id == "NEW") {
                                    isNewBurger = true
                                    rating = 0
                                } else {
                                    selectedBurgerDefId = id
                                    rating = burgerDefs.find { it.id == id }?.currentRating ?: 0
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = newBurgerName,
                                onValueChange = { newBurgerName = it },
                                label = { Text("Nombre de la Hamburguesa") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Calificación", style = MaterialTheme.typography.labelLarge)
                    Row {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(32.dp).clickable { rating = star }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notas") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(onClick = {
                        // Lógica de guardado
                        var finalPlaceId = selectedPlaceId
                        var finalBurgerId = selectedBurgerDefId

                        if (selectedTab == LogType.STREET) {
                            if (isNewPlace) {
                                val p = onAddPlace(newPlaceName, 0xFFFFA000) // Color default naranja
                                finalPlaceId = p.id
                            }
                            if (isNewBurger || isNewPlace) {
                                val b = onAddBurgerDef(finalPlaceId, newBurgerName, rating)
                                finalBurgerId = b.id
                            } else {
                                // Update rating if changed
                                onUpdateBurgerRating(finalBurgerId, rating)
                            }
                        }

                        val log = BurgerLog(
                            id = System.currentTimeMillis().toString(),
                            date = LocalDate.now(),
                            type = selectedTab,
                            quantity = quantity.toIntOrNull() ?: 1,
                            burgerDefId = if (selectedTab == LogType.STREET) finalBurgerId else null,
                            note = note
                        )
                        onSaveLog(log)

                    }) { Text("Guardar") }
                }
            }
        }
    }
}

@Composable
fun TabButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun PlaceSelector(places: List<Place>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = places.find { it.id == selectedId }?.name ?: "Selecciona lugar..."

    Box(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).clickable { expanded = true }.padding(16.dp)) {
        Text(selectedName)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            places.forEach { place ->
                DropdownMenuItem(text = { Text(place.name) }, onClick = { onSelect(place.id); expanded = false })
            }
            DropdownMenuItem(text = { Text("+ Nuevo Lugar") }, onClick = { onSelect("NEW"); expanded = false })
        }
    }
}

@Composable
fun BurgerSelector(burgers: List<BurgerDef>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = burgers.find { it.id == selectedId }?.name ?: "Selecciona hamburguesa..."

    Box(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).clickable { expanded = true }.padding(16.dp)) {
        Text(selectedName)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            burgers.forEach { burger ->
                DropdownMenuItem(text = { Text(burger.name) }, onClick = { onSelect(burger.id); expanded = false })
            }
            DropdownMenuItem(text = { Text("+ Nueva Hamburguesa") }, onClick = { onSelect("NEW"); expanded = false })
        }
    }
}