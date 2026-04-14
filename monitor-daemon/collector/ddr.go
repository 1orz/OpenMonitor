package collector

import (
	"strconv"
	"strings"
	"sync"
)

// DDR frequency paths by vendor.
// MediaTek: helio-dvfsrc dump or devfreq cur_freq (Hz)
// Qualcomm: bus_dcvs DDR cur_freq (kHz) or Scene's clk_measure (Hz/2)
var ddrFreqPaths = []string{
	"/sys/class/devfreq/mtk-dvfsrc-devfreq/cur_freq",                                    // MTK devfreq, Hz
	"/sys/devices/system/cpu/bus_dcvs/DDR/cur_freq",                                      // Qualcomm DDR bus DCVS, kHz
	"/sys/kernel/helio-dvfsrc/dvfsrc_dump",                                               // MTK DVFSRC dump
	"/sys/devices/platform/10012000.dvfsrc/helio-dvfsrc/dvfsrc_dump",                     // MTK variant
	"/sys/devices/platform/1c00f000.dvfsrc/1c00f000.dvfsrc:dvfsrc-helper/dvfsrc_dump",   // MTK variant
	"/sys/devices/platform/soc/1c00f000.dvfsrc/helio-dvfsrc/dvfsrc_dump",                // MTK variant
}

var (
	ddrOnce     sync.Once
	ddrFile     *sysFile
	ddrPathIdx  int // index in ddrFreqPaths of the resolved path
)

func resolveDdrPaths() {
	ddrOnce.Do(func() {
		for i, p := range ddrFreqPaths {
			sf := openSysFile(p)
			if sf != nil {
				ddrFile = sf
				ddrPathIdx = i
				logInfo("ddr", "freq path: %s", p)
				return
			}
		}
		logWarn("ddr", "no DDR freq path accessible (root required)")
	})
}

// readDdrFreqMbps returns DDR frequency in Mbps, or nil if unavailable.
func readDdrFreqMbps() *int {
	resolveDdrPaths()
	if ddrFile == nil {
		return nil
	}
	s := ddrFile.readString()
	if s == "" {
		return nil
	}

	switch ddrPathIdx {
	case 0:
		// MTK devfreq: value in Hz, convert to Mbps = Hz / 1e6 / 2
		return parseDdrHz(s)
	case 1:
		// Qualcomm bus_dcvs: value in kHz, convert to Mbps = kHz / 1000 / 2 * 2 = kHz / 500
		return parseDdrQcom(s)
	default:
		// DVFSRC dump: multi-line text, find DDR line
		return parseDdrDvfsrcDump(s)
	}
}

func parseDdrHz(s string) *int {
	fields := strings.Fields(s)
	if len(fields) == 0 {
		return nil
	}
	hz, err := strconv.ParseInt(fields[0], 10, 64)
	if err != nil || hz <= 0 {
		return nil
	}
	mbps := int(hz / 1_000_000 / 2)
	if mbps <= 0 {
		return nil
	}
	return &mbps
}

func parseDdrQcom(s string) *int {
	fields := strings.Fields(s)
	if len(fields) == 0 {
		return nil
	}
	khz, err := strconv.ParseInt(fields[0], 10, 64)
	if err != nil || khz <= 0 {
		return nil
	}
	mbps := int(khz / 500)
	if mbps <= 0 {
		return nil
	}
	return &mbps
}

func parseDdrDvfsrcDump(s string) *int {
	for _, line := range strings.Split(s, "\n") {
		if !strings.Contains(line, "DDR") {
			continue
		}
		if !strings.Contains(line, ":") {
			continue
		}
		parts := strings.SplitN(line, ":", 2)
		if len(parts) < 2 {
			continue
		}
		val := strings.TrimSpace(strings.ToLower(parts[1]))
		val = strings.TrimSuffix(val, " ")
		if strings.Contains(val, "khz") {
			val = strings.ReplaceAll(val, "khz", "")
			val = strings.TrimSpace(val)
			khz, err := strconv.ParseInt(val, 10, 64)
			if err == nil && khz > 0 {
				return intPtr(int(khz / 1000))
			}
		}
		if strings.Contains(val, "mbps") {
			val = strings.ReplaceAll(val, "mbps", "")
			val = strings.TrimSpace(val)
			mbps, err := strconv.Atoi(val)
			if err == nil && mbps > 0 {
				return &mbps
			}
		}
		// Try raw number (assume kHz)
		val = strings.TrimSpace(parts[1])
		v, err := strconv.ParseInt(val, 10, 64)
		if err == nil && v > 0 {
			if v > 100000 {
				return intPtr(int(v / 1000))
			}
			return intPtr(int(v))
		}
	}
	return nil
}
