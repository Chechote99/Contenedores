package com.contenedores.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME
fun nowText(): String = LocalDateTime.now().format(DATE_TIME)
fun todayText(): String = LocalDate.now().toString()
fun euro(cents: Long?): String = "%.2f €".format((cents ?: 0L) / 100.0)
fun billingPeriod(date: LocalDate): Pair<LocalDate, LocalDate> = if (date.dayOfMonth >= 16) date.withDayOfMonth(16) to date.plusMonths(1).withDayOfMonth(15) else date.minusMonths(1).withDayOfMonth(16) to date.withDayOfMonth(15)
fun tariffFor(number: Int, tiers: List<TramoTarifaEntity>): Long? = tiers.firstOrNull { it.activo && number in it.desdeServicio..it.hastaServicio }?.importeCentimos
fun billingTotal(count: Int, tiers: List<TramoTarifaEntity>): Long {
    var total = 0L
    for (t in tiers.sortedBy { it.desdeServicio }) {
        if (count < t.desdeServicio) break
        val qty = minOf(count, t.hastaServicio) - t.desdeServicio + 1
        if (qty > 0) total += qty * t.importeCentimos
    }
    return total
}

class SeedData(private val db: AppDatabase) {
    suspend fun ensure() {
        if (db.tarifas().activas().isEmpty()) {
            db.tarifas().insertar(TramoTarifaEntity(desdeServicio = 1, hastaServicio = 130, importeCentimos = 400))
            db.tarifas().insertar(TramoTarifaEntity(desdeServicio = 131, hastaServicio = 150, importeCentimos = 1500))
            db.tarifas().insertar(TramoTarifaEntity(desdeServicio = 151, hastaServicio = 180, importeCentimos = 2000))
        }
        if (db.vehiculos().principal() == null) db.vehiculos().insertar(VehiculoEntity(matricula = "", odometroActual = 0))
        ensurePeriod(LocalDate.now())
    }
    suspend fun ensurePeriod(date: LocalDate): PeriodoFacturacionEntity {
        db.periodos().porFecha(date.toString())?.let { return it }
        val (start, end) = billingPeriod(date)
        db.periodos().porInicio(start.toString())?.let { return it }
        return PeriodoFacturacionEntity(fechaInicio = start.toString(), fechaFin = end.toString()).also { db.periodos().insertar(it) }
    }
}

