//! CPU load and frequency readers.
//!
//! Load: derived from /proc/stat differential (user+nice+system+irq+softirq vs
//!       idle+iowait), per core. First sample reports 0 (no previous to diff
//!       against).
//! Freq: /sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq (kHz → MHz).

use super::cached_file::CachedSysFile;
use std::fs::File;
use std::io::{Read, Seek, SeekFrom};

pub struct CpuSample {
    pub load_pct: Vec<i32>,
    pub freq_mhz: Vec<i32>,
}

pub struct CpuReader {
    stat: Option<File>,
    stat_buf: Vec<u8>,
    prev: Vec<CpuTicks>,
    freq_files: Vec<Option<CachedSysFile>>,
}

#[derive(Default, Clone, Copy)]
struct CpuTicks {
    busy: u64,
    total: u64,
}

impl CpuReader {
    pub fn open() -> Self {
        let stat = File::open("/proc/stat").ok();
        let mut freq_files = Vec::new();
        for cpu in 0..crate::snapshot::CPU_CORES_MAX {
            let path = format!("/sys/devices/system/cpu/cpu{cpu}/cpufreq/scaling_cur_freq");
            freq_files.push(CachedSysFile::try_open(&path));
        }
        Self { stat, stat_buf: Vec::with_capacity(4096), prev: Vec::new(), freq_files }
    }

    pub fn sample(&mut self) -> CpuSample {
        let now = read_cpu_ticks(self.stat.as_mut(), &mut self.stat_buf);

        let load_pct: Vec<i32> = if self.prev.is_empty() || self.prev.len() != now.len() {
            vec![0; now.len()]
        } else {
            now.iter().zip(self.prev.iter()).map(|(n, p)| {
                let dt = n.total.saturating_sub(p.total);
                let db = n.busy.saturating_sub(p.busy);
                if dt == 0 { 0 } else { ((db as f64 / dt as f64) * 100.0).round() as i32 }
            }).collect()
        };
        self.prev = now;

        let freq_mhz: Vec<i32> = self.freq_files.iter_mut().map(|opt| {
            match opt {
                Some(f) => {
                    let khz = f.read_i64_or_minus1();
                    if khz <= 0 { -1 } else { (khz / 1000) as i32 }
                }
                None => -1,
            }
        }).collect();

        CpuSample { load_pct, freq_mhz }
    }
}

/// Parse per-cpu tick totals. Line format:
///   cpu0 user nice system idle iowait irq softirq steal guest guest_nice
/// We skip the aggregate "cpu " line and collect "cpuN " rows.
fn read_cpu_ticks(file: Option<&mut File>, buf: &mut Vec<u8>) -> Vec<CpuTicks> {
    let Some(f) = file else { return Vec::new() };
    buf.clear();
    if f.seek(SeekFrom::Start(0)).is_err() { return Vec::new(); }
    // Read ~4KB is more than enough for /proc/stat's cpu lines.
    let mut scratch = [0u8; 4096];
    let Ok(n) = f.read(&mut scratch) else { return Vec::new() };
    buf.extend_from_slice(&scratch[..n]);

    let text = std::str::from_utf8(buf).unwrap_or("");
    let mut out = Vec::new();
    for line in text.lines() {
        if !line.starts_with("cpu") { break; } // cpu*, then softirq etc.
        if line.starts_with("cpu ") { continue; } // aggregate

        let mut it = line.split_ascii_whitespace();
        let label = it.next();
        if label.map(|l| !l.starts_with("cpu")).unwrap_or(true) { continue; }
        let nums: Vec<u64> = it.filter_map(|t| t.parse::<u64>().ok()).collect();
        if nums.len() < 4 { continue; }
        // user nice system idle iowait irq softirq steal guest guest_nice
        let idle = nums.get(3).copied().unwrap_or(0) + nums.get(4).copied().unwrap_or(0);
        let total: u64 = nums.iter().sum();
        out.push(CpuTicks { busy: total.saturating_sub(idle), total });
    }
    out
}
