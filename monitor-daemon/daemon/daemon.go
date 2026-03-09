package daemon

import (
	"fmt"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"syscall"
)

const (
	LogPath = "/data/local/tmp/daemon.log"
	PidPath = "/data/local/tmp/monitor-daemon.pid"
)

// CheckRunning returns an error if another daemon instance is already running.
func CheckRunning() error {
	data, err := os.ReadFile(PidPath)
	if err != nil {
		return nil
	}
	pid, err := strconv.Atoi(strings.TrimSpace(string(data)))
	if err != nil {
		return nil
	}
	if err := syscall.Kill(pid, 0); err == nil {
		return fmt.Errorf("already running (pid %d)", pid)
	}
	return nil
}

// Daemonize re-execs the current binary as a background child with stdio
// redirected to the log file, then the parent exits immediately.
// This is the standard Go daemonization pattern (Go doesn't support fork).
// The calling shell gets exit 0 and returns at once — like nginx.
func Daemonize(addr string) {
	if err := CheckRunning(); err != nil {
		fmt.Fprintf(os.Stderr, "monitor-daemon: %v\n", err)
		os.Exit(1)
	}

	logFile, err := os.OpenFile(LogPath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
	if err != nil {
		fmt.Fprintf(os.Stderr, "monitor-daemon: open log: %v\n", err)
		os.Exit(1)
	}

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

	// Write child PID, parent exits
	os.WriteFile(PidPath, []byte(strconv.Itoa(cmd.Process.Pid)), 0644)
	logFile.Close()
	os.Exit(0)
}

// CleanupPidFile removes the PID file on exit.
func CleanupPidFile() {
	os.Remove(PidPath)
}
