//! FPS collector — port of `reference/go-daemon/collector/fps.go`.
//!
//! Runs in its own thread (`om-fps`), sampling SurfaceFlinger timestats every
//! 500 ms. Uses the same sticky-layer, BLAST-priority, `--latency` fallback,
//! and PerfDog jank algorithm as the validated Go daemon.
//!
//! Every `dumpsys` call goes through [`crate::subproc::run`] with a hard
//! 5-second SIGKILL timeout so a slow `system_server` cannot stall the main
//! data plane.

use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant};

use crate::snapshot::SnapshotStore;
use crate::subproc;

/// 5 s SIGKILL ceiling for every subprocess.
const CMD_TIMEOUT: Duration = Duration::from_secs(5);

/// One frame at 60 Hz — the PerfDog baseline (ms).
const JANK_BASELINE_MS: f64 = 1000.0 / 60.0;

/// Spawn the FPS collector thread. Returns immediately.
pub fn start(store: SnapshotStore, exit: Arc<AtomicBool>) {
    thread::Builder::new()
        .name("om-fps".into())
        .spawn(move || run_loop(store, exit))
        .expect("spawn fps thread");
}

// ── Internal state ──────────────────────────────────────────────────

struct JankState {
    /// Last processed `actualPresentTime` (ns since boot).
    prev_ts: i64,
    /// Previous frame time (ms).
    prev_frame_time_ms: f64,
    /// Frame time before that (ms).
    prev2_frame_time_ms: f64,
}

impl Default for JankState {
    fn default() -> Self {
        Self { prev_ts: 0, prev_frame_time_ms: 0.0, prev2_frame_time_ms: 0.0 }
    }
}

struct State {
    prev_counts: HashMap<String, i64>,
    prev_time: Option<Instant>,
    prev_focus_pkg: String,

    tracked_layer: String,

    jank: JankState,
    /// Cumulative jank counts for the current app session.
    jank_count: i32,
    big_jank_count: i32,

    zero_streak: u32,
    last_frame_ns: i64,

    consecutive_errors: u32,
    last_reenable: Instant,
}

impl State {
    fn new() -> Self {
        Self {
            prev_counts: HashMap::new(),
            prev_time: None,
            prev_focus_pkg: String::new(),
            tracked_layer: String::new(),
            jank: JankState::default(),
            jank_count: 0,
            big_jank_count: 0,
            zero_streak: 0,
            last_frame_ns: 0,
            consecutive_errors: 0,
            last_reenable: Instant::now(),
        }
    }
}

// ── Main loop ───────────────────────────────────────────────────────

fn run_loop(store: SnapshotStore, exit: Arc<AtomicBool>) {
    enable_timestats();
    log::info!("fps: timestats enabled, sampling every 500 ms");

    let mut state = State::new();

    loop {
        if exit.load(Ordering::Acquire) {
            log::info!("fps: exit flag set, stopping");
            return;
        }

        sample(&mut state, &store);

        // 500 ms cadence — sleep in short ticks to honour the exit flag faster.
        let deadline = Instant::now() + Duration::from_millis(500);
        while Instant::now() < deadline {
            if exit.load(Ordering::Acquire) {
                return;
            }
            thread::sleep(Duration::from_millis(50));
        }
    }
}

// ── Timestats enable / recovery ─────────────────────────────────────

fn enable_timestats() {
    subproc::run(
        &["dumpsys", "SurfaceFlinger", "--timestats", "-enable"],
        CMD_TIMEOUT,
    );
    subproc::run(
        &["dumpsys", "SurfaceFlinger", "--timestats", "-clear"],
        CMD_TIMEOUT,
    );
}

fn reenable_if_needed(state: &mut State) {
    if state.consecutive_errors >= 6
        || state.last_reenable.elapsed() > Duration::from_secs(600)
    {
        log::info!(
            "fps: re-enabling timestats (errors={}, last_reenable={:.0}s ago)",
            state.consecutive_errors,
            state.last_reenable.elapsed().as_secs_f64()
        );
        enable_timestats();
        state.consecutive_errors = 0;
        state.last_reenable = Instant::now();
        state.prev_counts.clear();
        state.prev_time = None;
    }
}

