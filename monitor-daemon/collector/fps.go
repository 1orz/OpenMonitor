package collector

import (
	"bufio"
	"fmt"
	"math"
	"os/exec"
	"strings"
	"sync"
	"time"
)

// FpsResult is the current FPS snapshot exposed to callers.
type FpsResult struct {
	FPS     float64 `json:"fps"`
	Jank    int     `json:"jank"`
	BigJank int     `json:"big_jank"`
	Layer   string  `json:"layer"`
	Source  string  `json:"source"`
}

// fpsCollector samples SurfaceFlinger frame counts in the background.
// No smoothing — raw FPS values are output directly for instant transitions.
type fpsCollector struct {
	mu     sync.RWMutex
	result FpsResult

	prevCounts   map[string]int64
	prevTime     time.Time
	prevFocusPkg string

	trackedLayer string

	// Jank detection: rolling 3-sample window.
	frameTimeHistory [3]float64
	frameTimeIdx     int
	frameTimeFilled  bool

	zeroStreak  int
	lastFrameNs int64 // latest actualPresentTime from --latency (ns since boot)
}

func newFpsCollector() *fpsCollector {
	return &fpsCollector{
		prevCounts: make(map[string]int64),
	}
}

func (fc *fpsCollector) start() {
	go func() {
		exec.Command("dumpsys", "SurfaceFlinger", "--timestats", "-enable").Run() //nolint
		exec.Command("dumpsys", "SurfaceFlinger", "--timestats", "-clear").Run()  //nolint
		logInfo("fps", "timestats enabled, sampling every 500ms")

		for {
			fc.sample()
			time.Sleep(500 * time.Millisecond)
		}
	}()
}

func (fc *fpsCollector) get() FpsResult {
	fc.mu.RLock()
	defer fc.mu.RUnlock()
	return fc.result
}

// ─── Foreground detection ───────────────────────────────────────────

func getFocusedPkg() string {
	if pkg := getFocusedFromActivity(); pkg != "" {
		return pkg
	}
	return getFocusedFromWindow()
}

func getFocusedFromActivity() string {
	out, err := exec.Command("dumpsys", "activity", "activities").Output()
	if err != nil {
		return ""
	}
	for _, line := range strings.Split(string(out), "\n") {
		if strings.Contains(line, "mResumedActivity") {
			return extractPkgFromRecord(line)
		}
	}
	return ""
}

func getFocusedFromWindow() string {
	out, err := exec.Command("dumpsys", "window", "displays").Output()
	if err != nil {
		return ""
	}
	for _, line := range strings.Split(string(out), "\n") {
		if strings.Contains(line, "mFocusedApp=") {
			return extractPkgFromRecord(line)
		}
	}
	return ""
}

func extractPkgFromRecord(line string) string {
	for _, prefix := range []string{" u0 ", " u1 ", " u2 ", " u10 "} {
		idx := strings.Index(line, prefix)
		if idx >= 0 {
			rest := line[idx+len(prefix):]
			slash := strings.Index(rest, "/")
			if slash > 0 {
				pkg := rest[:slash]
				if strings.Contains(pkg, ".") {
					return pkg
				}
			}
		}
	}
	return ""
}

// ─── Core sampling logic ────────────────────────────────────────────

