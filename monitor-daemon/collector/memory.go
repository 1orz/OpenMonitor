package collector

import (
	"strconv"
	"strings"
	"sync"
)

var (
	memOnce     sync.Once
	memInfoFile *procFile
)

func initMemFiles() {
	memOnce.Do(func() {
		memInfoFile = openProcFile("/proc/meminfo")
		if memInfoFile == nil {
			logWarn("memory", "failed to open /proc/meminfo")
		} else {
			logInfo("memory", "persistent FD: /proc/meminfo")
		}
	})
}

// readMemInfo reads /proc/meminfo and returns MemTotal/MemAvailable in MB.
// Returns nil pointers when the file cannot be read.
func readMemInfo() (*int64, *int64) {
	initMemFiles()
	if memInfoFile == nil {
		return nil, nil
	}

	var totalKB, availKB int64
	sc := memInfoFile.newScanner()
	for sc.Scan() {
		line := sc.Text()
		if strings.HasPrefix(line, "MemTotal:") {
			totalKB = parseMemLine(line)
		} else if strings.HasPrefix(line, "MemAvailable:") {
			availKB = parseMemLine(line)
		}
		if totalKB > 0 && availKB > 0 {
			break
		}
	}
	if totalKB == 0 {
		return nil, nil
	}
	totalMB := totalKB / 1024
	availMB := availKB / 1024
	return &totalMB, &availMB
}

func parseMemLine(line string) int64 {
	fields := strings.Fields(line)
	if len(fields) < 2 {
		return 0
	}
	v, _ := strconv.ParseInt(fields[1], 10, 64)
	return v
}
