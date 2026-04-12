package collector

import (
	"fmt"
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
	CurrentUA *int     `json:"current_ua"` // raw µA from sysfs, negative=discharging
	VoltageMV *int     `json:"voltage_mv"` // mV
	VoltageUV *int     `json:"voltage_uv"` // raw µV from sysfs
	Temp      *float64 `json:"temp"`       // °C
	Capacity  *int     `json:"capacity"`   // %
	Status    *string  `json:"status"`     // Charging / Discharging / Full / Not charging
	PowerMW   *int     `json:"power_mw"`   // computed from raw µA×µV for precision
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
	batteryUseDumpsys bool
	batCurrentFile    *sysFile
	batVoltageFile    *sysFile
	batTempFile       *sysFile
	batCapFile        *sysFile
	batStatusFile     *sysFile
)

func initBattery() {
	batteryOnce.Do(func() {
		base := findBatteryBase()
		if base != "" {
			logInfo("battery", "sysfs base: %s", base)
			batCurrentFile = openSysFile(base + "/current_now")
			batVoltageFile = openSysFile(base + "/voltage_now")
			batTempFile = openSysFile(base + "/temp")
			batCapFile = openSysFile(base + "/capacity")
			batStatusFile = openSysFile(base + "/status")

			opened := 0
			for _, sf := range []*sysFile{batCurrentFile, batVoltageFile, batTempFile, batCapFile, batStatusFile} {
				if sf != nil {
					opened++
				}
			}
			logInfo("battery", "persistent FDs: %d/5 files opened", opened)
		} else {
			batteryUseDumpsys = true
			logWarn("battery", "no sysfs battery found, using dumpsys fallback")
		}

		unlockOPlusGaugeUpdate()
	})
}

func readBattery() BatteryInfo {
	initBattery()
	if !batteryUseDumpsys {
		info := readBatterySysfs()
		if info.Capacity != nil && *info.Capacity > 0 {
			return info
		}
		logWarn("battery", "sysfs read incomplete, using dumpsys fallback")
	}
	return readBatteryDumpsys()
}

func readBatterySysfs() BatteryInfo {
	info := BatteryInfo{}

	if v, ok := batCurrentFile.readInt(); ok {
		info.CurrentUA = &v
		ma := v / 1000
		info.CurrentMA = &ma
	}
	if v, ok := batVoltageFile.readInt(); ok {
		info.VoltageUV = &v
		mv := v / 1000
		info.VoltageMV = &mv
	}
	if v, ok := batTempFile.readInt(); ok {
		t := float64(v) / 10.0
		info.Temp = &t
	}
	if v, ok := batCapFile.readInt(); ok {
		info.Capacity = &v
	}
	if s := batStatusFile.readString(); s != "" {
		info.Status = &s
	}

	// Normalize: negative = discharging, positive = charging.
	if info.Status != nil && info.CurrentUA != nil {
		if *info.Status == "Discharging" && *info.CurrentUA > 0 {
			negUa := -(*info.CurrentUA)
			info.CurrentUA = &negUa
			negMa := -(*info.CurrentMA)
			info.CurrentMA = &negMa
		} else if (*info.Status == "Charging" || *info.Status == "Full") && *info.CurrentUA < 0 {
			posUa := -(*info.CurrentUA)
			info.CurrentUA = &posUa
			posMa := -(*info.CurrentMA)
			info.CurrentMA = &posMa
		}
	}

	// Power: compute from raw µA×µV for maximum precision.
	if info.CurrentUA != nil && info.VoltageUV != nil {
		cur := int64(*info.CurrentUA)
		if cur < 0 {
			cur = -cur
		}
		pw := int(cur * int64(*info.VoltageUV) / 1_000_000_000)
		info.PowerMW = &pw
	}
	return info
}

// readBatteryDumpsys parses `dumpsys battery` output.
// current_ma/current_ua are not available via this path — stays nil.
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

// unlockOPlusGaugeUpdate removes the OPlus vendor sampling frequency limit
// so current_now refreshes at our desired rate instead of the default 5s.
func unlockOPlusGaugeUpdate() {
	const forceActive = "/proc/oplus-votable/GAUGE_UPDATE/force_active"
	const forceVal = "/proc/oplus-votable/GAUGE_UPDATE/force_val"

	if _, err := os.Stat(forceActive); err != nil {
		return
	}

	intervalMs := GetSampleInterval()
	logInfo("battery", "OPlus GAUGE_UPDATE detected, unlocking to %dms", intervalMs)

	if err := os.WriteFile(forceVal, []byte(fmt.Sprintf("%d\n", intervalMs)), 0644); err != nil {
		logWarn("battery", "failed to write GAUGE_UPDATE force_val: %v", err)
		return
	}
	if err := os.WriteFile(forceActive, []byte("1\n"), 0644); err != nil {
		logWarn("battery", "failed to write GAUGE_UPDATE force_active: %v", err)
	}
}
