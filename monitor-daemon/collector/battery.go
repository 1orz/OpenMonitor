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

	// Extended battery info (sysfs only, null on devices that lack these files)
	CycleCount        *int `json:"cycle_count"`          // charge cycles
	ChargeFullUah     *int `json:"charge_full_uah"`      // actual full capacity µAh (degrades over time)
	ChargeFullDesign  *int `json:"charge_full_design_uah"` // design full capacity µAh
	ChargeCounterUah  *int `json:"charge_counter_uah"`   // current remaining charge µAh
	Health            *string `json:"health"`             // "Good" / "Overheat" / "Dead" / ...
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
	batPowerFile      *sysFile
	// Extended info files
	batCycleCountFile      *sysFile
	batChargeFullFile      *sysFile
	batChargeFullDesignFile *sysFile
	batChargeCounterFile   *sysFile
	batHealthFile          *sysFile
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
			batPowerFile = openSysFile(base + "/power_now")
			batCycleCountFile = openSysFile(base + "/cycle_count")
			batChargeFullFile = openSysFile(base + "/charge_full")
			batChargeFullDesignFile = openSysFile(base + "/charge_full_design")
			batChargeCounterFile = openSysFile(base + "/charge_counter")
			batHealthFile = openSysFile(base + "/health")

			opened := 0
			for _, sf := range []*sysFile{batCurrentFile, batVoltageFile, batTempFile, batCapFile, batStatusFile, batPowerFile,
				batCycleCountFile, batChargeFullFile, batChargeFullDesignFile, batChargeCounterFile, batHealthFile} {
				if sf != nil {
					opened++
				}
			}
			logInfo("battery", "persistent FDs: %d/11 files opened", opened)
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

	// current_now: some devices report µA (abs >= 10000), others report mA directly.
	if v, ok := batCurrentFile.readInt(); ok {
		abs := v
		if abs < 0 {
			abs = -abs
		}
		if abs >= 10000 {
			// Device reports µA — we have raw precision
			info.CurrentUA = &v
			ma := v / 1000
			info.CurrentMA = &ma
		} else {
			// Device reports mA (e.g. OnePlus/OPlus) — no µA precision
			info.CurrentMA = &v
			// CurrentUA stays nil — no raw µA available
		}
	}

	// voltage_now: most devices report µV (abs >= 100000), some report mV.
	if v, ok := batVoltageFile.readInt(); ok {
		abs := v
		if abs < 0 {
			abs = -abs
		}
		if abs >= 100000 {
			info.VoltageUV = &v
			mv := v / 1000
			info.VoltageMV = &mv
		} else {
			info.VoltageMV = &v
		}
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

	// Normalize sign: negative = discharging, positive = charging.
	if info.Status != nil {
		isDischarging := *info.Status == "Discharging"
		isCharging := *info.Status == "Charging" || *info.Status == "Full"
		if info.CurrentMA != nil {
			if (isDischarging && *info.CurrentMA > 0) || (isCharging && *info.CurrentMA < 0) {
				neg := -(*info.CurrentMA)
				info.CurrentMA = &neg
			}
		}
		if info.CurrentUA != nil {
			if (isDischarging && *info.CurrentUA > 0) || (isCharging && *info.CurrentUA < 0) {
				neg := -(*info.CurrentUA)
				info.CurrentUA = &neg
			}
		}
	}

	// Power: prefer raw µA×µV for max precision, fallback to mA×mV.
	if info.CurrentUA != nil && info.VoltageUV != nil {
		cur := int64(*info.CurrentUA)
		if cur < 0 {
			cur = -cur
		}
		pw := int(cur * int64(*info.VoltageUV) / 1_000_000_000)
		info.PowerMW = &pw
	} else if info.CurrentMA != nil && info.VoltageMV != nil {
		cur := *info.CurrentMA
		if cur < 0 {
			cur = -cur
		}
		pw := cur * (*info.VoltageMV) / 1000
		info.PowerMW = &pw
	}

	// power_now sysfs: use as fallback if we couldn't compute above.
	if info.PowerMW == nil || *info.PowerMW == 0 {
		if v, ok := batPowerFile.readInt(); ok && v != 0 {
			// power_now is typically in µW
			mw := v / 1000
			if mw == 0 {
				mw = v // might already be mW
			}
			info.PowerMW = &mw
		}
	}

	// Extended battery info
	if v, ok := batCycleCountFile.readInt(); ok {
		info.CycleCount = &v
	}
	if v, ok := batChargeFullFile.readInt(); ok {
		info.ChargeFullUah = &v
	}
	if v, ok := batChargeFullDesignFile.readInt(); ok {
		info.ChargeFullDesign = &v
	}
	if v, ok := batChargeCounterFile.readInt(); ok {
		info.ChargeCounterUah = &v
	}
	if s := batHealthFile.readString(); s != "" {
		info.Health = &s
	}

	return info
}

// readBatteryDumpsys parses `dumpsys battery` output.
// Also parses OEM-specific fields (e.g. OPlus "Battery current").
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
		// OPlus-specific: "Battery current : 157" (mA)
		case strings.HasPrefix(line, "Battery current"):
			parts := strings.SplitN(line, ":", 2)
			if len(parts) == 2 {
				if v, e := strconv.Atoi(strings.TrimSpace(parts[1])); e == nil && v != 0 {
					info.CurrentMA = &v
				}
			}
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

	// Normalize sign for dumpsys current (OPlus reports positive regardless).
	if info.CurrentMA != nil {
		if status == "Discharging" && *info.CurrentMA > 0 {
			neg := -(*info.CurrentMA)
			info.CurrentMA = &neg
		}
		// Compute power from mA × mV.
		if info.VoltageMV != nil {
			cur := *info.CurrentMA
			if cur < 0 {
				cur = -cur
			}
			pw := cur * (*info.VoltageMV) / 1000
			info.PowerMW = &pw
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

// unlockOPlusGaugeUpdate removes the OPlus vendor sampling frequency limit
// so current_now refreshes at our desired rate instead of the default 5s.
func unlockOPlusGaugeUpdate() {
	const forceActive = "/proc/oplus-votable/GAUGE_UPDATE/force_active"
	const forceVal = "/proc/oplus-votable/GAUGE_UPDATE/force_val"

	if _, err := os.Stat(forceActive); err != nil {
		return
	}

	logInfo("battery", "OPlus GAUGE_UPDATE detected, unlocking to %dms", sampleIntervalMs)

	if err := os.WriteFile(forceVal, []byte(fmt.Sprintf("%d\n", sampleIntervalMs)), 0644); err != nil {
		logWarn("battery", "failed to write GAUGE_UPDATE force_val: %v", err)
		return
	}
	if err := os.WriteFile(forceActive, []byte("1\n"), 0644); err != nil {
		logWarn("battery", "failed to write GAUGE_UPDATE force_active: %v", err)
	}
}
