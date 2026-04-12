package server

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"strconv"
	"strings"
	"sync/atomic"
	"time"

	"monitor-daemon/collector"
	"monitor-daemon/daemon"
	"monitor-daemon/proto"
)

// Server listens on TCP and handles daemon commands.
type Server struct {
	addr      string
	collector *collector.Collector
	ln        net.Listener
	done      chan struct{}
	watchdog  Watchdog

	// Heartbeat auto-shutdown: daemon exits if no ping for heartbeatTimeout seconds.
	// 0 = disabled (ADB mode). Updated atomically.
	lastPingTime     atomic.Value // time.Time
	heartbeatTimeout atomic.Int64 // seconds
}

// New creates a Server bound to addr (e.g. "0.0.0.0:9876").
func New(addr string, c *collector.Collector) *Server {
	s := &Server{addr: addr, collector: c, done: make(chan struct{})}
	s.lastPingTime.Store(time.Now())
	return s
}

// Start accepts connections; each is handled in its own goroutine.
// Blocks until Shutdown is called.
func (s *Server) Start() {
	ln, err := net.Listen("tcp", s.addr)
	if err != nil {
		log.Fatalf("listen %s: %v", s.addr, err)
	}
	s.ln = ln
	log.Printf("[server] listening on %s", s.addr)

	go func() {
		for {
			conn, err := ln.Accept()
			if err != nil {
				select {
				case <-s.done:
					return
				default:
					log.Printf("[server] accept error: %v", err)
					continue
				}
			}
			go s.handle(conn)
		}
	}()

	// Heartbeat timeout monitor: exit if no ping received within timeout
	go func() {
		ticker := time.NewTicker(5 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-s.done:
				return
			case <-ticker.C:
				timeout := s.heartbeatTimeout.Load()
				if timeout <= 0 {
					continue
				}
				last := s.lastPingTime.Load().(time.Time)
				elapsed := time.Since(last)
				if elapsed > time.Duration(timeout)*time.Second {
					log.Printf("[heartbeat] no ping for %ds (timeout=%ds), shutting down",
						int(elapsed.Seconds()), timeout)
					s.watchdog.Stop()
					s.Shutdown()
					return
				}
			}
		}
	}()

	<-s.done // block until Shutdown
}

// Shutdown stops accepting connections and unblocks Start.
// Safe to call multiple times.
func (s *Server) Shutdown() {
	select {
	case <-s.done:
		return // already shutting down
	default:
		close(s.done)
		if s.ln != nil {
			s.ln.Close()
		}
	}
}

func (s *Server) handle(conn net.Conn) {
	defer conn.Close()
	remote := conn.RemoteAddr().String()
	log.Printf("[conn] %s connected", remote)

	for {
		conn.SetReadDeadline(time.Now().Add(30 * time.Second))
		buf, err := proto.ReadFrame(conn)
		if err != nil {
			if err != io.EOF {
				log.Printf("[conn] %s read frame: %v", remote, err)
			}
			return
		}

		cmd := strings.TrimSpace(string(buf))
		collector.LogDebug("cmd", "%s: %q", remote, cmd)

		if cmd == "@signal:exit" {
			return
		}

		// Stream mode: "stream:<ms>\n<cmd>"
		if strings.HasPrefix(cmd, "stream:") {
			s.handleStream(conn, cmd)
			return
		}

		resp := s.dispatch(cmd)
		if err := proto.WriteFrame(conn, resp); err != nil {
			log.Printf("[conn] %s write: %v", remote, err)
			return
		}

		if strings.TrimSpace(strings.SplitN(cmd, "\n", 2)[0]) == "daemon-exit" {
			return
		}
	}
}

// handleStream pushes frames at the requested interval until the client disconnects
// or sends @signal:exit. Each tick samples fresh data on demand.
func (s *Server) handleStream(conn net.Conn, streamCmd string) {
	parts := strings.SplitN(streamCmd, "\n", 2)
	header := strings.TrimPrefix(strings.TrimSpace(parts[0]), "stream:")
	intervalMs, err := strconv.Atoi(strings.TrimSpace(header))
	if err != nil || intervalMs < 100 {
		proto.WriteFrame(conn, []byte(`{"error":"invalid stream interval, min 100ms"}`))
		return
	}
	innerCmd := ""
	if len(parts) == 2 {
		innerCmd = strings.TrimSpace(parts[1])
	}
	log.Printf("[stream] start cmd=%q interval=%dms", innerCmd, intervalMs)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	conn.SetReadDeadline(time.Time{})
	go func() {
		defer cancel()
		for {
			frame, err := proto.ReadFrame(conn)
			if err != nil {
				return
			}
			if strings.TrimSpace(string(frame)) == "@signal:exit" {
				log.Printf("[stream] exit signal received")
				return
			}
		}
	}()

	ticker := time.NewTicker(time.Duration(intervalMs) * time.Millisecond)
	defer ticker.Stop()

	// Send first frame immediately.
	conn.SetWriteDeadline(time.Now().Add(5 * time.Second))
	if err := proto.WriteFrame(conn, s.dispatch(innerCmd)); err != nil {
		log.Printf("[stream] initial write: %v", err)
		return
	}

	for {
		select {
		case <-ctx.Done():
			log.Printf("[stream] done")
			return
		case <-ticker.C:
			conn.SetWriteDeadline(time.Now().Add(5 * time.Second))
			if err := proto.WriteFrame(conn, s.dispatch(innerCmd)); err != nil {
				log.Printf("[stream] write: %v", err)
				return
			}
		}
	}
}