class MainViewModel(private val db: AppDatabase) : ViewModel() {
    var jornada: JornadaEntity? = null; private set
    var viaje: ViajeEntity? = null; private set
    var servicio: ServicioEntity? = null; private set
    var serviciosHoy: List<ServicioEntity> = emptyList(); private set
    var serviciosPeriodo: List<ServicioEntity> = emptyList(); private set
    var viajesHoy: List<ViajeEntity> = emptyList(); private set
    var error: String? = null; private set
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SeedData(db).ensure()
                jornada = db.jornadas().abierta()
                viaje = jornada?.let { db.viajes().enCurso(it.id) }
                servicio = viaje?.let { db.servicios().enCursoViaje(it.id) }
                val start = LocalDate.now().atStartOfDay().format(DATE_TIME)
                val end = LocalDate.now().plusDays(1).atStartOfDay().format(DATE_TIME)
                serviciosHoy = db.servicios().finalizadosEntre(start, end)
                val period = db.periodos().porFecha(todayText())
                serviciosPeriodo = period?.let { db.servicios().delPeriodo(it.id) } ?: emptyList()
                viajesHoy = jornada?.let { db.viajes().deJornada(it.id) } ?: emptyList()
                error = null
            } catch (e: Exception) { error = e.message ?: "Error" }
            _version.value++
        }
    }

    fun abrirJornada(odometro: Int) = action {
        require(jornada == null) { "Ya existe una jornada abierta." }
        require(odometro >= 0) { "Odómetro no válido." }
        require(db.jornadas().porFecha(todayText()) == null) { "Ya existe una jornada para hoy." }
        db.jornadas().insertar(JornadaEntity(fecha=todayText(), inicio=nowText(), odometroInicial=odometro))
    }
    fun iniciarViaje() = action {
        val j = db.jornadas().abierta() ?: error("No hay jornada abierta.")
        require(db.viajes().enCurso(j.id) == null) { "Ya hay un viaje en curso." }
        db.viajes().insertar(ViajeEntity(jornadaId=j.id, numero=db.viajes().ultimoNumero(j.id)+1, inicio=nowText()))
    }
    fun iniciarServicio(tipo: String, capacidad: Int?, retirada: Int?, puesta: Int?, direccion: String) = action {
        val j = db.jornadas().abierta() ?: error("No hay jornada abierta.")
        val v = db.viajes().enCurso(j.id) ?: error("Primero inicia un viaje.")
        require(db.servicios().enCursoViaje(v.id) == null) { "Ya hay un servicio en curso." }
        require(tipo in listOf("PUESTA","RETIRADA","CAMBIO"))
        if (tipo == "CAMBIO") require(retirada in setOf(3,6,9) && puesta in setOf(3,6,9) && retirada != puesta) { "Las capacidades del cambio deben ser diferentes." }
        else require(capacidad in setOf(3,6,9)) { "Selecciona una capacidad." }
        val p = SeedData(db).ensurePeriod(LocalDate.now())
        db.servicios().insertar(ServicioEntity(periodoFacturacionId=p.id,jornadaId=j.id,viajeId=v.id,inicio=nowText(),tipo=tipo,capacidad=if(tipo=="CAMBIO") null else capacidad,capacidadRetirada=retirada,capacidadPuesta=puesta,direccion=direccion.ifBlank { null }))
    }
    fun finalizarServicio() {
        viewModelScope.launch {
            try {
                db.withWriteTransaction {
                    val j = db.jornadas().abierta()
                        ?: error("No hay jornada abierta.")

                    val v = db.viajes().enCurso(j.id)
                        ?: error("No hay viaje abierto.")

                    val s = db.servicios().enCursoViaje(v.id)
                        ?: error("No hay servicio abierto.")

                    val tiers = db.tarifas().activas()

                    val next =
                        db.servicios()
                            .ultimoNumeroPeriodo(s.periodoFacturacionId) + 1

                    val tariff =
                        tariffFor(next, tiers)
                            ?: error(
                                "Tarifa no configurada para el servicio $next."
                            )

                    db.servicios().actualizar(
                        s.copy(
                            fin = nowText(),
                            numeroFacturacion = next,
                            tarifaCentimos = tariff,
                            importeCentimos = tariff,
                            estado = "FINALIZADO"
                        )
                    )
                }
            } catch (e: Exception) {
                // Mantén aquí el mecanismo de error que ya utilice tu ViewModel.
                // Si no existe ninguno, temporalmente:
                e.printStackTrace()
            }
        }
    }
    fun cancelarServicio() = action {
        val j = db.jornadas().abierta() ?: return@action
        val v = db.viajes().enCurso(j.id) ?: return@action
        db.servicios().enCursoViaje(v.id)?.let { db.servicios().eliminar(it) }
    }
    fun finalizarViaje(km: Double?) = action {
        val j = db.jornadas().abierta() ?: error("No hay jornada abierta.")
        val v = db.viajes().enCurso(j.id) ?: error("No hay viaje abierto.")
        require(db.servicios().enCursoViaje(v.id) == null) { "Finaliza primero el servicio en curso." }
        require((km ?: 0.0) >= 0.0) { "Los kilómetros no pueden ser negativos." }
        db.viajes().actualizar(v.copy(fin=nowText(),kmViaje=km,estado="FINALIZADO"))
    }
    fun finalizarJornada(odometroFinal: Int, descanso: Int) = action {
        val j = db.jornadas().abierta() ?: error("No hay jornada abierta.")
        require(db.viajes().enCurso(j.id) == null) { "Finaliza primero el viaje en curso." }
        require(db.servicios().enCursoJornada(j.id) == null) { "Finaliza primero el servicio en curso." }
        require(odometroFinal >= j.odometroInicial) { "El odómetro final no puede ser menor que el inicial." }
        require(descanso >= 0) { "Descanso no válido." }
        db.jornadas().actualizar(j.copy(fin=nowText(),odometroFinal=odometroFinal,descansoMinutos=descanso,estado="CERRADA"))
        db.vehiculos().principal()?.let { db.vehiculos().actualizar(it.copy(odometroActual=odometroFinal)) }
    }
    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try { block(); error=null } catch(e: Exception) { error=e.message ?: "Error" }
            refresh()
        }
    }
}
