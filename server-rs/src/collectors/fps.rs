//! FPS collector — ports the Go daemon's `collector/fps.go` to Rust.
//!
//! ARCHITECTURE:
//! - Foreground package: driven by ITaskStackListener event (task_stack.rs).
//!   Each sample copies the current value from a shared Mutex<String>.
//! - FPS / jank / timestats: binder `dump()` on SurfaceFlinger with
//!   `["--timestats", "-dump"]`. Zero fork, zero shell.
//! - Jank detection: PerfDog algorithm on per-frame actualPresentTime
//!   from `["--latency", layer]`.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::Instant;

use crate::events::sf_dump;

/// One frame at 60 Hz = 16.67 ms. Used as jank baseline.
const JANK_BASELINE_MS: f64 = 1000.0 / 60.0;

/// Max idle gap in ms to ignore in jank detection (pause/suspend).
const JANK_MAX_GAP_MS: f64 = 500.0;

/// Consecutive failures before re-enabling timestats.
const REENABLE_FAILURE_COUNT: u32 = 6;

/// Minimum interval between timestats re-enable attempts.
const REENABLE_INTERVAL_SECS: u64 = 600; // 10 minutes

pub struct FpsSample {
    pub fps_x100: i32,
    pub jank: i32,
    pub big_jank: i32,
    pub layer: String,
    pub focused_pkg: String,
}

pub struct FpsReader {
    foreground_pkg: Arc<Mutex<String>>,

    prev_counts: HashMap<String, i64>,
    prev_time: Option<Instant>,
    prev_focus_pkg: String,
    tracked_layer: String,
    zero_streak: u32,
    last_frame_ns: i64,
    jank_state: JankState,

    // Recovery
    consecutive_errors: u32,
    last_enable_time: Option<Instant>,

    // Accumulated jank counts per sample window
    jank_count: i32,
    big_jank_count: i32,
}

#[derive(Default)]
struct JankState {
    prev_ts: i64,
    prev_frame_time_ms: f64,
    prev2_frame_time_ms: f64,
}

impl FpsReader {
    pub fn open() -> Self {
        // Enable timestats on startup
        sf_dump::enable_timestats();
        Self {
            foreground_pkg: Arc::new(Mutex::new(String::new())),
            prev_counts: HashMap::new(),
            prev_time: None,
            prev_focus_pkg: String::new(),
            tracked_layer: String::new(),
            zero_streak: 0,
            last_frame_ns: 0,
            jank_state: JankState::default(),
            consecutive_errors: 0,
            last_enable_time: Some(Instant::now()),
            jank_count: 0,
            big_jank_count: 0,
        }
    }

    /// Returns a shared handle to the foreground package.
    /// Called by `events::task_stack` to share the listener's state.
    pub fn attach_focus(&self) -> Arc<Mutex<String>> {
        self.foreground_pkg.clone()
    }

