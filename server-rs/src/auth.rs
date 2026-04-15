//! Peer authentication for incoming UDS connections.
//!
//! Phase A2: full APK v2 signing-block certificate pinning.
//!
//!   SO_PEERCRED → uid/pid
//!     → /data/system/packages.list lookup (uid → pkg)
//!     → walk /data/app/ for <pkg>-*/base.apk
//!     → parse APK v2 signing block (src/apk_sign.rs)
//!     → sha256(certs[0] DER) == EXPECTED_CERT_SHA256
//!
//! The baseline SHA-256 is injected at build time by `build.rs` (from
//! `OPENMONITOR_CERT_SHA256`, populated by the `computeCertSha256` Gradle
//! task). When the env var is absent at build time, `build.rs` emits 32 zero
//! bytes and the verifier refuses every peer — this is the safe default for
//! `cargo check` on a dev laptop.

use crate::apk_sign::{self, ApkSigError};
use std::os::unix::io::RawFd;
use std::path::PathBuf;

include!(concat!(env!("OUT_DIR"), "/cert_hash.rs"));

/// Runtime-visible policy used by the IPC layer.
#[derive(Clone, Copy, Debug)]
pub struct AuthConfig {
    /// 32-byte SHA-256 of the expected APK signing certificate (DER).
    pub expected_cert_sha256: [u8; 32],
    /// When true, skip signature checks entirely — only peer-uid-in-app-range
    /// is enforced. Used for the ADB launch mode where there's no app to pin.
    pub permissive: bool,
}

impl AuthConfig {
    pub fn from_build() -> Self {
        Self {
            expected_cert_sha256: EXPECTED_CERT_SHA256,
            permissive: false,
        }
    }

    /// ADB-mode server — no certificate pinning, callers may be arbitrary apps
    /// since the human operator manually started the daemon.
    pub fn permissive() -> Self {
        Self {
            expected_cert_sha256: [0u8; 32],
            permissive: true,
        }
    }

    /// Returns true if the build-time cert hash is the sentinel (unset).
    pub fn is_sentinel(&self) -> bool {
        self.expected_cert_sha256.iter().all(|&b| b == 0)
    }
}

/// Outcome of [`verify_peer`].
#[derive(Debug)]
pub enum AuthResult {
    Allow { uid: u32, pid: u32, pkg: Option<String> },
    Deny(String),
}

