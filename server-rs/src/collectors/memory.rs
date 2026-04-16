//! /proc/meminfo reader + DDR freq probe.
//!
//! meminfo lines of interest:
//!   MemTotal:         7932232 kB
//!   MemAvailable:     3012456 kB
//!
//! DDR freq is vendor-specific. We try common devfreq paths at startup and
//! fall back to -1 if none exist.

use super::cached_file::CachedSysFile;
use std::fs::File;
use std::io::{Read, Seek, SeekFrom};

pub struct MemorySample {
    pub total_mb: i32,
    pub avail_mb: i32,
    pub ddr_freq_mbps: i32,
}

pub struct MemoryReader {
    meminfo: Option<File>,
    buf: [u8; 4096],
    ddr: Option<CachedSysFile>,
    ddr_is_hz: bool,
}

impl MemoryReader {
    pub fn open() -> Self {
        let meminfo = File::open("/proc/meminfo").ok();
        // Common Qualcomm / MediaTek / Samsung paths:
        let candidates: &[(&str, bool)] = &[
            ("/sys/class/devfreq/mtk-dvfsrc-devfreq/cur_freq", true),
            ("/sys/class/devfreq/dsu-devfreq/cur_freq", true),
            ("/sys/class/devfreq/soc:qcom,cpu-cpu-llcc-bw/cur_freq", true),
            ("/sys/kernel/ddr_boost/ddr_freq", false),
        ];
        let mut ddr = None;
        let mut ddr_is_hz = false;
        for (path, is_hz) in candidates {
            if let Some(f) = CachedSysFile::try_open(path) {
                ddr = Some(f);
                ddr_is_hz = *is_hz;
                break;
            }
        }
        Self { meminfo, buf: [0u8; 4096], ddr, ddr_is_hz }
    }

    pub fn sample(&mut self) -> MemorySample {
        let (total_kb, avail_kb) = read_meminfo(self.meminfo.as_mut(), &mut self.buf);

        let ddr_raw = self.ddr.as_mut().map(|f| f.read_i64_or_minus1()).unwrap_or(-1);
        let ddr_freq_mbps = match ddr_raw {
            n if n < 0 => -1,
            n if self.ddr_is_hz => (n / 1_000_000) as i32,
            n => n as i32,
        };

        MemorySample {
            total_mb: if total_kb < 0 { -1 } else { (total_kb / 1024) as i32 },
            avail_mb: if avail_kb < 0 { -1 } else { (avail_kb / 1024) as i32 },
            ddr_freq_mbps,
        }
    }
}

fn read_meminfo(file: Option<&mut File>, buf: &mut [u8]) -> (i64, i64) {
    let Some(f) = file else { return (-1, -1); };
    if f.seek(SeekFrom::Start(0)).is_err() { return (-1, -1); }
    let n = f.read(buf).unwrap_or(0);
    let text = std::str::from_utf8(&buf[..n]).unwrap_or("");

    let mut total = -1i64;
    let mut avail = -1i64;
    for line in text.lines() {
        if let Some(v) = strip_prefix_kb(line, "MemTotal:") { total = v; }
        else if let Some(v) = strip_prefix_kb(line, "MemAvailable:") { avail = v; }
        if total >= 0 && avail >= 0 { break; }
    }
    (total, avail)
}

fn strip_prefix_kb(line: &str, prefix: &str) -> Option<i64> {
    let rest = line.strip_prefix(prefix)?.trim();
    let num: String = rest.chars().take_while(|c| c.is_ascii_digit()).collect();
    num.parse().ok()
}
