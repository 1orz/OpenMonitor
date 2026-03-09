package collector

import (
	"bufio"
	"os"
	"strconv"
	"strings"
)

// readMemInfo reads /proc/meminfo and returns MemTotal/MemAvailable in MB.
func readMemInfo() (totalMB, availMB int64) {
	f, err := os.Open("/proc/meminfo")
	if err != nil {
		return
	}
	defer f.Close()

	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := sc.Text()
		if strings.HasPrefix(line, "MemTotal:") {
			totalMB = parseMemLine(line) / 1024
		} else if strings.HasPrefix(line, "MemAvailable:") {
			availMB = parseMemLine(line) / 1024
		}
		if totalMB > 0 && availMB > 0 {
			break
		}
	}
	return
}

func parseMemLine(line string) int64 {
	fields := strings.Fields(line)
	if len(fields) < 2 {
		return 0
	}
	v, _ := strconv.ParseInt(fields[1], 10, 64)
	return v
}
