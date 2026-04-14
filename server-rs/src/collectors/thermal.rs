//! Thermal zones. Port of Go daemon's thermal probing.
//!
//! Strategy: at startup, walk /sys/class/thermal/thermal_zone*/ and find the
//! first zone whose `type` contains "cpu" (case-insensitive). Cache its FD.
//! Report `-1` for °C×10 if no matching zone was found.

use super::cached_file::CachedSysFile;
use std::fs::read_to_string;

pub struct ThermalSample {
    pub cpu_c_x10: i32,
}

pub struct ThermalReader {
    cpu_temp: Option<CachedSysFile>,
}

impl ThermalReader {
    pub fn open() -> Self {
        let zone = discover_cpu_zone();
        let cpu_temp = zone.and_then(|i| {
            CachedSysFile::try_open(&format!("/sys/class/thermal/thermal_zone{i}/temp"))
        });
        Self { cpu_temp }
    }

    pub fn sample(&mut self) -> ThermalSample {
        let raw = self.cpu_temp.as_mut().map(|f| f.read_i64_or_minus1()).unwrap_or(-1);
        // thermal_zone temps are usually millidegrees (e.g. 48532 = 48.5 °C).
        // Some OEMs use decidegrees (485 = 48.5 °C). Normalize to °C × 10.
        let c_x10: i32 = if raw < 0 {
            -1
        } else if raw >= 1000 {
            (raw / 100) as i32
        } else {
            raw as i32
        };
        ThermalSample { cpu_c_x10: c_x10 }
    }
}

fn discover_cpu_zone() -> Option<u32> {
    for i in 0..64u32 {
        let t = read_to_string(format!("/sys/class/thermal/thermal_zone{i}/type")).ok()?;
        let t = t.trim().to_ascii_lowercase();
        if t.contains("cpu") || t == "soc_thermal" || t.starts_with("tsens") || t.contains("bigcluster") {
            return Some(i);
        }
    }
    None
}
