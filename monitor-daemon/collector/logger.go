package collector

import (
	"log"
	"strings"
	"sync/atomic"
)

// Log levels
const (
	LevelDebug   int32 = 0
	LevelInfo    int32 = 1
	LevelWarning int32 = 2
	LevelError   int32 = 3
)

// currentLevel is the global log level (default: info).
var currentLevel int32 = LevelInfo

// SetLogLevel sets the global log level.
func SetLogLevel(level int32) {
	atomic.StoreInt32(&currentLevel, level)
	log.Printf("[logger] level set to %s", LevelName(level))
}

// GetLogLevel returns the current log level.
func GetLogLevel() int32 {
	return atomic.LoadInt32(&currentLevel)
}

// LevelName returns the string name for a level.
func LevelName(level int32) string {
	switch level {
	case LevelDebug:
		return "debug"
	case LevelInfo:
		return "info"
	case LevelWarning:
		return "warning"
	case LevelError:
		return "error"
	default:
		return "unknown"
	}
}

// ParseLogLevel maps a string to a log level.
func ParseLogLevel(s string) (int32, bool) {
	switch strings.ToLower(strings.TrimSpace(s)) {
	case "debug":
		return LevelDebug, true
	case "info":
		return LevelInfo, true
	case "warning", "warn":
		return LevelWarning, true
	case "error":
		return LevelError, true
	default:
		return 0, false
	}
}

// LogDebug logs a debug-level message.
func LogDebug(module, format string, args ...any) {
	if atomic.LoadInt32(&currentLevel) <= LevelDebug {
		log.Printf("["+module+"] DEBUG "+format, args...)
	}
}

// logInfo logs an informational message for the given module.
func logInfo(module, format string, args ...any) {
	if atomic.LoadInt32(&currentLevel) <= LevelInfo {
		log.Printf("["+module+"] "+format, args...)
	}
}

// logWarn logs a warning (non-fatal degradation, fallback taken).
func logWarn(module, format string, args ...any) {
	if atomic.LoadInt32(&currentLevel) <= LevelWarning {
		log.Printf("["+module+"] WARN  "+format, args...)
	}
}

// logError logs an unexpected error.
func logError(module, format string, args ...any) {
	log.Printf("["+module+"] ERROR "+format, args...)
}
