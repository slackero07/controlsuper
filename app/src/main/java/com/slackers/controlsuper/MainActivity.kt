package com.slackers.controlsuper

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProductoCompra(
    val descripcion: String,
    val cantidad: Double,
    val presentacion: String,
    val precio: Double
) {
    val subtotal: Double
        get() = cantidad * precio
}

data class CompraMercado(
    val id: Int,
    val fecha: String,
    val supermercado: String,
    val productos: List<ProductoCompra>,
    val estado: String
) {
    val montoTotal: Double
        get() = productos.sumOf { it.subtotal }
}

enum class Pantalla {
    PRINCIPAL,
    FORMULARIO_COMPRA,
    VISUALIZAR_COMPRA
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppCompras()
            }
        }
    }
}

@Composable
fun AppCompras() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val compras = remember { mutableStateListOf<CompraMercado>() }

    var pantallaActual by remember { mutableStateOf(Pantalla.PRINCIPAL) }
    var compraSeleccionadaId by remember { mutableStateOf<Int?>(null) }
    var siguienteId by remember { mutableStateOf(1) }

    var mostrarDialogoNuevaCompra by remember { mutableStateOf(false) }
    var nuevoSupermercado by remember { mutableStateOf("") }

    var confirmarBorradoCompraId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        val comprasCargadas = cargarComprasDesdeJson(context)
        compras.clear()
        compras.addAll(comprasCargadas)

        siguienteId = if (compras.isEmpty()) 1 else (compras.maxOfOrNull { it.id } ?: 0) + 1
    }

    fun guardarSilencioso() {
        scope.launch {
            guardarComprasEnJson(context, compras.toList())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (pantallaActual) {
            Pantalla.PRINCIPAL -> {
                PantallaPrincipal(
                    compras = compras,
                    compraSeleccionadaId = compraSeleccionadaId,
                    onSeleccionarCompra = { id -> compraSeleccionadaId = id },
                    onAgregarCompra = { mostrarDialogoNuevaCompra = true },
                    onVerCompra = { id ->
                        compraSeleccionadaId = id
                        pantallaActual = Pantalla.VISUALIZAR_COMPRA
                    },
                    onEditarCompra = { id ->
                        compraSeleccionadaId = id
                        pantallaActual = Pantalla.FORMULARIO_COMPRA
                    },
                    onBorrarCompra = { id ->
                        confirmarBorradoCompraId = id
                    }
                )
            }

            Pantalla.FORMULARIO_COMPRA -> {
                val compraBase = compras.find { it.id == compraSeleccionadaId }

                if (compraBase != null) {
                    PantallaFormularioCompra(
                        compra = compraBase,
                        onVolver = {
                            pantallaActual = Pantalla.PRINCIPAL
                        },
                        onGuardarCompra = { compraActualizada ->
                            val indice = compras.indexOfFirst { it.id == compraActualizada.id }
                            if (indice >= 0) {
                                compras[indice] = compraActualizada
                                guardarSilencioso()
                            }
                            pantallaActual = Pantalla.PRINCIPAL
                        }
                    )
                }
            }

            Pantalla.VISUALIZAR_COMPRA -> {
                val compraBase = compras.find { it.id == compraSeleccionadaId }

                if (compraBase != null) {
                    PantallaVisualizarCompra(
                        compra = compraBase,
                        onVolver = {
                            pantallaActual = Pantalla.PRINCIPAL
                        },
                        onEditar = {
                            pantallaActual = Pantalla.FORMULARIO_COMPRA
                        }
                    )
                }
            }
        }

        if (mostrarDialogoNuevaCompra) {
            AlertDialog(
                onDismissRequest = { mostrarDialogoNuevaCompra = false },
                title = { Text("Agregar compra") },
                text = {
                    OutlinedTextField(
                        value = nuevoSupermercado,
                        onValueChange = { nuevoSupermercado = it },
                        label = { Text("Supermercado") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (nuevoSupermercado.isNotBlank()) {
                                val nuevaCompra = CompraMercado(
                                    id = siguienteId,
                                    fecha = obtenerFechaActual(),
                                    supermercado = nuevoSupermercado.trim(),
                                    productos = emptyList(),
                                    estado = "Abierta"
                                )

                                compras.add(0, nuevaCompra)
                                compraSeleccionadaId = siguienteId
                                siguienteId++
                                nuevoSupermercado = ""
                                mostrarDialogoNuevaCompra = false
                                guardarSilencioso()
                                pantallaActual = Pantalla.FORMULARIO_COMPRA
                            }
                        }
                    ) {
                        Text("Continuar")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            nuevoSupermercado = ""
                            mostrarDialogoNuevaCompra = false
                        }
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (confirmarBorradoCompraId != null) {
            AlertDialog(
                onDismissRequest = { confirmarBorradoCompraId = null },
                title = { Text("Confirmar borrado") },
                text = { Text("¿Seguro que querés borrar esta compra?") },
                confirmButton = {
                    Button(
                        onClick = {
                            val id = confirmarBorradoCompraId
                            if (id != null) {
                                val indice = compras.indexOfFirst { it.id == id }
                                if (indice >= 0) {
                                    compras.removeAt(indice)
                                    if (compraSeleccionadaId == id) {
                                        compraSeleccionadaId = null
                                    }
                                    guardarSilencioso()
                                }
                            }
                            confirmarBorradoCompraId = null
                        }
                    ) {
                        Text("Borrar")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { confirmarBorradoCompraId = null }
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun PantallaPrincipal(
    compras: List<CompraMercado>,
    compraSeleccionadaId: Int?,
    onSeleccionarCompra: (Int) -> Unit,
    onAgregarCompra: () -> Unit,
    onVerCompra: (Int) -> Unit,
    onEditarCompra: (Int) -> Unit,
    onBorrarCompra: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Compras de Supermercado",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onAgregarCompra,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Agregar Compra")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (compras.isEmpty()) {
            Text("Todavía no hay compras cargadas.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(compras) { _, compra ->
                    val seleccionada = compra.id == compraSeleccionadaId

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeleccionarCompra(compra.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (seleccionada) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${compra.fecha} - ${compra.supermercado}",
                                fontWeight = FontWeight.Bold
                            )

                            Text("Monto Total: ${formatearDinero(compra.montoTotal)}")
                            Text("Estado: ${compra.estado}")

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(onClick = { onVerCompra(compra.id) }) {
                                    Text("Ver")
                                }

                                Button(onClick = { onEditarCompra(compra.id) }) {
                                    Text("Editar")
                                }

                                Button(onClick = { onBorrarCompra(compra.id) }) {
                                    Text("Borrar")
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Version 1.0 04/2026 Desarrollado por: Charty/Slackero",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PantallaFormularioCompra(
    compra: CompraMercado,
    onVolver: () -> Unit,
    onGuardarCompra: (CompraMercado) -> Unit
) {
    val productosTemporales = remember(compra.id) {
        mutableStateListOf<ProductoCompra>().apply {
            addAll(compra.productos)
        }
    }

    var producto by remember(compra.id) { mutableStateOf("") }
    var cantidad by remember(compra.id) { mutableStateOf("") }
    var precio by remember(compra.id) { mutableStateOf("") }

    val opcionesPresentacion = listOf("KG", "LT", "UN", "CARTON", "PAQ", "BOTELLA")
    var presentacion by remember(compra.id) { mutableStateOf("KG") }
    var expandirPresentacion by remember(compra.id) { mutableStateOf(false) }

    var confirmarBorradoProductoIndice by remember(compra.id) { mutableStateOf<Int?>(null) }
    var mensajeError by remember(compra.id) { mutableStateOf("") }

    val totalAcumulado = productosTemporales.sumOf { it.subtotal }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Carga de Productos",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Fecha: ${compra.fecha}", color = Color.Cyan)
        Text("Supermercado: ${compra.supermercado}", color = Color.Cyan)
        Text("Estado: Abierta", color = Color.Red)

        HorizontalDivider()

        OutlinedTextField(
            value = producto,
            onValueChange = {
                producto = it
                mensajeError = ""
            },
            label = { Text("Producto") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = cantidad,
                onValueChange = {
                    cantidad = it
                    mensajeError = ""
                },
                label = { Text("Cantidad") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { expandirPresentacion = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Presentación: $presentacion")
                }

                DropdownMenu(
                    expanded = expandirPresentacion,
                    onDismissRequest = { expandirPresentacion = false }
                ) {
                    opcionesPresentacion.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(opcion) },
                            onClick = {
                                presentacion = opcion
                                expandirPresentacion = false
                            }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = precio,
            onValueChange = {
                precio = it
                mensajeError = ""
            },
            label = { Text("Precio") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val cantidadNumero = cantidad.replace(",", ".").toDoubleOrNull()
                    val precioNumero = precio.replace(",", ".").toDoubleOrNull()

                    if (producto.isBlank() || cantidadNumero == null || precioNumero == null) {
                        mensajeError = "Revisá producto, cantidad y precio."
                    } else {
                        productosTemporales.add(
                            ProductoCompra(
                                descripcion = producto.trim(),
                                cantidad = cantidadNumero,
                                presentacion = presentacion,
                                precio = precioNumero
                            )
                        )

                        producto = ""
                        cantidad = ""
                        precio = ""
                        presentacion = "KG"
                        mensajeError = ""
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Cargar Producto")
            }

            Button(
                onClick = {
                    val compraActualizada = compra.copy(
                        productos = productosTemporales.toList(),
                        estado = "Guardada"
                    )
                    onGuardarCompra(compraActualizada)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Guardar y Salir")
            }
        }

        if (mensajeError.isNotBlank()) {
            Text(
                text = mensajeError,
                color = MaterialTheme.colorScheme.error
            )
        }

        OutlinedTextField(
            value = formatearDinero(totalAcumulado),
            onValueChange = {},
            readOnly = true,
            label = { Text("Total acumulado") },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()

        Text(
            text = "Productos Cargados",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (productosTemporales.isEmpty()) {
            Text("Todavía no hay productos cargados.")
        } else {
            TablaProductosEditable(
                productos = productosTemporales,
                onBorrar = { indice ->
                    confirmarBorradoProductoIndice = indice
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onVolver,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver")
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Version 1.0 04/2026 Desarrollado por: Charty/Slackero",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (confirmarBorradoProductoIndice != null) {
        AlertDialog(
            onDismissRequest = { confirmarBorradoProductoIndice = null },
            title = { Text("Confirmar Borrado") },
            text = { Text("¿Seguro que querés borrar este producto?") },
            confirmButton = {
                Button(
                    onClick = {
                        val indice = confirmarBorradoProductoIndice
                        if (indice != null && indice in productosTemporales.indices) {
                            productosTemporales.removeAt(indice)
                        }
                        confirmarBorradoProductoIndice = null
                    }
                ) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmarBorradoProductoIndice = null }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun TablaProductosEditable(
    productos: List<ProductoCompra>,
    onBorrar: (Int) -> Unit
) {
    val scrollHorizontal = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollHorizontal)
    ) {
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            Text("ITEM", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("DESCRIPCION", modifier = Modifier.width(170.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("CANT", modifier = Modifier.width(70.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("PRESENTACION", modifier = Modifier.width(130.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("PRECIO", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("SUBTOTAL", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("BORRAR", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }

        HorizontalDivider()

        productos.reversed().forEachIndexed { indiceVista, producto ->
            val indiceReal = productos.lastIndex - indiceVista
            val itemNumero = productos.size - indiceVista

            Row(modifier = Modifier.padding(vertical = 6.dp)) {
                Text("$itemNumero.", modifier = Modifier.width(60.dp), color = MaterialTheme.colorScheme.onSurface)
                Text(producto.descripcion.uppercase(), modifier = Modifier.width(170.dp), color = MaterialTheme.colorScheme.onSurface)
                Text(formatearNumero(producto.cantidad), modifier = Modifier.width(70.dp), color = MaterialTheme.colorScheme.onSurface)
                Text(producto.presentacion, modifier = Modifier.width(130.dp), color = MaterialTheme.colorScheme.onSurface)
                Text(formatearDinero(producto.precio), modifier = Modifier.width(100.dp), color = MaterialTheme.colorScheme.onSurface)
                Text(formatearDinero(producto.subtotal), modifier = Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurface)

                Button(
                    onClick = { onBorrar(indiceReal) },
                    modifier = Modifier.width(100.dp)
                ) {
                    Text("Borrar")
                }
            }
        }
    }
}

@Composable
fun PantallaVisualizarCompra(
    compra: CompraMercado,
    onVolver: () -> Unit,
    onEditar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Visualizar Compra",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text("Fecha: ${compra.fecha}", color = Color.Cyan)
        Text("Supermercado: ${compra.supermercado}", color = Color.Cyan)
        Text("Estado: ${compra.estado}", color = Color.Red)

        OutlinedTextField(
            value = formatearDinero(compra.montoTotal),
            onValueChange = {},
            readOnly = true,
            label = { Text("Monto total") },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()

        if (compra.productos.isEmpty()) {
            Text("Esta compra no tiene productos cargados.")
        } else {
            TablaProductosSoloLectura(productos = compra.productos)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onEditar,
                modifier = Modifier.weight(1f)
            ) {
                Text("Editar")
            }

            Button(
                onClick = onVolver,
                modifier = Modifier.weight(1f)
            ) {
                Text("Volver")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Version 1.0 04/2026 Desarrollado por: Charty/Slackero",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TablaProductosSoloLectura(productos: List<ProductoCompra>) {
    val scrollHorizontal = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollHorizontal)
    ) {
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            Text("ITEM", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("DESCRIPCION", modifier = Modifier.width(170.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("CANT", modifier = Modifier.width(70.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("PRESENTACION", modifier = Modifier.width(130.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("PRECIO", modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("SUBTOTAL", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }

        HorizontalDivider()

        productos.reversed().forEachIndexed { indiceVista, producto ->
            val itemNumero = productos.size - indiceVista

            Row(modifier = Modifier.padding(vertical = 6.dp)) {
                Text("$itemNumero.", modifier = Modifier.width(60.dp), color = MaterialTheme.colorScheme.onSurface)
                Text(producto.descripcion.uppercase(), modifier = Modifier.width(170.dp), color = MaterialTheme.colorScheme.onSurface)
                Text(formatearNumero(producto.cantidad), modifier = Modifier.width(70.dp), color = MaterialTheme.colorScheme.onSurface)
                Text(producto.presentacion, modifier = Modifier.width(130.dp), color = MaterialTheme.colorScheme.onSurface)
                Text(formatearNumero(producto.precio), modifier = Modifier.width(100.dp), color = MaterialTheme.colorScheme.onSurface)
                Text(formatearNumero(producto.subtotal), modifier = Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

fun obtenerFechaActual(): String {
    val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return formato.format(Date())
}

fun formatearNumero(valor: Double): String {
    return if (valor % 1.0 == 0.0) {
        valor.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.2f", valor)
    }
}

//fun formatearDinero(valor: Double): String {
//    val numero = formatearNumero(valor)
//    return "$ $numero"
//}
fun formatearDinero(valor: Double): String {
    val formato = java.text.NumberFormat.getNumberInstance(Locale("es", "AR"))
    formato.minimumFractionDigits = 2
    formato.maximumFractionDigits = 2
    //return "$ " + formato.format(valor)
    return formato.format(valor)
}

fun obtenerArchivoJson(context: Context): File {
    return File(context.filesDir, "compras_supermercado.json")
}

fun guardarComprasEnJson(context: Context, compras: List<CompraMercado>) {
    try {
        val jsonArray = JSONArray()

        compras.forEach { compra ->
            val compraJson = JSONObject()
            compraJson.put("id", compra.id)
            compraJson.put("fecha", compra.fecha)
            compraJson.put("supermercado", compra.supermercado)
            compraJson.put("estado", compra.estado)

            val productosArray = JSONArray()
            compra.productos.forEach { producto ->
                val productoJson = JSONObject()
                productoJson.put("descripcion", producto.descripcion)
                productoJson.put("cantidad", producto.cantidad)
                productoJson.put("presentacion", producto.presentacion)
                productoJson.put("precio", producto.precio)
                productosArray.put(productoJson)
            }

            compraJson.put("productos", productosArray)
            jsonArray.put(compraJson)
        }

        obtenerArchivoJson(context).writeText(jsonArray.toString(), Charsets.UTF_8)
    } catch (_: Exception) {
    }
}

fun cargarComprasDesdeJson(context: Context): List<CompraMercado> {
    return try {
        val archivo = obtenerArchivoJson(context)
        if (!archivo.exists()) return emptyList()

        val contenido = archivo.readText(Charsets.UTF_8)
        if (contenido.isBlank()) return emptyList()

        val jsonArray = JSONArray(contenido)
        val lista = mutableListOf<CompraMercado>()

        for (i in 0 until jsonArray.length()) {
            val compraJson = jsonArray.getJSONObject(i)

            val productosArray = compraJson.optJSONArray("productos") ?: JSONArray()
            val productos = mutableListOf<ProductoCompra>()

            for (j in 0 until productosArray.length()) {
                val productoJson = productosArray.getJSONObject(j)
                productos.add(
                    ProductoCompra(
                        descripcion = productoJson.optString("descripcion", ""),
                        cantidad = productoJson.optDouble("cantidad", 0.0),
                        presentacion = productoJson.optString("presentacion", "UN"),
                        precio = productoJson.optDouble("precio", 0.0)
                    )
                )
            }

            lista.add(
                CompraMercado(
                    id = compraJson.optInt("id", 0),
                    fecha = compraJson.optString("fecha", ""),
                    supermercado = compraJson.optString("supermercado", ""),
                    productos = productos,
                    estado = compraJson.optString("estado", "Guardada")
                )
            )
        }

        lista
    } catch (_: Exception) {
        emptyList()
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewAppCompras() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        AppCompras()
    }
}