// dispatch handles a command and returns the response bytes.
func (s *Server) dispatch(cmd string) []byte {
	name := strings.TrimSpace(strings.SplitN(cmd, "\n", 2)[0])

	switch name {
	case "ping":
		s.lastPingTime.Store(time.Now())
		return []byte(collector.PingInfo())
	case "heartbeat-timeout":
		parts := strings.SplitN(cmd, "\n", 2)
		if len(parts) < 2 {
			return []byte(fmt.Sprintf(`{"status":"ok","timeout_s":%d}`, s.heartbeatTimeout.Load()))
		}
		secs, err := strconv.ParseInt(strings.TrimSpace(parts[1]), 10, 64)
		if err != nil || secs < 0 {
			return []byte(`{"error":"invalid timeout value"}`)
		}
		s.heartbeatTimeout.Store(secs)
		s.lastPingTime.Store(time.Now())
		log.Printf("[heartbeat] timeout set to %ds (0=disabled)", secs)
		return []byte(fmt.Sprintf(`{"status":"ok","timeout_s":%d}`, secs))
	case "daemon-version":
		return []byte(collector.VersionJSON())
	case "monitor":
		return jsonBytes(s.collector.GetSnapshot())
	case "sample-interval":
		parts := strings.SplitN(cmd, "\n", 2)
		if len(parts) < 2 {
			return []byte(fmt.Sprintf(`{"status":"ok","interval_ms":%d}`, collector.GetSampleInterval()))
		}
		ms, err := strconv.ParseInt(strings.TrimSpace(parts[1]), 10, 64)
		if err != nil {
			return []byte(fmt.Sprintf(`{"error":"invalid interval: %s"}`, strings.TrimSpace(parts[1])))
		}
		collector.SetSampleInterval(ms)
		return []byte(fmt.Sprintf(`{"status":"ok","interval_ms":%d}`, collector.GetSampleInterval()))
	case "log-level":
		parts := strings.SplitN(cmd, "\n", 2)
		if len(parts) < 2 {
			return []byte(`{"error":"usage: log-level\\n<debug|info|warning|error>"}`)
		}
		level, ok := collector.ParseLogLevel(parts[1])
		if !ok {
			return []byte(fmt.Sprintf(`{"error":"unknown level: %s"}`, strings.TrimSpace(parts[1])))
		}
		collector.SetLogLevel(level)
		return []byte(fmt.Sprintf(`{"status":"ok","level":"%s"}`, collector.LevelName(level)))
	case "watchdog-start":
		started := s.watchdog.Start()
		return []byte(fmt.Sprintf(`{"status":"ok","watchdog":true,"changed":%v}`, started))
	case "watchdog-stop":
		stopped := s.watchdog.Stop()
		return []byte(fmt.Sprintf(`{"status":"ok","watchdog":false,"changed":%v}`, stopped))
	case "watchdog-status":
		return []byte(fmt.Sprintf(`{"watchdog":%v}`, s.watchdog.IsRunning()))
	case "clear-log":
		p := daemon.LogPath
		if p == "" {
			return []byte(`{"error":"log path not set"}`)
		}
		if err := os.Truncate(p, 0); err != nil {
			return []byte(fmt.Sprintf(`{"error":"%s"}`, err.Error()))
		}
		log.Printf("[server] log cleared by client")
		return []byte(`{"status":"ok"}`)
	case "daemon-exit":
		log.Printf("[server] daemon-exit received, scheduling shutdown")
		s.watchdog.Stop()
		go func() {
			time.Sleep(100 * time.Millisecond)
			s.Shutdown()
		}()
		return []byte(`{"status":"exiting"}`)
	default:
		return []byte(fmt.Sprintf(`{"error":"unknown command: %s"}`, name))
	}
}

func jsonBytes(v any) []byte {
	b, err := json.Marshal(v)
	if err != nil {
		return []byte(`{"error":"json marshal failed"}`)
	}
	return b
}
