//! Foreground package detection via binder dump (zero fork).
//!
//! Replaces `dumpsys activity activities` / `dumpsys window displays` shell
//! forks from the Go daemon. Uses `IBinder::dump()` on the "activity" and
//! "window" services via rsbinder — same mechanism as sf_dump, zero exec.
//!
//! This is the intermediate approach. Full Phase 4 will use
//! `ITaskStackListener` (event-driven, zero polling), but this gives us
//! correct FPS data without waiting for hidden AIDL extraction.

/// Parse foreground package from `dumpsys activity activities` output.
/// Looks for `mResumedActivity` line and extracts the package name.
pub fn parse_resumed_activity(text: &str) -> Option<String> {
    for line in text.lines() {
        if line.contains("mResumedActivity") {
            return extract_pkg_from_record(line);
        }
    }
    None
}

/// Parse foreground package from `dumpsys window displays` output.
/// Looks for `mFocusedApp=` line and extracts the package name.
pub fn parse_focused_app(text: &str) -> Option<String> {
    for line in text.lines() {
        if line.contains("mFocusedApp=") {
            return extract_pkg_from_record(line);
        }
    }
    None
}

/// Extract package name from an ActivityRecord-style line.
/// Handles patterns like:
///   `mResumedActivity: ActivityRecord{... u0 com.example.app/com.example.app.MainActivity t123}`
///   `mFocusedApp=ActivityRecord{... u0 com.example.app/com.example.app.MainActivity t123}`
fn extract_pkg_from_record(line: &str) -> Option<String> {
    // Try user prefixes: u0, u1, u2, u10 (multi-user Android)
    for prefix in &[" u0 ", " u1 ", " u2 ", " u10 "] {
        if let Some(idx) = line.find(prefix) {
            let rest = &line[idx + prefix.len()..];
            if let Some(slash) = rest.find('/') {
                let pkg = &rest[..slash];
                if pkg.contains('.') {
                    return Some(pkg.to_string());
                }
            }
        }
    }
    None
}

/// Dump a named system service via binder (zero fork).
/// Equivalent to `dumpsys <service_name> [args...]` but without exec.
#[cfg(target_os = "android")]
pub fn service_dump(service_name: &str, args: &[&str]) -> Option<String> {
    use std::os::unix::io::{AsRawFd, FromRawFd, IntoRawFd};

    let svc = rsbinder::hub::get_service(service_name)?;
    let proxy = svc.as_proxy()?;

    let (rd, wr) = nix::unistd::pipe().ok()?;
    let str_args: Vec<String> = args.iter().map(|s| s.to_string()).collect();
    let wr_raw = wr.into_raw_fd();
    if let Err(e) = proxy.dump(wr_raw, &str_args) {
        unsafe { libc::close(wr_raw); }
        log::warn!("service_dump({service_name}) failed: {e:?}");
        return None;
    }
    // Close write end so read_to_end sees EOF once the service finishes writing.
    unsafe { libc::close(wr_raw); }

    let mut out = Vec::new();
    let mut f = unsafe { std::fs::File::from_raw_fd(rd.into_raw_fd()) };
    std::io::Read::read_to_end(&mut f, &mut out).ok()?;
    Some(String::from_utf8_lossy(&out).into_owned())
}

#[cfg(not(target_os = "android"))]
pub fn service_dump(_service_name: &str, _args: &[&str]) -> Option<String> {
    None
}

/// Get the current foreground package. Tries `activity` first, falls back
/// to `window` service. Zero fork on Android; returns None on host.
pub fn get_focused_package() -> Option<String> {
    // Primary: dumpsys activity activities (via binder dump)
    if let Some(text) = service_dump("activity", &["activities"]) {
        if let Some(pkg) = parse_resumed_activity(&text) {
            return Some(pkg);
        }
    }
    // Fallback: dumpsys window displays (via binder dump)
    if let Some(text) = service_dump("window", &["displays"]) {
        if let Some(pkg) = parse_focused_app(&text) {
            return Some(pkg);
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_resumed_activity_typical() {
        let text = r#"
  Task id #1234
    mResumedActivity: ActivityRecord{abc1234 u0 com.tencent.lolm/com.riotgames.leagueoflegends.RiotMainActivity t1234}
    mLastPausedActivity: ActivityRecord{def5678 u0 com.android.launcher3/.Launcher t1}
"#;
        assert_eq!(
            parse_resumed_activity(text),
            Some("com.tencent.lolm".into())
        );
    }

    #[test]
    fn parse_resumed_activity_multi_user() {
        let text = "  mResumedActivity: ActivityRecord{abc u10 com.example.app/com.example.app.Main t5}";
        assert_eq!(
            parse_resumed_activity(text),
            Some("com.example.app".into())
        );
    }

    #[test]
    fn parse_resumed_activity_no_match() {
        let text = "some random log output\nno activity record here\n";
        assert_eq!(parse_resumed_activity(text), None);
    }

    #[test]
    fn parse_focused_app_typical() {
        let text = r#"
  mFocusedApp=ActivityRecord{abc1234 u0 com.example.game/com.example.game.MainActivity t1234}
"#;
        assert_eq!(
            parse_focused_app(text),
            Some("com.example.game".into())
        );
    }

    #[test]
    fn extract_pkg_no_dot() {
        // No dot in the "package" → should not match
        let line = "  mResumedActivity: ActivityRecord{abc u0 launcher/ t1}";
        assert_eq!(extract_pkg_from_record(line), None);
    }
}
