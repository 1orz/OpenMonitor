//! AF_UNIX listener + wire protocol.
//!
//! Wire format — length-prefixed JSON frames:
//!
//!   [u32 BE payload_len] [payload_len bytes UTF-8 JSON]
//!
//! Maximum frame size is [`MAX_FRAME_BYTES`]. Larger frames from a peer are
//! treated as fatal (the connection is torn down) to prevent a
//! malicious / corrupted peer from driving the daemon OOM.
//!
//! Handshake: immediately after `accept()` the server runs auth and emits
//!
//!   { "type": "auth_ok",   "version": N }
//!   { "type": "auth_fail", "reason": "..." }
//!
//! On `auth_fail` the server writes the frame, `shutdown(WRITE)`s and closes.
//!
//! After a successful handshake the client may send:
//!
//!   { "cmd": "ping" }
//!   { "cmd": "subscribe",   "interval_ms": 500 }
//!   { "cmd": "unsubscribe" }
//!   { "cmd": "exit" }
//!
//! Server replies with:
//!
//!   { "type": "pong" }
//!   { "type": "snapshot", ...SnapshotData fields... }
//!   { "type": "error", "code": N, "msg": "..." }
//!
//! Each accepted connection owns one std thread (the handler). The subscribe
//! loop is driven from the same thread so the connection's read/write paths
//! stay single-threaded and lock-free.

use std::io::{self, Read, Write};
use std::os::unix::fs::PermissionsExt;
use std::os::unix::io::AsRawFd;
use std::os::unix::net::{UnixListener, UnixStream};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;

use serde::{Deserialize, Serialize};

use crate::auth::{verify_peer, AuthConfig, AuthResult};
use crate::snapshot::{SnapshotData, SnapshotStore};

/// Bumped whenever the wire format changes in a way clients must observe.
pub const PROTOCOL_VERSION: u32 = 3;

pub const MAX_FRAME_BYTES: usize = 1 * 1024 * 1024;
pub const MIN_SUBSCRIBE_INTERVAL_MS: u32 = 100;
pub const DEFAULT_SUBSCRIBE_INTERVAL_MS: u32 = 500;

/// Bind an AF_UNIX socket at `path`, accept connections until `exit_flag`
/// flips. Each connection is authenticated and dispatched on its own thread.
///
/// The returned result only covers startup — once bind/listen succeeds the
/// function loops until `exit_flag` is observed.
pub fn serve(
    path: &Path,
    store: SnapshotStore,
    auth: AuthConfig,
    exit_flag: Arc<AtomicBool>,
) -> io::Result<()> {
    let listener = bind_socket(path)?;
    log::info!("ipc: listening on {}", path.display());

    // Non-blocking so the accept loop observes `exit_flag` promptly.
    listener.set_nonblocking(true)?;

    let next_conn_id = Arc::new(AtomicU64::new(1));

    while !exit_flag.load(Ordering::Acquire) {
        match listener.accept() {
            Ok((stream, _addr)) => {
                let id = next_conn_id.fetch_add(1, Ordering::Relaxed);
                let store = store.clone();
                let exit = exit_flag.clone();
                thread::Builder::new()
                    .name(format!("om-conn-{id}"))
                    .spawn(move || handle_connection(id, stream, store, auth, exit))
                    .ok();
            }
            Err(e) if e.kind() == io::ErrorKind::WouldBlock => {
                thread::sleep(Duration::from_millis(200));
            }
            Err(e) => {
                log::error!("accept failed: {e}");
                thread::sleep(Duration::from_millis(500));
            }
        }
    }

    log::info!("ipc: exit flag set, stopping listener");
    // Best-effort unlink so we don't leave a dead socket around.
    let _ = std::fs::remove_file(path);
    Ok(())
}

