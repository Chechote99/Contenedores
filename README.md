# Contenedores — V1

Aplicación Android local para gestionar jornadas, viajes físicos, servicios facturables y facturación de operaciones de contenedores.

## Modelo
Jornada → Viaje → Servicio → Facturación.

- Puesta, Retirada y Cambio.
- Capacidades 3, 6 y 9 m³.
- Cambio exige capacidades distintas.
- Cada Servicio finalizado es una unidad facturable.
- Un Viaje puede contener varios Servicios.
- Periodo de facturación: 16 → 15.
- Tarifas iniciales: 1–130 = 4 €, 131–150 = 15 €, 151–180 = 20 €.
- Servicio 181+ queda bloqueado hasta configurar un nuevo tramo.
- Km reales proceden del odómetro; Km del Viaje son independientes.
- Sin servidor: Room/SQLite local en el teléfono.

## Entorno
- Android Studio Quail 3.
- JDK 17.
- AGP 9.2.0.
- Gradle 9.4.1.
- compileSdk/targetSdk 37.
- Room 3.0.2 + KSP 2.3.10.
- Compose BOM 2026.08.00.

## Google
Copia `local.properties.example` como `local.properties` y añade las claves si vas a usar Google Maps/Places/Routes.
No subas `local.properties` a Git.

## Generar APK
En Android Studio: Sync Project with Gradle Files → Build → Generate App Bundles or APKs → Generate APKs.
El APK debug queda en `app/build/outputs/apk/debug/app-debug.apk`.
