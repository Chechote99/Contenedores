package com.contenedores.app

import androidx.room3.Database
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.Update
import java.util.UUID

@Entity(tableName = "jornadas", indices = [Index(value = ["fecha"], unique = true)])
data class JornadaEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fecha: String,
    val inicio: String,
    val fin: String? = null,
    val descansoMinutos: Int = 0,
    val odometroInicial: Int,
    val odometroFinal: Int? = null,
    val observaciones: String? = null,
    val estado: String = "ABIERTA"
)

@Entity(tableName = "viajes", indices = [Index("jornadaId"), Index(value = ["jornadaId", "numero"], unique = true)])
data class ViajeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val jornadaId: String,
    val numero: Int,
    val inicio: String,
    val fin: String? = null,
    val origenTexto: String? = null,
    val destinoTexto: String? = null,
    val kmViaje: Double? = null,
    val observaciones: String? = null,
    val estado: String = "EN_CURSO"
)

@Entity(
    tableName = "servicios",
    indices = [
        Index(value = ["periodoFacturacionId", "numeroFacturacion"], unique = true),
        Index("jornadaId"), Index("viajeId"), Index("periodoFacturacionId")
    ]
)
data class ServicioEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val periodoFacturacionId: String,
    val jornadaId: String,
    val viajeId: String,
    val numeroFacturacion: Int? = null,
    val inicio: String,
    val fin: String? = null,
    val tipo: String,
    val capacidad: Int? = null,
    val capacidadRetirada: Int? = null,
    val capacidadPuesta: Int? = null,
    val lugarId: String? = null,
    val direccion: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val placeId: String? = null,
    val tarifaCentimos: Long? = null,
    val importeCentimos: Long? = null,
    val observaciones: String? = null,
    val estado: String = "EN_CURSO"
)

@Entity(tableName = "periodos_facturacion", indices = [Index(value = ["fechaInicio"], unique = true)])
data class PeriodoFacturacionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fechaInicio: String,
    val fechaFin: String,
    val estado: String = "ACTIVO"
)

@Entity(tableName = "tramos_tarifa", indices = [Index(value = ["desdeServicio"], unique = true)])
data class TramoTarifaEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val desdeServicio: Int,
    val hastaServicio: Int,
    val importeCentimos: Long,
    val activo: Boolean = true
)

@Entity(tableName = "lugares")
data class LugarEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val nombre: String,
    val direccion: String,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val placeId: String? = null,
    val categoria: String = "OBRA"
)

@Entity(tableName = "vehiculos")
data class VehiculoEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val matricula: String,
    val marca: String? = null,
    val modelo: String? = null,
    val odometroActual: Int,
    val alturaMetros: Double? = null,
    val anchuraMetros: Double? = null,
    val longitudMetros: Double? = null,
    val pesoToneladas: Double? = null
)

@androidx.room3.Dao
interface JornadaDao {
    @Query("SELECT * FROM jornadas WHERE estado='ABIERTA' LIMIT 1") suspend fun abierta(): JornadaEntity?
    @Query("SELECT * FROM jornadas WHERE fecha=:fecha LIMIT 1") suspend fun porFecha(fecha: String): JornadaEntity?
    @Query("SELECT * FROM jornadas WHERE id=:id LIMIT 1") suspend fun porId(id: String): JornadaEntity?
    @Query("SELECT * FROM jornadas ORDER BY fecha DESC") suspend fun todas(): List<JornadaEntity>
    @Insert suspend fun insertar(item: JornadaEntity)
    @Update suspend fun actualizar(item: JornadaEntity)
}

@androidx.room3.Dao
interface ViajeDao {
    @Query("SELECT * FROM viajes WHERE id=:id LIMIT 1") suspend fun porId(id: String): ViajeEntity?
    @Query("SELECT * FROM viajes WHERE jornadaId=:jornadaId AND estado='EN_CURSO' LIMIT 1") suspend fun enCurso(jornadaId: String): ViajeEntity?
    @Query("SELECT COALESCE(MAX(numero),0) FROM viajes WHERE jornadaId=:jornadaId") suspend fun ultimoNumero(jornadaId: String): Int
    @Query("SELECT * FROM viajes WHERE jornadaId=:jornadaId ORDER BY numero") suspend fun deJornada(jornadaId: String): List<ViajeEntity>
    @Query("SELECT * FROM viajes ORDER BY inicio DESC") suspend fun todos(): List<ViajeEntity>
    @Insert suspend fun insertar(item: ViajeEntity)
    @Update suspend fun actualizar(item: ViajeEntity)
}

