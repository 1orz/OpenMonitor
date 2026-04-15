//! Subprocess helper: spawn, capture stdout, kill after timeout.
//!
//! Every `dumpsys` call in the daemon runs through [`run`] so that a stuck
//! `system_server` can never stall the sampling threads.

use std::io::Read;
use std::process::{Command, Stdio};
use std::time::{Duration, Instant};

/// Execute `argv[0]` with args `argv[1..]` (PATH lookup), capture stdout,
/// and return `(stdout_bytes, exit_status_ok)`.
///
/// If the process does not exit within `timeout`, send SIGKILL and return
/// whatever stdout was captured so far paired with `ok = false`.
///
/// Never blocks longer than `timeout + ~200 ms` (drain overhead).
pub fn run(argv: &[&str], timeout: Duration) -> (Vec<u8>, bool) {
    if argv.is_empty() {
        return (Vec::new(), false);
    }

    let mut cmd = Command::new(argv[0]);
    if argv.len() > 1 {
        cmd.args(&argv[1..]);
    }
    cmd.stdout(Stdio::piped()).stderr(Stdio::null());

    let mut child = match cmd.spawn() {
        Ok(c) => c,
        Err(e) => {
            log::warn!("subproc: failed to spawn {:?}: {}", argv, e);
            return (Vec::new(), false);
        }
    };

    let deadline = Instant::now() + timeout;
    let poll_interval = Duration::from_millis(50);

    // Poll for exit until timeout.
    loop {
        match child.try_wait() {
            Ok(Some(status)) => {
                // Process exited normally — drain stdout.
                let mut buf = Vec::new();
                if let Some(mut stdout) = child.stdout.take() {
                    let _ = stdout.read_to_end(&mut buf);
                }
                return (buf, status.success());
            }
            Ok(None) => {
                // Still running.
                if Instant::now() >= deadline {
                    break; // timeout
                }
                std::thread::sleep(poll_interval.min(deadline.saturating_duration_since(Instant::now())));
            }
            Err(e) => {
                log::warn!("subproc: try_wait error for {:?}: {}", argv, e);
                let _ = child.kill();
                return (Vec::new(), false);
            }
        }
    }

    // Timeout reached — kill the process.
    log::warn!("subproc: timeout ({:?}) reached for {:?}, sending SIGKILL", timeout, argv);
    let _ = child.kill(); // sends SIGKILL on Unix
    let _ = child.wait(); // reap zombie

    // Drain whatever stdout was buffered.
    let mut buf = Vec::new();
    if let Some(mut stdout) = child.stdout.take() {
        let _ = stdout.read_to_end(&mut buf);
    }
    (buf, false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn run_echo() {
        let (out, ok) = run(&["echo", "hello"], Duration::from_secs(5));
        assert!(ok);
        let s = String::from_utf8_lossy(&out);
        assert!(s.contains("hello"), "expected 'hello' in output, got: {}", s);
    }

    #[test]
    fn run_nonexistent() {
        let (_out, ok) = run(&["__nonexistent_binary_xyz__"], Duration::from_secs(1));
        assert!(!ok);
    }

    #[test]
    fn run_empty_argv() {
        let (out, ok) = run(&[], Duration::from_secs(1));
        assert!(!ok);
        assert!(out.is_empty());
    }

    #[test]
    fn run_false_returns_not_ok() {
        let (_out, ok) = run(&["false"], Duration::from_secs(5));
        assert!(!ok);
    }
}
