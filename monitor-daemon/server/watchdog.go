package server

import (
	"log"
	"os/exec"
	"strings"
	"sync"
	"time"
)

const (
	watchdogInterval = 5 * time.Second
	appPackage       = "com.cloudorz.openmonitor"
	serviceAction    = "com.cloudorz.openmonitor.service.FLOAT_START"
	serviceComponent = appPackage + "/" + appPackage + ".service.FloatMonitorService"
)

// Watchdog monitors the app process and restarts the float service if it dies.
type Watchdog struct {
	mu      sync.Mutex
	running bool
	stopCh  chan struct{}
}

func (w *Watchdog) Start() bool {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.running {
		return false
	}
	w.running = true
	w.stopCh = make(chan struct{})
	go w.loop(w.stopCh)
	log.Printf("[watchdog] started (interval=%v)", watchdogInterval)
	return true
}

func (w *Watchdog) Stop() bool {
	w.mu.Lock()
	defer w.mu.Unlock()
	if !w.running {
		return false
	}
	close(w.stopCh)
	w.running = false
	log.Printf("[watchdog] stopped")
	return true
}

func (w *Watchdog) IsRunning() bool {
	w.mu.Lock()
	defer w.mu.Unlock()
	return w.running
}

func (w *Watchdog) loop(stop chan struct{}) {
	ticker := time.NewTicker(watchdogInterval)
	defer ticker.Stop()

	for {
		select {
		case <-stop:
			return
		case <-ticker.C:
			if !isAppAlive() {
				log.Printf("[watchdog] app process dead, restarting float service")
				restartFloatService()
			}
		}
	}
}

func isAppAlive() bool {
	out, err := exec.Command("pidof", appPackage).Output()
	if err != nil {
		return false
	}
	return strings.TrimSpace(string(out)) != ""
}

func restartFloatService() {
	cmd := exec.Command("am", "start-foreground-service",
		"-a", serviceAction,
		serviceComponent)
	if err := cmd.Run(); err != nil {
		log.Printf("[watchdog] restart failed: %v", err)
	} else {
		log.Printf("[watchdog] float service restarted")
	}
}