func (fc *fpsCollector) sample() {
	pkg := getFocusedPkg()
	if pkg == "" {
		return
	}

	counts, err := parseTimestats(pkg)
	if err != nil {
		return
	}

	now := time.Now()

	fc.mu.Lock()
	defer fc.mu.Unlock()

	// No matching layers for foreground package — count as idle
	if len(counts) == 0 {
		fc.zeroStreak++
		if fc.zeroStreak >= 2 {
			fc.result.FPS = 0
		}
		return
	}

	// ── App switch: full reset ──
	if pkg != fc.prevFocusPkg {
		logInfo("fps", "app switch: %s → %s", fc.prevFocusPkg, pkg)
		fc.prevFocusPkg = pkg
		fc.prevCounts = make(map[string]int64)
		fc.trackedLayer = ""
		fc.prevTime = time.Time{}
		fc.result.FPS = 0
		fc.result.Jank = 0
		fc.result.BigJank = 0
		fc.zeroStreak = 0
		fc.lastFrameNs = 0
		fc.frameTimeFilled = false
		fc.frameTimeIdx = 0
		for k, v := range counts {
			fc.prevCounts[k] = v
		}
		fc.prevTime = now
		return
	}

	if fc.prevTime.IsZero() {
		for k, v := range counts {
			fc.prevCounts[k] = v
		}
		fc.prevTime = now
		return
	}

	dt := now.Sub(fc.prevTime).Seconds()
	fc.prevTime = now
	if dt <= 0 {
		return
	}

	// ── Compute delta for ALL layers ──
	deltas := make(map[string]int64, len(counts))
	for layer, curCount := range counts {
		prevCount := fc.prevCounts[layer]
		delta := curCount - prevCount
		if delta > 0 {
			deltas[layer] = delta
		}
		fc.prevCounts[layer] = curCount
	}

	bestLayer, bestDelta := fc.selectLayer(deltas)

	if bestDelta <= 0 {
		// Timestats delta=0 — probe --latency to distinguish frozen counter from idle.
		// Idle layers return 0 timestamps; active layers (even during rate change) have data.
		if fc.probeLatency() {
			return
		}
		fc.zeroStreak++
		if fc.zeroStreak >= 2 {
			fc.result.FPS = 0
		}
		return
	}

	fc.zeroStreak = 0
	fc.lastFrameNs = 0 // reset latency baseline when timestats is working

	if bestLayer != fc.trackedLayer && fc.trackedLayer != "" {
		logInfo("fps", "layer switch: %s → %s (delta=%d)", fc.trackedLayer, bestLayer, bestDelta)
	}
	fc.trackedLayer = bestLayer

	rawFps := float64(bestDelta) / dt

	// ── No smoothing — direct output ──
	fc.result.FPS = math.Round(rawFps*10) / 10
	fc.result.Layer = bestLayer
	fc.result.Source = "timestats"

	// ── Jank detection ──
	avgFrameTimeMs := 1000.0 / rawFps
	fc.detectJank(avgFrameTimeMs)
}

// ─── Sticky layer selection ──────────────────────────────────────────

func (fc *fpsCollector) selectLayer(deltas map[string]int64) (string, int64) {
	if len(deltas) == 0 {
		return fc.trackedLayer, 0
	}

	trackedDelta := deltas[fc.trackedLayer]

	if trackedDelta > 0 {
		for layer, delta := range deltas {
			if layer != fc.trackedLayer && delta > trackedDelta*2 {
				return layer, delta
			}
		}
		return fc.trackedLayer, trackedDelta
	}

	var bestLayer string
	var bestDelta int64
	bestIsBLAST := false

	for layer, delta := range deltas {
		isBLAST := isBLASTLayer(layer)
		switch {
		case delta > bestDelta:
			bestLayer = layer
			bestDelta = delta
			bestIsBLAST = isBLAST
		case delta == bestDelta && isBLAST && !bestIsBLAST:
			bestLayer = layer
			bestIsBLAST = true
		}
	}
	return bestLayer, bestDelta
}

func isBLASTLayer(layerName string) bool {
	return strings.Contains(layerName, "SurfaceView") && strings.Contains(layerName, "BLAST")
}

// ─── Latency probe (fallback when timestats delta=0) ────────────────

