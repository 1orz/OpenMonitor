package collector

import (
	"os"
	"os/exec"
	"strconv"
	"strings"
	"sync"
)

// BatteryInfo holds real-time battery metrics.
// Pointer fields are null (JSON null) when the corresponding data cannot be read.
type BatteryInfo struct {
	CurrentMA *int     `json:"current_ma"` // mA, negative=discharging
	VoltageMV *int     `json:"voltage_mv"` // mV
	Temp      *float64 `json:"temp"`       // °C
	Capacity  *int     `json:"capacity"`   // %
	Status    *string  `json:"status"`     // Charging / Discharging / Full / Not charging
	PowerMW   *int     `json:"power_mw"`   // abs(current_ma) * voltage_mv / 1000
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
	batteryOnce       sync.Once
	cachedBatteryBase string
	batteryUseDumpsys bool
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
		info := readBatterySysfs(cachedBatteryBase)
		if info.Capacity != nil && *info.Capacity > 0 {
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
		ma := v / 1000
		info.CurrentMA = &ma
	}
	if v, ok := readSysInt(base + "/voltage_now"); ok {
		mv := v / 1000
		info.VoltageMV = &mv
	}
	if v, ok := readSysInt(base + "/temp"); ok {
		t := float64(v) / 10.0
		info.Temp = &t
	}
	if v, ok := readSysInt(base + "/capacity"); ok {
		info.Capacity = &v
	}
	if b, err := os.ReadFile(base + "/status"); err == nil {
		s := strings.TrimSpace(string(b))
		info.Status = &s
	}

	// Normalize: negative = discharging, positive = charging.
	if info.Status != nil && info.CurrentMA != nil {
		if *info.Status == "Discharging" && *info.CurrentMA > 0 {
			neg := -(*info.CurrentMA)
			info.CurrentMA = &neg
		} else if (*info.Status == "Charging" || *info.Status == "Full") && *info.CurrentMA < 0 {
			pos := -(*info.CurrentMA)
			info.CurrentMA = &pos
		}
	}

	if info.CurrentMA != nil && info.VoltageMV != nil {
		cur := *info.CurrentMA
		if cur < 0 {
			cur = -cur
		}
		pw := cur * (*info.VoltageMV) / 1000
		info.PowerMW = &pw
	}
	return info
}

// readBatteryDumpsys parses `dumpsys battery` output.
// current_ma is not available via this path — stays nil.
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
			if v, e := strconv.Atoi(strings.TrimSpace(strings.TrimPrefix(line, "level:"))); e == nil {
				info.Capacity = &v
			}
		case strings.HasPrefix(line, "voltage:"):
			if v, e := strconv.Atoi(strings.TrimSpace(strings.TrimPrefix(line, "voltage:"))); e == nil {
				info.VoltageMV = &v
			}
		case strings.HasPrefix(line, "temperature:"):
			if v, e := strconv.Atoi(strings.TrimSpace(strings.TrimPrefix(line, "temperature:"))); e == nil {
				t := float64(v) / 10.0
				info.Temp = &t
			}
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

	var status string
	switch statusCode {
	case 2:
		status = "Charging"
	case 3:
		status = "Discharging"
	case 4:
		status = "Not charging"
	case 5:
		status = "Full"
	default:
		if acPowered || usbPowered || wirelessPowered {
			status = "Charging"
		} else {
			status = "Discharging"
		}
	}
	info.Status = &status

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
