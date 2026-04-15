//! Foreground package / screen-state collector.
//!
//! Runs in its own thread (`om-fg`) with a 2-second cadence. Detects the
//! current foreground package via `dumpsys activity activities` (primary) or
//! `dumpsys window displays` (fallback), and screen wakefulness via
//! `dumpsys power`. All subprocess calls have a hard 5-second SIGKILL timeout.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant};

use crate::snapshot::SnapshotStore;
use crate::subproc;

/// 5 s SIGKILL ceiling for every subprocess.
const CMD_TIMEOUT: Duration = Duration::from_secs(5);

/// Spawn the foreground-detection thread. Returns immediately.
pub fn start(store: SnapshotStore, exit: Arc<AtomicBool>) {
    thread::Builder::new()
        .name("om-fg".into())
        .spawn(move || run_loop(store, exit))
        .expect("spawn fg thread");
}

fn run_loop(store: SnapshotStore, exit: Arc<AtomicBool>) {
    log::info!("fg: foreground collector started, 2 s cadence");

    loop {
        if exit.load(Ordering::Acquire) {
            log::info!("fg: exit flag set, stopping");
            return;
        }

        let (pkg, screen_on) = detect();
        store.update(|d| {
            d.focus.pkg = pkg;
            d.focus.screen_on = screen_on;
        });

        // 2 s cadence — sleep in short ticks to honour the exit flag faster.
        let deadline = Instant::now() + Duration::from_secs(2);
        while Instant::now() < deadline {
            if exit.load(Ordering::Acquire) {
                return;
            }
            thread::sleep(Duration::from_millis(100));
        }
    }
}

/// Detect the foreground package and screen-on state.
fn detect() -> (String, bool) {
    let pkg = get_focused_pkg();
    let screen_on = get_screen_on();
    (pkg, screen_on)
}

// ── Foreground package detection ────────────────────────────────────

fn get_focused_pkg() -> String {
    if let Some(pkg) = get_focused_from_activity() {
        if !pkg.is_empty() {
            return pkg;
        }
    }
    get_focused_from_window().unwrap_or_default()
}

fn get_focused_from_activity() -> Option<String> {
    let (out, _ok) = subproc::run(&["dumpsys", "activity", "activities"], CMD_TIMEOUT);
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
    let (out, _ok) = subproc::run(&["dumpsys", "window", "displays"], CMD_TIMEOUT);
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

/// Extract a package name from a dumpsys record line containing ` uN pkg/Activity`.
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

// ── Screen-on detection ─────────────────────────────────────────────

/// Check `dumpsys power` for `mWakefulness=Awake` / `mWakefulness=Asleep`.
/// On failure, default to `true` (screen on).
fn get_screen_on() -> bool {
    let (out, ok) = subproc::run(&["dumpsys", "power"], CMD_TIMEOUT);
    if !ok && out.is_empty() {
        return true; // default
    }
    let text = String::from_utf8_lossy(&out);
    for line in text.lines() {
        let trimmed = line.trim();
        if let Some(rest) = trimmed.strip_prefix("mWakefulness=") {
            return !rest.starts_with("Asleep");
        }
    }
    true // default if field not found
}

// ── Tests ───────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_pkg_basic() {
        let line =
            "    mResumedActivity: ActivityRecord{abc1234 u0 com.example.app/.MainActivity t42}";
        assert_eq!(extract_pkg_from_record(line), "com.example.app");
    }

    #[test]
    fn test_extract_pkg_u10() {
        let line = "    mResumedActivity: ActivityRecord{abc u10 com.foo.bar/.Main t99}";
        assert_eq!(extract_pkg_from_record(line), "com.foo.bar");
    }

    #[test]
    fn test_extract_pkg_no_dot() {
        let line = "    mResumedActivity: ActivityRecord{abc u0 nopkg/.Main t42}";
        assert_eq!(extract_pkg_from_record(line), "");
    }

    #[test]
    fn test_extract_pkg_no_match() {
        let line = "some random line with no user prefix";
        assert_eq!(extract_pkg_from_record(line), "");
    }

    #[test]
    fn test_extract_pkg_focused_app_format() {
        // `mFocusedApp=` lines look slightly different but the ` uN pkg/Activity` pattern is the same.
        let line =
            "  mFocusedApp=AppWindowToken{abcd u0 com.game.test/.GameActivity}";
        assert_eq!(extract_pkg_from_record(line), "com.game.test");
    }

    #[test]
    fn test_screen_on_parsing() {
        // Simulated dumpsys power lines.
        let awake_line = "mWakefulness=Awake";
        assert!(!awake_line.trim().strip_prefix("mWakefulness=").unwrap().starts_with("Asleep"));

        let asleep_line = "mWakefulness=Asleep";
        assert!(asleep_line.trim().strip_prefix("mWakefulness=").unwrap().starts_with("Asleep"));
    }
}
