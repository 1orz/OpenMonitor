//! GPU freq + load. Two vendor paths are tried at startup; the first one that
//! yields a sane read wins.
//!
//!   Qualcomm Adreno (kgsl):
//!     /sys/class/kgsl/kgsl-3d0/gpuclk        freq (Hz)
//!     /sys/class/kgsl/kgsl-3d0/gpubusy       "busy total" — %ratio = busy/total*100
//!
//!   ARM Mali:
//!     /sys/class/misc/mali0/device/clock          or /cur_freq (MHz or Hz)
//!     /sys/class/misc/mali0/device/utilization    int 0-100
//!
//! On unknown SoCs we report -1 for both fields.

use super::cached_file::CachedSysFile;

pub struct GpuSample {
    pub freq_mhz: i32,
    pub load_pct: i32,
}

pub struct GpuReader {
    impl_: GpuImpl,
}

enum GpuImpl {
    Adreno { gpuclk: CachedSysFile, gpubusy: CachedSysFile },
    Mali { freq: CachedSysFile, util: CachedSysFile },
    Unknown,
}

impl GpuReader {
    pub fn open() -> Self {
        if let (Some(clk), Some(busy)) = (
            CachedSysFile::try_open("/sys/class/kgsl/kgsl-3d0/gpuclk"),
            CachedSysFile::try_open("/sys/class/kgsl/kgsl-3d0/gpubusy"),
        ) {
            return Self { impl_: GpuImpl::Adreno { gpuclk: clk, gpubusy: busy } };
        }
        if let (Some(freq), Some(util)) = (
            CachedSysFile::try_open("/sys/class/misc/mali0/device/clock")
                .or_else(|| CachedSysFile::try_open("/sys/class/misc/mali0/device/cur_freq")),
            CachedSysFile::try_open("/sys/class/misc/mali0/device/utilization"),
        ) {
            return Self { impl_: GpuImpl::Mali { freq, util } };
        }
        Self { impl_: GpuImpl::Unknown }
    }

    pub fn sample(&mut self) -> GpuSample {
        match &mut self.impl_ {
            GpuImpl::Adreno { gpuclk, gpubusy } => {
                let hz = gpuclk.read_i64_or_minus1();
                let freq_mhz = if hz <= 0 { -1 } else { (hz / 1_000_000) as i32 };
                let load_pct = parse_adreno_busy(gpubusy.read_str().ok().unwrap_or(""));
                GpuSample { freq_mhz, load_pct }
            }
            GpuImpl::Mali { freq, util } => {
                // Mali nodes may be in MHz already or raw Hz. Normalize: if
                // the value is absurdly large assume Hz.
                let raw = freq.read_i64_or_minus1();
                let freq_mhz = match raw {
                    n if n <= 0 => -1,
                    n if n > 10_000 => (n / 1_000_000) as i32,
                    n => n as i32,
                };
                let u = util.read_i64_or_minus1();
                let load_pct = if u < 0 { -1 } else { u.clamp(0, 100) as i32 };
                GpuSample { freq_mhz, load_pct }
            }
            GpuImpl::Unknown => GpuSample { freq_mhz: -1, load_pct: -1 },
        }
    }
}

/// Adreno `gpubusy` file format: "<busy> <total>\n". Compute busy/total*100.
fn parse_adreno_busy(s: &str) -> i32 {
    let mut it = s.split_ascii_whitespace();
    let busy: i64 = it.next().and_then(|t| t.parse().ok()).unwrap_or(-1);
    let total: i64 = it.next().and_then(|t| t.parse().ok()).unwrap_or(-1);
    if busy < 0 || total <= 0 { return -1; }
    ((busy as f64 / total as f64) * 100.0).round().clamp(0.0, 100.0) as i32
}
