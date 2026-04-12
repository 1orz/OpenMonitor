package collector

import (
	"os"
	"path/filepath"
	"strings"
	"sync"
)

// Thermal zone type keywords that indicate CPU temperature sensors.
var cpuThermalTypes = []string{
	"cpu", "soc", "tsens_tz_sensor", "cpu-1-0", "cpu-1-1",
	"cpu-1-2", "cpu-1-3", "thermal_zone0", "skin", "quiet-therm",
}

var (
	thermalOnce     sync.Once
	cachedTempFile  *sysFile
)

// resolveThermalPath probes thermal zones once and opens a persistent FD.
func resolveThermalPath() {
	thermalOnce.Do(func() {
		zones, err := filepath.Glob("/sys/class/thermal/thermal_zone*/temp")
		if err != nil || len(zones) == 0 {
			logWarn("thermal", "no thermal zones found under /sys/class/thermal/")
			return
		}

		for _, p := range zones {
			typeFile := strings.Replace(p, "temp", "type", 1)
			b, err := os.ReadFile(typeFile)
			if err != nil {
				continue
			}
			zoneType := strings.TrimSpace(string(b))
			if matchesCpuType(zoneType) {
				cachedTempFile = openSysFile(p)
				logInfo("thermal", "CPU zone: %s (type=%s)", p, zoneType)
				return
			}
		}

		// Fallback: use thermal_zone0
		cachedTempFile = openSysFile(zones[0])
		logWarn("thermal", "no CPU-type zone matched, using fallback: %s", zones[0])
	})
}

// readCpuTemp returns CPU/SoC temperature in °C, or nil if unavailable.
func readCpuTemp() *float64 {
	resolveThermalPath()
	if cachedTempFile == nil {
		return nil
	}
	raw, ok := cachedTempFile.readInt()
	if !ok {
		return nil
	}
	// Kernel reports millidegrees or degrees depending on driver
	var v float64
	if raw > 1000 {
		v = float64(int(float64(raw)/100)) / 10.0
	} else {
		v = float64(int(float64(raw)*10)) / 10.0
	}
	return &v
}

func matchesCpuType(t string) bool {
	tl := strings.ToLower(t)
	for _, kw := range cpuThermalTypes {
		if strings.Contains(tl, kw) {
			return true
		}
	}
	return false
}
