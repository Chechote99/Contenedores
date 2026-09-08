package com.contenedores.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = (application as ContenedoresApplication).database
        setContent { ContenedoresApp(db) }
    }
}

@Composable
fun ContenedoresApp(db: AppDatabase) {
    val vm: MainViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(db) as T
    })
    val version by vm.version.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var showOpen by remember { mutableStateOf(false) }
    var showService by remember { mutableStateOf(false) }
    var showFinishTrip by remember { mutableStateOf(false) }
    var showFinishDay by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(version, vm.error) { vm.error?.let { showError = it } }

    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("Hoy", "Calendario", "Estadísticas", "Facturación", "Ajustes").forEachIndexed { i, label ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(label) })
                    }
                }
            }
        ) { padding ->
            Surface(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> HoyScreen(vm, onOpen = { showOpen = true }, onService = { showService = true }, onFinishTrip = { showFinishTrip = true }, onFinishDay = { showFinishDay = true })
                    1 -> CalendarScreen(vm)
                    2 -> StatsScreen(vm)
                    3 -> BillingScreen(vm)
                    else -> SettingsScreen()
                }
            }
        }
    }

    if (showOpen) OpenJourneyDialog(onDismiss = { showOpen = false }, onConfirm = { od -> showOpen = false; vm.abrirJornada(od) })
    if (showService) ServiceDialog(onDismiss = { showService = false }, onConfirm = { t,c,r,p,d -> showService = false; vm.iniciarServicio(t,c,r,p,d) })
    if (showFinishTrip) FinishTripDialog(onDismiss = { showFinishTrip = false }, onConfirm = { km -> showFinishTrip = false; vm.finalizarViaje(km) })
    if (showFinishDay) FinishDayDialog(vm.jornada, onDismiss = { showFinishDay = false }, onConfirm = { od, descanso -> showFinishDay = false; vm.finalizarJornada(od, descanso) })
    showError?.let { msg -> AlertDialog(onDismissRequest = { showError = null }, title = { Text("Aviso") }, text = { Text(msg) }, confirmButton = { TextButton(onClick = { showError = null }) { Text("OK") } }) }
}

@Composable
private fun HoyScreen(vm: MainViewModel, onOpen: () -> Unit, onService: () -> Unit, onFinishTrip: () -> Unit, onFinishDay: () -> Unit) {
    val j = vm.jornada
    val v = vm.viaje
    val s = vm.servicio
    val periodCount = vm.serviciosPeriodo.size
    val total = billingTotal(periodCount, listOf(
        TramoTarifaEntity(desdeServicio=1,hastaServicio=130,importeCentimos=400),
        TramoTarifaEntity(desdeServicio=131,hastaServicio=150,importeCentimos=1500),
        TramoTarifaEntity(desdeServicio=151,hastaServicio=180,importeCentimos=2000)
    ))
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("HOY · ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM"))}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (j == null) {
            item { Card { Column(Modifier.padding(16.dp)) { Text("SIN JORNADA", fontWeight=FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("No hay una jornada abierta."); Spacer(Modifier.height(16.dp)); Button(onClick=onOpen, modifier=Modifier.fillMaxWidth()) { Text("+ ABRIR JORNADA") } } } }
        } else {
            item { Card { Column(Modifier.padding(16.dp)) {
                Text("🟢 JORNADA EN CURSO", fontWeight=FontWeight.Bold)
                Text("Inicio ${formatTime(j.inicio)}")
                Text("Odómetro ${j.odometroInicial} km")
                Spacer(Modifier.height(8.dp))
                Text("${vm.serviciosHoy.size} servicios hoy · ${euro(total)} periodo", style=MaterialTheme.typography.titleMedium)
            } } }
            if (v == null) item { Button(onClick=vm::iniciarViaje, modifier=Modifier.fillMaxWidth()) { Text("+ INICIAR VIAJE") } }
            else item { Card { Column(Modifier.padding(16.dp)) {
                Text("VIAJE ${v.numero.toString().padStart(2,'0')}", fontWeight=FontWeight.Bold)
                Text("${vm.serviciosPeriodo.size} servicios periodo")
                Text(if (v.kmViaje != null) "${v.kmViaje} km" else "Km del viaje pendientes")
                Spacer(Modifier.height(12.dp))
                if (s == null) Button(onClick=onService, modifier=Modifier.fillMaxWidth()) { Text("+ AÑADIR SERVICIO") }
                else { Text("SERVICIO EN CURSO · ${s.tipo}", fontWeight=FontWeight.Bold); Spacer(Modifier.height(8.dp)); Button(onClick=vm::finalizarServicio, modifier=Modifier.fillMaxWidth()) { Text("FINALIZAR SERVICIO") }; Spacer(Modifier.height(8.dp)); OutlinedButton(onClick=vm::cancelarServicio, modifier=Modifier.fillMaxWidth()) { Text("CANCELAR SERVICIO") } }
                Spacer(Modifier.height(8.dp)); OutlinedButton(onClick=onFinishTrip, modifier=Modifier.fillMaxWidth()) { Text("FINALIZAR VIAJE") }
            } } }
            item { OutlinedButton(onClick=onFinishDay, modifier=Modifier.fillMaxWidth()) { Text("FINALIZAR JORNADA") } }
        }
    }
}