// ── Core sample cycle ───────────────────────────────────────────────

fn sample(state: &mut State, store: &SnapshotStore) {
    reenable_if_needed(state);

    let pkg = get_focused_pkg();
    if pkg.is_empty() {
        state.consecutive_errors += 1;
        return;
    }

    let counts = match parse_timestats(&pkg) {
        Some(c) => c,
        None => {
            state.consecutive_errors += 1;
            return;
        }
    };
    state.consecutive_errors = 0;

    let now = Instant::now();

    // No matching layers for foreground package — count as idle.
    if counts.is_empty() {
        state.zero_streak += 1;
        if state.zero_streak >= 2 {
            store.update(|d| {
                d.fps.x100 = 0;
            });
        }
        return;
    }

    // ── App switch: full reset ──
    if pkg != state.prev_focus_pkg {
        log::info!("fps: app switch: {} → {}", state.prev_focus_pkg, pkg);
        state.prev_focus_pkg = pkg;
        state.prev_counts.clear();
        state.tracked_layer.clear();
        state.prev_time = None;
        state.zero_streak = 0;
        state.last_frame_ns = 0;
        state.jank = JankState::default();
        state.jank_count = 0;
        state.big_jank_count = 0;

        for (k, v) in &counts {
            state.prev_counts.insert(k.clone(), *v);
        }
        state.prev_time = Some(now);

        store.update(|d| {
            d.fps.x100 = 0;
            d.fps.jank = 0;
            d.fps.big_jank = 0;
            d.fps.layer.clear();
        });
        return;
    }

    let prev_time = match state.prev_time {
        Some(t) => t,
        None => {
            for (k, v) in &counts {
                state.prev_counts.insert(k.clone(), *v);
            }
            state.prev_time = Some(now);
            return;
        }
    };

    let dt = now.duration_since(prev_time).as_secs_f64();
    state.prev_time = Some(now);
    if dt <= 0.0 {
        return;
    }

    // ── Compute delta for ALL layers ──
    let mut deltas: HashMap<String, i64> = HashMap::new();
    for (layer, cur_count) in &counts {
        let prev_count = state.prev_counts.get(layer).copied().unwrap_or(0);
        let delta = cur_count - prev_count;
        if delta > 0 {
            deltas.insert(layer.clone(), delta);
        }
        state.prev_counts.insert(layer.clone(), *cur_count);
    }

    let (best_layer, best_delta) = select_layer(&state.tracked_layer, &deltas);

    if best_delta <= 0 {
        // Timestats delta=0 — probe --latency to distinguish frozen counter from idle.
        if probe_latency(state, store) {
            return;
        }
        state.zero_streak += 1;
        if state.zero_streak >= 2 {
            store.update(|d| {
                d.fps.x100 = 0;
            });
        }
        return;
    }

    state.zero_streak = 0;
    state.last_frame_ns = 0; // reset latency baseline when timestats is working

    if best_layer != state.tracked_layer && !state.tracked_layer.is_empty() {
        log::info!(
            "fps: layer switch: {} → {} (delta={})",
            state.tracked_layer,
            best_layer,
            best_delta
        );
    }
    state.tracked_layer = best_layer.clone();

    let raw_fps = best_delta as f64 / dt;
    let x100 = (raw_fps * 100.0).round() as i32;

    // ── PerfDog jank detection from per-frame --latency timestamps ──
    detect_jank_perfdog(state);

    store.update(|d| {
        d.fps.x100 = x100;
        d.fps.jank = state.jank_count;
        d.fps.big_jank = state.big_jank_count;
        d.fps.layer = best_layer;
    });
}

// ── Foreground detection (used by FPS to scope timestats to active pkg) ─

fn get_focused_pkg() -> String {
    if let Some(pkg) = get_focused_from_activity() {
        if !pkg.is_empty() {
            return pkg;
        }
    }
    get_focused_from_window().unwrap_or_default()
}

