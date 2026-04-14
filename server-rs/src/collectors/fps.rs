//! FPS collector.
//!
//! ARCHITECTURE:
//! - Foreground package: NOT probed from here. Driven by ITaskStackListener
//!   in `events/task_stack.rs` which writes into a shared Mutex<String>. Each
//!   sample copies the current value into the snapshot. Fully event-driven —
//!   no `dumpsys activity` fork per sample.
//! - FPS / jank / timestats: binder `dump()` on the SurfaceFlinger service
//!   with `["--timestats", "-dump"]`. Zero fork, zero shell. See
//!   `events/sf_dump.rs` for the binder round-trip and parser.
//!
//! This module just glues the two together and maintains differential state
//! (previous timestats → delta FPS).

use std::sync::{Arc, Mutex};

pub struct FpsSample {
    pub fps_x100: i32,
    pub jank: i32,
    pub big_jank: i32,
    pub layer: String,
    pub focused_pkg: String,
}

pub struct FpsReader {
    foreground_pkg: Arc<Mutex<String>>,
    // prev_timestats: Option<TimestatsSnapshot>,
}

impl FpsReader {
    pub fn open() -> Self {
        Self {
            foreground_pkg: Arc::new(Mutex::new(String::new())),
        }
    }

    /// Called by `events::task_stack` to share the listener's state.
    pub fn attach_focus(&self) -> Arc<Mutex<String>> { self.foreground_pkg.clone() }

    pub fn sample(&mut self) -> FpsSample {
        // TODO(Phase 5): invoke SurfaceFlinger.dump and diff vs prev.
        let focused = self.foreground_pkg.lock().map(|g| g.clone()).unwrap_or_default();
        FpsSample {
            fps_x100: -1,
            jank: 0,
            big_jank: 0,
            layer: String::new(),
            focused_pkg: focused,
        }
    }
}
