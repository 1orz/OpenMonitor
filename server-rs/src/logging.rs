//! logcat-backed `log` crate implementation.
//!
//! Uses the NDK `__android_log_print` syscall so messages land in logcat
//! alongside system logs. Tag defaults to whatever `init()` is given.

use std::ffi::CString;
use std::sync::Once;

static INIT: Once = Once::new();

pub fn init(tag: &str) {
    INIT.call_once(|| {
        let logger = AndroidLogger {
            tag: CString::new(tag).unwrap_or_else(|_| CString::new("openmonitor").unwrap()),
        };
        let _ = log::set_boxed_logger(Box::new(logger));
        log::set_max_level(log::LevelFilter::Info);
    });
}

struct AndroidLogger {
    tag: CString,
}

impl log::Log for AndroidLogger {
    fn enabled(&self, _: &log::Metadata) -> bool { true }

    fn log(&self, record: &log::Record) {
        #[cfg(target_os = "android")]
        {
            let prio = match record.level() {
                log::Level::Error => android_log_sys::LogPriority::ERROR,
                log::Level::Warn => android_log_sys::LogPriority::WARN,
                log::Level::Info => android_log_sys::LogPriority::INFO,
                log::Level::Debug => android_log_sys::LogPriority::DEBUG,
                log::Level::Trace => android_log_sys::LogPriority::VERBOSE,
            };
            if let Ok(msg) = CString::new(format!("{}", record.args())) {
                unsafe {
                    android_log_sys::__android_log_write(
                        prio as libc::c_int,
                        self.tag.as_ptr(),
                        msg.as_ptr(),
                    );
                }
            }
        }
        #[cfg(not(target_os = "android"))]
        {
            eprintln!(
                "[{}] {}: {}",
                record.level(),
                self.tag.to_string_lossy(),
                record.args()
            );
        }
    }

    fn flush(&self) {}
}
