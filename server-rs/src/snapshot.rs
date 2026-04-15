//! In-process snapshot store.
//!
//! Three producer threads (sysfs sampler / fg detector / FPS collector) each
//! call [`SnapshotStore::update_*`] on their own cadence. The IPC subscribe
//! loop calls [`SnapshotStore::read`] and serializes to JSON.
//!
//! No seqlock, no shared memory — every producer takes the same Mutex. We're
//! writing at most 2 Hz per field, so contention is non-existent. The lock is
//! held only long enough to memcpy into a `SnapshotData`.
//!
//! Wire format is the serde-JSON representation of [`SnapshotData`]. Field
//! names here MUST match the `DaemonClient.kt` deserializer on the App side
//! (see `core-data/src/main/java/.../ipc/DaemonClient.kt`).
//!
//! Missing / unavailable fields use sentinel values rather than `null` to
//! keep the wire format fixed-shape: integer fields default to `-1`, string
//! fields default to `""`. The App side treats sentinels as "no data".

use std::sync::{Arc, Mutex};

use serde::Serialize;

pub const CPU_CORES_MAX: usize = 16;

/// Snapshot written to the wire on every subscribe tick.
#[derive(Clone, Debug, Default, Serialize)]
pub struct SnapshotData {
    /// Monotonic clock timestamp, nanoseconds since boot.
    pub ts_ns: u64,
    pub cpu: CpuBlock,
    pub gpu: GpuBlock,
    pub mem: MemBlock,
    pub batt: BattBlock,
    pub fps: FpsBlock,
    pub focus: FocusBlock,
}

#[derive(Clone, Debug, Serialize)]
pub struct CpuBlock {
    /// Per-core load percentage (0-100). `-1` = core not present / not sampled yet.
    pub load: [i32; CPU_CORES_MAX],
    /// Per-core current frequency, MHz. `-1` = unavailable.
    pub freq: [i32; CPU_CORES_MAX],
    /// CPU thermal zone °C × 10. `-1` = unavailable.
    pub temp_x10: i32,
}

impl Default for CpuBlock {
    fn default() -> Self {
        Self {
            load: [-1; CPU_CORES_MAX],
            freq: [-1; CPU_CORES_MAX],
            temp_x10: -1,
        }
    }
}

#[derive(Clone, Debug, Default, Serialize)]
pub struct GpuBlock {
    pub load: i32,
    pub freq: i32,
}

#[derive(Clone, Debug, Default, Serialize)]
pub struct MemBlock {
    pub total_mb: i32,
    pub avail_mb: i32,
    pub ddr_mbps: i32,
}

#[derive(Clone, Debug, Default, Serialize)]
pub struct BattBlock {
    pub current_ma: i32,
    pub voltage_mv: i32,
    pub temp_x10: i32,
    pub capacity: i32,
    /// BatteryManager.BATTERY_STATUS_* constants.
    pub status: i32,
}

#[derive(Clone, Debug, Default, Serialize)]
pub struct FpsBlock {
    /// FPS × 100 (fixed point). `-1` = unavailable.
    pub x100: i32,
    pub jank: i32,
    pub big_jank: i32,
    pub layer: String,
}

#[derive(Clone, Debug, Default, Serialize)]
pub struct FocusBlock {
    pub pkg: String,
    pub screen_on: bool,
}

/// Thread-safe handle to a single shared `SnapshotData`.
#[derive(Clone)]
pub struct SnapshotStore {
    inner: Arc<Mutex<SnapshotData>>,
}

impl SnapshotStore {
    pub fn new() -> Self {
        Self { inner: Arc::new(Mutex::new(SnapshotData::default())) }
    }

    /// Run `f` with mutable access to the shared snapshot. The lock is held
    /// only for the closure's duration.
    pub fn update<F: FnOnce(&mut SnapshotData)>(&self, f: F) {
        if let Ok(mut g) = self.inner.lock() {
            f(&mut g);
        }
    }

    /// Deep clone of the current snapshot. Used by the IPC subscribe loop.
    pub fn read(&self) -> SnapshotData {
        self.inner.lock().map(|g| g.clone()).unwrap_or_default()
    }
}

impl Default for SnapshotStore {
    fn default() -> Self { Self::new() }
}