fn get_focused_from_activity() -> Option<String> {
    let (out, _ok) = subproc::run(
        &["dumpsys", "activity", "activities"],
        CMD_TIMEOUT,
    );
    let text = String::from_utf8_lossy(&out);
    for line in text.lines() {
        if line.contains("mResumedActivity") {
            let pkg = extract_pkg_from_record(line);
            if !pkg.is_empty() {
                return Some(pkg);
            }
        }
    }
    None
}

fn get_focused_from_window() -> Option<String> {
    let (out, _ok) = subproc::run(
        &["dumpsys", "window", "displays"],
        CMD_TIMEOUT,
    );
    let text = String::from_utf8_lossy(&out);
    for line in text.lines() {
        if line.contains("mFocusedApp=") {
            let pkg = extract_pkg_from_record(line);
            if !pkg.is_empty() {
                return Some(pkg);
            }
        }
    }
    None
}

/// Extract a package name from a dumpsys record line containing ` u0 pkg/Activity`.
fn extract_pkg_from_record(line: &str) -> String {
    for prefix in &[" u0 ", " u1 ", " u2 ", " u10 "] {
        if let Some(idx) = line.find(prefix) {
            let rest = &line[idx + prefix.len()..];
            if let Some(slash) = rest.find('/') {
                let pkg = &rest[..slash];
                if pkg.contains('.') {
                    return pkg.to_string();
                }
            }
        }
    }
    String::new()
}

// ── Sticky layer selection ──────────────────────────────────────────

fn select_layer(tracked: &str, deltas: &HashMap<String, i64>) -> (String, i64) {
    if deltas.is_empty() {
        return (tracked.to_string(), 0);
    }

    let tracked_delta = deltas.get(tracked).copied().unwrap_or(0);

    if tracked_delta > 0 {
        // Check if another layer has >2x the tracked layer's delta.
        for (layer, &delta) in deltas {
            if layer != tracked && delta > tracked_delta * 2 {
                return (layer.clone(), delta);
            }
        }
        return (tracked.to_string(), tracked_delta);
    }

    // Tracked layer has zero delta (or not tracked yet) — pick best from scratch.
    let mut best_layer = String::new();
    let mut best_delta: i64 = 0;
    let mut best_is_blast = false;

    for (layer, &delta) in deltas {
        let is_blast = is_blast_layer(layer);
        if delta > best_delta || (delta == best_delta && is_blast && !best_is_blast) {
            best_layer = layer.clone();
            best_delta = delta;
            best_is_blast = is_blast;
        }
    }
    (best_layer, best_delta)
}

fn is_blast_layer(name: &str) -> bool {
    name.contains("SurfaceView") && name.contains("BLAST")
}

// ── Latency probe (fallback when timestats delta=0) ────────────────

/// Returns `true` if frames were found (FPS updated or baseline set).
fn probe_latency(state: &mut State, store: &SnapshotStore) -> bool {
    if state.tracked_layer.is_empty() {
        return false;
    }

    let layer_name = state
        .tracked_layer
        .strip_prefix("layerName = ")
        .unwrap_or(&state.tracked_layer);

    let timestamps = parse_latency(layer_name);
    if timestamps.is_empty() {
        return false;
    }

    let latest = timestamps.iter().copied().max().unwrap_or(0);
    let new_count = if state.last_frame_ns > 0 {
        timestamps.iter().filter(|&&ts| ts > state.last_frame_ns).count()
    } else {
        0
    };

    // First probe: establish baseline, hold previous FPS.
    if state.last_frame_ns == 0 {
        state.last_frame_ns = latest;
        return true;
    }

    if new_count == 0 {
        return false;
    }

    let dt = (latest - state.last_frame_ns) as f64 / 1e9;
    state.last_frame_ns = latest;

    if dt >= 0.01 {
        let fps = new_count as f64 / dt;
        let x100 = (fps * 100.0).round() as i32;
        let layer = state.tracked_layer.clone();
        state.zero_streak = 0;

        log::info!(
            "fps: latency: {} new frames in {:.3}s → {:.1} fps",
            new_count,
            dt,
            fps
        );

        store.update(|d| {
            d.fps.x100 = x100;
            d.fps.layer = layer;
        });
        return true;
    }
    false
}

