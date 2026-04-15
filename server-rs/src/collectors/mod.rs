//! Data collectors. One sampling thread drives them all — we don't want
//! N threads fighting for the seqlock. Every 500 ms the thread polls each
//! collector for its latest numbers, then writes the whole Snapshot in a
//! single seqlock-protected critical section.
//!
//! Individual collectors hold their own state (cached FDs, /proc/stat deltas,
//! etc.) but never touch the shared region directly.

use std::sync::Arc;
use std::thread;
use std::time::Duration;

use crate::service::RuntimeHandle;
use crate::shm::{SharedSnapshot, CPU_CORES_MAX, FPS_LAYER_LEN, LAST_FOCUSED_PKG_LEN};

pub mod cached_file;
pub mod cpu;
pub mod fps;
pub mod gpu;
pub mod memory;
pub mod power;
pub mod thermal;

pub fn start_all(shm: Arc<SharedSnapshot>, rt: RuntimeHandle) {
    thread::Builder::new()
        .name("openmonitor-sampler".into())
        .spawn(move || sampler_loop(shm, rt))
        .expect("spawn sampler thread");
}

fn sampler_loop(shm: Arc<SharedSnapshot>, rt: RuntimeHandle) {
    let mut power = power::PowerReader::open();
    let mut cpu = cpu::CpuReader::open();
    let mut thermal = thermal::ThermalReader::open();
    let mut gpu = gpu::GpuReader::open();
    let mut mem = memory::MemoryReader::open();
    let mut fps = fps::FpsReader::open();
    let focus_handle = fps.attach_focus();

    start_foreground_thread(focus_handle, rt.clone());

    loop {
        let t0 = std::time::Instant::now();

        let now_ns = now_monotonic_ns();
        let p = power.sample();
        let c = cpu.sample();
        let th = thermal.sample();
        let g = gpu.sample();
        let m = mem.sample();
        let f = fps.sample();

        shm.with_write(|snap| {
            snap.header.timestamp_ns = now_ns;

            // CPU
            let n = c.load_pct.len().min(CPU_CORES_MAX);
            for i in 0..CPU_CORES_MAX {
                snap.cpu_load_pct[i] = if i < n { c.load_pct[i] } else { -1 };
                snap.cpu_freq_mhz[i] = if i < n { c.freq_mhz[i] } else { -1 };
            }
            snap.cpu_temp_c_x10 = th.cpu_c_x10;

            // GPU
            snap.gpu_freq_mhz = g.freq_mhz;
            snap.gpu_load_pct = g.load_pct;

            // Memory
            snap.mem_total_mb = m.total_mb;
            snap.mem_avail_mb = m.avail_mb;
            snap.ddr_freq_mbps = m.ddr_freq_mbps;

            // Battery
            snap.battery_current_ma = p.current_ma;
            snap.battery_voltage_mv = p.voltage_mv;
            snap.battery_temp_c_x10 = p.temp_c_x10;
            snap.battery_capacity = p.capacity;
            snap.battery_status = p.status;

            // FPS
            snap.fps_x100 = f.fps_x100;
            snap.jank = f.jank;
            snap.big_jank = f.big_jank;
            copy_cstr(&mut snap.fps_layer, &f.layer, FPS_LAYER_LEN);
            copy_cstr(&mut snap.last_focused_pkg, &f.focused_pkg, LAST_FOCUSED_PKG_LEN);

            // Event state (placeholder — will be driven by event collectors).
            snap.screen_interactive = 1;
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

fn copy_cstr(dst: &mut [u8], s: &str, cap: usize) {
    let bytes = s.as_bytes();
    let n = bytes.len().min(cap.saturating_sub(1));
    dst[..n].copy_from_slice(&bytes[..n]);
    if n < cap { dst[n] = 0; }
    for b in &mut dst[n + 1..] { *b = 0; }
}

/// Foreground detection on a dedicated thread — binder dumps can be slow and
/// must not block the 500 ms sampler cadence.
fn start_foreground_thread(
    focus: Arc<std::sync::Mutex<String>>,
    rt: RuntimeHandle,
) {
    thread::Builder::new()
        .name("openmonitor-fg".into())
        .spawn(move || loop {
            if let Some(pkg) = crate::events::foreground::get_focused_package() {
                if let Ok(mut g) = focus.lock() {
                    if g.as_str() != pkg {
                        log::info!("focus → {pkg}");
                        rt.notify_focus(&pkg);
                        *g = pkg;
                    }
                }
            }
            thread::sleep(Duration::from_secs(2));
        })
        .expect("spawn foreground thread");
}