@Composable
private fun CalendarScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("CALENDARIO", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        vm.jornada?.let { Text("Hoy: ${it.fecha} · ${vm.serviciosHoy.size} servicios") } ?: Text("Selecciona una jornada desde tus datos históricos.")
        Spacer(Modifier.height(12.dp))
        Text("La primera versión mantiene el calendario centrado en el registro diario; el historial detallado se ampliará sobre esta misma base.")
    }
}

@Composable
private fun StatsScreen(vm: MainViewModel) {
    val services = vm.serviciosPeriodo
    val puestas = services.count { it.tipo == "PUESTA" }
    val retiradas = services.count { it.tipo == "RETIRADA" }
    val cambios = services.count { it.tipo == "CAMBIO" }
    val km = vm.jornada?.let { j -> j.odometroFinal?.minus(j.odometroInicial) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("ESTADÍSTICAS", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
        Text("Periodo 16 → 15")
        Metric("Servicios", services.size.toString())
        Metric("Puestas", puestas.toString())
        Metric("Retiradas", retiradas.toString())
        Metric("Cambios", cambios.toString())
        Metric("Km reales", km?.toString() ?: "—")
        Metric("Facturación", euro(services.sumOf { it.importeCentimos ?: 0L }))
    }
}

@Composable
private fun BillingScreen(vm: MainViewModel) {
    val (start,end) = billingPeriod(LocalDate.now())
    val count = vm.serviciosPeriodo.size
    val tiers = listOf(TramoTarifaEntity(desdeServicio=1,hastaServicio=130,importeCentimos=400),TramoTarifaEntity(desdeServicio=131,hastaServicio=150,importeCentimos=1500),TramoTarifaEntity(desdeServicio=151,hastaServicio=180,importeCentimos=2000))
    val next = count + 1
    val nextTariff = tariffFor(next, tiers)
    val remaining = tiers.firstOrNull { next in it.desdeServicio..it.hastaServicio }?.hastaServicio?.minus(count)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("FACTURACIÓN", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
        Text("${start} → ${end}")
        Metric("Servicios", count.toString())
        Metric("Total", euro(billingTotal(count, tiers)))
        Metric("Próximo servicio", next.toString())
        Metric("Tarifa", nextTariff?.let(::euro) ?: "No configurada")
        Metric("Faltan para siguiente límite", remaining?.toString() ?: "—")
        HorizontalDivider()
        Text("Desglose", fontWeight=FontWeight.Bold)
        Text("1–130 · 4 €")
        Text("131–150 · 15 €")
        Text("151–180 · 20 €")
    }
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("AJUSTES", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
        Card { Column(Modifier.padding(16.dp)) { Text("FACTURACIÓN", fontWeight=FontWeight.Bold); Text("Tramos configurados: 1–130 = 4 €, 131–150 = 15 €, 151–180 = 20 €") } }
        Card { Column(Modifier.padding(16.dp)) { Text("DATOS", fontWeight=FontWeight.Bold); Text("La base de datos se guarda localmente en el teléfono. La exportación/backup se incorpora en la siguiente iteración de esta V1.") } }
        Card { Column(Modifier.padding(16.dp)) { Text("MAPS", fontWeight=FontWeight.Bold); Text("Puedes abrir Google Maps desde enlaces de direcciones cuando haya datos configurados.") } }
    }
}

@Composable private fun Metric(label: String, value: String) { Card { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement=Arrangement.SpaceBetween) { Text(label); Text(value, fontWeight=FontWeight.Bold) } } }