/// Authenticate an incoming UDS peer.
///
/// 1. Read `SO_PEERCRED` to obtain the peer's uid/pid.
/// 2. Privileged uids (root, shell) are allowed unconditionally.
/// 3. In permissive mode, any app-range uid is allowed.
/// 4. Otherwise, resolve uid → package → APK path → v2 cert SHA-256 and
///    compare against the build-time pinned hash.
pub fn verify_peer(fd: RawFd, cfg: &AuthConfig) -> AuthResult {
    let (uid, pid) = match peer_cred(fd) {
        Ok(t) => t,
        Err(e) => return AuthResult::Deny(format!("SO_PEERCRED failed: {e}")),
    };

    // Root / shell can always connect (adb debugging, mcli).
    if uid == 0 || uid == 2000 {
        log::info!("auth: uid={uid} pid={pid} — privileged, allowed");
        return AuthResult::Allow { uid, pid, pkg: None };
    }

    // App processes live in [10000, 19999] (primary user) and
    // [90000, 99999] (secondary / work-profile users).
    let in_app_range = (10_000..=19_999).contains(&uid) || (90_000..=99_999).contains(&uid);
    if !in_app_range {
        return AuthResult::Deny(format!("peer uid {uid} not in app/privileged range"));
    }

    if cfg.permissive {
        log::info!("auth(permissive): uid={uid} pid={pid} — allowed");
        return AuthResult::Allow { uid, pid, pkg: None };
    }

    if cfg.is_sentinel() {
        return AuthResult::Deny(
            "cert pinning disabled — build did not set OPENMONITOR_CERT_SHA256".into(),
        );
    }

    // Resolve uid → package name → APK path → v2 cert hash.
    let pkg = match resolve_uid_to_package(uid) {
        Ok(p) => p,
        Err(e) => return AuthResult::Deny(format!("uid {uid} → package lookup failed: {e}")),
    };

    let apk = match resolve_package_to_apk(&pkg) {
        Ok(a) => a,
        Err(e) => return AuthResult::Deny(format!("package {pkg} → APK lookup failed: {e}")),
    };

    let actual = match apk_sign::v2_cert_sha256(&apk) {
        Ok(h) => h,
        Err(e) => {
            return AuthResult::Deny(format!(
                "APK v2 signature parse failed for {}: {e}",
                apk.display()
            ))
        }
    };

    if actual != cfg.expected_cert_sha256 {
        return AuthResult::Deny(format!(
            "cert mismatch for {pkg}: expected {}, got {}",
            hex32(&cfg.expected_cert_sha256),
            hex32(&actual),
        ));
    }

    log::info!("auth: uid={uid} pid={pid} pkg={pkg} — cert verified, allowed");
    AuthResult::Allow {
        uid,
        pid,
        pkg: Some(pkg),
    }
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

/// Format a 32-byte hash as a lowercase hex string (for log messages).
fn hex32(bytes: &[u8; 32]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

/// Read `SO_PEERCRED` into a `(uid, pid)` tuple. Linux/Android only.
#[cfg(any(target_os = "linux", target_os = "android"))]
fn peer_cred(fd: RawFd) -> std::io::Result<(u32, u32)> {
    let mut cred: libc::ucred = unsafe { std::mem::zeroed() };
    let mut len = std::mem::size_of::<libc::ucred>() as libc::socklen_t;
    let rc = unsafe {
        libc::getsockopt(
            fd,
            libc::SOL_SOCKET,
            libc::SO_PEERCRED,
            (&mut cred) as *mut libc::ucred as *mut libc::c_void,
            &mut len,
        )
    };
    if rc != 0 {
        return Err(std::io::Error::last_os_error());
    }
    Ok((cred.uid as u32, cred.pid as u32))
}

/// Host-side stub for macOS / non-Linux targets — we don't run there in
/// production, but `cargo check` on the dev laptop needs a definition.
#[cfg(not(any(target_os = "linux", target_os = "android")))]
fn peer_cred(_fd: RawFd) -> std::io::Result<(u32, u32)> {
    let uid = unsafe { libc::getuid() } as u32;
    let pid = unsafe { libc::getpid() } as u32;
    Ok((uid, pid))
}

/// Resolve a Linux uid to a package name by reading `/data/system/packages.list`.
///
/// Each line: `<pkg> <uid> <debuggable> <dataDir> <seinfo> [<gids>]`
fn resolve_uid_to_package(uid: u32) -> Result<String, AuthError> {
    let content = std::fs::read_to_string("/data/system/packages.list")
        .map_err(AuthError::Io)?;
    let uid_str = uid.to_string();
    for line in content.lines() {
        let mut cols = line.split_whitespace();
        let pkg = match cols.next() {
            Some(p) => p,
            None => continue,
        };
        if let Some(uid_col) = cols.next() {
            if uid_col == uid_str {
                return Ok(pkg.to_string());
            }
        }
    }
    Err(AuthError::NotFound(format!(
        "no package with uid {uid} in packages.list"
    )))
}

/// Resolve a package name to its `base.apk` path under `/data/app/`.
///
/// Handles two layouts:
///   - Post-Oreo (API 26+): `/data/app/~~<random>~~/<pkg>-<random>/base.apk`
///   - Legacy: `/data/app/<pkg>-<n>/base.apk`
fn resolve_package_to_apk(pkg: &str) -> Result<PathBuf, AuthError> {
    let prefix = format!("{pkg}-");
    let data_app = std::path::Path::new("/data/app");

    // Walk /data/app/ entries.
    let top_entries = std::fs::read_dir(data_app).map_err(AuthError::Io)?;
    for top in top_entries {
        let top = top.map_err(AuthError::Io)?;
        let top_name = top.file_name();
        let top_name = top_name.to_string_lossy();

        // Direct match: /data/app/<pkg>-<something>/base.apk  (legacy layout)
        if top_name.starts_with(&prefix) {
            let candidate = top.path().join("base.apk");
            if candidate.is_file() {
                return Ok(candidate);
            }
        }

        // Randomised layout: /data/app/~~<random>~~/<pkg>-<random>/base.apk
        if top_name.starts_with("~~") && top_name.ends_with("~~") {
            if let Ok(inner_entries) = std::fs::read_dir(top.path()) {
                for inner in inner_entries {
                    let inner = match inner {
                        Ok(e) => e,
                        Err(_) => continue,
                    };
                    let inner_name = inner.file_name();
                    let inner_name = inner_name.to_string_lossy();
                    if inner_name.starts_with(&prefix) {
                        let candidate = inner.path().join("base.apk");
                        if candidate.is_file() {
                            return Ok(candidate);
                        }
                    }
                }
            }
        }
    }

    Err(AuthError::NotFound(format!(
        "no APK found for package {pkg} under /data/app/"
    )))
}

// ---------------------------------------------------------------------------
// Internal error type
// ---------------------------------------------------------------------------

#[derive(Debug)]
enum AuthError {
    Io(std::io::Error),
    NotFound(String),
    #[allow(dead_code)]
    ApkSig(ApkSigError),
}

impl std::fmt::Display for AuthError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            AuthError::Io(e) => write!(f, "I/O error: {e}"),
            AuthError::NotFound(msg) => write!(f, "{msg}"),
            AuthError::ApkSig(e) => write!(f, "APK signature: {e}"),
        }
    }
}
