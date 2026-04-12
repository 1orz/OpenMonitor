package main

import (
	"flag"
	"log"
	"os"
	"os/signal"
	"syscall"

	"monitor-daemon/collector"
	"monitor-daemon/daemon"
	"monitor-daemon/server"
)

func main() {
	addr := flag.String("addr", "0.0.0.0:9876", "TCP listen address")
	sampleMs := flag.Int64("sample-ms", 200, "system sampling interval in ms (min 100)")
	noDetach := flag.Bool("no-detach", false, "stay foreground (dev)")
	dataDir := flag.String("data-dir", "/data/local/tmp", "directory for PID and log files")
	flag.Parse()

	if !*noDetach {
		daemon.Daemonize(*addr, *sampleMs, *dataDir)
	}

	daemon.PidPath = *dataDir + "/monitor-daemon.pid"
	defer daemon.CleanupPidFile()

	log.SetFlags(log.Ltime | log.Lmicroseconds)
	log.Printf("[main] monitor-daemon starting, addr=%s sample=%dms commit=%s pid=%d data-dir=%s",
		*addr, *sampleMs, collector.GitCommit, os.Getpid(), *dataDir)

	collector.SetSampleInterval(*sampleMs)

	c := collector.New()
	c.Start()

	s := server.New(*addr, c)

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGTERM, syscall.SIGINT)
	go func() {
		sig := <-sigCh
		log.Printf("[main] signal %v received, shutting down", sig)
		s.Shutdown()
	}()

	s.Start()
	log.Printf("[main] exited")
}
