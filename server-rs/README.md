# openmonitor-server (Rust)

Privileged data-collection daemon for OpenMonitor.
Replaces the former Go-based `monitor-daemon` and the binder/AIDL IPC layer.

## Architecture

A standalone Rust binary (`openmonitor-server`) that double-forks into the
background, binds an **AF_UNIX stream socket** under the app's `filesDir`
(or `/data/local/tmp/openmonitor` for shell-launched modes), and serves
**length-prefixed JSON frames** over the connection.

### Launch modes

| Mode | Launcher | UID | Socket location |
|------|----------|-----|-----------------|
| `root` | libsu `Shell.cmd` | 0 | `$filesDir/openmonitor.sock` |
| `shizuku` | Shizuku `ShellUserService` exec | 2000 | `$filesDir/openmonitor.sock` |
| `adb` | Operator via `adb shell` | 2000 | `/data/local/tmp/openmonitor/openmonitor.sock` |

All three paths exec the same binary with `--mode <mode> --data-dir <dir>`.
The binary daemonizes (double-fork + `setsid`), writes the resolved socket
path to `sock.path` in the data directory, then enters `run_server`.

### Authentication

- **SO_PEERCRED** — every accepted connection is checked for UID match.
- **APK v2 certificate pinning** — on non-ADB modes the daemon reads the
  caller's APK from `/proc/<pid>/cmdline` and verifies the signing-block
  certificate SHA-256 against a build-time-embedded hash (Gradle
  `computeCertSha256` task). ADB mode is explicitly permissive.

### Threads

| Thread | Purpose |
|--------|---------|
| **sampler** | Polls sysfs every ~1 s: battery, CPU freq/load, thermal zones, GPU, memory/devfreq. Writes into `SnapshotStore`. |
| **fg** | Detects foreground app via `dumpsys activity` (fork+exec with SIGKILL timeout). |
| **fps** | Parses SurfaceFlinger timestats via `dumpsys SurfaceFlinger` (fork+exec with SIGKILL timeout). |
| **main** | Accepts AF_UNIX connections; reads requests, writes JSON snapshots. |

### Wire protocol

```
[4 bytes: payload length, big-endian u32][JSON payload]
```

Request and response are `serde`-derived Rust structs mirrored by Kotlin
`@Serializable` classes in `DaemonClient.kt` / `ServerSnapshot.kt`.

### Crate layout

```
Cargo.toml          bin + lib, release profile optimized for size
src/
  main.rs           CLI entry: arg parsing, double-fork daemonize
  lib.rs            Library surface (for host tests)
  core.rs           run_server: socket bind, thread spawn, serve loop
  ipc.rs            AF_UNIX accept + length-prefixed JSON framing
  auth.rs           SO_PEERCRED + APK v2 cert pinning policy
  apk_sign.rs       APK Signing Block v2 parser + SHA-256 fingerprint
  snapshot.rs       SnapshotStore (Arc<Mutex<ServerSnapshot>>)
  logging.rs        android_log / stderr log sink
  subproc.rs        fork+exec+SIGKILL-timeout helper for dumpsys
  fg.rs             Foreground-app detection thread
  fps.rs            SurfaceFlinger timestats parser thread
  collectors/       Per-subsystem sysfs readers (sampler thread)
```

### Snapshot layout

Defined once in
[SnapshotLayout.kt](../core/core-server-api/src/main/java/com/cloudorz/openmonitor/server/SnapshotLayout.kt)
(Kotlin `@Serializable` data class) and serialized as JSON by the Rust side.

## Build

Integrated into the Android build via `rust-android-gradle`:

```bash
./gradlew :server-rs:cargoBuild          # cross-compile arm64 binary + lib
./gradlew :server-rs:copyServerBinary    # copy ELF into jniLibs
./gradlew :app:assembleDebug             # packages everything
```

`cargo-ndk` must be installed. The NDK's shipped clang/lld is used via
`rust-android-gradle`'s `prebuiltToolchains = true`.
