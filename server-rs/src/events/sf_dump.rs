//! SurfaceFlinger FPS timestats + latency, extracted without forking `dumpsys`.
//!
//! On Android, uses binder `dump(fd, args)` on the SurfaceFlinger service.
//! On host, stubs return None (no SurfaceFlinger).
//!
//! The parsers are pure string processing — testable on any platform.

use std::collections::HashMap;

/// Per-layer totalFrames extracted from `--timestats -dump`.
pub type LayerCounts = HashMap<String, i64>;

/// Parse `dumpsys SurfaceFlinger --timestats -dump` output.
/// Returns a map of `"layerName = ..." → totalFrames` for layers matching `pkg`.
pub fn parse_timestats(text: &str, pkg: &str) -> LayerCounts {
    let mut result = HashMap::new();
    let mut seen = std::collections::HashSet::new();
    let mut in_layer = false;
    let mut cur_layer = String::new();

    for raw_line in text.lines() {
        let line = raw_line.trim();

        if let Some(rest) = line.strip_prefix("layerName =") {
            let _ = rest; // full line is used as key
            if line.contains(pkg) {
                if seen.insert(line.to_string()) {
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
            if let Some(val) = line.strip_prefix("totalFrames =") {
                if let Ok(count) = val.trim().parse::<i64>() {
                    result.insert(cur_layer.clone(), count);
                }
                in_layer = false;
            }
        }
    }
    result
}

/// Parse `dumpsys SurfaceFlinger --latency <layer>` output.
/// Returns valid actualPresentTime values (nanoseconds since boot).
pub fn parse_latency(text: &str) -> Vec<i64> {
    let mut timestamps = Vec::new();
    let mut lines = text.lines();
    // Skip first line (vsync period)
    lines.next();

    for line in lines {
        let fields: Vec<&str> = line.split_whitespace().collect();
        if fields.len() < 2 {
            continue;
        }
        if let Ok(ts) = fields[1].parse::<i64>() {
            if ts > 0 && ts < 1_000_000_000_000_000_000 {
                timestamps.push(ts);
            }
        }
    }
    timestamps
}

/// Execute a binder dump on SurfaceFlinger and return the text output.
/// Returns None on host or if the service is unavailable.
#[cfg(target_os = "android")]
pub fn sf_dump(args: &[&str]) -> Option<String> {
    use std::os::unix::io::{AsRawFd, FromRawFd, IntoRawFd};

    // Get SurfaceFlinger binder from ServiceManager
    let sf = rsbinder::hub::get_service("SurfaceFlinger")?;
    let proxy = sf.as_proxy()?;

    // Create a pipe
    let (rd, wr) = nix::unistd::pipe().ok()?;

    // Dump to the write end — proxy.dump takes IntoRawFd
    let str_args: Vec<String> = args.iter().map(|s| s.to_string()).collect();
    let wr_raw = wr.into_raw_fd(); // transfers ownership
    if let Err(e) = proxy.dump(wr_raw, &str_args) {
        log::warn!("sf_dump failed: {e:?}");
        // wr_raw was consumed by dump (IntoRawFd)
        return None;
    }
    // wr_raw was consumed by dump — write end is now closed by proxy

    // Read the pipe
    let mut out = Vec::new();
    let mut f = unsafe { std::fs::File::from_raw_fd(rd.as_raw_fd()) };
    std::mem::forget(rd); // File now owns the fd
    std::io::Read::read_to_end(&mut f, &mut out).ok()?;
    Some(String::from_utf8_lossy(&out).into_owned())
}

#[cfg(not(target_os = "android"))]
pub fn sf_dump(_args: &[&str]) -> Option<String> {
    None
}

/// Dump timestats from SurfaceFlinger via binder.
pub fn dump_timestats() -> Option<String> {
    sf_dump(&["--timestats", "-dump"])
}

/// Dump latency for a specific layer from SurfaceFlinger via binder.
pub fn dump_latency(layer: &str) -> Option<String> {
    sf_dump(&["--latency", layer])
}

/// Re-enable timestats collection on SurfaceFlinger.
pub fn enable_timestats() {
    sf_dump(&["--timestats", "-enable"]);
    sf_dump(&["--timestats", "-clear"]);
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE_TIMESTATS: &str = r#"
layerName = SurfaceView[com.example.game/com.example.game.MainActivity]#42
totalFrames = 1234
presentToPresent
    4 - 5ms
    10 - 11ms
layerName = com.example.game/com.example.game.MainActivity#41
totalFrames = 567
presentToPresent
    2 - 3ms
layerName = StatusBar#0
totalFrames = 99
presentToPresent
    1 - 2ms
"#;

    #[test]
    fn parse_timestats_filters_by_package() {
        let result = parse_timestats(SAMPLE_TIMESTATS, "com.example.game");
        assert_eq!(result.len(), 2);
        assert_eq!(
            result["layerName = SurfaceView[com.example.game/com.example.game.MainActivity]#42"],
            1234
        );
        assert_eq!(
            result["layerName = com.example.game/com.example.game.MainActivity#41"],
            567
        );
        // StatusBar should not match
        assert!(!result.contains_key("layerName = StatusBar#0"));
    }

    #[test]
    fn parse_timestats_no_match() {
        let result = parse_timestats(SAMPLE_TIMESTATS, "com.other.app");
        assert!(result.is_empty());
    }

    #[test]
    fn parse_timestats_dedup() {
        let text = r#"
layerName = Foo[com.x]#1
totalFrames = 10
layerName = Foo[com.x]#1
totalFrames = 20
"#;
        let result = parse_timestats(text, "com.x");
        // Second occurrence of same layer name is ignored
        assert_eq!(result.len(), 1);
        assert_eq!(result["layerName = Foo[com.x]#1"], 10);
    }

    const SAMPLE_LATENCY: &str = r#"16666666
0	150000000	0
0	166666666	0
0	183333333	0
0	200000000	0
"#;

    #[test]
    fn parse_latency_basic() {
        let ts = parse_latency(SAMPLE_LATENCY);
        assert_eq!(ts, vec![150000000, 166666666, 183333333, 200000000]);
    }

    #[test]
    fn parse_latency_filters_invalid() {
        let text = "16666666\n0\t0\t0\n0\t-5\t0\n0\t100\t0\n";
        let ts = parse_latency(text);
        assert_eq!(ts, vec![100]);
    }
}
