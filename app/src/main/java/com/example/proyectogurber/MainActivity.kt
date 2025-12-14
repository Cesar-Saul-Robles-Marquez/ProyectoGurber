package com.example.proyectogurber

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// --- MODELOS DE DATOS ---

enum class LogType { HOME, STREET }

// Enum para los filtros de tiempo
enum class TimeFilter { DAY, WEEK, MONTH, YEAR }

data class Place(
    val id: String,
    val name: String,
    val colorHex: Long, // Color en formato 0xFFRRGGBB
    val icon: String = "🍔",
    val iconUri: Uri? = null // Icono personalizado (PNG/JPG)
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
    BurgerLog("l3", LocalDate.now().minusDays(2), LogType.STREET, 1, "b2", "Rápida y barata"),
    BurgerLog("l4", LocalDate.now(), LogType.HOME, 2, null, "Hamburguesas de hoy"),
)

// Colores para etiquetas
val TAG_COLORS = listOf(
    0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5,
    0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4, 0xFF009688, 0xFF4CAF50,
    0xFF8BC34A, 0xFFCDDC39, 0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800,
    0xFFFF5722, 0xFF795548, 0xFF9E9E9E, 0xFF607D8B, 0xFF000000
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
    var currentScreen by remember { mutableStateOf("home") } // "home", "stats", "manage"
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
                NavigationBarItem(
                    icon = { Icon(Icons.Outlined.Label, contentDescription = "Gestión") },
                    label = { Text("Gestión") },
                    selected = currentScreen == "manage",
                    onClick = { currentScreen = "manage" }
                )
            }
        },
        floatingActionButton = {
            if (currentScreen == "home") {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                "home" -> HomeScreen(logs, burgerDefs, places)
                "stats" -> StatsScreen(logs, burgerDefs, places)
                "manage" -> ManageTagsScreen(
                    places = places,
                    burgerDefs = burgerDefs,
                    onUpdatePlace = { updatedPlace ->
                        places = places.map { if (it.id == updatedPlace.id) updatedPlace else it }
                    },
                    onAddPlace = { newPlace ->
                        places = places + newPlace
                    },
                    onDeletePlace = { placeId ->
                        places = places.filter { it.id != placeId }
                    },
                    onUpdateBurgerDef = { updatedDef ->
                        burgerDefs = burgerDefs.map { if (it.id == updatedDef.id) updatedDef else it }
                    },
                    onDeleteBurgerDef = { defId ->
                        burgerDefs = burgerDefs.filter { it.id != defId }
                    }
                )
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

// --- LOGICA DE RECORTE (BITMAP CROP) ---

fun cropAndSaveImage(
    context: Context,
    bitmap: Bitmap,
    scale: Float,
    offset: Offset,
    viewWidth: Int,
    viewHeight: Int
): Uri? {
    return try {
        // El círculo de recorte es el 40% del tamaño menor de la vista (según UI)
        val circleRadius = min(viewWidth, viewHeight) / 2.5f
        val circleCenterX = viewWidth / 2f
        val circleCenterY = viewHeight / 2f

        // Calcular las dimensiones de la imagen escalada
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale

        // Posición de la imagen en pantalla (centrada + offset)
        val imageX = (viewWidth - scaledWidth) / 2f + offset.x
        val imageY = (viewHeight - scaledHeight) / 2f + offset.y

        // Coordenadas del círculo relativas a la imagen
        val cropX = (circleCenterX - circleRadius - imageX) / scale
        val cropY = (circleCenterY - circleRadius - imageY) / scale
        val cropSize = (circleRadius * 2) / scale

        // Asegurar límites seguros
        val finalX = max(0f, cropX).toInt()
        val finalY = max(0f, cropY).toInt()

        // Evitar que el ancho/alto exceda el bitmap original si el usuario hace zoom out excesivo
        val finalWidth = min((cropSize).toInt(), bitmap.width - finalX)
        val finalHeight = min((cropSize).toInt(), bitmap.height - finalY)

        if (finalWidth <= 0 || finalHeight <= 0) return null

        // Crear el bitmap recortado
        val croppedBitmap = Bitmap.createBitmap(bitmap, finalX, finalY, finalWidth, finalHeight)

        // Guardar en archivo
        val file = File(context.cacheDir, "cropped_icon_${System.currentTimeMillis()}.jpg")
        val stream = FileOutputStream(file)
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        stream.close()

        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// --- PANTALLA DE RECORTE DE IMAGEN (SIMPLE CROP) ---

@Composable
fun ImageCropperDialog(
    imageUri: Uri,
    onCropDone: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    // Cargar Bitmap
    val bitmap = remember(imageUri) {
        if (Build.VERSION.SDK_INT < 28) {
            MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
        } else {
            val source = ImageDecoder.createSource(context.contentResolver, imageUri)
            ImageDecoder.decodeBitmap(source)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Área de Recorte
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            viewSize = coordinates.size
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = max(0.5f, min(5f, scale * zoom))
                                offset += pan
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Recortar",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )

                    // Máscara Circular (Overlay)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val circleRadius = size.minDimension / 2.5f // Tamaño del círculo de recorte
                        val circleCenter = center

                        // Oscurecer todo excepto el círculo
                        clipPath(
                            path = Path().apply {
                                addOval(Rect(center = circleCenter, radius = circleRadius))
                            },
                            clipOp = ClipOp.Difference
                        ) {
                            drawRect(Color.Black.copy(alpha = 0.6f))
                        }

                        // Borde blanco del círculo
                        drawCircle(
                            color = Color.White,
                            radius = circleRadius,
                            center = circleCenter,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // Botones
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                        Text("Cancelar")
                    }
                    Button(onClick = {
                        // Guardado REAL del recorte
                        if (viewSize.width > 0 && viewSize.height > 0) {
                            val croppedUri = cropAndSaveImage(
                                context,
                                bitmap,
                                scale,
                                offset,
                                viewSize.width,
                                viewSize.height
                            )
                            if (croppedUri != null) {
                                onCropDone(croppedUri)
                            } else {
                                onCropDone(imageUri) // Fallback
                            }
                        } else {
                            onCropDone(imageUri)
                        }
                    }) {
                        Text("Usar Foto")
                    }
                }

                Text(
                    "Pellizca para zoom y arrastra para ajustar",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                )
            }
        }
    }
}

// --- PANTALLA DE GESTIÓN (TAGS) ---

@Composable
fun ManageTagsScreen(
    places: List<Place>,
    burgerDefs: List<BurgerDef>,
    onUpdatePlace: (Place) -> Unit,
    onAddPlace: (Place) -> Unit,
    onDeletePlace: (String) -> Unit,
    onUpdateBurgerDef: (BurgerDef) -> Unit,
    onDeleteBurgerDef: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Lugares, 1: Nombres
    var showEditPlaceDialog by remember { mutableStateOf<Place?>(null) }
    var showEditBurgerDialog by remember { mutableStateOf<BurgerDef?>(null) }
    var showNewPlaceDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Lugares") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Hamburguesas") })
        }

        if (selectedTab == 0) {
            // LISTA DE LUGARES
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(places) { place ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color.LightGray)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Icono
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(place.colorHex), CircleShape)
                                        .border(1.dp, Color.Gray, CircleShape)
                                        .clip(CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (place.iconUri != null) {
                                        AsyncImage(
                                            model = place.iconUri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(place.icon, fontSize = 20.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(place.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { showEditPlaceDialog = place }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.Gray)
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
                FloatingActionButton(
                    onClick = { showNewPlaceDialog = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo Lugar", tint = Color.White)
                }
            }
        } else {
            // LISTA DE HAMBURGUESAS
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(burgerDefs) { burger ->
                    val place = places.find { it.id == burger.placeId }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color.LightGray)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(burger.name, style = MaterialTheme.typography.titleMedium)
                                Text(place?.name ?: "Desconocido", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            IconButton(onClick = { showEditBurgerDialog = burger }) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGOS DE EDICIÓN ---

    if (showEditPlaceDialog != null || showNewPlaceDialog) {
        val isEditing = showEditPlaceDialog != null
        val initialPlace = showEditPlaceDialog ?: Place(
            id = System.currentTimeMillis().toString() + "p",
            name = "",
            colorHex = 0xFFFFC107
        )

        var name by remember { mutableStateOf(initialPlace.name) }
        var selectedColor by remember { mutableStateOf(initialPlace.colorHex) }
        var iconUri by remember { mutableStateOf(initialPlace.iconUri) }

        // Estado para el cropper
        var tempImageUri by remember { mutableStateOf<Uri?>(null) }
        var showCropper by remember { mutableStateOf(false) }

        val galleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri ->
                if (uri != null) {
                    tempImageUri = uri
                    showCropper = true
                }
            }
        )

        if (showCropper && tempImageUri != null) {
            ImageCropperDialog(
                imageUri = tempImageUri!!,
                onCropDone = { croppedUri ->
                    iconUri = croppedUri
                    showCropper = false
                },
                onDismiss = { showCropper = false }
            )
        }

        Dialog(onDismissRequest = {
            showEditPlaceDialog = null
            showNewPlaceDialog = false
        }) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text(if (isEditing) "Editar Lugar" else "Nuevo Lugar", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre del Lugar") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Color de Etiqueta", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Selector de Color Simple
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TAG_COLORS.take(5).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .border(if (selectedColor == color) 2.dp else 0.dp, Color.Black, CircleShape)
                                    .clickable { selectedColor = color }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TAG_COLORS.drop(5).take(5).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .border(if (selectedColor == color) 2.dp else 0.dp, Color.Black, CircleShape)
                                    .clickable { selectedColor = color }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Icono del Lugar (Opcional)", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            if (iconUri != null) {
                                AsyncImage(
                                    model = iconUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Text("Subir Logo")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showEditPlaceDialog = null; showNewPlaceDialog = false }) { Text("Cancelar") }
                        Button(onClick = {
                            val newPlaceData = initialPlace.copy(
                                name = name,
                                colorHex = selectedColor,
                                iconUri = iconUri
                            )
                            if (isEditing) onUpdatePlace(newPlaceData) else onAddPlace(newPlaceData)
                            showEditPlaceDialog = null
                            showNewPlaceDialog = false
                        }, enabled = name.isNotBlank()) {
                            Text("Guardar")
                        }
                    }
                }
            }
        }
    }

    if (showEditBurgerDialog != null) {
        val burger = showEditBurgerDialog!!
        var name by remember { mutableStateOf(burger.name) }

        AlertDialog(
            onDismissRequest = { showEditBurgerDialog = null },
            title = { Text("Editar Hamburguesa") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateBurgerDef(burger.copy(name = name))
                    showEditBurgerDialog = null
                }, enabled = name.isNotBlank()) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBurgerDialog = null }) { Text("Cancelar") }
            }
        )
    }
}

