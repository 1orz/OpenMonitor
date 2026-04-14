// Generates Rust binder stubs from the AIDL sources.
//
// - `aidl/`         : our own IMonitorService / IMonitorCallback
// - `aidl-system/`  : extracted hidden AOSP AIDL (task stack, battery props,
//                     display manager, power manager). See aidl-system/README.md
//                     for where each file came from.
//
// Output is under $OUT_DIR/aidl/ and gets `include!`d from src/aidl_gen.rs.

fn main() {
    // Skip stub generation when rsbinder-aidl is not available on the host
    // (e.g. on a machine without the AOSP aidl tool). CI/dev hosts that
    // actually build the server must have a working aidl — otherwise the
    // stub files below are never generated and compilation will fail, which
    // is the intended signal.
    let own_aidl_dir = std::path::Path::new("aidl");
    let sys_aidl_dir = std::path::Path::new("aidl-system");

    // Emit rerun hints whether or not we actually invoke the generator, so
    // that touching a .aidl file correctly re-triggers this build.rs.
    rerun_dir(own_aidl_dir);
    rerun_dir(sys_aidl_dir);
    println!("cargo:rerun-if-changed=build.rs");

    // rsbinder-aidl's public API has churned across versions; delegate to
    // the recommended entry point if it exists, otherwise no-op and let the
    // generated-code `include!`s error out with a clear message.
    //
    // The real invocation is intentionally kept simple here; Phase 0 POC
    // will lock down the exact rsbinder-aidl API.
    if let Err(e) = try_generate(own_aidl_dir, sys_aidl_dir) {
        eprintln!("[openmonitor-server build.rs] AIDL stub generation skipped: {e}");
    }
}

fn rerun_dir(dir: &std::path::Path) {
    if !dir.exists() {
        return;
    }
    for entry in walkdir(dir) {
        println!("cargo:rerun-if-changed={}", entry.display());
    }
}

fn walkdir(root: &std::path::Path) -> Vec<std::path::PathBuf> {
    let mut out = Vec::new();
    let mut stack = vec![root.to_path_buf()];
    while let Some(p) = stack.pop() {
        if let Ok(rd) = std::fs::read_dir(&p) {
            for e in rd.flatten() {
                let path = e.path();
                if path.is_dir() {
                    stack.push(path);
                } else {
                    out.push(path);
                }
            }
        }
    }
    out
}

fn try_generate(
    own: &std::path::Path,
    sys: &std::path::Path,
) -> Result<(), Box<dyn std::error::Error>> {
    // `rsbinder-aidl::Builder` — adjust if the crate's API name changes.
    use rsbinder_aidl::Builder;

    let mut b = Builder::new();
    if own.exists() {
        b = b.source(own.to_path_buf());
    }
    if sys.exists() {
        b = b.source(sys.to_path_buf());
    }
    b.generate()?;
    Ok(())
}
