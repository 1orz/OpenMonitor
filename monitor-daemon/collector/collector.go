package collector

import (
	"fmt"
	"os"
	"sync"
	"time"
)

// daemonRunner is the identity of the current process: "root" or "shell".
var daemonRunner = func() string {
	if os.Getuid() == 0 {
		return "root"
	}
	return "shell"
}()

// Runner returns the privilege identity of the running daemon process.
func Runner() string { return daemonRunner }

// Pointer helpers for nullable JSON fields.
func intPtr(v int) *int             { return &v }
func int64Ptr(v int64) *int64       { return &v }
func float64Ptr(v float64) *float64 { return &v }
func stringPtr(v string) *string    { return &v }

// Snapshot is the full system performance data returned to clients.
// Pointer fields marshal to JSON null when the underlying data is unavailable.
type Snapshot struct {
	// FPS — nil before first successful collection
	FPS      *float64 `json:"fps"`
	Jank     *int     `json:"jank"`
	BigJank  *int     `json:"big_jank"`
	FpsLayer string   `json:"fps_layer,omitempty"`
	FpsSrc   string   `json:"fps_source,omitempty"`

	// CPU — nil slices marshal to JSON null
	CpuLoads []float64 `json:"cpu_load"`
	CpuFreqs []int     `json:"cpu_freq"`
	CpuTemp  *float64  `json:"cpu_temp"`

	// GPU — nil when sysfs paths are inaccessible
	GpuFreq *int `json:"gpu_freq"`
	GpuLoad *int `json:"gpu_load"`

	// Memory (MB) — nil when /proc/meminfo unreadable
	MemTotalMB *int64 `json:"memory_total_mb"`
	MemAvailMB *int64 `json:"memory_avail_mb"`

	// Battery
	Battery BatteryInfo `json:"battery"`

	// Meta
	Runner      string `json:"runner"`
	TimestampMs int64  `json:"timestamp_ms"`
}

// sampleIntervalMs is the fixed background sampling rate.
const sampleIntervalMs = 500

// Collector runs background high-frequency sampling.
// Clients read the latest cached snapshot via GetSnapshot().
type Collector struct {
	mu      sync.RWMutex
	fps     *fpsCollector
	prevCpu []cpuStat
	snap    Snapshot

	initLogOnce sync.Once
}

// New creates a Collector.
func New() *Collector {
	return &Collector{
		fps: newFpsCollector(),
	}
}

// Start launches background sampling goroutines.
func (c *Collector) Start() {
	// FPS: background 500ms sampling (SurfaceFlinger needs continuous tracking)
	c.fps.start()

	// System stats: high-frequency background sampling
	go func() {
		for {
			c.sampleSystem()
			time.Sleep(sampleIntervalMs * time.Millisecond)
		}
	}()
}

func (c *Collector) sampleSystem() {
	currCpu := readCpuStats()

	c.initLogOnce.Do(func() {
		cores := 0
		if len(currCpu) > 1 {
			cores = len(currCpu) - 1
		}
		totalMB, _ := readMemInfo()
		var totalVal int64
		if totalMB != nil {
			totalVal = *totalMB
		}
		logInfo("collector", "runner=%s uid=%d cores=%d mem_total=%dMB",
			daemonRunner, os.Getuid(), cores, totalVal)
	})

	c.mu.Lock()
	loads := calcCpuLoad(c.prevCpu, currCpu)
	c.prevCpu = currCpu
	c.mu.Unlock()

	freqs := readCpuFreqs()
	temp := readCpuTemp()
	gpuFreq := readGpuFreqMhz()
	gpuLoad := readGpuLoad()
	totalMB, availMB := readMemInfo()
	battery := readBattery()
	fpsResult := c.fps.get()

	c.mu.Lock()
	c.snap = Snapshot{
		FPS:         fpsResult.FPS,
		Jank:        fpsResult.Jank,
		BigJank:     fpsResult.BigJank,
		FpsLayer:    fpsResult.Layer,
		FpsSrc:      fpsResult.Source,
		CpuLoads:    loads,
		CpuFreqs:    freqs,
		CpuTemp:     temp,
		GpuFreq:     gpuFreq,
		GpuLoad:     gpuLoad,
		MemTotalMB:  totalMB,
		MemAvailMB:  availMB,
		Battery:     battery,
		Runner:      daemonRunner,
		TimestampMs: time.Now().UnixMilli(),
	}
	c.mu.Unlock()
}

// GetSnapshot returns the latest cached snapshot (never blocks on I/O).
func (c *Collector) GetSnapshot() Snapshot {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.snap
}

// GitCommit is set at build time via -ldflags "-X collector.GitCommit=xxx".
var GitCommit = "dev"

// Version is set at build time via -ldflags "-X collector.Version=x.y.z".
var Version = "0.0.1"

var startedAt = time.Now()

// PingInfo returns rich JSON for the ping command, including version, uptime, etc.
func PingInfo() string {
	uptime := int64(time.Since(startedAt).Seconds())
	return fmt.Sprintf(
		`{"status":"pong","version":%q,"commit":%q,"runner":%q,"pid":%d,"started_at":%q,"uptime_s":%d}`,
		Version, GitCommit, daemonRunner, os.Getpid(), startedAt.Format(time.RFC3339), uptime)
}

// VersionJSON returns daemon version/identity as JSON (alias for PingInfo).
func VersionJSON() string {
	return PingInfo()
}