// probeLatency checks --latency for actual frame data.
// Returns true if frames were found (FPS updated or baseline set).
func (fc *fpsCollector) probeLatency() bool {
	if fc.trackedLayer == "" {
		return false
	}
	layerName := strings.TrimPrefix(fc.trackedLayer, "layerName = ")
	timestamps := parseLatency(layerName)
	if len(timestamps) == 0 {
		return false // no frame data → genuinely idle
	}

	// Find latest timestamp and count frames newer than baseline
	var latest int64
	var newCount int
	for _, ts := range timestamps {
		if ts > latest {
			latest = ts
		}
		if fc.lastFrameNs > 0 && ts > fc.lastFrameNs {
			newCount++
		}
	}

	// First probe: establish baseline, hold previous FPS
	if fc.lastFrameNs == 0 {
		fc.lastFrameNs = latest
		return true
	}

	if newCount == 0 {
		return false // all timestamps are old → idle
	}

	dt := float64(latest-fc.lastFrameNs) / 1e9
	fc.lastFrameNs = latest

	if dt >= 0.01 {
		fc.result.FPS = math.Round(float64(newCount)/dt*10) / 10
		fc.result.Layer = fc.trackedLayer
		fc.result.Source = "latency"
		fc.zeroStreak = 0
		logInfo("fps", "latency: %d new frames in %.3fs → %.1f fps", newCount, dt, fc.result.FPS)
		return true
	}
	return false
}

// parseLatency runs `dumpsys SurfaceFlinger --latency <layer>` and returns
// valid actualPresentTime values (nanoseconds since boot).
func parseLatency(layer string) []int64 {
	out, err := exec.Command("dumpsys", "SurfaceFlinger", "--latency", layer).Output()
	if err != nil {
		return nil
	}
	lines := strings.Split(strings.TrimSpace(string(out)), "\n")
	if len(lines) < 2 {
		return nil
	}
	var timestamps []int64
	for _, line := range lines[1:] { // skip vsync period line
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}
		ts, err := parseInt64(fields[1]) // actualPresentTime
		if err != nil || ts <= 0 || ts > 1e18 {
			continue
		}
		timestamps = append(timestamps, ts)
	}
	return timestamps
}

// ─── Jank detection (PerfDog-inspired) ──────────────────────────────

func (fc *fpsCollector) detectJank(curFrameTimeMs float64) {
	if fc.frameTimeFilled {
		avg := (fc.frameTimeHistory[0] + fc.frameTimeHistory[1] + fc.frameTimeHistory[2]) / 3.0
		if curFrameTimeMs > 2*avg {
			if curFrameTimeMs > 125.0 {
				fc.result.BigJank++
			} else if curFrameTimeMs > 83.3 {
				fc.result.Jank++
			}
		}
	}

	fc.frameTimeHistory[fc.frameTimeIdx] = curFrameTimeMs
	fc.frameTimeIdx = (fc.frameTimeIdx + 1) % 3
	if fc.frameTimeIdx == 0 && !fc.frameTimeFilled {
		fc.frameTimeFilled = true
	}
}

// ─── Timestats parsing ──────────────────────────────────────────────

func parseTimestats(pkg string) (map[string]int64, error) {
	out, err := exec.Command("dumpsys", "SurfaceFlinger", "--timestats", "-dump").Output()
	if err != nil {
		return nil, err
	}

	result := make(map[string]int64)
	seen := make(map[string]bool)
	var inLayer bool
	var curLayer string

	sc := bufio.NewScanner(strings.NewReader(string(out)))
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())

		if strings.HasPrefix(line, "layerName =") {
			if strings.Contains(line, pkg) {
				if !seen[line] {
					seen[line] = true
					inLayer = true
					curLayer = line
				} else {
					inLayer = false
				}
			} else {
				inLayer = false
			}
			continue
		}

		if inLayer && strings.HasPrefix(line, "totalFrames =") {
			var count int64
			_, err := parseIntField(line, "totalFrames =", &count)
			if err == nil {
				result[curLayer] = count
			}
			inLayer = false
		}
	}
	return result, nil
}

func parseIntField(line, prefix string, out *int64) (string, error) {
	val := strings.TrimPrefix(line, prefix)
	val = strings.TrimSpace(val)
	n, err := parseInt64(val)
	if err == nil {
		*out = n
	}
	return val, err
}

func parseInt64(s string) (int64, error) {
	s = strings.TrimSpace(s)
	var n int64
	_, err := fmt.Sscanf(s, "%d", &n)
	return n, err
}
