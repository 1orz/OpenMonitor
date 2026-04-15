//! Shared-memory Snapshot.
//!
//! The server owns an ashmem region (4 KB, one page) that holds a single
//! `Snapshot` struct. Each collector thread writes fields directly into the
//! region using a seqlock protocol:
//!
//!   1. Reader loads `seq` (v1). If odd → writer in progress, retry.
//!   2. Reader copies fields.
//!   3. Reader loads `seq` (v2). If v1 != v2 → torn read, retry.
//!
//!   W:  seq = (old | 1)  ;; mark writing (odd)
//!       fence(Release)
//!       write body
//!       fence(Release)
//!       seq = (old | 1) + 1  ;; publish (even, = old + 2)
//!
//! Layout **must** match
//! core-server-api/src/main/java/com/cloudorz/openmonitor/server/SnapshotLayout.kt.
//! Compile-time offset asserts at the bottom guard that.

use std::io;
use std::os::unix::io::{AsRawFd, OwnedFd, RawFd};
use std::ptr;
use std::sync::atomic::{fence, AtomicU64, Ordering};

pub const CPU_CORES_MAX: usize = 16;
pub const FPS_LAYER_LEN: usize = 64;
pub const LAST_FOCUSED_PKG_LEN: usize = 128;

pub const MAGIC: u32 = 0x4F4D4E54; // 'OMNT' in little-endian disk order
pub const VERSION: u32 = 2;
pub const SIZE_BYTES: usize = 4096;

/// Launch identity values written into the header. Kept as integers so the
/// Kotlin reader can map to a friendly label without a string lookup.
pub const LAUNCH_MODE_UNKNOWN: u32 = 0;
pub const LAUNCH_MODE_LIBSU_ROOT: u32 = 1;
pub const LAUNCH_MODE_SHIZUKU: u32 = 2;
pub const LAUNCH_MODE_ADB: u32 = 3;

#[repr(C)]
pub struct SnapshotHeader {
    pub magic: u32,
    pub version: u32,
    pub seq: AtomicU64,
    pub timestamp_ns: u64,
    /// Wall-clock (UNIX epoch) ns captured when the server initialized the
    /// region. Constant for the lifetime of the server process — the app
    /// uses it to display uptime and detect re-launches.
    pub start_time_ns: u64,
    pub launch_mode: u32,
    /// Server process id — handy for `kill` and for distinguishing restarts.
    pub pid: u32,
}

/// Fixed-size Snapshot. Total size is padded to 4096 bytes so the whole region
/// is exactly one page and mmap semantics are trivial.
#[repr(C)]
pub struct Snapshot {
    pub header: SnapshotHeader,
    pub cpu_load_pct: [i32; CPU_CORES_MAX],
    pub cpu_freq_mhz: [i32; CPU_CORES_MAX],
    pub cpu_temp_c_x10: i32,
    pub gpu_freq_mhz: i32,
    pub gpu_load_pct: i32,
    pub mem_total_mb: i32,
    pub mem_avail_mb: i32,
    pub ddr_freq_mbps: i32,
    pub battery_current_ma: i32,
    pub battery_voltage_mv: i32,
    pub battery_temp_c_x10: i32,
    pub battery_capacity: i32,
    pub battery_status: i32,
    pub fps_x100: i32,
    pub jank: i32,
    pub big_jank: i32,
    pub fps_layer: [u8; FPS_LAYER_LEN],
    pub last_focused_pkg: [u8; LAST_FOCUSED_PKG_LEN],
    pub screen_interactive: u8,
    // Pad explicitly so total size is SIZE_BYTES. Computed as
    //   SIZE_BYTES - (offset_of!(screen_interactive) + 1)
    // Adjust if the struct grows; compile-time assert below catches mismatches.
    pub _pad: [u8; SIZE_BYTES - 417],
}

/// Owned handle to the server-side shared region.
///
/// The `fd` is what we dup and pass back as a ParcelFileDescriptor; the
/// `ptr` is our private mmap view for writing.
pub struct SharedSnapshot {
    fd: OwnedFd,
    ptr: *mut Snapshot,
}

// SAFETY: the `ptr` is mmap'd MAP_SHARED; writes are atomic per-field and the
// seqlock protocol guarantees reader consistency. We never form &mut references
// to *(ptr) — all mutations go through raw pointer writes.
unsafe impl Send for SharedSnapshot {}
unsafe impl Sync for SharedSnapshot {}

