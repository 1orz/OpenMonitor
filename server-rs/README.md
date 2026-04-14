# openmonitor-server (Rust)

Privileged data-collection server for OpenMonitor, replacing `monitor-daemon/`
(Go + TCP + JSON). Written in Rust, communicates with the App over AIDL
Binder (control plane) and a shared-memory Snapshot (data plane).

## Crate layout

```
Cargo.toml           bin + cdylib, release profile optimized for size
build.rs             rsbinder-aidl stub generation from aidl/ and aidl-system/
aidl/                Our own IMonitorService / IMonitorCallback
aidl-system/         Hidden AOSP AIDL (populated during Phase 4)
src/
  main.rs            libsu launch path entry (standalone ELF, uid 0/2000)
  lib.rs             Shizuku launch path surface (cdylib)
  core.rs            Common server bootstrap (mode-independent)
  logging.rs         logcat-backed `log` crate sink
  service.rs         IMonitorService impl + callback registry
  aidl_gen.rs        include!() of rsbinder-generated stubs
  shm.rs             4KB ashmem + seqlock Snapshot region
  collectors/        Per-subsystem sysfs readers (one sampling thread)
    cached_file.rs     persistent-FD read_int pattern
    power.rs           battery sysfs
    cpu.rs             /proc/stat + cpufreq
    thermal.rs         thermal_zone probe + cache
    gpu.rs             Adreno / Mali branches
    memory.rs          /proc/meminfo + devfreq
    fps.rs             Glue only — binder/dump in events/sf_dump.rs
  events/            Hidden-AIDL event listeners (push, not poll)
    task_stack.rs      ITaskStackListener → focused package
    battery_props.rs   IBatteryPropertiesRegistrar fallback current
    display.rs         IDisplayManager screen on/off
    sf_dump.rs         SurfaceFlinger binder dump → timestats parser
  binder_push.rs     Reverse provider.call("setBinder", …) publication
  jni_entry.rs       Java_… symbols for RustEntry.kt
```

## Launch paths

### libsu (preferred — zero JVM)

```
App  ─ libsu Shell.cmd ─▶  /data/…/files/openmonitor-server
                            ├─ daemonize (fork×2 + setsid)
                            ├─ create ashmem + spawn sampler thread
                            ├─ register IMonitorService binder
                            └─ am.getContentProviderExternal(<pkg>.binderProvider)
                               .call("setBinder", Bundle{binder = self})
                                                ↑
            App  ◀─ BinderProvider.call("setBinder") ─────────┘
                 └─ MonitorClient.onBinderReceived
```

### Shizuku (JVM baseline ~20MB, needed only without root)

```
App  ─ Shizuku.bindUserService ──▶  Shizuku server
                                     └─ app_process -cp <apk>
                                        com.cloudorz.openmonitor.server.RustEntry
                                           │
                                           ├─ System.loadLibrary("openmonitor_server")
                                           ├─ nativeMain() → core::run_server
                                           └─ nativeGetBinder() returns the jobject

App  ◀─ onServiceConnected(binder) ──  Shizuku (handles return value routing)
```

## Data model

Snapshot layout is defined *once* in
[SnapshotLayout.kt](../core/core-server-api/src/main/java/com/cloudorz/openmonitor/server/SnapshotLayout.kt)
and mirrored by `#[repr(C)] struct Snapshot` in `src/shm.rs`. Compile-time
`offset_of!` asserts on the Rust side guard against drift.

Seqlock protocol:

```
writer (Rust sampler thread, single writer):
    prev = seq.load(Relaxed)
    seq.store(prev | 1, Release)        ; mark writing (odd)
    fence(Release)
    write fields into struct
    fence(Release)
    seq.store((prev | 1) + 1, Release)  ; publish (even)

reader (Kotlin / anyone with read-only mmap):
    loop:
        v1 = seq.load(Acquire)          ; if odd → retry
        copy all fields
        v2 = seq.load(Acquire)          ; if v1 != v2 → retry (torn read)
```

## Build (aspirational — Phase 0 to validate)

Integrated into Android build via `rust-android-gradle`:

```
./gradlew :server-rs:cargoBuild            # compiles arm64 cdylib + bin
./gradlew :server-rs:copyServerBinary      # renames ELF into jniLibs
./gradlew :app:assembleDebug               # packages everything together
```

`cargo-ndk` must be installed; rust-android-gradle's `prebuiltToolchains = true`
uses the NDK's shipped clang/lld — no extra toolchain setup needed on CI.

## Phased rollout

| Phase | Deliverable                                          | State |
|-------|------------------------------------------------------|-------|
| 0     | rsbinder POC: AIDL round-trip on-device              | TODO  |
| 1     | AIDL module + Shizuku launcher + minimal server      | SCAFFOLDED |
| 2     | ashmem + seqlock double-ended, layout asserts        | DONE  |
| 3     | sysfs collectors (power/cpu/thermal/gpu/memory)       | DONE  |
| 4     | Hidden AIDL extraction + event listeners             | TODO  |
| 5     | SurfaceFlinger binder dump + timestats parser        | TODO  |
| 6     | libsu path + reverse ContentProvider push             | SCAFFOLDED |
| 7     | App-side MonitorClient + feature repo migration      | PARTIAL (client + adapter done; repositories still read DaemonDataSource) |
| 8     | Delete monitor-daemon/ and old Daemon* datasources   | TODO  |
