package daemon

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
)

var (
	// Resolved at runtime by Daemonize / openLogFile / resolveWritablePid
	LogPath string
	PidPath string
)

// CheckRunning returns an error if another daemon instance is already running.
// It verifies both that the PID is alive AND that it belongs to monitor-daemon,
// preventing false positives from PID reuse by unrelated processes.
func CheckRunning(dataDir string) error {
	pidPath := filepath.Join(dataDir, "monitor-daemon.pid")
	data, err := os.ReadFile(pidPath)
	if err != nil {
		return nil // no PID file — not running
	}
	pid, err := strconv.Atoi(strings.TrimSpace(string(data)))
	if err != nil {
		os.Remove(pidPath)
		return nil
	}
	if err := syscall.Kill(pid, 0); err != nil {
		// Process dead — remove stale PID file
		os.Remove(pidPath)
		return nil
	}
	// Process alive — verify it's actually monitor-daemon (not PID reuse)
	if !isMonitorDaemon(pid) {
		os.Remove(pidPath)
		return nil
	}
	return fmt.Errorf("already running (pid %d)", pid)
}

// isMonitorDaemon checks if the given PID belongs to a monitor-daemon process
// by reading /proc/<pid>/cmdline.
func isMonitorDaemon(pid int) bool {
	cmdline, err := os.ReadFile(fmt.Sprintf("/proc/%d/cmdline", pid))
	if err != nil {
		return false
	}
	// cmdline uses NUL as separator
	return strings.Contains(string(cmdline), "monitor-daemon")
}

// openLogFile opens the log file in dataDir.
// Explicitly chmod 0666 after creation to override root's umask (typically 022),
// so the app process can also read/write the file.
func openLogFile(dataDir string) (*os.File, error) {
	p := filepath.Join(dataDir, "daemon.log")
	f, err := os.OpenFile(p, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
	if err == nil {
		os.Chmod(p, 0666) // override umask
		LogPath = p
		return f, nil
	}
	// If file exists but wrong owner, remove and retry
	if os.IsPermission(err) {
		os.Remove(p)
		f, err = os.OpenFile(p, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
		if err == nil {
			os.Chmod(p, 0666)
			LogPath = p
			return f, nil
		}
	}
	return nil, fmt.Errorf("cannot open log file %s: %v", p, err)
}

// Daemonize re-execs the current binary as a background child with stdio
// redirected to the log file, then the parent exits immediately.
func Daemonize(addr string, sampleMs int64, dataDir string, pprofAddr string) {
	os.MkdirAll(dataDir, 0777)

	if err := CheckRunning(dataDir); err != nil {
		fmt.Fprintf(os.Stderr, "monitor-daemon: %v\n", err)
		os.Exit(1)
	}

	logFile, err := openLogFile(dataDir)
	if err != nil {
		// Fall back to /dev/null — daemon should still run even without logs
		fmt.Fprintf(os.Stderr, "monitor-daemon: %v, using /dev/null\n", err)
		logFile, _ = os.Open(os.DevNull)
	}

	pidPath := filepath.Join(dataDir, "monitor-daemon.pid")
	PidPath = pidPath

	args := []string{"--no-detach", "--addr", addr,
		"--sample-ms", strconv.FormatInt(sampleMs, 10),
		"--data-dir", dataDir}
	if pprofAddr != "" {
		args = append(args, "--pprof-addr", pprofAddr)
	}
	cmd := exec.Command(os.Args[0], args...)
	cmd.Stdout = logFile
	cmd.Stderr = logFile
	cmd.SysProcAttr = &syscall.SysProcAttr{Setsid: true}

	if err := cmd.Start(); err != nil {
		fmt.Fprintf(os.Stderr, "monitor-daemon: fork: %v\n", err)
		logFile.Close()
		os.Exit(1)
	}

	os.WriteFile(pidPath, []byte(strconv.Itoa(cmd.Process.Pid)), 0666)
	logFile.Close()
	os.Exit(0)
}

// CleanupPidFile removes the PID file on exit.
func CleanupPidFile() {
	if PidPath != "" {
		os.Remove(PidPath)
	}
}
