//! Persistent-FD sysfs reader.
//!
//! Android sysfs pseudo-files regenerate their content on every read. The
//! pattern used by the old Go daemon (open → read → close each sample) is
//! correct but wastes two syscalls per sample — the kernel needs to walk the
//! dentry cache and reinitialize the seq_file every time.
//!
//! We keep the FD open and `seek(0)` + `read()` each sample. This matches the
//! BatteryRecorder `power_reader.cpp` optimization.

use std::fs::{File, OpenOptions};
use std::io::{self, Read, Seek, SeekFrom};

pub struct CachedSysFile {
    file: File,
    buf: [u8; 128],
}

impl CachedSysFile {
    pub fn open(path: &str) -> io::Result<Self> {
        let file = OpenOptions::new().read(true).open(path)?;
        Ok(Self { file, buf: [0u8; 128] })
    }

    /// Try to open; if it fails return None. Callers use this to probe optional
    /// sysfs entries that might not exist on the target SoC.
    pub fn try_open(path: &str) -> Option<Self> {
        Self::open(path).ok()
    }

    /// Read the file and parse a single integer (decimal). Returns -1 for any
    /// I/O or parse error so callers can route absent / transient values
    /// through the `-1 = unknown` sentinel.
    pub fn read_i64_or_minus1(&mut self) -> i64 {
        self.read_i64().unwrap_or(-1)
    }

    pub fn read_i64(&mut self) -> io::Result<i64> {
        let s = self.read_str()?;
        parse_signed(s).ok_or_else(|| io::Error::new(io::ErrorKind::InvalidData, "parse_signed"))
    }

    /// Read the raw bytes (up to buffer cap) as a UTF-8 str. `seek(0)` before
    /// read is mandatory on sysfs.
    pub fn read_str(&mut self) -> io::Result<&str> {
        self.file.seek(SeekFrom::Start(0))?;
        let n = self.file.read(&mut self.buf)?;
        std::str::from_utf8(&self.buf[..n])
            .map_err(|_| io::Error::new(io::ErrorKind::InvalidData, "utf-8"))
    }
}

/// Parse the first signed-integer token from `s`. Tolerates trailing newlines
/// and units like "72000 uA".
fn parse_signed(s: &str) -> Option<i64> {
    let s = s.trim_start();
    let mut end = 0;
    for (i, ch) in s.char_indices() {
        if i == 0 && ch == '-' { continue; }
        if !ch.is_ascii_digit() { break; }
        end = i + ch.len_utf8();
    }
    if end == 0 { return None; }
    s[..end].parse().ok()
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn parse_variations() {
        assert_eq!(parse_signed("123"), Some(123));
        assert_eq!(parse_signed("-456\n"), Some(-456));
        assert_eq!(parse_signed("789 uA"), Some(789));
        assert_eq!(parse_signed("garbage"), None);
    }
}
