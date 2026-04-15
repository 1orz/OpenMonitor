//! APK v2 signing block parser.
//!
//! Extracts the SHA-256 fingerprint of the first signer's first X.509
//! certificate from an APK's v2 signing block.  The implementation follows the
//! AOSP APK Signature Scheme v2 specification and is modelled (at the
//! byte-layout level) after KernelSU's `apk_sign.c:check_v2_signature`.
//!
//! The parser is pure Rust, uses only `std` + `sha2`, and performs seek-based
//! reads so it never loads the entire APK into memory.

use sha2::{Digest, Sha256};
use std::io::{Read, Seek, SeekFrom};
use std::path::Path;

// ---------------------------------------------------------------------------
// Public error type
// ---------------------------------------------------------------------------

#[derive(Debug)]
pub enum ApkSigError {
    Io(std::io::Error),
    NotZip,
    NoSigningBlock,
    NoV2Pair,
    Truncated,
    Malformed(&'static str),
}

impl From<std::io::Error> for ApkSigError {
    fn from(e: std::io::Error) -> Self {
        ApkSigError::Io(e)
    }
}

impl std::fmt::Display for ApkSigError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ApkSigError::Io(e) => write!(f, "I/O error: {e}"),
            ApkSigError::NotZip => write!(f, "not a valid ZIP file (EOCD not found)"),
            ApkSigError::NoSigningBlock => write!(f, "no APK v2 signing block found"),
            ApkSigError::NoV2Pair => write!(f, "signing block has no v2 scheme pair (0x7109871a)"),
            ApkSigError::Truncated => write!(f, "file truncated or too short"),
            ApkSigError::Malformed(msg) => write!(f, "malformed signing block: {msg}"),
        }
    }
}

impl std::error::Error for ApkSigError {}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/// ZIP End-of-Central-Directory signature (little-endian).
const EOCD_MAGIC: [u8; 4] = [0x50, 0x4b, 0x05, 0x06];

/// APK Signing Block magic — the ASCII string "APK Sig Block 42" (16 bytes).
const APK_SIG_BLOCK_MAGIC: [u8; 16] = *b"APK Sig Block 42";

/// Pair ID for APK Signature Scheme v2.
const V2_PAIR_ID: u32 = 0x7109871a;

/// Minimum EOCD size (no comment).
const EOCD_MIN_SIZE: usize = 22;

/// Maximum bytes from EOF to search for EOCD (22 + 65535 comment bytes).
const EOCD_MAX_SEARCH: u64 = EOCD_MIN_SIZE as u64 + 65535;

// ---------------------------------------------------------------------------
// Helpers — little-endian reads from a slice
// ---------------------------------------------------------------------------

fn read_u16_le(buf: &[u8], off: usize) -> Result<u16, ApkSigError> {
    buf.get(off..off + 2)
        .map(|b| u16::from_le_bytes([b[0], b[1]]))
        .ok_or(ApkSigError::Truncated)
}

fn read_u32_le(buf: &[u8], off: usize) -> Result<u32, ApkSigError> {
    buf.get(off..off + 4)
        .map(|b| u32::from_le_bytes([b[0], b[1], b[2], b[3]]))
        .ok_or(ApkSigError::Truncated)
}

fn read_u64_le(buf: &[u8], off: usize) -> Result<u64, ApkSigError> {
    buf.get(off..off + 8)
        .map(|b| u64::from_le_bytes([b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7]]))
        .ok_or(ApkSigError::Truncated)
}

// ---------------------------------------------------------------------------
// Core implementation
// ---------------------------------------------------------------------------

