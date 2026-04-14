//! SurfaceFlinger FPS timestats, extracted without forking `dumpsys`.
//!
//! The kernel Binder driver supports a "dump(fd, args)" transaction on every
//! IBinder. NDK C API: `AIBinder_dump(binder, fd, args, num_args)`. rsbinder
//! exposes this through `SpIBinder::dump`. SurfaceFlinger responds to the
//! `--timestats` / `-dump` / `-disable` args the same way `dumpsys` would —
//! we just bypass the middleman.
//!
//! Flow (Phase 5):
//!   1. pipe(2) → (rd_fd, wr_fd).
//!   2. sf_binder.dump(wr_fd, &["--timestats", "-dump"]).
//!   3. close(wr_fd) from our side (server already holds a dup).
//!   4. read(rd_fd) → text → `parse_timestats(text)`.
//!
//! Parser is a line-oriented port of the Go daemon's collector/fps.go
//! `parseTimestats`, factored out so unit tests can feed recorded dump text.

#[allow(dead_code)]
pub struct Timestats {
    pub fps_x100: i32,
    pub jank: i32,
    pub big_jank: i32,
    pub layer: String,
}

pub fn sample() -> Option<Timestats> {
    // TODO(Phase 5): real dump via rsbinder SpIBinder::dump + pipe.
    None
}

/// Tests call this directly with captured dump text.
#[allow(dead_code)]
pub(crate) fn parse_timestats(_text: &str) -> Option<Timestats> {
    // TODO(Phase 5): port `parseTimestats` from monitor-daemon/collector/fps.go.
    None
}
