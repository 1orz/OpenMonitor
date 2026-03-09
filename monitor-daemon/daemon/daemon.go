package daemon

import (
	"fmt"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"syscall"
)

var (
	logCandidates = []string{
		"/data/local/tmp/daemon.log",
		"/sdcard/daemon.log",
	}
	pidCandidates = []string{
		"/data/local/tmp/monitor-daemon.pid",
		"/sdcard/monitor-daemon.pid",
	}

	// Resolved at runtime by openOrFallback / resolveWritablePath
	LogPath string
	PidPath string
)

// CheckRunning returns an error if another daemon instance is already running.
func CheckRunning() error {
	for _, p := range pidCandidates {
		data, err := os.ReadFile(p)
		if err != nil {
			continue
		}
		pid, err := strconv.Atoi(strings.TrimSpace(string(data)))
		if err != nil {
			continue
		}
		if err := syscall.Kill(pid, 0); err == nil {
			return fmt.Errorf("already running (pid %d)", pid)
		}
	}
	return nil
}

// openLogFile tries each candidate path and returns the first writable file.
func openLogFile() (*os.File, error) {
	for _, p := range logCandidates {
		f, err := os.OpenFile(p, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
		if err == nil {
			LogPath = p
			return f, nil
		}
	}
	return nil, fmt.Errorf("no writable log path among %v", logCandidates)
}

// resolveWritablePid picks the first writable PID path.
func resolveWritablePid() string {
	for _, p := range pidCandidates {
		f, err := os.OpenFile(p, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0644)
		if err == nil {
			f.Close()
			PidPath = p
			return p
		}
	}
	PidPath = pidCandidates[0]
	return PidPath
}

// Daemonize re-execs the current binary as a background child with stdio
// redirected to the log file, then the parent exits immediately.
func Daemonize(addr string) {
	if err := CheckRunning(); err != nil {
		fmt.Fprintf(os.Stderr, "monitor-daemon: %v\n", err)
		os.Exit(1)
	}

	logFile, err := openLogFile()
	if err != nil {
		fmt.Fprintf(os.Stderr, "monitor-daemon: %v\n", err)
		os.Exit(1)
	}

	pidPath := resolveWritablePid()

	// Re-exec self with --no-detach so the child won't fork again
	cmd := exec.Command(os.Args[0], "--no-detach", "--addr", addr)
	cmd.Stdout = logFile
	cmd.Stderr = logFile
	cmd.SysProcAttr = &syscall.SysProcAttr{Setsid: true}

	if err := cmd.Start(); err != nil {
		fmt.Fprintf(os.Stderr, "monitor-daemon: fork: %v\n", err)
		logFile.Close()
		os.Exit(1)
	}

	os.WriteFile(pidPath, []byte(strconv.Itoa(cmd.Process.Pid)), 0644)
	logFile.Close()
	os.Exit(0)
}

// CleanupPidFile removes the PID file on exit.
func CleanupPidFile() {
	for _, p := range pidCandidates {
		os.Remove(p)
	}
}
