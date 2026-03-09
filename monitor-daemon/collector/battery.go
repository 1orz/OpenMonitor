package collector

import (
	"os"
	"os/exec"
	"strconv"
	"strings"
	"sync"
)

// BatteryInfo holds real-time battery metrics.
type BatteryInfo struct {
	CurrentMA int     `json:"current_ma"` // mA, negative=discharging
	VoltageMV int     `json:"voltage_mv"` // mV
	Temp      float64 `json:"temp"`       // °C
	Capacity  int     `json:"capacity"`   // %
	Status    string  `json:"status"`     // Charging / Discharging / Full / Not charging
	PowerMW   int     `json:"power_mw"`   // abs(current_ma) * voltage_mv / 1000
}

// batteryBases lists candidate sysfs directories in priority order.
var batteryBases = []string{
	"/sys/class/power_supply/battery",
	"/sys/class/power_supply/Battery",
	"/sys/class/power_supply/bms",
	"/sys/class/power_supply/BAT0",
	"/sys/class/power_supply/BAT1",
}

var (
	batteryOnce        sync.Once
	cachedBatteryBase  string
	batteryUseDumpsys  bool
)

func initBattery() {
	batteryOnce.Do(func() {
		cachedBatteryBase = findBatteryBase()
		if cachedBatteryBase != "" {
			logInfo("battery", "sysfs base: %s", cachedBatteryBase)
		} else {
			batteryUseDumpsys = true
			logWarn("battery", "no sysfs battery found, using dumpsys fallback")
		}
	})
}

func readBattery() BatteryInfo {
	initBattery()
	if cachedBatteryBase != "" {
		if info := readBatterySysfs(cachedBatteryBase); info.Capacity > 0 {
			return info
		}
		// sysfs base exists but read failed (e.g. current_now locked by vendor)
		logWarn("battery", "sysfs read incomplete, using dumpsys fallback")
	}
	return readBatteryDumpsys()
}

func readBatterySysfs(base string) BatteryInfo {
	info := BatteryInfo{}

	if v, ok := readSysInt(base + "/current_now"); ok {
		info.CurrentMA = v / 1000
	}
	if v, ok := readSysInt(base + "/voltage_now"); ok {
		info.VoltageMV = v / 1000
	}
	if v, ok := readSysInt(base + "/temp"); ok {
		info.Temp = float64(v) / 10.0
	}
	if v, ok := readSysInt(base + "/capacity"); ok {
		info.Capacity = v
	}
	if b, err := os.ReadFile(base + "/status"); err == nil {
		info.Status = strings.TrimSpace(string(b))
	}

	// Normalize: negative = discharging, positive = charging.
	if info.Status == "Discharging" && info.CurrentMA > 0 {
		info.CurrentMA = -info.CurrentMA
	} else if (info.Status == "Charging" || info.Status == "Full") && info.CurrentMA < 0 {
		info.CurrentMA = -info.CurrentMA
	}

	if info.CurrentMA != 0 && info.VoltageMV != 0 {
		cur := info.CurrentMA
		if cur < 0 {
			cur = -cur
		}
		info.PowerMW = cur * info.VoltageMV / 1000
	}
	return info
}

// readBatteryDumpsys parses `dumpsys battery` output.
// current_ma is not available via this path (requires root sysfs).
func readBatteryDumpsys() BatteryInfo {
	out, err := exec.Command("dumpsys", "battery").Output()
	if err != nil {
		return BatteryInfo{}
	}

	info := BatteryInfo{}
	acPowered, usbPowered, wirelessPowered := false, false, false
	statusCode := 0

	for _, raw := range strings.Split(string(out), "\n") {
		line := strings.TrimSpace(raw)
		switch {
		case strings.HasPrefix(line, "level:"):
			info.Capacity, _ = strconv.Atoi(strings.TrimSpace(strings.TrimPrefix(line, "level:")))
		case strings.HasPrefix(line, "voltage:"):
			info.VoltageMV, _ = strconv.Atoi(strings.TrimSpace(strings.TrimPrefix(line, "voltage:")))
		case strings.HasPrefix(line, "temperature:"):
			v, _ := strconv.Atoi(strings.TrimSpace(strings.TrimPrefix(line, "temperature:")))
			info.Temp = float64(v) / 10.0
		case strings.HasPrefix(line, "status:"):
			statusCode, _ = strconv.Atoi(strings.TrimSpace(strings.TrimPrefix(line, "status:")))
		case line == "AC powered: true":
			acPowered = true
		case line == "USB powered: true":
			usbPowered = true
		case line == "Wireless powered: true":
			wirelessPowered = true
		}
	}

	switch statusCode {
	case 2:
		info.Status = "Charging"
	case 3:
		info.Status = "Discharging"
	case 4:
		info.Status = "Not charging"
	case 5:
		info.Status = "Full"
	default:
		if acPowered || usbPowered || wirelessPowered {
			info.Status = "Charging"
		} else {
			info.Status = "Discharging"
		}
	}
	return info
}

func findBatteryBase() string {
	for _, p := range batteryBases {
		if _, err := os.Stat(p + "/capacity"); err == nil {
			return p
		}
	}
	return ""
}

// readSysInt reads a single integer from a sysfs file.
func readSysInt(path string) (int, bool) {
	b, err := os.ReadFile(path)
	if err != nil {
		return 0, false
	}
	v, err := strconv.Atoi(strings.TrimSpace(string(b)))
	if err != nil {
		return 0, false
	}
	return v, true
}