    pub fn sample(&mut self) -> FpsSample {
        self.reenable_if_needed();

        let focused = self
            .foreground_pkg
            .lock()
            .map(|g| g.clone())
            .unwrap_or_default();

        if focused.is_empty() {
            self.consecutive_errors += 1;
            return self.make_sample(focused);
        }

        let text = match sf_dump::dump_timestats() {
            Some(t) => t,
            None => {
                self.consecutive_errors += 1;
                return self.make_sample(focused);
            }
        };

        let counts = sf_dump::parse_timestats(&text, &focused);
        self.consecutive_errors = 0;

        let now = Instant::now();

        // No matching layers for foreground package
        if counts.is_empty() {
            self.zero_streak += 1;
            if self.zero_streak >= 2 {
                return self.make_sample_with_fps(0, focused);
            }
            return self.make_sample(focused);
        }

        // App switch: full reset
        if focused != self.prev_focus_pkg {
            log::info!("fps: app switch: {} → {}", self.prev_focus_pkg, focused);
            self.prev_focus_pkg = focused.clone();
            self.prev_counts = counts;
            self.tracked_layer.clear();
            self.prev_time = Some(now);
            self.zero_streak = 0;
            self.last_frame_ns = 0;
            self.jank_state = JankState::default();
            self.jank_count = 0;
            self.big_jank_count = 0;
            return self.make_sample_with_fps(0, focused);
        }

        let prev_time = match self.prev_time {
            Some(t) => t,
            None => {
                self.prev_counts = counts;
                self.prev_time = Some(now);
                return self.make_sample(focused);
            }
        };

        let dt = now.duration_since(prev_time).as_secs_f64();
        self.prev_time = Some(now);
        if dt <= 0.0 {
            return self.make_sample(focused);
        }

        // Compute deltas for all layers
        let mut deltas = HashMap::new();
        for (layer, cur_count) in &counts {
            let prev = self.prev_counts.get(layer).copied().unwrap_or(0);
            let delta = cur_count - prev;
            if delta > 0 {
                deltas.insert(layer.clone(), delta);
            }
        }
        self.prev_counts = counts;

        let (best_layer, best_delta) = self.select_layer(&deltas);

        if best_delta <= 0 {
            // Probe latency as fallback
            if self.probe_latency(&focused) {
                return self.make_sample(focused);
            }
            self.zero_streak += 1;
            if self.zero_streak >= 2 {
                return self.make_sample_with_fps(0, focused);
            }
            return self.make_sample(focused);
        }

        self.zero_streak = 0;
        self.last_frame_ns = 0;

        if !best_layer.is_empty() && best_layer != self.tracked_layer && !self.tracked_layer.is_empty() {
            log::info!(
                "fps: layer switch: {} → {} (delta={})",
                self.tracked_layer,
                best_layer,
                best_delta
            );
        }
        self.tracked_layer = best_layer;

        let raw_fps = best_delta as f64 / dt;
        let fps_x100 = (raw_fps * 100.0).round() as i32;

        // Jank detection from latency timestamps
        self.detect_jank_perfdog();

        let mut s = self.make_sample_with_fps(fps_x100, focused);
        s.layer = self.tracked_layer.clone();
        s.jank = self.jank_count;
        s.big_jank = self.big_jank_count;
        // Reset per-sample jank counters
        self.jank_count = 0;
        self.big_jank_count = 0;
        s
    }

    fn make_sample(&self, focused_pkg: String) -> FpsSample {
        FpsSample {
            fps_x100: -1,
            jank: 0,
            big_jank: 0,
            layer: self.tracked_layer.clone(),
            focused_pkg,
        }
    }

    fn make_sample_with_fps(&self, fps_x100: i32, focused_pkg: String) -> FpsSample {
        FpsSample {
            fps_x100,
            jank: 0,
            big_jank: 0,
            layer: self.tracked_layer.clone(),
            focused_pkg,
        }
    }

