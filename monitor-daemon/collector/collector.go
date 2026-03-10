package collector

import (
	"fmt"
	"os"
	"sync"
	"sync/atomic"
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

// Snapshot is the full system performance data returned to clients.
type Snapshot struct {
	// FPS
	FPS      float64 `json:"fps"`
	Jank     int     `json:"jank"`
	BigJank  int     `json:"big_jank"`
	FpsLayer string  `json:"fps_layer,omitempty"`
	FpsSrc   string  `json:"fps_source,omitempty"`

	// CPU
	CpuLoads []float64 `json:"cpu_load"`
	CpuFreqs []int     `json:"cpu_freq"`
	CpuTemp  float64   `json:"cpu_temp"`

	// GPU
	GpuFreq int `json:"gpu_freq"` // MHz
	GpuLoad int `json:"gpu_load"` // %

	// Memory (MB)
	MemTotalMB int64 `json:"memory_total_mb"`
	MemAvailMB int64 `json:"memory_avail_mb"`

	// Battery
	Battery BatteryInfo `json:"battery"`

	// Meta
	Runner      string `json:"runner"`       // "root" or "shell"
	TimestampMs int64  `json:"timestamp_ms"`
}

// sampleIntervalMs is the background sampling rate, adjustable at runtime.
var sampleIntervalMs int64 = 200

// SetSampleInterval adjusts the background sampling rate (minimum 100ms).
func SetSampleInterval(ms int64) {
	if ms < 100 {
		ms = 100
	}
	atomic.StoreInt64(&sampleIntervalMs, ms)
}

// GetSampleInterval returns the current sampling interval in ms.
func GetSampleInterval() int64 {
	return atomic.LoadInt64(&sampleIntervalMs)
}

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
			time.Sleep(time.Duration(atomic.LoadInt64(&sampleIntervalMs)) * time.Millisecond)
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
		logInfo("collector", "runner=%s uid=%d cores=%d mem_total=%dMB",
			daemonRunner, os.Getuid(), cores, totalMB)
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

var startedAt = time.Now()

// PingInfo returns rich JSON for the ping command, including version, uptime, etc.
func PingInfo() string {
	uptime := int64(time.Since(startedAt).Seconds())
	return fmt.Sprintf(
		`{"status":"pong","version":"1.0.0","commit":%q,"runner":%q,"pid":%d,"started_at":%q,"uptime_s":%d}`,
		GitCommit, daemonRunner, os.Getpid(), startedAt.Format(time.RFC3339), uptime)
}

// Version returns daemon version/identity as JSON (alias for backward compat).
func Version() string {
	return PingInfo()
}
