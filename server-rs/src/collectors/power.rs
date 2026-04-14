//! Battery / power sysfs. Ports the Go daemon's battery.go readers.
//!
//! Common paths on Android:
//!   /sys/class/power_supply/battery/current_now   (int, µA — sign varies by OEM)
//!   /sys/class/power_supply/battery/voltage_now   (int, µV)
//!   /sys/class/power_supply/battery/temp          (int, 0.1°C)
//!   /sys/class/power_supply/battery/capacity      (int, 0-100)
//!   /sys/class/power_supply/battery/status        (string: Charging / Discharging / Full / Not charging)

use super::cached_file::CachedSysFile;

pub struct PowerSample {
    pub current_ma: i32,
    pub voltage_mv: i32,
    pub temp_c_x10: i32,
    pub capacity: i32,
    pub status: i32, // BatteryManager.BATTERY_STATUS_*
}

impl Default for PowerSample {
    fn default() -> Self {
        Self { current_ma: 0, voltage_mv: 0, temp_c_x10: -1, capacity: -1, status: 0 }
    }
}

pub struct PowerReader {
    current: Option<CachedSysFile>,
    voltage: Option<CachedSysFile>,
    temp: Option<CachedSysFile>,
    capacity: Option<CachedSysFile>,
    status: Option<CachedSysFile>,
}

impl PowerReader {
    pub fn open() -> Self {
        let base = "/sys/class/power_supply/battery";
        Self {
            current: CachedSysFile::try_open(&format!("{base}/current_now")),
            voltage: CachedSysFile::try_open(&format!("{base}/voltage_now")),
            temp: CachedSysFile::try_open(&format!("{base}/temp")),
            capacity: CachedSysFile::try_open(&format!("{base}/capacity")),
            status: CachedSysFile::try_open(&format!("{base}/status")),
        }
    }

    pub fn sample(&mut self) -> PowerSample {
        let current_ua = self.current.as_mut().map(|f| f.read_i64_or_minus1()).unwrap_or(-1);
        let voltage_uv = self.voltage.as_mut().map(|f| f.read_i64_or_minus1()).unwrap_or(-1);
        let temp_dec = self.temp.as_mut().map(|f| f.read_i64_or_minus1()).unwrap_or(-1);
        let cap = self.capacity.as_mut().map(|f| f.read_i64_or_minus1()).unwrap_or(-1);
        let status_s = self.status.as_mut().and_then(|f| f.read_str().ok().map(|s| s.trim().to_string()));

        PowerSample {
            current_ma: if current_ua < 0 { 0 } else { (current_ua / 1000) as i32 },
            voltage_mv: if voltage_uv < 0 { 0 } else { (voltage_uv / 1000) as i32 },
            temp_c_x10: if temp_dec < 0 { -1 } else { temp_dec as i32 },
            capacity: cap as i32,
            status: parse_status(status_s.as_deref()),
        }
    }
}

/// Translate the sysfs string to android.os.BatteryManager.BATTERY_STATUS_*.
fn parse_status(s: Option<&str>) -> i32 {
    match s.unwrap_or("") {
        "Charging" => 2,       // BATTERY_STATUS_CHARGING
        "Discharging" => 3,    // BATTERY_STATUS_DISCHARGING
        "Not charging" => 4,   // BATTERY_STATUS_NOT_CHARGING
        "Full" => 5,           // BATTERY_STATUS_FULL
        _ => 1,                // BATTERY_STATUS_UNKNOWN
    }
}
