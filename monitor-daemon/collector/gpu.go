package collector

import (
	"os"
	"strconv"
	"strings"
	"sync"
)

// GPU frequency paths (OEM-specific, try in order)
var gpuFreqPaths = []string{
	"/sys/class/kgsl/kgsl-3d0/gpuclk",                              // Adreno current clock Hz
	"/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",                    // Adreno devfreq Hz
	"/sys/kernel/gpu/gpu_clock",                                     // Some Snapdragon
	"/sys/devices/platform/mali.0/devfreq/mali.0/cur_freq",          // Mali
	"/sys/class/misc/mali0/device/devfreq/gpufreq/cur_freq",
}

// GPU load paths
var gpuLoadPaths = []string{
	"/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", // Adreno  e.g. "23 %"
	"/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
	"/sys/kernel/gpu/gpu_busy",
	"/sys/devices/platform/mali.0/utilization",
}

var (
	gpuOnce        sync.Once
	cachedFreqPath string
	cachedLoadPath string
)

// resolveGpuPaths probes all candidate paths once and caches the first readable one.
func resolveGpuPaths() {
	gpuOnce.Do(func() {
		for _, p := range gpuFreqPaths {
			if _, err := os.ReadFile(p); err == nil {
				cachedFreqPath = p
				logInfo("gpu", "freq path: %s", p)
				break
			}
		}
		if cachedFreqPath == "" {
			logWarn("gpu", "no freq path accessible (root required for /sys/class/kgsl)")
		}

		for _, p := range gpuLoadPaths {
			if _, err := os.ReadFile(p); err == nil {
				cachedLoadPath = p
				logInfo("gpu", "load path: %s", p)
				break
			}
		}
		if cachedLoadPath == "" {
			logWarn("gpu", "no load path accessible")
		}
	})
}

// readGpuFreqMhz returns GPU frequency in MHz, or nil if unavailable.
func readGpuFreqMhz() *int {
	resolveGpuPaths()
	if cachedFreqPath == "" {
		return nil
	}
	b, err := os.ReadFile(cachedFreqPath)
	if err != nil {
		return nil
	}
	fields := strings.Fields(strings.TrimSpace(string(b)))
	if len(fields) == 0 {
		return nil
	}
	hz, err := strconv.ParseInt(fields[0], 10, 64)
	if err != nil {
		return nil
	}
	var mhz int
	if hz > 100_000 {
		mhz = int(hz / 1_000_000)
	} else {
		mhz = int(hz)
	}
	return &mhz
}

// readGpuLoad returns GPU load as integer percentage 0-100, or nil if unavailable.
func readGpuLoad() *int {
	resolveGpuPaths()
	if cachedLoadPath == "" {
		return nil
	}
	b, err := os.ReadFile(cachedLoadPath)
	if err != nil {
		return nil
	}
	fields := strings.Fields(strings.TrimSpace(string(b)))
	if len(fields) == 0 {
		return nil
	}
	v, err := strconv.Atoi(fields[0])
	if err != nil {
		return nil
	}
	return &v
}
