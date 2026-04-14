package collector

import (
	"fmt"
	"strconv"
	"strings"
	"sync"
)

type cpuStat struct {
	total uint64
	idle  uint64
}

var (
	cpuOnce      sync.Once
	procStatFile *procFile
	cpuFreqFiles []*sysFile
)

func initCpuFiles() {
	cpuOnce.Do(func() {
		procStatFile = openProcFile("/proc/stat")
		if procStatFile == nil {
			logWarn("cpu", "failed to open /proc/stat")
		}
		for i := 0; i < 16; i++ {
			path := fmt.Sprintf("/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq", i)
			sf := openSysFile(path)
			if sf == nil {
				break
			}
			cpuFreqFiles = append(cpuFreqFiles, sf)
		}
		logInfo("cpu", "persistent FDs: /proc/stat + %d core freq files", len(cpuFreqFiles))
	})
}

// readCpuStats reads /proc/stat and returns per-core stats (index 0 = aggregate).
func readCpuStats() []cpuStat {
	initCpuFiles()
	if procStatFile == nil {
		return nil
	}

	var stats []cpuStat
	sc := procStatFile.newScanner()
	for sc.Scan() {
		line := sc.Text()
		if !strings.HasPrefix(line, "cpu") {
			break
		}
		fields := strings.Fields(line)
		if len(fields) < 8 {
			continue
		}
		var vals [10]uint64
		for i := 1; i < len(fields) && i <= 10; i++ {
			vals[i-1], _ = strconv.ParseUint(fields[i], 10, 64)
		}
		// idle = idle + iowait
		idle := vals[3] + vals[4]
		total := uint64(0)
		for i := 0; i < 10; i++ {
			total += vals[i]
		}
		stats = append(stats, cpuStat{total: total, idle: idle})
	}
	return stats
}

// calcCpuLoad computes load % per core given two snapshots.
// Returns per-core loads (index 0 excluded — that's the aggregate).
func calcCpuLoad(prev, curr []cpuStat) []float64 {
	if len(prev) == 0 || len(curr) == 0 {
		return nil
	}
	// index 0 is the aggregate "cpu" line — skip, return per-core only
	n := len(curr) - 1
	if n <= 0 {
		return nil
	}
	loads := make([]float64, n)
	for i := 0; i < n; i++ {
		pi, ci := i+1, i+1
		if pi >= len(prev) || ci >= len(curr) {
			break
		}
		dTotal := float64(curr[ci].total - prev[pi].total)
		dIdle := float64(curr[ci].idle - prev[pi].idle)
		if dTotal <= 0 {
			loads[i] = 0
			continue
		}
		loads[i] = float64(int((1.0-dIdle/dTotal)*1000)) / 10.0
	}
	return loads
}

// readCpuFreqs reads current CPU frequencies from /sys (MHz).
func readCpuFreqs() []int {
	initCpuFiles()
	var freqs []int
	for _, sf := range cpuFreqFiles {
		khz, ok := sf.readInt()
		if !ok {
			freqs = append(freqs, 0)
			continue
		}
		freqs = append(freqs, khz/1000)
	}
	return freqs
}
