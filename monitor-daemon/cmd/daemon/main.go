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
	noDetach := flag.Bool("no-detach", false, "stay foreground (dev)")
	flag.Parse()

	// Default: fork child and parent exits (like nginx).
	// --no-detach: run in foreground (used by the forked child, or for dev).
	if !*noDetach {
		daemon.Daemonize(*addr) // never returns — parent exits
	}

	defer daemon.CleanupPidFile()

	log.SetFlags(log.Ltime | log.Lmicroseconds)
	log.Printf("[main] monitor-daemon starting, addr=%s commit=%s pid=%d", *addr, collector.GitCommit, os.Getpid())

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

	s.Start() // blocks until Shutdown
	log.Printf("[main] exited")
}