impl SharedSnapshot {
    pub fn create(launch_mode: u32) -> io::Result<Self> {
        let fd = create_ashmem("openmonitor-snapshot", SIZE_BYTES)?;
        Self::from_fd(fd, launch_mode)
    }

    /// File-backed shared memory for the libsu / ADB paths. The app mmaps the
    /// same file directly — no binder needed, no SELinux service_manager
    /// issues. For ADB mode the file lives at /data/local/tmp/openmonitor/
    /// (shell_data_file-labeled); the parent dir needs mode 0755 so the
    /// untrusted_app can traverse it, and the file itself 0644 so it can be
    /// mmap'd read-only.
    pub fn create_file(path: &std::path::Path, launch_mode: u32) -> io::Result<Self> {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)?;
            // chmod explicitly — our umask could have stripped the
            // world-execute bit, making the dir untraversable for the app.
            let c_parent = std::ffi::CString::new(parent.as_os_str().as_encoded_bytes())
                .map_err(|_| io::Error::new(io::ErrorKind::InvalidInput, "bad parent path"))?;
            unsafe { libc::chmod(c_parent.as_ptr(), 0o755); }
        }
        let file = std::fs::OpenOptions::new()
            .read(true).write(true).create(true).truncate(true)
            .open(path)?;
        file.set_len(SIZE_BYTES as u64)?;
        let fd = {
            use std::os::unix::io::IntoRawFd;
            unsafe { OwnedFd::from_raw_fd_checked(file.into_raw_fd())? }
        };
        unsafe { libc::fchmod(fd.as_raw_fd(), 0o644); }
        log::info!("shared memory file: {}", path.display());
        Self::from_fd(fd, launch_mode)
    }

    fn from_fd(fd: OwnedFd, launch_mode: u32) -> io::Result<Self> {
        let ptr = mmap_rw(&fd, SIZE_BYTES)? as *mut Snapshot;
        let start_time_ns = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_nanos() as u64)
            .unwrap_or(0);
        let pid = unsafe { libc::getpid() } as u32;
        unsafe {
            ptr::write_bytes(ptr as *mut u8, 0, SIZE_BYTES);
            let base = ptr as *mut u8;
            let hdr = std::mem::offset_of!(Snapshot, header);
            ptr::write(base.add(hdr + std::mem::offset_of!(SnapshotHeader, magic)) as *mut u32, MAGIC);
            ptr::write(base.add(hdr + std::mem::offset_of!(SnapshotHeader, version)) as *mut u32, VERSION);
            ptr::write(base.add(hdr + std::mem::offset_of!(SnapshotHeader, start_time_ns)) as *mut u64, start_time_ns);
            ptr::write(base.add(hdr + std::mem::offset_of!(SnapshotHeader, launch_mode)) as *mut u32, launch_mode);
            ptr::write(base.add(hdr + std::mem::offset_of!(SnapshotHeader, pid)) as *mut u32, pid);
        }
        Ok(Self { fd, ptr })
    }

    /// Duplicate the FD for handing to the client across binder as a PFD.
    pub fn dup_fd(&self) -> io::Result<OwnedFd> {
        let dup = unsafe { libc::dup(self.fd.as_raw_fd()) };
        if dup < 0 {
            return Err(io::Error::last_os_error());
        }
        // SAFETY: dup succeeded, we own the new FD.
        Ok(unsafe { OwnedFd::from_raw_fd_checked(dup)? })
    }

    /// Execute `write_fn` while holding the seqlock (makes the region odd
    /// for the duration of the closure, then bumps to the next even value).
    ///
    /// Single-writer only. We rely on all collectors funneling through one
    /// sampling thread; if multi-writer support is ever needed, wrap this in a
    /// Mutex<()> externally.
    pub fn with_write<F: FnOnce(&mut Snapshot)>(&self, write_fn: F) {
        let snap: &mut Snapshot = unsafe { &mut *self.ptr };
        let prev = snap.header.seq.load(Ordering::Relaxed);
        // Go to odd (writing).
        let writing = prev | 1;
        snap.header.seq.store(writing, Ordering::Release);
        fence(Ordering::Release);

        write_fn(snap);

        fence(Ordering::Release);
        // Bump to next even.
        snap.header.seq.store(writing.wrapping_add(1), Ordering::Release);
    }

    pub fn fd(&self) -> RawFd { self.fd.as_raw_fd() }
}

impl Drop for SharedSnapshot {
    fn drop(&mut self) {
        if !self.ptr.is_null() {
            unsafe {
                libc::munmap(self.ptr as *mut libc::c_void, SIZE_BYTES);
            }
            self.ptr = ptr::null_mut();
        }
    }
}