@androidx.room3.Dao
interface ServicioDao {
    @Query("SELECT * FROM servicios WHERE id=:id LIMIT 1") suspend fun porId(id: String): ServicioEntity?
    @Query("SELECT * FROM servicios WHERE viajeId=:viajeId AND estado='EN_CURSO' LIMIT 1") suspend fun enCursoViaje(viajeId: String): ServicioEntity?
    @Query("SELECT * FROM servicios WHERE jornadaId=:jornadaId AND estado='EN_CURSO' LIMIT 1") suspend fun enCursoJornada(jornadaId: String): ServicioEntity?
    @Query("SELECT * FROM servicios WHERE viajeId=:viajeId AND estado='FINALIZADO' ORDER BY fin") suspend fun deViaje(viajeId: String): List<ServicioEntity>
    @Query("SELECT * FROM servicios WHERE jornadaId=:jornadaId AND estado='FINALIZADO' ORDER BY fin") suspend fun deJornada(jornadaId: String): List<ServicioEntity>
    @Query("SELECT * FROM servicios WHERE periodoFacturacionId=:periodoId AND estado='FINALIZADO' ORDER BY numeroFacturacion") suspend fun delPeriodo(periodoId: String): List<ServicioEntity>
    @Query("SELECT COUNT(*) FROM servicios WHERE periodoFacturacionId=:periodoId AND estado='FINALIZADO'") suspend fun contarPeriodo(periodoId: String): Int
    @Query("SELECT COALESCE(MAX(numeroFacturacion),0) FROM servicios WHERE periodoFacturacionId=:periodoId AND estado='FINALIZADO'") suspend fun ultimoNumeroPeriodo(periodoId: String): Int
    @Query("SELECT * FROM servicios WHERE estado='FINALIZADO' AND fin >= :desde AND fin < :hasta ORDER BY fin") suspend fun finalizadosEntre(desde: String, hasta: String): List<ServicioEntity>
    @Query("SELECT * FROM servicios ORDER BY inicio DESC") suspend fun todos(): List<ServicioEntity>
    @Insert suspend fun insertar(item: ServicioEntity)
    @Update suspend fun actualizar(item: ServicioEntity)
    @Delete suspend fun eliminar(item: ServicioEntity)
}

@androidx.room3.Dao
interface PeriodoDao {
    @Query("SELECT * FROM periodos_facturacion WHERE fechaInicio=:inicio LIMIT 1") suspend fun porInicio(inicio: String): PeriodoFacturacionEntity?
    @Query("SELECT * FROM periodos_facturacion WHERE :fecha BETWEEN fechaInicio AND fechaFin LIMIT 1") suspend fun porFecha(fecha: String): PeriodoFacturacionEntity?
    @Query("SELECT * FROM periodos_facturacion ORDER BY fechaInicio DESC") suspend fun todos(): List<PeriodoFacturacionEntity>
    @Insert suspend fun insertar(item: PeriodoFacturacionEntity)
}

@androidx.room3.Dao
interface TarifaDao {
    @Query("SELECT * FROM tramos_tarifa WHERE activo=1 ORDER BY desdeServicio") suspend fun activas(): List<TramoTarifaEntity>
    @Insert suspend fun insertar(item: TramoTarifaEntity)
}

@androidx.room3.Dao
interface LugarDao {
    @Query("SELECT * FROM lugares ORDER BY nombre") suspend fun todos(): List<LugarEntity>
    @Insert suspend fun insertar(item: LugarEntity)
    @Update suspend fun actualizar(item: LugarEntity)
    @Delete suspend fun eliminar(item: LugarEntity)
}

@androidx.room3.Dao
interface VehiculoDao {
    @Query("SELECT * FROM vehiculos LIMIT 1") suspend fun principal(): VehiculoEntity?
    @Insert suspend fun insertar(item: VehiculoEntity)
    @Update suspend fun actualizar(item: VehiculoEntity)
}

@Database(
    entities = [JornadaEntity::class, ViajeEntity::class, ServicioEntity::class, PeriodoFacturacionEntity::class, TramoTarifaEntity::class, LugarEntity::class, VehiculoEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jornadas(): JornadaDao
    abstract fun viajes(): ViajeDao
    abstract fun servicios(): ServicioDao
    abstract fun periodos(): PeriodoDao
    abstract fun tarifas(): TarifaDao
    abstract fun lugares(): LugarDao
    abstract fun vehiculos(): VehiculoDao
}