@Composable
private fun OpenJourneyDialog(onDismiss:()->Unit,onConfirm:(Int)->Unit) {
    var od by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("ABRIR JORNADA")},text={OutlinedTextField(value=od,onValueChange={od=it.filter(Char::isDigit)},label={Text("Odómetro inicial")},singleLine=true)},confirmButton={Button(onClick={od.toIntOrNull()?.let(onConfirm) ?: onDismiss()}){Text("ABRIR")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
}

@Composable
private fun ServiceDialog(onDismiss:()->Unit,onConfirm:(String,Int?,Int?,Int?,String)->Unit) {
    var type by remember { mutableStateOf("PUESTA") }; var cap by remember { mutableStateOf<Int?>(null) }; var rem by remember { mutableStateOf<Int?>(null) }; var put by remember { mutableStateOf<Int?>(null) }; var address by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("AÑADIR SERVICIO")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("PUESTA","RETIRADA","CAMBIO").forEach{FilterChip(selected=type==it,onClick={type=it},label={Text(it)})}}
        if(type!="CAMBIO") Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(3,6,9).forEach{FilterChip(selected=cap==it,onClick={cap=it},label={Text("$it m³")})}}
        else { Text("Retirada"); Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(3,6,9).forEach{FilterChip(selected=rem==it,onClick={rem=it},label={Text("$it")})}}; Text("Puesta"); Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(3,6,9).forEach{FilterChip(selected=put==it,onClick={put=it},label={Text("$it")})}} }
        OutlinedTextField(value=address,onValueChange={address=it},label={Text("Dirección / obra")},singleLine=true)
    }},confirmButton={Button(onClick={onConfirm(type,cap,rem,put,address)}){Text("INICIAR")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
}

@Composable private fun FinishTripDialog(onDismiss:()->Unit,onConfirm:(Double?)->Unit){var km by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text("FINALIZAR VIAJE")},text={OutlinedTextField(value=km,onValueChange={km=it.replace(',','.')},label={Text("Km del viaje")},singleLine=true)},confirmButton={Button(onClick={onConfirm(km.toDoubleOrNull())}){Text("FINALIZAR")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})}

@Composable private fun FinishDayDialog(j:JornadaEntity?,onDismiss:()->Unit,onConfirm:(Int,Int)->Unit){var od by remember{mutableStateOf("")};var rest by remember{mutableStateOf("0")};AlertDialog(onDismissRequest=onDismiss,title={Text("FINALIZAR JORNADA")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Odómetro inicial: ${j?.odometroInicial ?: 0}");OutlinedTextField(value=od,onValueChange={od=it.filter(Char::isDigit)},label={Text("Odómetro final")},singleLine=true);OutlinedTextField(value=rest,onValueChange={rest=it.filter(Char::isDigit)},label={Text("Descanso (min)")},singleLine=true)}},confirmButton={Button(onClick={val a=od.toIntOrNull();val b=rest.toIntOrNull();if(a!=null&&b!=null)onConfirm(a,b)}){Text("FINALIZAR")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})}

private fun formatTime(text: String): String = runCatching { LocalDateTime.parse(text).format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrDefault(text)
