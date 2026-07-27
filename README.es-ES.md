<div align="center">

<img src="icon/openmonitor_logo.svg" width="128" height="128" alt="OpenMonitor Logo" />

# OpenMonitor

**Un potente monitor de sistema para Android de código abierto construido con Jetpack Compose & Material 3**

Monitoreo en tiempo real de CPU, GPU, batería, FPS, térmicas, memoria y procesos — con soporte para ROOT, Shizuku y ADB.

[![Release](https://img.shields.io/github/v/release/1orz/OpenMonitor?include_prereleases&logo=github&label=Release)](https://github.com/1orz/OpenMonitor/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen?logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)
[![Stars](https://img.shields.io/github/stars/1orz/OpenMonitor?style=flat&logo=github&label=Stars)](https://github.com/1orz/OpenMonitor/stargazers)
[![License](https://img.shields.io/github/license/1orz/OpenMonitor?label=License)](https://github.com/1orz/OpenMonitor/blob/main/LICENSE)

[**Descargar**](https://github.com/1orz/OpenMonitor/releases/latest) · [**Problemas (Issues)**](https://github.com/1orz/OpenMonitor/issues) · [**Canal QQ (CN)**](https://pd.qq.com/s/ft7tc3xae) · [**Grupo QQ (CN)**](https://qm.qq.com/cgi-bin/qm/qr?k=Kx6t5VIe6vwKSyk6gsf2CGCABO3V7qfe&jump_from=webapi&authKey=Qq+pLqZq/1Px+1g/r49xi2QDzcCfxxiH6kK5c2qdTNMvnggkmbbq6Aptw8oKruyz) · [**Telegram (CN/EN)**](https://t.me/OpenMonitor) · [**Sitio Web**](https://om.cloudorz.com/)

**English** | [**简体中文**](README_zh-CN.md) | [**日本語**](README_ja.md)

---

</div>

## Historial de Estrellas

<div align="center">

[![Star History Chart](https://api.star-history.com/svg?repos=1orz/OpenMonitor&type=Date)](https://star-history.com/#1orz/OpenMonitor&Date)

</div>

---

## Aspectos Destacados

- Monitoreo en tiempo real de CPU, GPU, batería, FPS, memoria, térmicas y procesos
- Múltiples modos de privilegios: ROOT (Magisk / KernelSU / APatch), Shizuku, ADB y Básico
- Superposición flotante para monitoreo siempre visible sobre cualquier aplicación
- Botón en Ajustes Rápidos y widget de pantalla de inicio para acceso instantáneo
- Grabación de FPS con detección de saltos (jank), reproducción de sesiones y exportación de imágenes/CSV
- Especificaciones completas de hardware con base de datos de más de 900 SoC, enumeración de Vulkan & OpenGL
- Atestación de claves respaldada por hardware y verificación de integridad del dispositivo
- Análisis de red celular: señal por RAT, captura de señalización RRC/NAS/IMS, eventos del módem, exportación PCAP
- Tematización dinámica de Material 3 con modos Sistema / Claro / Oscuro / AMOLED
- Demonio privilegiado basado en Rust para recolección de datos de alto rendimiento y bajo consumo
- Localizado en inglés, chino simplificado, chino tradicional y japonés

---

## Tabla de Contenidos

- [Primeros Pasos](#getting-started)
- [Modos de Permiso](#permission-modes)
- [Características](#features)
  - [Panel de Descripción General](#overview-dashboard)
  - [Análisis de CPU](#cpu-analysis)
  - [Batería](#battery)
  - [Grabación de FPS](#fps-recording)
  - [Gestor de Procesos](#process-manager)
  - [Información de Hardware](#hardware-info)
  - [Red Celular](#cellular-network)
  - [Superposición Flotante](#float-overlay)
  - [Atestación de Claves](#key-attestation)
  - [Botón de Ajustes Rápidos y Widget](#quick-settings-tile--widget)
- [Arquitectura](#architecture)
- [Stack Tecnológico](#tech-stack)
- [Compilación desde el Código Fuente](#building-from-source)
- [Privacidad](#privacy)
- [Contribución](#contributing)
- [Licencia](#license)

---

## Primeros Pasos

1. **Descarga** el APK más reciente desde [GitHub Releases](https://github.com/1orz/OpenMonitor/releases/latest) e instálalo.
2. **Inicia** OpenMonitor. En el primer inicio verás un acuerdo de privacidad; revísalo y acéptalo para continuar.
3. **Elige un modo de permiso.** La aplicación detecta automáticamente los marcos de privilegios disponibles (ROOT, Shizuku, ADB) y recomienda la mejor opción. Si no hay acceso privilegiado disponible, el modo Básico sigue proporcionando monitoreo básico desde el sysfs legible globalmente.
4. **Explora.** La navegación inferior tiene tres pestañas:
   - **Características** — accede a todos los módulos de monitoreo (Hardware, Batería, FPS, Procesos, Flotante, Atestación de Claves, etc.)
   - **Descripción General** — un panel en vivo que muestra todas las métricas clave de un vistazo
   - **Ajustes** — configura el tema, idioma, modo de privilegios, respuesta háptica y más

---

## Modos de Permiso

OpenMonitor se adapta a los permisos disponibles de tu dispositivo con una degradación gradual:

| Modo | Cómo funciona | Capacidades |
|:-----|:-------------|:-------------|
| **ROOT** | Acceso privilegiado completo vía Magisk / KernelSU / APatch. El demonio de Rust se inicia automáticamente vía libsu. | Todas las funciones: FPS, matar procesos, frecuencia de GPU, térmicas completas, superposición flotante, corriente/potencia de batería |
| **Shizuku** | Permisos delegados vía el gestor de Shizuku. El demonio se ejecuta como un Shizuku UserService. | Monitoreo de FPS, gestión de procesos, métricas mejoradas |
| **ADB** | Inicia el demonio manualmente vía `adb shell`. No requiere apps adicionales. | Igual que Shizuku, apto para dispositivos sin root |
| **Básico** | No requiere permisos especiales. | Carga/frecuencia de CPU, memoria, conceptos básicos de batería desde sysfs legible globalmente |

> Puedes cambiar los modos de permiso en cualquier momento desde **Ajustes > Sistema > Modo de Privilegios**.

---

## Características

### Panel de Descripción General

La pestaña de Descripción General proporciona una instantánea completa y en tiempo real de tu dispositivo:

- **CPU** — Porcentaje de carga total (codificado por colores: verde < 50%, amarillo < 80%, rojo >= 80%), frecuencia promedio (MHz), temperatura, recuento de núcleos activos/totales e indicadores de estado individuales por núcleo
- **Memoria** — Calibrador de arco que muestra el porcentaje de uso, RAM física usada/total, memoria disponible, uso de swap y ratio de compresión de ZRam
- **GPU** — Nombre del modelo, frecuencia actual/máxima, porcentaje de carga con barra de progreso, gobernador activo
- **Info de SoC** — Nombre del dispositivo, logo del proveedor, modelo de SoC, insignia del proceso de fabricación, arquitectura, ABI, configuración de clústeres de CPU
- **Caché** — Tamaños de caché L1D / L1I / L2 / L3, soporte ARM NEON
- **Chips del Sistema** — Consumo de energía (W), nivel y voltaje de batería, temperatura, versión de Android, tiempo de actividad del sistema

Todas las métricas se actualizan en tiempo real. El panel está diseñado para dar un vistazo rápido de "si todo está bien".

### Análisis de CPU

Desglose detallado de la CPU por núcleo accesible desde Descripción General o Información de Hardware:

- **Vista de Clúster** — Agrupa los núcleos por clúster (Little / Big / Prime) con gobernador por clúster, rango de frecuencia (min–max MHz) y carga promedio
- **Estado por núcleo** — Cada núcleo muestra su número, nombre de la microarquitectura, % de carga actual, frecuencia en MHz y estado activo/inactivo
- **Detalles del SoC** — ID de hardware, arquitectura, ABI, política del gobernador, configuración completa de clústeres con rangos de frecuencia
- **Jerarquía de Caché** — Tamaños de L1D/L1I/L2/L3 por núcleo/clúster

### Batería

Analítica profunda de la batería con seguimiento histórico:

- **Tarjeta de estado** — Capacidad actual %, voltaje (V), temperatura con indicador de salud codificado por colores, tiempo estimado restante o tiempo de carga
- **Gráfico de historial** — Gráfico de líneas que muestra el nivel de batería a lo largo del tiempo con rangos temporales seleccionables mediante chips de filtro
- **Análisis de descarga** — Visualización de tendencias durante el período seleccionado para entender los patrones de consumo
- **Desglose de uso de apps** — Consumo de batería por aplicación con iconos, mostrando el porcentaje de drenaje estimado y mAh (requiere permiso de Estadísticas de Uso; la app te pedirá concederlo si no está activado)
- **Exportación CSV** — Comparte los datos del historial de batería como un archivo CSV mediante el botón de compartir

### Grabación de FPS

Monitoreo de tasa de fotogramas de grado profesional con gestión de sesiones:

**Iniciar una Grabación**
1. Navega a **Características > Grabación de FPS**
2. Activa el interruptor de **FPS Flotante** para habilitar la superposición de FPS
3. Abre el juego o la aplicación que quieras probar; la superposición muestra los FPS en vivo
4. La sesión se graba automáticamente. Cuando termines, vuelve a OpenMonitor para finalizar la sesión.

**Lista de Sesiones**
- Todas las sesiones se muestran como tarjetas con el icono de la app, nombre de la app, FPS promedio, duración y marca de tiempo
- La calidad de los FPS está codificada por colores: verde (>= 50 fps), amarillo (>= 30 fps), rojo (< 30 fps)
- Mantén presionado para entrar en modo de selección múltiple para eliminación masiva
- Toca el icono de editar para renombrar sesiones

**Detalle y Análisis de Sesión**

Al tocar una sesión se abre una vista de análisis exhaustiva:

- **Información del dispositivo** — Plataforma de CPU, modelo del dispositivo, versión del SO
- **Encabezado de sesión** — Icono de la app, nombre, versión, nombre del paquete, resolución de pantalla, marca de tiempo, duración
- **Cuadrícula de estadísticas** con seis métricas clave:
  - FPS Máximo / Mínimo / Promedio (codificado por colores)
  - Varianza de FPS (desviación estándar)
  - Fluidez (% de fotogramas >= 45 fps)
  - 5% Low (percentil 5 más bajo)
  - Temperatura máxima de la batería
  - Consumo de energía promedio (W)
- **Línea de tiempo de cambio de app** — Barra horizontal desplazable que muestra qué aplicaciones estuvieron en foco y cuándo
- **Gráficos interactivos** (actívalos vía el icono de ajustes):
  - FPS y Temperatura (temp CPU, temp GPU, uso CPU/GPU)
  - Frame Time (tiempo de fotograma máximo en ms)
  - Uso de CPU/GPU (carga total de CPU, GPU y por clúster)
  - Frecuencia de CPU/GPU (por clúster, frecuencia de GPU)
  - Gráfico de Jank (deltas de jank y big jank por fotograma)
  - Energía / Batería (energía en W, % de batería)
  - Corriente de Batería (mA con máx/mín/prom)
  - Temperatura (CPU, GPU, batería)
- Cada gráfico permite **mostrar/ocultar series individuales** para un análisis enfocado
- Todos los gráficos son **desplazables y zoomables**

**Opciones de Exportación**
- **Guardar como PNG** — Genera una imagen compartible con todos los gráficos, info del dispositivo y un banner de marca con código QR
- **Guardar como CSV** — Exporta los datos brutos como hoja de cálculo para análisis externo

### Gestor de Procesos

Monitoreo de procesos en tiempo real con capacidades de gestión:

- **Buscar** — Filtra procesos por nombre con la barra de búsqueda
- **Modos de filtro** — Alterna entre "Todos los Procesos" y "Solo Apps" (aplicaciones instaladas)
- **Opciones de orden** — Ordenar por % de CPU, Memoria (MB), Nombre (alfabético) o PID
- **Lista de procesos** — Cada entrada muestra el icono de la app (o icono genérico), nombre del proceso, PID, uso de CPU (codificado por colores: verde < 5%, amarillo 5–20%, rojo > 20%) y memoria en MB
- **Detalle del proceso** — Toca cualquier proceso para ver información detallada
- **Recuento de procesos** — Recuento total de procesos filtrados mostrado en la parte superior

> La funcionalidad de matar procesos está disponible en los modos ROOT y Shizuku.

### Información de Hardware

Especificaciones completas del hardware del dispositivo:

- **Procesador** — Logo del proveedor, nombre del SoC, insignia del proceso de fabricación, recuento de núcleos, insignia de 64 bits, configuración de CPU por clúster con rangos de frecuencia, gobernador, arquitectura, ABI. Enlaces al Análisis de CPU para inspección profunda.
- **GPU** — Nombre del modelo/renderizador, ID del chip, proveedor, tamaño de GMEM, núcleos de sombreado, ancho de bus, caché L2 (para GPUs Mali). Botones para explorar **Características de Vulkan** y **Características de OpenGL ES**.
- **Pantalla** — Resolución, tasa de refresco, PPI, tamaño físico (pulgadas/mm), relación de aspecto, bucket de densidad, gama de colores amplia, capacidades HDR, nombre del panel. Lista todas las resoluciones y tasas de refresco soportadas.
- **Memoria** — Etiqueta de tamaño de RAM (redondeada al tamaño comercial), barra de uso con total/usado/libre. Sección de ZRam con ratio de compresión si está disponible.
- **Almacenamiento** — Tamaño comercial, tipo de almacenamiento (ej. UFS 3.1), barra de uso, desglose de particiones con espacio usado, tipo de sistema de archivos, tamaño de bloque.

**Pantalla de Info de Vulkan** — Análisis profundo de las capacidades de la GPU:
- Identidad del dispositivo (nombre, proveedor, ID del dispositivo, tipo, versión de API, versión del controlador)
- Montones de memoria (memory heaps) con etiquetas de local unidad
- Límites (dimensiones máximas de imagen, invocaciones de cómputo, rangos de búfer, anisotropía)
- Características principales (acceso robusto al búfer, sombreadores de geometría/teselación, multi-viewport, etc.)
- Familias de colas con banderas y bits de marca de tiempo
- Listas completas de extensiones de dispositivo e instancia

### Red Celular

Inspección profunda de radio celular que cubre estado de RF, señalización bruta y eventos del módem. Una sola pantalla con selector de ranura SIM en la barra de herramientas y cuatro sub-pestañas:

**Dispositivo** — Instantánea de RF y módem

- **Tarjeta de sesión DIAG** — accesibilidad del puente DIAG directo al módem (AF_QIPCRTR / QMI). Se contrae automáticamente cuando el demonio está offline; recurre a la API pública de Telephony.
- **Red de servicio** — nombre del operador, PLMN, MCC/MNC, modo RAT (NR5G / LTE / WCDMA / GSM), bandera de LTE CA activo, NR5G ARFCN
- **Celda de servicio (módem)** — LTE Global Cell ID, ECI, TAC, PCI, estado del UE; NR5G NCI, PCI, ARFCN
- **Banda RF / canal** — banda y canal activos por RAT
- **Cadenas Rx** — división por cadena (Rx0…Rx3) con fase y estado de Tx (en tráfico / inactivo) — solo expuesto vía DIAG del módem
- **Capacidades de banda** — máscara de bits de bandas LTE soportadas, lista completa de bandas soportadas, bandas NR5G FR1
- **Identidad del módem** — versión de la banda base, modo de operación, estado de VoLTE / VT / Wi-Fi Calling
- **Diagnóstico de disponibilidad de campos** — cada campo muestra una etiqueta de "Disponible / Bloqueado / No soportado" con explicación al tocar, para que quede claro qué campos desbloquea el modo de privilegio actual

**Red** — Celda de servicio por RAT + vecinos

La página se adapta a la RAT de servicio de la SIM activa, mostrando **solo** la RAT a la que realmente está conectada (NR / LTE / GSM / WCDMA / TD-SCDMA), no un carrusel de pestañas fijo:

- **Configuración de celda de servicio** — PCI / EARFCN / NRARFCN / UARFCN / ARFCN, banda, ancho de banda (MHz), CQI e identificadores por RAT (TAC / ECI / NCI / LAC / CI / BSIC / PSC)
- **Señal en tiempo real** — gráfico de tendencia en vivo de RSRP / RSRQ / RSSI / SINR (LTE) y SS-RSRP / SS-RSRQ / SS-SINR / CSI-RSRP (NR5G)
- **Celdas vecinas** — gráfico de barras de vecinos detectados con PCI y fuerza de señal
- **Matriz de agregación de portadoras** — rol por portadora de componente (PCC / SCC), estado (Activada / Desactivada), ancho de banda DL/UL, canal UL
- **Rango de frecuencia NR** — etiqueta de bucket (Bajo / Medio / Alto / mmWave) más estado NSA / SA

**Señalización** — Traza de protocolo bruta

Captura en vivo de señalización RRC / NR-RRC / NAS / NR-NAS / IMS desde el módem:

- **Vista de flujo** — lista desplazable de tramas con nombre del método, dirección (UL / DL), RAT, longitud en bytes; auto-desplazamiento anclado (desplazar manualmente hacia atrás pausa el seguimiento)
- **Chips de filtro** — bucket de familia de protocolo (IMS / RRC / 5G-RRC / NAS / 5G-NAS / Otro) + dirección + búsqueda difusa (método / IE / hex)
- **Hoja de detalles** — canal, dirección, RAT, código de log, PCI, EARFCN/ARFCN, bytes PDU completos. Vista de árbol ASN.1 donde el decodificador esté implementando; mensajes SIP renderizados con encabezados completos.
- **Grabación PCAP** — toca **Grabar**, opera el dispositivo, toca de nuevo para detener y compartir/copiar el `.pcap` resultante para Wireshark
- **Exportación multi-selección** — mantén presionado para entrar en modo selección, exporta filas en lote como un archivo JSON estructurado

**Eventos** — Log de estado del lado del módem

- Entradas en vivo para attach / detach / handover / reselección de celda / establecimiento de llamada / registro IMS
- Etiquetas de resultado: Éxito / Fallo / En progreso
- Hoja de detalles por evento: marca de tiempo, RAT, categoría, tipo, carga útil estructurada

> **Cobertura de Privilegios.** Los datos de la API de Telephony (PCI, EARFCN, RSRP/RSRQ, básicos de celda de servicio) funcionan en cualquier dispositivo. El DIAG directo al módem — cadenas Rx, máscaras de bits por banda, versión de banda base, señalización bruta, eventos del módem — actualmente está dirigido a dispositivos **Qualcomm Snapdragon** en modo **ROOT** o **Shizuku**. La pestaña Dispositivo hace explícita la cobertura por campo.

> **Permisos.** La primera entrada solicita **Estado del teléfono** y **Ubicación** — sin ellos, el SO oculta los IDs de celda y la fuerza de la señal independientemente del estado de root.

### Superposición Flotante

Superposición de monitoreo siempre visible:

- **Múltiples monitores** — Activa superposiciones individuales para FPS, CPU, Memoria, GPU y Temperatura
- **Gestión de permisos** — Si el permiso de superposición no ha sido concedido, una tarjeta te pedirá habilitarlo en los ajustes del sistema
- **Acceso rápido** — También puede activarse desde el botón de Ajustes Rápidos

La superposición permanece visible en todas las apps, ideal para monitorear durante juegos o benchmarks.

### Atestación de Claves

Verificación de seguridad del dispositivo respaldada por hardware:

- **Banner de confianza** — Muestra si la cadena de certificados es confiable con un indicador de estado claro
- **Estado de arranque** — Estado Verificado / Autofirmado / Fallido con estado de bloqueo del bootloader (iconos codificados por colores)
- **Información de atestación** — Versión de atestación, nivel de seguridad (TEE / StrongBox / Software), versión de Keymaster, desafío e ID único
- **Lista de revocación** — Estado de revocación del certificado del dispositivo con obtención de red, recuento de entradas y expiración de caché
- **Cadena de certificados** — Detalles por certificado incluyendo sujeto, emisor, fechas de validez y estado de revocación con motivo
- **Lista de autorización** — Visualización campo por campo con etiquetas HW/SW que indican ejecución por hardware o software
- **Menú de opciones:**
  - Alternar "Usar Clave de Atestación" / "Usar StrongBox" / "Modo Shizuku"
  - Reiniciar clave de atestación persistente
  - Guardar cadena de certificados como archivo `.p7b`
  - Cargar cadena de certificados desde archivo

### Botón de Ajustes Rápidos y Widget

**Botón de Ajustes Rápidos**
- Añade el botón "Monitor" a tu panel de Ajustes Rápidos
- Toca para activar/desactivar la superposición flotante del monitor
- Muestra el estado activo/inactivo

**Widget de Pantalla de Inicio**
- Muestra tres métricas clave: % de carga de CPU, temperatura de CPU y % de uso de memoria
- Se actualiza automáticamente; toca cualquier métrica para abrir OpenMonitor
- Ligero — lee directamente de `/proc/stat`, `/sys/class/thermal/` y `/proc/meminfo`

---

## Arquitectura

```
┌──────────────────────────────────────────────────────────────┐
│                           app                                │
│    MainActivity · Splash · Agreement · Settings · Nav3       │
├──────────┬──────────┬──────────┬──────────┬──────────────────┤
│ feature- │ feature- │ feature- │ feature- │    feature-      │
│ overview │ battery  │   fps    │ process  │    hardware      │
├──────────┼──────────┼──────────┼──────────┼──────────────────┤
│ feature- │ feature- │ feature- │ feature- │    feature-      │
│  float   │   cpu    │  signal  │signaling │     events       │
├──────────┴──────────┴──────────┴──────────┼──────────────────┤
│         feature-key-attestation           │     service      │
├───────────────────────────────────────────┴──────────────────┤
│  core-ui   │  core-data  │  core-model  │   core-common      │
├────────────┼─────────────┼──────────────┼────────────────────┤
│            │core-database│core-daemon-api│                   │
├────────────┴─────────────┴──────────────┴────────────────────┤
│              daemon-rust (Rust privileged daemon)            │
│  AF_UNIX IPC · sysfs polling · SurfaceFlinger FPS · DIAG     │
│  QMI / AF_QIPCRTR · RRC / NAS PDU capture · PCAP writer      │
└──────────────────────────────────────────────────────────────┘
```

### Demonio Privilegiado de Rust (`daemon-rust`)

Un binario de Rust ligero que se ejecuta con privilegios elevados para recolectar datos del sistema inaccesibles para aplicaciones normales:

- **Daemonización double-fork** con IPC de socket AF_UNIX
- **Protocolo de cable JSON** con prefijo de longitud
- **Muestreo multi-hilo** — freq/carga de CPU, zonas térmicas, estado de GPU, corriente/potencia de batería, FPS vía SurfaceFlinger
- **Puente DIAG del módem** — los servicios AF_QIPCRTR / QMI (NAS para señal y celda, DMS para info del dispositivo) alimentan la pestaña Dispositivo de Red Celular; la captura de PDU RRC / NAS / IMS con escritor PCAP potencia las pestañas de Señalización y Eventos (solo Qualcomm Snapdragon)
- **Anclaje de certificados APK v2** para autenticación de cliente
- **Modos de inicio** — ROOT (libsu), Shizuku (UserService), ADB (CLI)

### Capa Nativa

- **cpuinfo-bridge** — Puente JNI hacia [cpuinfo](https://github.com/pytorch/cpuinfo) para topología detallada de CPU e información de caché
- **vulkan-info** — Enumeración de capacidades de Vulkan vía JNI

---

## Stack Tecnológico

| Categoría | Tecnología | Versión |
|:---------|:-----------|:--------|
| Lenguaje | Kotlin | 2.3 |
| UI | Jetpack Compose + Material 3 | BOM 2026.03 |
| Navegación | Navigation3 (runtime + ui) | 1.1.0 |
| DI | Dagger Hilt | 2.59.2 |
| Base de Datos | Room | 2.8.4 |
| Asíncrono | Kotlin Coroutines | 1.10.2 |
| Acceso Root | libsu | 6.0.0 |
| Permisos | Shizuku API | 13.1.5 |
| Fondo | WorkManager | 2.11.2 |
| Gráficos | Vico | 3.1.0 |
| Tematización | Material Kolor | 4.1.1 |
| Analítica | Firebase Analytics | BOM 34.12.0 |
| Demonio | Rust (daemon-rust) | Edition 2021 |
| Nativo | cpuinfo + Vulkan Info | arm64-v8a |
| Markdown | multiplatform-markdown-renderer | 0.40.2 |

---

## Compilación desde el Código Fuente

### Prerrequisitos

- JDK 17+
- Android SDK con compile SDK 36
- CMake 3.22.1 (instalado automáticamente por Gradle)
- NDK (para módulos nativos de cpuinfo & Vulkan)

### Build

```bash
git clone https://github.com/1orz/OpenMonitor-src.git
cd OpenMonitor-src
./gradlew assembleDebug
```

El APK se generará en `app/build/outputs/apk/debug/`.

### aarch64 Linux (Termux)

Para compilar en dispositivos Linux ARM64:

```bash
bash scripts/setup-aarch64-build-env.sh
./gradlew assembleDebug
```

El script de configuración maneja automáticamente todas las adaptaciones de la cadena de herramientas específicas de la plataforma (aapt2, CMake, wrappers de NDK).

---

## Privacidad

- **Todos los datos de monitoreo permanecen en el dispositivo** — no hay telemetría de métricas del sistema
- **Firebase Analytics** es opcional y anónimo
- **Todas las operaciones son de solo lectura** — OpenMonitor nunca modifica tu sistema
- **Código abierto** — inspecciona cada línea de código tú mismo

Consulta la [Política de Privacidad](https://om.cloudorz.com/) completa para más detalles.

---

## Contribución

¡Las contribuciones son bienvenidas! No dudes en enviar problemas (issues) y pull requests en [GitHub](https://github.com/1orz/OpenMonitor/issues).

---

## Licencia

Consulta el archivo [LICENSE](LICENSE) para más detalles.

---

<div align="center">

**Hecho con Kotlin, Rust y Compose**

</div>