/// Run `dumpsys SurfaceFlinger --latency <layer>` and return valid
/// `actualPresentTime` values (nanoseconds since boot).
fn parse_latency(layer: &str) -> Vec<i64> {
    let (out, _ok) = subproc::run(
        &["dumpsys", "SurfaceFlinger", "--latency", layer],
        CMD_TIMEOUT,
    );
    let text = String::from_utf8_lossy(&out);
    let text = text.trim();
    if text.is_empty() {
        return Vec::new();
    }

    let mut timestamps = Vec::new();
    for (i, line) in text.lines().enumerate() {
        if i == 0 {
            continue; // skip vsync period line
        }
        let fields: Vec<&str> = line.split_whitespace().collect();
        if fields.len() < 2 {
            continue;
        }
        if let Ok(ts) = fields[1].parse::<i64>() {
            if ts > 0 && (ts as f64) < 1e18 {
                timestamps.push(ts);
            }
        }
    }
    timestamps
}

// ── PerfDog jank detection ─────────────────────────────────────────

fn detect_jank_perfdog(state: &mut State) {
    if state.tracked_layer.is_empty() {
        return;
    }

    let layer_name = state
        .tracked_layer
        .strip_prefix("layerName = ")
        .unwrap_or(&state.tracked_layer);

    let timestamps = parse_latency(layer_name);
    if timestamps.len() < 2 {
        return;
    }

    for i in 1..timestamps.len() {
        let ts = timestamps[i];
        if ts <= state.jank.prev_ts {
            continue;
        }

        let prev = timestamps[i - 1];
        if prev <= 0 {
            state.jank.prev_ts = ts;
            continue;
        }

        let frame_time_ms = (ts - prev) as f64 / 1e6;
        if frame_time_ms <= 0.0 || frame_time_ms > 500.0 {
            // sanity: ignore gaps > 500 ms (pause/idle)
            state.jank.prev_ts = ts;
            continue;
        }

        // Need two previous frames to compute the rolling average.
        if state.jank.prev_frame_time_ms > 0.0 && state.jank.prev2_frame_time_ms > 0.0 {
            let avg = (state.jank.prev_frame_time_ms + state.jank.prev2_frame_time_ms) / 2.0;
            if frame_time_ms > 3.0 * avg && frame_time_ms > 3.0 * JANK_BASELINE_MS {
                state.big_jank_count += 1;
            } else if frame_time_ms > 2.0 * avg && frame_time_ms > 2.0 * JANK_BASELINE_MS {
                state.jank_count += 1;
            }
        }

        state.jank.prev2_frame_time_ms = state.jank.prev_frame_time_ms;
        state.jank.prev_frame_time_ms = frame_time_ms;
        state.jank.prev_ts = ts;
    }
}

// ── Timestats parsing ──────────────────────────────────────────────

/// Parse `dumpsys SurfaceFlinger --timestats -dump` output, returning
/// `{ "layerName = ..." => totalFrames }` for layers whose name contains `pkg`.
fn parse_timestats(pkg: &str) -> Option<HashMap<String, i64>> {
    let (out, _ok) = subproc::run(
        &["dumpsys", "SurfaceFlinger", "--timestats", "-dump"],
        CMD_TIMEOUT,
    );
    let text = String::from_utf8_lossy(&out);

    let mut result: HashMap<String, i64> = HashMap::new();
    let mut seen: HashMap<String, bool> = HashMap::new();
    let mut in_layer = false;
    let mut cur_layer = String::new();

    for line in text.lines() {
        let line = line.trim();

        if line.starts_with("layerName =") {
            if line.contains(pkg) {
                if !seen.contains_key(line) {
                    seen.insert(line.to_string(), true);
                    in_layer = true;
                    cur_layer = line.to_string();
                } else {
                    in_layer = false;
                }
            } else {
                in_layer = false;
            }
            continue;
        }

        if in_layer {
            if let Some(rest) = line.strip_prefix("totalFrames =") {
                let rest = rest.trim();
                if let Ok(count) = rest.parse::<i64>() {
                    result.insert(cur_layer.clone(), count);
                }
                in_layer = false;
            }
        }
    }
    Some(result)
}

