//! TCP listener + wire protocol.
//!
//! Wire format — length-prefixed JSON frames:
//!
//!   [u32 BE payload_len] [payload_len bytes UTF-8 JSON]
//!
//! Handshake: immediately after `accept()` the server emits
//!
//!   { "type": "auth_ok",   "version": N }
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

use std::io::{self, Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;

use serde::{Deserialize, Serialize};

use crate::snapshot::{SnapshotData, SnapshotStore};

pub const PROTOCOL_VERSION: u32 = 3;
pub const DEFAULT_PORT: u16 = 9876;

pub const MAX_FRAME_BYTES: usize = 1024 * 1024;
pub const MIN_SUBSCRIBE_INTERVAL_MS: u32 = 100;
pub const DEFAULT_SUBSCRIBE_INTERVAL_MS: u32 = 500;

/// Bind TCP on localhost, accept connections until `exit_flag` flips.
pub fn serve(
    port: u16,
    store: SnapshotStore,
    exit_flag: Arc<AtomicBool>,
) -> io::Result<u16> {
    let listener = TcpListener::bind(format!("127.0.0.1:{port}"))?;
    let actual_port = listener.local_addr()?.port();
    log::info!("ipc: listening on 127.0.0.1:{actual_port}");

    listener.set_nonblocking(true)?;

    let next_conn_id = Arc::new(AtomicU64::new(1));

    while !exit_flag.load(Ordering::Acquire) {
        match listener.accept() {
            Ok((stream, addr)) => {
                if !addr.ip().is_loopback() {
                    log::warn!("ipc: rejected non-localhost connection from {addr}");
                    continue;
                }
                let id = next_conn_id.fetch_add(1, Ordering::Relaxed);
                let store = store.clone();
                let exit = exit_flag.clone();
                thread::Builder::new()
                    .name(format!("om-conn-{id}"))
                    .spawn(move || handle_connection(id, stream, store, exit))
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
    Ok(actual_port)
}

// ---- per-connection handler ------------------------------------------------

fn handle_connection(
    id: u64,
    mut stream: TcpStream,
    store: SnapshotStore,
    exit_flag: Arc<AtomicBool>,
) {
    log::info!("conn#{id}: accepted from {:?}", stream.peer_addr());

    if let Err(e) = write_json(&mut stream, &ServerFrame::AuthOk { version: PROTOCOL_VERSION }) {
        log::warn!("conn#{id}: write auth_ok failed: {e}");
        return;
    }

    let mut subscribe_interval: Option<u32> = None;
    let _ = stream.set_read_timeout(Some(Duration::from_millis(50)));

    let mut next_push = std::time::Instant::now();

    loop {
        if exit_flag.load(Ordering::Acquire) {
            break;
        }

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

// ---- framing ---------------------------------------------------------------

fn read_frame(stream: &mut TcpStream) -> io::Result<Option<Vec<u8>>> {
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

fn write_frame(stream: &mut TcpStream, payload: &[u8]) -> io::Result<()> {
    if payload.len() > MAX_FRAME_BYTES {
        return Err(io::Error::new(io::ErrorKind::InvalidData, "outbound frame too large"));
    }
    let len = (payload.len() as u32).to_be_bytes();
    stream.write_all(&len)?;
    stream.write_all(payload)?;
    stream.flush()
}

fn write_json<T: Serialize>(stream: &mut TcpStream, value: &T) -> io::Result<()> {
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
    #[allow(dead_code)]
    AuthFail { reason: String },
    Pong,
    Error { code: i32, msg: String },
}

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