/// Returns `sha256(DER)` of the first signer's first certificate in the v2
/// signing block of the APK at `apk_path`.
pub fn v2_cert_sha256(apk_path: &Path) -> Result<[u8; 32], ApkSigError> {
    let mut file = std::fs::File::open(apk_path)?;
    let file_len = file.seek(SeekFrom::End(0))?;

    if file_len < EOCD_MIN_SIZE as u64 {
        return Err(ApkSigError::Truncated);
    }

    // Step 1: Find the EOCD record by scanning backwards from EOF.
    let eocd_buf = read_eocd_region(&mut file, file_len)?;
    let eocd_offset_in_buf = find_eocd(&eocd_buf)?;
    let eocd = &eocd_buf[eocd_offset_in_buf..];

    // Step 2: Read "Offset of start of central directory" from EOCD (bytes 16..20).
    let cd_offset = read_u32_le(eocd, 16)? as u64;

    if cd_offset > file_len {
        return Err(ApkSigError::Malformed("CD offset past EOF"));
    }

    // Step 3: Read the signing block that sits immediately before the CD.
    let signing_block = read_signing_block(&mut file, cd_offset)?;

    // Step 4: Find the v2 pair inside the signing block.
    let v2_value = find_v2_pair(&signing_block)?;

    // Step 5: Parse the v2 pair value to extract the first cert DER.
    let cert_der = extract_first_cert_der(v2_value)?;

    // Step 6: SHA-256 the DER.
    let hash = Sha256::digest(&cert_der);
    Ok(hash.into())
}

/// Read up to 64 KB + 22 bytes from the tail of the file — the maximum region
/// that can contain the EOCD record.
fn read_eocd_region(file: &mut std::fs::File, file_len: u64) -> Result<Vec<u8>, ApkSigError> {
    let search_len = file_len.min(EOCD_MAX_SEARCH) as usize;
    let start = file_len - search_len as u64;
    file.seek(SeekFrom::Start(start))?;
    let mut buf = vec![0u8; search_len];
    file.read_exact(&mut buf)?;
    Ok(buf)
}

/// Scan `buf` (the tail of the file) backwards for the EOCD magic. Returns
/// the offset *within* `buf` of the EOCD record.
fn find_eocd(buf: &[u8]) -> Result<usize, ApkSigError> {
    if buf.len() < EOCD_MIN_SIZE {
        return Err(ApkSigError::NotZip);
    }
    // Scan from the end towards the beginning.  The EOCD can appear anywhere
    // in the last 22 + 65535 bytes; we check the comment-length field to
    // validate each candidate.
    let earliest = if buf.len() > EOCD_MIN_SIZE + 65535 {
        buf.len() - EOCD_MIN_SIZE - 65535
    } else {
        0
    };
    let mut pos = buf.len() - EOCD_MIN_SIZE;
    loop {
        if buf[pos..pos + 4] == EOCD_MAGIC {
            // Validate: commentLength (u16 at offset 20) must agree with
            // remaining bytes after the fixed 22-byte EOCD header.
            if pos + EOCD_MIN_SIZE <= buf.len() {
                let comment_len = read_u16_le(buf, pos + 20).unwrap_or(0) as usize;
                if pos + EOCD_MIN_SIZE + comment_len == buf.len() {
                    return Ok(pos);
                }
            }
        }
        if pos == earliest {
            break;
        }
        pos -= 1;
    }
    Err(ApkSigError::NotZip)
}

/// Read and validate the APK Signing Block located immediately before `cd_offset`.
///
/// Returns the block's *pairs region* — everything between block_size_1 and
/// block_size_2 (i.e. the id/value pairs without the framing fields).
fn read_signing_block(
    file: &mut std::fs::File,
    cd_offset: u64,
) -> Result<Vec<u8>, ApkSigError> {
    // The block ends right before the CD.  The last 24 bytes before the CD
    // are: [block_size_2: u64][magic: 16 bytes].
    if cd_offset < 24 {
        return Err(ApkSigError::NoSigningBlock);
    }

    // Read the footer: 8 (block_size_2) + 16 (magic).
    let footer_start = cd_offset - 24;
    file.seek(SeekFrom::Start(footer_start))?;
    let mut footer = [0u8; 24];
    file.read_exact(&mut footer)?;

    // Verify magic.
    if footer[8..24] != APK_SIG_BLOCK_MAGIC {
        return Err(ApkSigError::NoSigningBlock);
    }

    let block_size_2 = u64::from_le_bytes(footer[0..8].try_into().unwrap());

    // block_size_2 includes magic (16) + itself (8) + pairs region.  It must
    // be at least 24 (empty pairs).  The total block on disk is
    // 8 (leading block_size_1) + block_size_2.
    if block_size_2 < 24 {
        return Err(ApkSigError::Malformed("block_size_2 too small"));
    }

    let total_block_size = 8u64 + block_size_2;
    if total_block_size > cd_offset {
        return Err(ApkSigError::Malformed("signing block exceeds file start"));
    }

    let block_start = cd_offset - total_block_size;
    file.seek(SeekFrom::Start(block_start))?;
    let mut block = vec![0u8; total_block_size as usize];
    file.read_exact(&mut block)?;

    // Validate block_size_1 (leading u64) equals block_size_2.
    let block_size_1 = read_u64_le(&block, 0)?;
    if block_size_1 != block_size_2 {
        return Err(ApkSigError::Malformed("block_size_1 != block_size_2"));
    }

    // Pairs region: bytes [8 .. total - 24] (between the leading size and
    // the trailing size+magic).
    let pairs_end = block.len() - 24;
    let pairs = block[8..pairs_end].to_vec();
    Ok(pairs)
}