/// Bind a UDS at `path`. If the directory doesn't exist, create it 0755.
/// Pre-existing dead sockets at `path` are removed so we don't fail on
/// EADDRINUSE after a kill-9.
fn bind_socket(path: &Path) -> io::Result<UnixListener> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
        // Explicit chmod — umask might have stripped world-execute, which
        // would leave the path untraversable for the app-uid client.
        let c_parent = std::ffi::CString::new(parent.as_os_str().as_encoded_bytes())
            .map_err(|_| io::Error::new(io::ErrorKind::InvalidInput, "bad parent path"))?;
        unsafe { libc::chmod(c_parent.as_ptr(), 0o755); }
    }
    // Remove any stale socket so bind() doesn't EADDRINUSE.
    let _ = std::fs::remove_file(path);

    let listener = UnixListener::bind(path)?;
    // 0666 so a non-root app-uid client can connect-and-write. SELinux will
    // still gate the actual access (app_data_file vs shell_data_file etc.);
    // this just removes DAC as a concern.
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o666))?;
    Ok(listener)
}

// ---- per-connection handler ------------------------------------------------

fn handle_connection(
    id: u64,
    mut stream: UnixStream,
    store: SnapshotStore,
    auth: AuthConfig,
    exit_flag: Arc<AtomicBool>,
) {
    log::info!("conn#{id}: accepted fd={}", stream.as_raw_fd());

    // 1. Auth.
    let auth_outcome = verify_peer(stream.as_raw_fd(), &auth);
    match auth_outcome {
        AuthResult::Allow { uid, pid, pkg } => {
            log::info!("conn#{id}: auth_ok uid={uid} pid={pid} pkg={pkg:?}");
            if let Err(e) = write_json(&mut stream, &ServerFrame::AuthOk { version: PROTOCOL_VERSION }) {
                log::warn!("conn#{id}: write auth_ok failed: {e}");
                return;
            }
        }
        AuthResult::Deny(reason) => {
            log::warn!("conn#{id}: auth_fail {reason}");
            let _ = write_json(&mut stream, &ServerFrame::AuthFail { reason: reason.clone() });
            let _ = stream.shutdown(std::net::Shutdown::Both);
            return;
        }
    }

    // 2. Command loop.
    let mut subscribe_interval: Option<u32> = None;
    // Reading has a short SO_RCVTIMEO so the thread can interleave reads and
    // snapshot pushes when a subscription is active.
    set_read_timeout(&stream, Duration::from_millis(50));

    let mut next_push = std::time::Instant::now();

    loop {
        if exit_flag.load(Ordering::Acquire) {
            break;
        }

        // Try to read one frame. If we time out, fall through to the
        // subscribe-push branch.
        match read_frame(&mut stream) {
            Ok(Some(payload)) => {
                let cmd: ClientCmd = match serde_json::from_slice(&payload) {
                    Ok(c) => c,
                    Err(e) => {
                        let _ = write_json(&mut stream, &ServerFrame::Error {
                            code: ErrorCode::BadRequest as i32,
                            msg: format!("parse: {e}"),
                        });
                        continue;
                    }
                };

                match cmd {
                    ClientCmd::Ping => {
                        let _ = write_json(&mut stream, &ServerFrame::Pong);
                    }
                    ClientCmd::Subscribe { interval_ms } => {
                        let iv = interval_ms
                            .unwrap_or(DEFAULT_SUBSCRIBE_INTERVAL_MS)
                            .max(MIN_SUBSCRIBE_INTERVAL_MS);
                        subscribe_interval = Some(iv);
                        next_push = std::time::Instant::now();
                        log::info!("conn#{id}: subscribed interval={iv}ms");
                    }
                    ClientCmd::Unsubscribe => {
                        subscribe_interval = None;
                        log::info!("conn#{id}: unsubscribed");
                    }
                    ClientCmd::Exit => {
                        log::info!("conn#{id}: exit requested");
                        let _ = write_json(&mut stream, &ServerFrame::Pong);
                        exit_flag.store(true, Ordering::Release);
                        break;
                    }
                }
            }
            Ok(None) => {
                log::info!("conn#{id}: peer closed");
                break;
            }
            Err(e) if e.kind() == io::ErrorKind::WouldBlock || e.kind() == io::ErrorKind::TimedOut => {
                // no frame ready — fall through to push
            }
            Err(e) => {
                log::warn!("conn#{id}: read error: {e}");
                break;
            }
        }

        // Push snapshot on schedule.
        if let Some(iv_ms) = subscribe_interval {
            let now = std::time::Instant::now();
            if now >= next_push {
                let snap = store.read();
                if let Err(e) = write_json(&mut stream, &SnapshotFrame::from(&snap)) {
                    log::warn!("conn#{id}: push failed: {e}");
                    break;
                }
                next_push = now + Duration::from_millis(iv_ms as u64);
            }
        }
    }

    log::info!("conn#{id}: done");
}