    /// Sticky layer selection with 2× threshold for switching.
    /// Prefers BLAST layers on tie.
    fn select_layer(&self, deltas: &HashMap<String, i64>) -> (String, i64) {
        if deltas.is_empty() {
            return (self.tracked_layer.clone(), 0);
        }

        let tracked_delta = deltas.get(&self.tracked_layer).copied().unwrap_or(0);

        if tracked_delta > 0 {
            for (layer, &delta) in deltas {
                if layer != &self.tracked_layer && delta > tracked_delta * 2 {
                    return (layer.clone(), delta);
                }
            }
            return (self.tracked_layer.clone(), tracked_delta);
        }

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

    /// Probe `--latency` when timestats delta is 0. Returns true if frames found.
    fn probe_latency(&mut self, _focused: &str) -> bool {
        if self.tracked_layer.is_empty() {
            return false;
        }
        let layer_name = self
            .tracked_layer
            .strip_prefix("layerName = ")
            .unwrap_or(&self.tracked_layer);

        let text = match sf_dump::dump_latency(layer_name) {
            Some(t) => t,
            None => return false,
        };
        let timestamps = sf_dump::parse_latency(&text);
        if timestamps.is_empty() {
            return false;
        }

        let latest = *timestamps.iter().max().unwrap_or(&0);

        if self.last_frame_ns == 0 {
            self.last_frame_ns = latest;
            return true;
        }

        let new_count = timestamps
            .iter()
            .filter(|&&ts| ts > self.last_frame_ns)
            .count();

        if new_count == 0 {
            return false;
        }

        let dt = (latest - self.last_frame_ns) as f64 / 1e9;
        self.last_frame_ns = latest;

        if dt >= 0.01 {
            let _fps = new_count as f64 / dt;
            self.zero_streak = 0;
            return true;
        }
        false
    }

    /// PerfDog-style jank detection from per-frame actualPresentTime.
    fn detect_jank_perfdog(&mut self) {
        if self.tracked_layer.is_empty() {
            return;
        }
        let layer_name = self
            .tracked_layer
            .strip_prefix("layerName = ")
            .unwrap_or(&self.tracked_layer);

        let text = match sf_dump::dump_latency(layer_name) {
            Some(t) => t,
            None => return,
        };
        let timestamps = sf_dump::parse_latency(&text);
        if timestamps.len() < 2 {
            return;
        }

        for i in 1..timestamps.len() {
            let ts = timestamps[i];
            if ts <= self.jank_state.prev_ts {
                continue;
            }

            let prev = timestamps[i - 1];
            if prev <= 0 {
                self.jank_state.prev_ts = ts;
                continue;
            }

            let frame_time_ms = (ts - prev) as f64 / 1e6;
            if frame_time_ms <= 0.0 || frame_time_ms > JANK_MAX_GAP_MS {
                self.jank_state.prev_ts = ts;
                continue;
            }

            if self.jank_state.prev_frame_time_ms > 0.0
                && self.jank_state.prev2_frame_time_ms > 0.0
            {
                let avg = (self.jank_state.prev_frame_time_ms
                    + self.jank_state.prev2_frame_time_ms)
                    / 2.0;
                if frame_time_ms > 3.0 * avg && frame_time_ms > 3.0 * JANK_BASELINE_MS {
                    self.big_jank_count += 1;
                } else if frame_time_ms > 2.0 * avg && frame_time_ms > 2.0 * JANK_BASELINE_MS {
                    self.jank_count += 1;
                }
            }

            self.jank_state.prev2_frame_time_ms = self.jank_state.prev_frame_time_ms;
            self.jank_state.prev_frame_time_ms = frame_time_ms;
            self.jank_state.prev_ts = ts;
        }
    }

    /// Re-enable timestats after consecutive failures or timeout.
    fn reenable_if_needed(&mut self) {
        let should_reenable = self.consecutive_errors >= REENABLE_FAILURE_COUNT
            || self
                .last_enable_time
                .map(|t| t.elapsed().as_secs() >= REENABLE_INTERVAL_SECS)
                .unwrap_or(true);

        if should_reenable {
            log::info!("fps: re-enabling timestats (errors={}, age={:?})",
                self.consecutive_errors,
                self.last_enable_time.map(|t| t.elapsed()));
            sf_dump::enable_timestats();
            self.consecutive_errors = 0;
            self.last_enable_time = Some(Instant::now());
        }
    }
}

fn is_blast_layer(name: &str) -> bool {
    name.contains("SurfaceView") && name.contains("BLAST")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn select_layer_prefers_tracked() {
        let reader = FpsReader {
            tracked_layer: "layerName = A".into(),
            ..make_test_reader()
        };
        let mut deltas = HashMap::new();
        deltas.insert("layerName = A".into(), 100);
        deltas.insert("layerName = B".into(), 150); // not 2× of 100
        let (layer, delta) = reader.select_layer(&deltas);
        assert_eq!(layer, "layerName = A");
        assert_eq!(delta, 100);
    }

    #[test]
    fn select_layer_switches_at_2x() {
        let reader = FpsReader {
            tracked_layer: "layerName = A".into(),
            ..make_test_reader()
        };
        let mut deltas = HashMap::new();
        deltas.insert("layerName = A".into(), 100);
        deltas.insert("layerName = B".into(), 201); // > 2× of 100
        let (layer, delta) = reader.select_layer(&deltas);
        assert_eq!(layer, "layerName = B");
        assert_eq!(delta, 201);
    }

    #[test]
    fn select_layer_prefers_blast() {
        let reader = make_test_reader();
        let mut deltas = HashMap::new();
        deltas.insert("layerName = Normal#1".into(), 100);
        deltas.insert("layerName = SurfaceView[BLAST]#1".into(), 100);
        let (layer, _) = reader.select_layer(&deltas);
        assert_eq!(layer, "layerName = SurfaceView[BLAST]#1");
    }

    fn make_test_reader() -> FpsReader {
        FpsReader {
            foreground_pkg: Arc::new(Mutex::new(String::new())),
            prev_counts: HashMap::new(),
            prev_time: None,
            prev_focus_pkg: String::new(),
            tracked_layer: String::new(),
            zero_streak: 0,
            last_frame_ns: 0,
            jank_state: JankState::default(),
            consecutive_errors: 0,
            last_enable_time: None,
            jank_count: 0,
            big_jank_count: 0,
        }
    }
}
