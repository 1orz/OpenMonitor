package main

import (
	"flag"
	"log"
	"net/http"
	_ "net/http/pprof"
	"os"
	"os/signal"
	"syscall"

	"monitor-daemon/collector"
	"monitor-daemon/daemon"
	"monitor-daemon/server"
)

func main() {
	addr := flag.String("addr", "0.0.0.0:9876", "TCP listen address")
	pprofAddr := flag.String("pprof-addr", "", "pprof HTTP address (e.g. 0.0.0.0:6060), disabled if empty")
	noDetach := flag.Bool("no-detach", false, "stay foreground (dev)")
	dataDir := flag.String("data-dir", "/data/local/tmp", "directory for PID and log files")
	flag.Parse()

	if !*noDetach {
		daemon.Daemonize(*addr, *dataDir, *pprofAddr)
	}

	daemon.PidPath = *dataDir + "/monitor-daemon.pid"
	defer daemon.CleanupPidFile()

	log.SetFlags(log.Ltime | log.Lmicroseconds)
	log.Printf("[main] monitor-daemon starting, addr=%s commit=%s pid=%d data-dir=%s",
		*addr, collector.GitCommit, os.Getpid(), *dataDir)

	if *pprofAddr != "" {
		go func() {
			log.Printf("[main] pprof listening on %s", *pprofAddr)
			if err := http.ListenAndServe(*pprofAddr, nil); err != nil {
				log.Printf("[main] pprof server error: %v", err)
			}
		}()
	}

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