fn set_read_timeout(stream: &UnixStream, dur: Duration) {
    let _ = stream.set_read_timeout(Some(dur));
}

// ---- framing ---------------------------------------------------------------

fn read_frame(stream: &mut UnixStream) -> io::Result<Option<Vec<u8>>> {
    let mut len_buf = [0u8; 4];
    match stream.read_exact(&mut len_buf) {
        Ok(()) => {}
        Err(e) if e.kind() == io::ErrorKind::UnexpectedEof => return Ok(None),
        Err(e) => return Err(e),
    }
    let len = u32::from_be_bytes(len_buf) as usize;
    if len > MAX_FRAME_BYTES {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            format!("frame too large: {len} > {MAX_FRAME_BYTES}"),
        ));
    }
    let mut buf = vec![0u8; len];
    stream.read_exact(&mut buf)?;
    Ok(Some(buf))
}

fn write_frame(stream: &mut UnixStream, payload: &[u8]) -> io::Result<()> {
    if payload.len() > MAX_FRAME_BYTES {
        return Err(io::Error::new(io::ErrorKind::InvalidData, "outbound frame too large"));
    }
    let len = (payload.len() as u32).to_be_bytes();
    stream.write_all(&len)?;
    stream.write_all(payload)?;
    stream.flush()
}

fn write_json<T: Serialize>(stream: &mut UnixStream, value: &T) -> io::Result<()> {
    let bytes = serde_json::to_vec(value).map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e))?;
    write_frame(stream, &bytes)
}

// ---- wire types ------------------------------------------------------------

#[derive(Debug, Deserialize)]
#[serde(tag = "cmd", rename_all = "snake_case")]
enum ClientCmd {
    Ping,
    Subscribe {
        #[serde(default)]
        interval_ms: Option<u32>,
    },
    Unsubscribe,
    Exit,
}

#[derive(Debug, Serialize)]
#[serde(tag = "type", rename_all = "snake_case")]
enum ServerFrame {
    AuthOk { version: u32 },
    AuthFail { reason: String },
    Pong,
    Error { code: i32, msg: String },
}

/// Snapshot wrapper that tags the frame with `"type":"snapshot"` and flattens
/// the [`SnapshotData`] fields alongside. The Kotlin side deserializes the
/// flat shape directly.
#[derive(Debug, Serialize)]
struct SnapshotFrame<'a> {
    #[serde(rename = "type")]
    kind: &'static str,
    #[serde(flatten)]
    data: &'a SnapshotData,
}

impl<'a> From<&'a SnapshotData> for SnapshotFrame<'a> {
    fn from(data: &'a SnapshotData) -> Self {
        SnapshotFrame { kind: "snapshot", data }
    }
}

#[repr(i32)]
enum ErrorCode {
    BadRequest = 400,
}

// ---- helpers for callers ---------------------------------------------------

/// Resolve the socket path given a data_dir. Callers use this to announce the
/// final path on stdout so launchers can connect without guessing.
pub fn socket_path_in(data_dir: &Path) -> PathBuf {
    data_dir.join("openmonitor.sock")
}