// ── Tests ───────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_pkg_from_record_basic() {
        let line = "    mResumedActivity: ActivityRecord{abc1234 u0 com.example.app/.MainActivity t42}";
        assert_eq!(extract_pkg_from_record(line), "com.example.app");
    }

    #[test]
    fn test_extract_pkg_from_record_u10() {
        let line = "    mResumedActivity: ActivityRecord{abc u10 com.foo.bar/.Main t99}";
        assert_eq!(extract_pkg_from_record(line), "com.foo.bar");
    }

    #[test]
    fn test_extract_pkg_from_record_no_dot() {
        // Package names without dots should be rejected.
        let line = "    mResumedActivity: ActivityRecord{abc u0 nopkg/.Main t42}";
        assert_eq!(extract_pkg_from_record(line), "");
    }

    #[test]
    fn test_extract_pkg_from_record_no_match() {
        let line = "some random line with no user prefix";
        assert_eq!(extract_pkg_from_record(line), "");
    }

    #[test]
    fn test_is_blast_layer() {
        assert!(is_blast_layer(
            "SurfaceView[com.example/Activity]#14149(BLAST)"
        ));
        assert!(!is_blast_layer("com.example/Activity#0"));
        assert!(!is_blast_layer("SurfaceView[com.example]#0"));
    }

    #[test]
    fn test_select_layer_blast_priority() {
        let mut deltas = HashMap::new();
        deltas.insert("layerName = regular#1".to_string(), 10);
        deltas.insert(
            "layerName = SurfaceView[com.x/A](BLAST)#2".to_string(),
            10,
        );

        let (best, delta) = select_layer("", &deltas);
        assert_eq!(delta, 10);
        assert!(best.contains("BLAST"), "BLAST layer should win on tie");
    }

    #[test]
    fn test_select_layer_sticky() {
        let tracked = "layerName = A#1";
        let mut deltas = HashMap::new();
        deltas.insert("layerName = A#1".to_string(), 10);
        deltas.insert("layerName = B#2".to_string(), 15); // not 2x, should stick

        let (best, delta) = select_layer(tracked, &deltas);
        assert_eq!(best, "layerName = A#1");
        assert_eq!(delta, 10);
    }

    #[test]
    fn test_select_layer_switch_on_2x() {
        let tracked = "layerName = A#1";
        let mut deltas = HashMap::new();
        deltas.insert("layerName = A#1".to_string(), 5);
        deltas.insert("layerName = B#2".to_string(), 11); // > 2x

        let (best, delta) = select_layer(tracked, &deltas);
        assert_eq!(best, "layerName = B#2");
        assert_eq!(delta, 11);
    }

    #[test]
    fn test_select_layer_empty() {
        let (best, delta) = select_layer("tracked", &HashMap::new());
        assert_eq!(best, "tracked");
        assert_eq!(delta, 0);
    }

    #[test]
    fn test_jank_detection() {
        // Simulate a series of frame times where one frame is janky.
        let mut state = State::new();
        state.tracked_layer = "test_layer".to_string();

        // Manually set up jank state with two previous frame times at ~16.67 ms.
        state.jank.prev_frame_time_ms = 16.67;
        state.jank.prev2_frame_time_ms = 16.67;
        state.jank.prev_ts = 0;

        // Build timestamps: 0, 16.67ms, 33.33ms, 105ms (big gap = ~71.67ms frame time)
        // prev2 avg = 16.67, frame_time = 71.67
        // 71.67 > 2 * 16.67 (33.34) => yes
        // 71.67 > 2 * 16.67 baseline (33.34) => yes => jank
        // 71.67 > 3 * 16.67 (50.01) => yes => big_jank
        let base: i64 = 1_000_000_000; // 1 second in ns
        let timestamps = vec![
            base,
            base + 16_670_000,    // 16.67 ms
            base + 33_340_000,    // 16.67 ms
            base + 105_010_000,   // 71.67 ms gap
        ];

        // Pre-process: set prev_ts so we process all frames.
        state.jank.prev_ts = base - 1;
        state.jank_count = 0;
        state.big_jank_count = 0;

        // Simulate what detect_jank_perfdog does but with known timestamps.
        for i in 1..timestamps.len() {
            let ts = timestamps[i];
            if ts <= state.jank.prev_ts {
                continue;
            }

            let prev = timestamps[i - 1];
            let frame_time_ms = (ts - prev) as f64 / 1e6;
            if frame_time_ms <= 0.0 || frame_time_ms > 500.0 {
                state.jank.prev_ts = ts;
                continue;
            }

            if state.jank.prev_frame_time_ms > 0.0 && state.jank.prev2_frame_time_ms > 0.0 {
                let avg =
                    (state.jank.prev_frame_time_ms + state.jank.prev2_frame_time_ms) / 2.0;
                if frame_time_ms > 3.0 * avg && frame_time_ms > 3.0 * JANK_BASELINE_MS {
                    state.big_jank_count += 1;
                } else if frame_time_ms > 2.0 * avg && frame_time_ms > 2.0 * JANK_BASELINE_MS
                {
                    state.jank_count += 1;
                }
            }

            state.jank.prev2_frame_time_ms = state.jank.prev_frame_time_ms;
            state.jank.prev_frame_time_ms = frame_time_ms;
            state.jank.prev_ts = ts;
        }

        // The third frame (71.67 ms) should be detected as big_jank.
        assert_eq!(state.big_jank_count, 1, "expected 1 big_jank");
        assert_eq!(state.jank_count, 0, "big_jank should not also count as jank");
    }

    #[test]
    fn test_parse_timestats_synthetic() {
        // We can't call parse_timestats directly without a subprocess, so test
        // the parsing logic with a synthetic string inline.
        let output = "\
layerName = SurfaceView[com.example.app/Main]#1234(BLAST)
totalFrames = 4200
presentToPresent =
    0ms to 1ms = 100
layerName = com.other.pkg/Activity#999
totalFrames = 50
layerName = SurfaceView[com.example.app/Main]#1234(BLAST)
totalFrames = 100
";
        let pkg = "com.example.app";
        let mut result: HashMap<String, i64> = HashMap::new();
        let mut seen: HashMap<String, bool> = HashMap::new();
        let mut in_layer = false;
        let mut cur_layer = String::new();

        for line in output.lines() {
            let line = line.trim();
            if line.starts_with("layerName =") {
                if line.contains(pkg) {
                    if !seen.contains_key(line) {
                        seen.insert(line.to_string(), true);
                        in_layer = true;
                        cur_layer = line.to_string();
                    } else {
                        in_layer = false;
                    }
                } else {
                    in_layer = false;
                }
                continue;
            }
            if in_layer {
                if let Some(rest) = line.strip_prefix("totalFrames =") {
                    let rest = rest.trim();
                    if let Ok(count) = rest.parse::<i64>() {
                        result.insert(cur_layer.clone(), count);
                    }
                    in_layer = false;
                }
            }
        }

        assert_eq!(result.len(), 1);
        assert_eq!(
            result["layerName = SurfaceView[com.example.app/Main]#1234(BLAST)"],
            4200
        );
    }

    #[test]
    fn test_parse_latency_synthetic() {
        let output = "16666666\n\
1000000000\t1000016666\t1000033333\n\
1000033333\t1000050000\t1000066666\n\
0\t0\t0\n";

        let mut timestamps = Vec::new();
        for (i, line) in output.lines().enumerate() {
            if i == 0 {
                continue;
            }
            let fields: Vec<&str> = line.split_whitespace().collect();
            if fields.len() < 2 {
                continue;
            }
            if let Ok(ts) = fields[1].parse::<i64>() {
                if ts > 0 && (ts as f64) < 1e18 {
                    timestamps.push(ts);
                }
            }
        }

        assert_eq!(timestamps.len(), 2);
        assert_eq!(timestamps[0], 1000016666);
        assert_eq!(timestamps[1], 1000050000);
    }
}
