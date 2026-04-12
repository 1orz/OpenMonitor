package server

import (
	"log"
	"os/exec"
	"strings"
	"sync"
	"time"
)

const (
	watchdogInterval   = 5 * time.Second
	appPackage         = "com.cloudorz.openmonitor"
	watchdogAction     = "com.cloudorz.openmonitor.WATCHDOG_RESTART"
	receiverComponent  = appPackage + "/" + appPackage + ".service.BootReceiver"
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
	cmd := exec.Command("am", "broadcast",
		"-a", watchdogAction,
		"-n", receiverComponent)
	out, err := cmd.CombinedOutput()
	if err != nil {
		log.Printf("[watchdog] broadcast failed: %v, output: %s", err, string(out))
	} else {
		log.Printf("[watchdog] broadcast sent: %s", strings.TrimSpace(string(out)))
	}
}
