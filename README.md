<div align="center">

<img src="icon/openmonitor_logo.svg" width="128" height="128" alt="OpenMonitor Logo" />

# OpenMonitor

**A powerful, open-source Android system monitor built with Jetpack Compose & Material 3**

Real-time CPU, GPU, battery, FPS, thermal, memory, and process monitoring — with ROOT, Shizuku, and ADB support.

[![Release](https://img.shields.io/github/v/release/1orz/OpenMonitor?include_prereleases&logo=github&label=Release)](https://github.com/1orz/OpenMonitor/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen?logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)
[![Stars](https://img.shields.io/github/stars/1orz/OpenMonitor?style=flat&logo=github&label=Stars)](https://github.com/1orz/OpenMonitor/stargazers)
[![License](https://img.shields.io/github/license/1orz/OpenMonitor?label=License)](https://github.com/1orz/OpenMonitor/blob/main/LICENSE)

[**Download**](https://github.com/1orz/OpenMonitor/releases/latest) · [**Issues**](https://github.com/1orz/OpenMonitor/issues) · [**QQ Channel (CN)**](https://pd.qq.com/s/ft7tc3xae) · [**QQ Group (CN)**](https://qm.qq.com/cgi-bin/qm/qr?k=Kx6t5VIe6vwKSyk6gsf2CGCABO3V7qfe&jump_from=webapi&authKey=Qq+pLqZq/1Px+1g/r49xi2QDzcCfxxiH6kK5c2qdTNMvnggkmbbq6Aptw8oKruyz) · [**Telegram (CN/EN)**](https://t.me/OpenMonitor) · [**Website**](https://om.cloudorz.com/)

**English** | [**简体中文**](README_zh-CN.md) | [**日本語**](README_ja.md)

---

</div>

## Star History

<div align="center">

[![Star History Chart](https://api.star-history.com/svg?repos=1orz/OpenMonitor&type=Date)](https://star-history.com/#1orz/OpenMonitor&Date)

</div>

---

## Highlights

- Real-time monitoring of CPU, GPU, battery, FPS, memory, thermal, and processes
- Multiple privilege modes: ROOT (Magisk / KernelSU / APatch), Shizuku, ADB, and Basic
- Floating overlay for always-on-top monitoring across any app
- Quick Settings tile and home screen widget for instant access
- FPS recording with jank detection, session playback, and image/CSV export
- Complete hardware specs with 900+ SoC database, Vulkan & OpenGL enumeration
- Hardware-backed key attestation and device integrity verification
- Material 3 dynamic theming with System / Light / Dark / AMOLED modes
- Rust-based privileged daemon for high-performance, low-overhead data collection
- Localized in English, Simplified Chinese, Traditional Chinese, and Japanese

---

## Table of Contents

- [Getting Started](#getting-started)
- [Permission Modes](#permission-modes)
- [Features](#features)
  - [Overview Dashboard](#overview-dashboard)
  - [CPU Analysis](#cpu-analysis)
  - [Battery](#battery)
  - [FPS Recording](#fps-recording)
  - [Process Manager](#process-manager)
  - [Hardware Info](#hardware-info)
  - [Float Overlay](#float-overlay)
  - [Key Attestation](#key-attestation)
  - [Quick Settings Tile & Widget](#quick-settings-tile--widget)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Building from Source](#building-from-source)
- [Privacy](#privacy)
- [Contributing](#contributing)
- [License](#license)

---

## Getting Started

1. **Download** the latest APK from [GitHub Releases](https://github.com/1orz/OpenMonitor/releases/latest) and install it.
2. **Launch** OpenMonitor. On first launch you'll see a privacy agreement — review and accept to continue.
3. **Choose a permission mode.** The app automatically detects available privilege frameworks (ROOT, Shizuku, ADB) and recommends the best option. If no privileged access is available, Basic mode still provides core monitoring from world-readable sysfs.
4. **Explore.** The bottom navigation has three tabs:
   - **Features** — access all monitoring modules (Hardware, Battery, FPS, Process, Float, Key Attestation, etc.)
   - **Overview** — a live dashboard showing all key metrics at a glance
   - **Settings** — configure theme, language, privilege mode, haptic feedback, and more

---

## Permission Modes

OpenMonitor adapts to your device's available permissions with graceful fallback:

| Mode | How It Works | Capabilities |
|:-----|:-------------|:-------------|
| **ROOT** | Full privileged access via Magisk / KernelSU / APatch. The Rust daemon launches automatically via libsu. | All features — FPS, process kill, GPU frequency, full thermal, float overlay, battery current/power |
| **Shizuku** | Delegated permissions via Shizuku manager. The daemon runs as a Shizuku UserService. | FPS monitoring, process management, enhanced metrics |
| **ADB** | Start the daemon manually via `adb shell`. No extra app needed. | Same as Shizuku, suitable for devices without root |
| **Basic** | No special permissions required. | CPU load/frequency, memory, battery basics from world-readable sysfs |

> You can switch permission modes at any time from **Settings > System > Privilege Mode**.

---

## Features

### Overview Dashboard

The Overview tab provides a comprehensive real-time snapshot of your device:

- **CPU** — Total load percentage (color-coded: green < 50%, yellow < 80%, red >= 80%), average frequency (MHz), temperature, online/total core count, and per-core mini status indicators
- **Memory** — Arc gauge showing usage percentage, physical RAM used/total, available memory, swap usage, and ZRam compression ratio
- **GPU** — Model name, current/max frequency, load percentage with progress bar, active governor
- **SoC Info** — Device name, vendor logo, SoC model, fabrication process badge, architecture, ABI, CPU cluster configuration
- **Cache** — L1D / L1I / L2 / L3 cache sizes, ARM NEON support
- **System Chips** — Power draw (W), battery level & voltage, temperature, Android version, system uptime

All metrics update in real-time. The dashboard is designed to give a quick "is everything okay" glance.

### CPU Analysis

Detailed per-core CPU breakdown accessed from Overview or Hardware Info:

- **Cluster view** — Groups cores by cluster (Little / Big / Prime) with per-cluster governor, frequency range (min–max MHz), and average load
- **Per-core status** — Each core shows its number, microarchitecture name, current load %, frequency in MHz, and online/offline status
- **SoC details** — Hardware ID, architecture, ABI, governor policy, full cluster configuration with frequency ranges
- **Cache hierarchy** — L1D/L1I/L2/L3 sizes per core/cluster

### Battery

In-depth battery analytics with historical tracking:

- **Status card** — Current capacity %, voltage (V), temperature with color-coded health indicator, estimated time remaining or charging time
- **History chart** — Line chart showing battery level over time with selectable time ranges via filter chips
- **Discharge analysis** — Trend visualization over the selected period to understand drain patterns
- **App usage breakdown** — Per-app battery consumption with app icons, showing estimated drain percentage and mAh (requires Usage Stats permission — the app prompts you to grant it if not enabled)
- **CSV export** — Share battery history data as a CSV file via the share button

### FPS Recording

Professional-grade frame rate monitoring with session management:

**Starting a Recording**
1. Navigate to **Features > FPS Recording**
2. Toggle the **FPS Float** switch to enable the floating FPS overlay
3. Open the game or app you want to test — the overlay shows live FPS
4. The session records automatically. When done, return to OpenMonitor to end the session.

**Session List**
- All sessions are displayed as cards showing the app icon, app name, average FPS, duration, and timestamp
- FPS quality is color-coded: green (>= 50 fps), yellow (>= 30 fps), red (< 30 fps)
- Long-press to enter multi-select mode for batch deletion
- Tap the edit icon to rename sessions

**Session Detail & Analysis**

Tapping a session opens a comprehensive analysis view:

- **Device info** — CPU platform, device model, OS version
- **Session header** — App icon, name, version, package name, screen resolution, timestamp, duration
- **Stats grid** with six key metrics:
  - Max / Min / Avg FPS (color-coded)
  - FPS Variance (standard deviation)
  - Smoothness (% of frames >= 45 fps)
  - 5% Low (bottom 5th percentile)
  - Max battery temperature
  - Average power draw (W)
- **App switch timeline** — Horizontal scrollable bar showing which apps were in focus and when
- **Interactive charts** (toggle via settings icon):
  - FPS & Temperature (CPU temp, GPU temp, CPU/GPU usage)
  - Frame Time (max frame time in ms)
  - CPU/GPU Usage (total CPU, GPU, per-cluster loads)
  - CPU/GPU Frequency (per-cluster, GPU frequency)
  - Jank chart (jank and big jank per-frame deltas)
  - Power / Battery (power in W, battery %)
  - Battery Current (mA with max/min/avg)
  - Temperature (CPU, GPU, battery)
- Each chart supports **show/hide individual series** for focused analysis
- All charts are **scrollable and zoomable**

**Export Options**
- **Save as PNG** — Generates a shareable image with all charts, device info, and a branded banner with QR code
- **Save as CSV** — Exports raw data as a spreadsheet for external analysis

### Process Manager

Real-time process monitoring with management capabilities:

- **Search** — Filter processes by name with the search bar
- **Filter modes** — Toggle between "All Processes" and "Apps Only" (installed applications)
- **Sort options** — Sort by CPU %, Memory (MB), Name (alphabetical), or PID
- **Process list** — Each entry shows the app icon (or fallback icon), process name, PID, CPU usage (color-coded: green < 5%, yellow 5–20%, red > 20%), and memory in MB
- **Process detail** — Tap any process to view detailed information
- **Process count** — Total filtered process count displayed at the top

> Process kill functionality is available in ROOT and Shizuku modes.

### Hardware Info

Comprehensive device hardware specifications:

- **Processor** — Vendor logo, SoC name, fabrication process badge, core count, 64-bit badge, per-cluster CPU configuration with frequency ranges, governor, architecture, ABI. Links to CPU Analysis for deeper inspection.
- **GPU** — Model/renderer name, chip ID, vendor, GMEM size, shader cores, bus width, L2 cache (for Mali GPUs). Buttons to explore **Vulkan Features** and **OpenGL ES Features**.
- **Display** — Resolution, refresh rate, PPI, physical size (inches/mm), aspect ratio, density bucket, wide color gamut, HDR capabilities, panel name. Lists all supported resolutions and refresh rates.
- **Memory** — RAM size label (rounded to marketing size), usage bar with total/used/free. ZRam section with compression ratio if available.
- **Storage** — Marketing size, storage type (e.g., UFS 3.1), usage bar, partition breakdown with used space, file system type, block size.

**Vulkan Info Screen** — Deep dive into GPU capabilities:
- Device identity (name, vendor, device ID, type, API version, driver version)
- Memory heaps with device-local tags
- Limits (max image dimensions, compute invocations, buffer ranges, anisotropy)
- Core features (robust buffer access, geometry/tessellation shaders, multi-viewport, etc.)
- Queue families with flags and timestamp bits
- Full device and instance extension lists

### Float Overlay

Always-on-top monitoring overlay:

- **Multiple monitors** — Toggle individual overlays for FPS, CPU, Memory, GPU, and Temperature
- **Permission handling** — If overlay permission is not granted, a card prompts you to enable it in system settings
- **Quick access** — Can also be toggled from the Quick Settings tile

The overlay stays visible across all apps, perfect for monitoring during gameplay or benchmarks.

### Key Attestation

Hardware-backed device security verification:

- **Trust banner** — Shows whether the certificate chain is trusted with a clear status indicator
- **Boot state** — Verified / Self-Signed / Failed status with bootloader lock state (color-coded icons)
- **Attestation info** — Attestation version, security level (TEE / StrongBox / Software), Keymaster version, challenge, and unique ID
- **Revocation list** — Device certificate revocation status with network fetch, entry count, and cache expiry
- **Certificate chain** — Per-certificate details including subject, issuer, validity dates, and revocation status with reason
- **Authorization list** — Field-by-field display with HW/SW badges indicating hardware or software enforcement
- **Options menu:**
  - Toggle "Use Attest Key" / "Use StrongBox" / "Shizuku Mode"
  - Reset persistent attest key
  - Save certificate chain as `.p7b` file
  - Load certificate chain from file

### Quick Settings Tile & Widget

**Quick Settings Tile**
- Add the "Monitor" tile to your Quick Settings panel
- Tap to toggle the floating monitor overlay on/off
- Shows active/inactive state

**Home Screen Widget**
- Displays three key metrics: CPU load %, CPU temperature, and memory usage %
- Updates automatically; tap any metric to open OpenMonitor
- Lightweight — reads directly from `/proc/stat`, `/sys/class/thermal/`, and `/proc/meminfo`

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                           app                                │
│    MainActivity · Splash · Agreement · Settings · Nav3       │
├──────────┬──────────┬──────────┬──────────┬──────────────────┤
│ feature- │ feature- │ feature- │ feature- │    feature-      │
│ overview │ battery  │   fps    │ process  │    hardware      │
├──────────┼──────────┼──────────┴──────────┼──────────────────┤
│ feature- │ feature- │    feature-key-     │     service      │
│  float   │   cpu    │    attestation      │                  │
├──────────┴──────────┴─────────────────────┴──────────────────┤
│  core-ui   │  core-data  │  core-model  │   core-common      │
├────────────┼─────────────┼──────────────┼────────────────────┤
│            │core-database│core-daemon-api│                   │
├────────────┴─────────────┴──────────────┴────────────────────┤
│              daemon-rust (Rust privileged daemon)            │
│      AF_UNIX IPC · sysfs polling · SurfaceFlinger FPS       │
└──────────────────────────────────────────────────────────────┘
```

### Rust Privileged Daemon (`daemon-rust`)

A lightweight Rust binary that runs with elevated privileges to collect system data inaccessible to normal apps:

- **Double-fork daemonization** with AF_UNIX socket IPC
- **Length-prefixed JSON** wire protocol
- **Multi-threaded sampling** — CPU freq/load, thermal zones, GPU state, battery current/power, FPS via SurfaceFlinger
- **APK v2 certificate pinning** for client authentication
- **Launch modes** — ROOT (libsu), Shizuku (UserService), ADB (CLI)

### Native Layer

- **cpuinfo-bridge** — JNI bridge to [cpuinfo](https://github.com/pytorch/cpuinfo) for detailed CPU topology and cache information
- **vulkan-info** — Vulkan capability enumeration via JNI

---

## Tech Stack

| Category | Technology | Version |
|:---------|:-----------|:--------|
| Language | Kotlin | 2.3 |
| UI | Jetpack Compose + Material 3 | BOM 2026.03 |
| Navigation | Navigation3 (runtime + ui) | 1.1.0 |
| DI | Dagger Hilt | 2.59.2 |
| Database | Room | 2.8.4 |
| Async | Kotlin Coroutines | 1.10.2 |
| Root Access | libsu | 6.0.0 |
| Permissions | Shizuku API | 13.1.5 |
| Background | WorkManager | 2.11.2 |
| Charts | Vico | 3.1.0 |
| Theming | Material Kolor | 4.1.1 |
| Analytics | Firebase Analytics | BOM 34.12.0 |
| Daemon | Rust (daemon-rust) | Edition 2021 |
| Native | cpuinfo + Vulkan Info | arm64-v8a |
| Markdown | multiplatform-markdown-renderer | 0.40.2 |

---

## Building from Source

### Prerequisites

- JDK 17+
- Android SDK with compile SDK 36
- CMake 3.22.1 (auto-installed by Gradle)
- NDK (for native cpuinfo & Vulkan modules)

### Build

```bash
git clone https://github.com/1orz/OpenMonitor-src.git
cd OpenMonitor-src
./gradlew assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/`.

### aarch64 Linux (Termux)

For building on ARM64 Linux devices:

```bash
bash scripts/setup-aarch64-build-env.sh
./gradlew assembleDebug
```

The setup script handles all platform-specific toolchain adaptations (aapt2, CMake, NDK wrappers) automatically.

---

## Privacy

- **All monitoring data stays on-device** — no telemetry of system metrics
- **Firebase Analytics** is optional and anonymous
- **All operations are read-only** — OpenMonitor never modifies your system
- **Open source** — inspect every line of code yourself

See the full [Privacy Policy](https://om.cloudorz.com/) for details.

---

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests on [GitHub](https://github.com/1orz/OpenMonitor/issues).

---

## License

See the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Made with Kotlin, Rust, and Compose**

</div>