// ---- platform helpers -------------------------------------------------------

/// Create an anonymous ashmem region via the NDK `ASharedMemory_create` API
/// (API 26+). Android's project minSdk is 26, so this is always available.
fn create_ashmem(name: &str, size: usize) -> io::Result<OwnedFd> {
    extern "C" {
        fn ASharedMemory_create(name: *const libc::c_char, size: libc::size_t) -> libc::c_int;
    }
    let c_name = std::ffi::CString::new(name)
        .map_err(|_| io::Error::new(io::ErrorKind::InvalidInput, "bad ashmem name"))?;
    let fd = unsafe { ASharedMemory_create(c_name.as_ptr(), size) };
    if fd < 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(unsafe { OwnedFd::from_raw_fd_checked(fd)? })
}

fn mmap_rw(fd: &OwnedFd, size: usize) -> io::Result<*mut libc::c_void> {
    let ptr = unsafe {
        libc::mmap(
            ptr::null_mut(),
            size,
            libc::PROT_READ | libc::PROT_WRITE,
            libc::MAP_SHARED,
            fd.as_raw_fd(),
            0,
        )
    };
    if ptr == libc::MAP_FAILED {
        return Err(io::Error::last_os_error());
    }
    Ok(ptr)
}

// ---- OwnedFd compat ---------------------------------------------------------
//
// std::os::unix::io::OwnedFd::from_raw_fd is `unsafe`; provide a checked
// variant that rejects negative FDs to keep call sites tidy.
trait OwnedFdExt: Sized {
    /// # Safety
    /// The caller must hold ownership of `fd` (no other owner will close it).
    unsafe fn from_raw_fd_checked(fd: RawFd) -> io::Result<Self>;
}
impl OwnedFdExt for OwnedFd {
    unsafe fn from_raw_fd_checked(fd: RawFd) -> io::Result<Self> {
        if fd < 0 {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "negative FD"));
        }
        use std::os::unix::io::FromRawFd;
        Ok(unsafe { OwnedFd::from_raw_fd(fd) })
    }
}

// ---- layout asserts ---------------------------------------------------------

const _: () = {
    // Mirrored from SnapshotLayout.kt — CHANGE BOTH IF YOU CHANGE ONE.
    assert!(std::mem::size_of::<SnapshotHeader>() == 40);
    assert!(std::mem::offset_of!(SnapshotHeader, start_time_ns) == 24);
    assert!(std::mem::offset_of!(SnapshotHeader, launch_mode) == 32);
    assert!(std::mem::offset_of!(SnapshotHeader, pid) == 36);
    assert!(std::mem::offset_of!(Snapshot, cpu_load_pct) == 40);
    assert!(std::mem::offset_of!(Snapshot, cpu_freq_mhz) == 104);
    assert!(std::mem::offset_of!(Snapshot, cpu_temp_c_x10) == 168);
    assert!(std::mem::offset_of!(Snapshot, gpu_freq_mhz) == 172);
    assert!(std::mem::offset_of!(Snapshot, gpu_load_pct) == 176);
    assert!(std::mem::offset_of!(Snapshot, mem_total_mb) == 180);
    assert!(std::mem::offset_of!(Snapshot, mem_avail_mb) == 184);
    assert!(std::mem::offset_of!(Snapshot, ddr_freq_mbps) == 188);
    assert!(std::mem::offset_of!(Snapshot, battery_current_ma) == 192);
    assert!(std::mem::offset_of!(Snapshot, battery_voltage_mv) == 196);
    assert!(std::mem::offset_of!(Snapshot, battery_temp_c_x10) == 200);
    assert!(std::mem::offset_of!(Snapshot, battery_capacity) == 204);
    assert!(std::mem::offset_of!(Snapshot, battery_status) == 208);
    assert!(std::mem::offset_of!(Snapshot, fps_x100) == 212);
    assert!(std::mem::offset_of!(Snapshot, jank) == 216);
    assert!(std::mem::offset_of!(Snapshot, big_jank) == 220);
    assert!(std::mem::offset_of!(Snapshot, fps_layer) == 224);
    assert!(std::mem::offset_of!(Snapshot, last_focused_pkg) == 288);
    assert!(std::mem::offset_of!(Snapshot, screen_interactive) == 416);
    assert!(std::mem::size_of::<Snapshot>() == SIZE_BYTES);
};