/// Iterate key-value pairs in the signing block's pairs region.  Each pair is:
///
///   [pair_size: u64 LE] [id: u32 LE] [value: pair_size-4 bytes]
///
/// Returns the *value* slice of the first pair whose id is [`V2_PAIR_ID`].
fn find_v2_pair(pairs: &[u8]) -> Result<Vec<u8>, ApkSigError> {
    let mut off = 0usize;
    while off + 12 <= pairs.len() {
        let pair_size = read_u64_le(pairs, off)? as usize;
        off += 8;
        if pair_size < 4 || off + pair_size > pairs.len() {
            return Err(ApkSigError::Malformed("pair size out of bounds"));
        }
        let id = read_u32_le(pairs, off)?;
        if id == V2_PAIR_ID {
            let value = pairs[off + 4..off + pair_size].to_vec();
            return Ok(value);
        }
        off += pair_size;
    }
    Err(ApkSigError::NoV2Pair)
}

/// Parse the v2 pair value to extract the first certificate DER from the first
/// signer.
///
/// Layout (all lengths u32 LE, length-prefixed):
/// ```text
/// signers_len [
///   signer_len [
///     signed_data_len [
///       digests_len [digests...]
///       certificates_len [
///         cert_len [cert DER bytes]
///         ...
///       ]
///       additional_attrs_len [...]
///     ]
///     signatures_len [...]
///     public_key_len [...]
///   ]
///   ...
/// ]
/// ```
fn extract_first_cert_der(data: Vec<u8>) -> Result<Vec<u8>, ApkSigError> {
    let buf = &data[..];
    let mut off = 0usize;

    // signers (length-prefixed sequence)
    let signers_len = read_u32_le(buf, off)? as usize;
    off += 4;
    if off + signers_len > buf.len() {
        return Err(ApkSigError::Truncated);
    }

    // first signer
    let signer_len = read_u32_le(buf, off)? as usize;
    off += 4;
    if off + signer_len > buf.len() {
        return Err(ApkSigError::Truncated);
    }
    let signer_end = off + signer_len;

    // signed_data (length-prefixed)
    let signed_data_len = read_u32_le(buf, off)? as usize;
    off += 4;
    if off + signed_data_len > signer_end {
        return Err(ApkSigError::Truncated);
    }

    // digests (skip)
    let digests_len = read_u32_le(buf, off)? as usize;
    off += 4;
    if off + digests_len > buf.len() {
        return Err(ApkSigError::Truncated);
    }
    off += digests_len;

    // certificates (length-prefixed sequence)
    let certs_len = read_u32_le(buf, off)? as usize;
    off += 4;
    if off + certs_len > buf.len() {
        return Err(ApkSigError::Truncated);
    }

    // first certificate
    let cert_len = read_u32_le(buf, off)? as usize;
    off += 4;
    if off + cert_len > buf.len() {
        return Err(ApkSigError::Truncated);
    }
    let cert_der = buf[off..off + cert_len].to_vec();
    if cert_der.is_empty() {
        return Err(ApkSigError::Malformed("empty certificate"));
    }
    Ok(cert_der)
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use sha2::{Digest, Sha256};
    use std::io::Write;

    /// Helper: build a minimal synthetic APK (in memory) with the given
    /// signing-block pairs region, then write it to a temp file.  The fake
    /// "central directory" is just 4 bytes of padding; the EOCD points to its
    /// offset.
    fn build_fake_apk(pairs: &[u8], corrupt_sizes: bool) -> tempfile::NamedTempFile {
        let mut apk = Vec::new();

        // ---- Pre-block content (arbitrary) ----
        let pre = b"PK_FAKE_LOCAL_HEADERS";
        apk.extend_from_slice(pre);

        // ---- APK Signing Block ----
        // pairs_len = pairs.len()
        // block_size = pairs_len + 24 (trailing size u64 + magic 16)
        let block_size = pairs.len() as u64 + 24;
        let block_size_1 = if corrupt_sizes {
            block_size + 1 // intentional mismatch
        } else {
            block_size
        };
        apk.extend_from_slice(&block_size_1.to_le_bytes()); // leading block_size_1
        apk.extend_from_slice(pairs);
        apk.extend_from_slice(&block_size.to_le_bytes()); // trailing block_size_2
        apk.extend_from_slice(&APK_SIG_BLOCK_MAGIC);

        // ---- Central Directory (fake, 4 bytes) ----
        let cd_offset = apk.len() as u32;
        let cd = b"CDCD";
        apk.extend_from_slice(cd);

        // ---- EOCD ----
        apk.extend_from_slice(&EOCD_MAGIC);
        apk.extend_from_slice(&[0u8; 4]); // disk number + disk with CD start
        apk.extend_from_slice(&[0u8; 4]); // num entries (this disk + total)
        apk.extend_from_slice(&(cd.len() as u32).to_le_bytes()); // size of CD
        apk.extend_from_slice(&cd_offset.to_le_bytes()); // offset of CD
        apk.extend_from_slice(&0u16.to_le_bytes()); // comment length

        let mut tmp = tempfile::NamedTempFile::new().unwrap();
        tmp.write_all(&apk).unwrap();
        tmp.flush().unwrap();
        tmp
    }

    /// Build the pairs region containing one v2 pair with the given cert DER.
    fn make_v2_pairs(cert_der: &[u8]) -> Vec<u8> {
        // Build the innermost structures outward.

        // certificates sequence: [cert_len][cert_der]
        let mut certs = Vec::new();
        certs.extend_from_slice(&(cert_der.len() as u32).to_le_bytes());
        certs.extend_from_slice(cert_der);

        // digests sequence: empty
        let digests = Vec::new();

        // additional_attrs: empty
        let attrs = Vec::new();

        // signed_data: [digests_len][digests][certs_len][certs][attrs_len][attrs]
        let mut signed_data = Vec::new();
        signed_data.extend_from_slice(&(digests.len() as u32).to_le_bytes());
        signed_data.extend_from_slice(&digests);
        signed_data.extend_from_slice(&(certs.len() as u32).to_le_bytes());
        signed_data.extend_from_slice(&certs);
        signed_data.extend_from_slice(&(attrs.len() as u32).to_le_bytes());
        signed_data.extend_from_slice(&attrs);

        // signatures: empty
        let signatures = Vec::new();

        // public_key: empty
        let public_key = Vec::new();

        // signer: [signed_data_len][signed_data][signatures_len][sigs][pk_len][pk]
        let mut signer = Vec::new();
        signer.extend_from_slice(&(signed_data.len() as u32).to_le_bytes());
        signer.extend_from_slice(&signed_data);
        signer.extend_from_slice(&(signatures.len() as u32).to_le_bytes());
        signer.extend_from_slice(&signatures);
        signer.extend_from_slice(&(public_key.len() as u32).to_le_bytes());
        signer.extend_from_slice(&public_key);

        // signers sequence: [signer_len][signer]
        let mut signers = Vec::new();
        signers.extend_from_slice(&(signer.len() as u32).to_le_bytes());
        signers.extend_from_slice(&signer);

        // v2 pair value: [signers_len][signers]
        let mut v2_value = Vec::new();
        v2_value.extend_from_slice(&(signers.len() as u32).to_le_bytes());
        v2_value.extend_from_slice(&signers);

        // pair: [pair_size: u64][id: u32][value]
        let pair_size = 4u64 + v2_value.len() as u64;
        let mut pairs = Vec::new();
        pairs.extend_from_slice(&pair_size.to_le_bytes());
        pairs.extend_from_slice(&V2_PAIR_ID.to_le_bytes());
        pairs.extend_from_slice(&v2_value);
        pairs
    }

    #[test]
    fn v2_happy_path() {
        let fake_cert = b"this is a fake X.509 DER certificate";
        let pairs = make_v2_pairs(fake_cert);
        let tmp = build_fake_apk(&pairs, false);

        let hash = v2_cert_sha256(tmp.path()).expect("should succeed");
        let expected = Sha256::digest(fake_cert);
        assert_eq!(hash, expected.as_slice());
    }

    #[test]
    fn no_signing_block() {
        // Build a file with a valid EOCD but no signing block magic.
        let mut apk = Vec::new();
        // Some padding where the CD would be.
        let cd_offset = 0u32;
        apk.extend_from_slice(&EOCD_MAGIC);
        apk.extend_from_slice(&[0u8; 4]);
        apk.extend_from_slice(&[0u8; 4]);
        apk.extend_from_slice(&0u32.to_le_bytes()); // CD size
        apk.extend_from_slice(&cd_offset.to_le_bytes());
        apk.extend_from_slice(&0u16.to_le_bytes());

        let mut tmp = tempfile::NamedTempFile::new().unwrap();
        tmp.write_all(&apk).unwrap();
        tmp.flush().unwrap();

        match v2_cert_sha256(tmp.path()) {
            Err(ApkSigError::NoSigningBlock) => {} // expected
            other => panic!("expected NoSigningBlock, got {other:?}"),
        }
    }

    #[test]
    fn truncated_eocd() {
        // A file shorter than 22 bytes cannot contain an EOCD.
        let mut tmp = tempfile::NamedTempFile::new().unwrap();
        tmp.write_all(b"too short").unwrap();
        tmp.flush().unwrap();

        match v2_cert_sha256(tmp.path()) {
            Err(ApkSigError::Truncated) => {}
            other => panic!("expected Truncated, got {other:?}"),
        }
    }

    #[test]
    fn v1_only() {
        // Valid EOCD, CD at a non-zero offset, but no signing block magic
        // before the CD.
        let mut apk = Vec::new();
        // Filler bytes (pretend local file headers).
        let filler = vec![0xAA; 64];
        apk.extend_from_slice(&filler);

        let cd_offset = apk.len() as u32;
        let cd = b"CDCD";
        apk.extend_from_slice(cd);

        // EOCD
        apk.extend_from_slice(&EOCD_MAGIC);
        apk.extend_from_slice(&[0u8; 4]);
        apk.extend_from_slice(&[0u8; 4]);
        apk.extend_from_slice(&(cd.len() as u32).to_le_bytes());
        apk.extend_from_slice(&cd_offset.to_le_bytes());
        apk.extend_from_slice(&0u16.to_le_bytes());

        let mut tmp = tempfile::NamedTempFile::new().unwrap();
        tmp.write_all(&apk).unwrap();
        tmp.flush().unwrap();

        match v2_cert_sha256(tmp.path()) {
            Err(ApkSigError::NoSigningBlock) => {}
            other => panic!("expected NoSigningBlock, got {other:?}"),
        }
    }

    #[test]
    fn size_mismatch() {
        let fake_cert = b"cert";
        let pairs = make_v2_pairs(fake_cert);
        let tmp = build_fake_apk(&pairs, true); // corrupt_sizes = true

        match v2_cert_sha256(tmp.path()) {
            Err(ApkSigError::Malformed(msg)) => {
                assert!(msg.contains("block_size_1 != block_size_2"), "got: {msg}");
            }
            other => panic!("expected Malformed(size mismatch), got {other:?}"),
        }
    }
}
