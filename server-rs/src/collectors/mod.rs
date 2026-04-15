//! Sysfs sampling thread.
//!
//! One thread drives the /proc + /sys readers at 500 ms cadence. Unlike the
//! previous seqlock-protected shm layout, writes now go into
//! `SnapshotStore` — a plain `Arc<Mutex<SnapshotData>>` shared with the IPC
//! subscribe loop. Contention is a non-issue at this cadence.
//!
//! FPS and foreground detection are handled by *separate* threads (see
//! `crate::fps` and `crate::fg`) so that a slow `dumpsys` subprocess can
//! never stall the main data plane.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;

use crate::snapshot::{SnapshotStore, CPU_CORES_MAX};

pub mod cached_file;
pub mod cpu;
pub mod gpu;
pub mod memory;
pub mod power;
pub mod thermal;

/// Spawn the sysfs sampler. The thread runs until `exit_flag` is observed.
pub fn start_sampler(store: SnapshotStore, exit_flag: Arc<AtomicBool>) {
    thread::Builder::new()
        .name("om-sampler".into())
        .spawn(move || sampler_loop(store, exit_flag))
        .expect("spawn sampler thread");
}

fn sampler_loop(store: SnapshotStore, exit_flag: Arc<AtomicBool>) {
    let mut power = power::PowerReader::open();
    let mut cpu = cpu::CpuReader::open();
    let mut thermal = thermal::ThermalReader::open();
    let mut gpu = gpu::GpuReader::open();
    let mut mem = memory::MemoryReader::open();

    while !exit_flag.load(Ordering::Acquire) {
        let t0 = std::time::Instant::now();

        let now_ns = now_monotonic_ns();
        let p = power.sample();
        let c = cpu.sample();
        let th = thermal.sample();
        let g = gpu.sample();
        let m = mem.sample();

        store.update(|snap| {
            snap.ts_ns = now_ns;

            // CPU
            let n = c.load_pct.len().min(CPU_CORES_MAX);
            for i in 0..CPU_CORES_MAX {
                snap.cpu.load[i] = if i < n { c.load_pct[i] } else { -1 };
                snap.cpu.freq[i] = if i < n { c.freq_mhz[i] } else { -1 };
            }
            snap.cpu.temp_x10 = th.cpu_c_x10;

            // GPU
            snap.gpu.freq = g.freq_mhz;
            snap.gpu.load = g.load_pct;

            // Memory
            snap.mem.total_mb = m.total_mb;
            snap.mem.avail_mb = m.avail_mb;
            snap.mem.ddr_mbps = m.ddr_freq_mbps;

            // Battery
            snap.batt.current_ma = p.current_ma;
            snap.batt.voltage_mv = p.voltage_mv;
            snap.batt.temp_x10 = p.temp_c_x10;
            snap.batt.capacity = p.capacity;
            snap.batt.status = p.status;
        });

        let elapsed = t0.elapsed();
        let target = Duration::from_millis(500);
        if elapsed < target {
            thread::sleep(target - elapsed);
        }
    }
}

fn now_monotonic_ns() -> u64 {
    let mut ts = libc::timespec { tv_sec: 0, tv_nsec: 0 };
    unsafe { libc::clock_gettime(libc::CLOCK_MONOTONIC, &mut ts); }
    (ts.tv_sec as u64) * 1_000_000_000 + ts.tv_nsec as u64
}