// --- PANTALLA PRINCIPAL (BITÁCORA) ---

@Composable
fun HomeScreen(
    logs: List<BurgerLog>,
    burgerDefs: List<BurgerDef>,
    places: List<Place>
) {
    // Estado para el filtro y la fecha seleccionada
    var timeFilter by remember { mutableStateOf(TimeFilter.YEAR) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    // Helpers para formatear y navegar
    val locale = Locale("es", "ES")

    val rangeText = remember(timeFilter, selectedDate) {
        when (timeFilter) {
            TimeFilter.DAY -> selectedDate.format(DateTimeFormatter.ofPattern("d 'de' MMMM yyyy", locale))
            TimeFilter.WEEK -> {
                val startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
                "${startOfWeek.format(formatter)} - ${endOfWeek.format(formatter)}"
            }
            TimeFilter.MONTH -> selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)).replaceFirstChar { it.uppercase() }
            TimeFilter.YEAR -> selectedDate.format(DateTimeFormatter.ofPattern("yyyy"))
        }
    }

    val filteredLogs = remember(logs, timeFilter, selectedDate) {
        logs.filter { log ->
            when (timeFilter) {
                TimeFilter.DAY -> log.date.isEqual(selectedDate)
                TimeFilter.WEEK -> {
                    val startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val endOfWeek = selectedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                    !log.date.isBefore(startOfWeek) && !log.date.isAfter(endOfWeek)
                }
                TimeFilter.MONTH -> log.date.month == selectedDate.month && log.date.year == selectedDate.year
                TimeFilter.YEAR -> log.date.year == selectedDate.year
            }
        }.sortedByDescending { it.date }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(selected = timeFilter == TimeFilter.DAY, onClick = { timeFilter = TimeFilter.DAY; selectedDate = LocalDate.now() }, label = { Text("Día") })
            FilterChip(selected = timeFilter == TimeFilter.WEEK, onClick = { timeFilter = TimeFilter.WEEK; selectedDate = LocalDate.now() }, label = { Text("Semana") })
            FilterChip(selected = timeFilter == TimeFilter.MONTH, onClick = { timeFilter = TimeFilter.MONTH; selectedDate = LocalDate.now() }, label = { Text("Mes") })
            FilterChip(selected = timeFilter == TimeFilter.YEAR, onClick = { timeFilter = TimeFilter.YEAR; selectedDate = LocalDate.now() }, label = { Text("Año") })
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    selectedDate = when(timeFilter) {
                        TimeFilter.DAY -> selectedDate.minusDays(1)
                        TimeFilter.WEEK -> selectedDate.minusWeeks(1)
                        TimeFilter.MONTH -> selectedDate.minusMonths(1)
                        TimeFilter.YEAR -> selectedDate.minusYears(1)
                    }
                }) { Icon(Icons.Default.ChevronLeft, contentDescription = "Anterior") }

                Text(text = rangeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                IconButton(onClick = {
                    selectedDate = when(timeFilter) {
                        TimeFilter.DAY -> selectedDate.plusDays(1)
                        TimeFilter.WEEK -> selectedDate.plusWeeks(1)
                        TimeFilter.MONTH -> selectedDate.plusMonths(1)
                        TimeFilter.YEAR -> selectedDate.plusYears(1)
                    }
                }) { Icon(Icons.Default.ChevronRight, contentDescription = "Siguiente") }
            }
        }

        if (filteredLogs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🍽️", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sin hamburguesas en este periodo", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredLogs) { log ->
                    LogCard(log, burgerDefs, places)
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
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
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(accentColor))

            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // ICONO DEL LUGAR (AQUI SE MUESTRA LA IMAGEN SUBIDA)
                Surface(
                    shape = CircleShape,
                    color = if (isHome) Color(0xFFC8E6C9) else Color(0xFFF5F5F5),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!isHome && place?.iconUri != null) {
                            AsyncImage(
                                model = place.iconUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(text = if (isHome) "🏡" else place?.icon ?: "🍔", fontSize = 24.sp)
                        }
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

            if (expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    if (log.note.isNotEmpty()) {
                        Text(text = "\"${log.note}\"", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Color.DarkGray)
                    }
                    if (log.photoUri != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AsyncImage(
                            model = log.photoUri,
                            contentDescription = "Foto de la hamburguesa",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                        )
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

    val tiers = mapOf(
        "S" to burgerDefs.filter { it.currentRating == 5 },
        "A" to burgerDefs.filter { it.currentRating == 4 },
        "B" to burgerDefs.filter { it.currentRating == 3 },
        "C" to burgerDefs.filter { it.currentRating <= 2 }
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
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

        PieChartSimple(logs, places)

        Spacer(modifier = Modifier.height(24.dp))
        Text("Tier List", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        tiers.forEach { (tier, items) ->
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 4.dp)) {
                Box(
                    modifier = Modifier.width(50.dp).fillMaxHeight().background(
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

                Column(modifier = Modifier.weight(1f).background(Color(0xFFF5F5F5), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)).padding(8.dp)) {
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
    val homeCount = logs.filter { it.type == LogType.HOME }.sumOf { it.quantity }
    val streetCounts = logs.filter { it.type == LogType.STREET }
        .groupingBy {
            val def = INITIAL_BURGER_DEFS.find { d -> d.id == it.burgerDefId }
            def?.placeId
        }
        .eachCount()

    val total = homeCount + streetCounts.values.sum()
    if (total == 0) return

    val slices = mutableListOf<Triple<Float, Color, String>>()

    if (homeCount > 0) {
        val sweep = (homeCount.toFloat() / total) * 360f
        slices.add(Triple(sweep, Color(0xFF2E7D32), "Caseras"))
    }

    streetCounts.forEach { (placeId, count) ->
        if (placeId != null) {
            val place = places.find { it.id == placeId }
            val sweep = (count.toFloat() / total) * 360f
            slices.add(Triple(sweep, Color(place?.colorHex ?: 0xFF000000), place?.name ?: "?"))
        }
    }

    Row(modifier = Modifier.fillMaxWidth().height(200.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(160.dp).weight(1f), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(160.dp)) {
                var startAngle = -90f
                slices.forEach { (sweep, color, _) ->
                    drawArc(color = color, startAngle = startAngle, sweepAngle = sweep, useCenter = true)
                    startAngle += sweep
                }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
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

    var isNewPlace by remember { mutableStateOf(false) }
    var newPlaceName by remember { mutableStateOf("") }
    var isNewBurger by remember { mutableStateOf(false) }
    var newBurgerName by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(0) }

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedImageUri = uri }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                selectedImageUri = tempPhotoUri
            }
        }
    )

    val isFormValid = remember(
        selectedTab, quantity, selectedPlaceId, selectedBurgerDefId,
        isNewPlace, newPlaceName, isNewBurger, newBurgerName, rating
    ) {
        if (selectedTab == LogType.HOME) {
            quantity.toIntOrNull() != null && quantity.toInt() > 0
        } else {
            val placeValid = if (isNewPlace) newPlaceName.isNotBlank() else selectedPlaceId.isNotEmpty()
            val burgerValid = if (isNewPlace || isNewBurger) newBurgerName.isNotBlank() else selectedBurgerDefId.isNotEmpty()
            val ratingValid = if (isNewPlace || isNewBurger) rating > 0 else true

            placeValid && burgerValid && ratingValid
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Nuevo Registro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFEEEEEE))) {
                    TabButton("De la Calle", selectedTab == LogType.STREET) { selectedTab = LogType.STREET }
                    TabButton("Casera", selectedTab == LogType.HOME) { selectedTab = LogType.HOME }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = selectedDate.format(DateTimeFormatter.ofPattern("dd 'de' MMMM yyyy")),
                    onValueChange = { },
                    label = { Text("Fecha") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Seleccionar fecha")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == LogType.HOME) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Cantidad *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = quantity.toIntOrNull() == null || quantity.toInt() <= 0
                    )
                } else {
                    Text("Lugar *", style = MaterialTheme.typography.labelLarge)
                    if (!isNewPlace) {
                        PlaceSelector(places, selectedPlaceId) { id ->
                            if (id == "NEW") isNewPlace = true else { selectedPlaceId = id; selectedBurgerDefId = "" }
                        }
                    } else {
                        OutlinedTextField(
                            value = newPlaceName,
                            onValueChange = { newPlaceName = it },
                            label = { Text("Nombre del Restaurante") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = newPlaceName.isBlank()
                        )
                        Button(onClick = { isNewPlace = false }, modifier = Modifier.align(Alignment.End)) { Text("Cancelar") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedPlaceId.isNotEmpty() || isNewPlace) {
                        Text("Hamburguesa *", style = MaterialTheme.typography.labelLarge)
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
                                modifier = Modifier.fillMaxWidth(),
                                isError = newBurgerName.isBlank()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Calificación ${if(isNewBurger || isNewPlace) "*" else ""}", style = MaterialTheme.typography.labelLarge)
                    Row {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if((isNewBurger || isNewPlace) && rating == 0) Color.Gray else Color(0xFFFFD700),
                                modifier = Modifier.size(32.dp).clickable { rating = star }
                            )
                        }
                    }
                    if((isNewBurger || isNewPlace) && rating == 0) {
                        Text("Calificación obligatoria para nuevas hamburguesas", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notas") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Foto (Opcional)", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val file = File.createTempFile("img_", ".jpg", context.cacheDir)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            tempPhotoUri = uri
                            cameraLauncher.launch(uri)
                        }
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cámara")
                    }

                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galería")
                    }
                }

                if (selectedImageUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Previsualización",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    )
                    TextButton(onClick = { selectedImageUri = null }) { Text("Eliminar foto") }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(
                        onClick = {
                            var finalPlaceId = selectedPlaceId
                            var finalBurgerId = selectedBurgerDefId

                            if (selectedTab == LogType.STREET) {
                                if (isNewPlace) {
                                    val p = onAddPlace(newPlaceName, 0xFFFFA000)
                                    finalPlaceId = p.id
                                }
                                if (isNewBurger || isNewPlace) {
                                    val b = onAddBurgerDef(finalPlaceId, newBurgerName, rating)
                                    finalBurgerId = b.id
                                } else {
                                    onUpdateBurgerRating(finalBurgerId, rating)
                                }
                            }

                            val log = BurgerLog(
                                id = System.currentTimeMillis().toString(),
                                date = selectedDate,
                                type = selectedTab,
                                quantity = quantity.toIntOrNull() ?: 1,
                                burgerDefId = if (selectedTab == LogType.STREET) finalBurgerId else null,
                                note = note,
                                photoUri = selectedImageUri
                            )
                            onSaveLog(log)

                        },
                        enabled = isFormValid
                    ) { Text("Guardar") }
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