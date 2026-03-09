package server

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"strconv"
	"strings"
	"time"

	"monitor-daemon/collector"
	"monitor-daemon/proto"
)

// Server listens on TCP and handles daemon commands.
type Server struct {
	addr      string
	collector *collector.Collector
	ln        net.Listener
	done      chan struct{}
	watchdog  Watchdog
}

// New creates a Server bound to addr (e.g. "0.0.0.0:9876").
func New(addr string, c *collector.Collector) *Server {
	return &Server{addr: addr, collector: c, done: make(chan struct{})}
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
		// 1. Read framed message
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

		// 3. Handle @signal:exit
		if cmd == "@signal:exit" {
			return
		}

		// 4. Stream mode: "stream:<ms>\n<cmd>"
		if strings.HasPrefix(cmd, "stream:") {
			s.handleStream(conn, cmd)
			return
		}

		// 5. Dispatch and write response
		resp := s.dispatch(cmd)
		if err := proto.WriteFrame(conn, resp); err != nil {
			log.Printf("[conn] %s write: %v", remote, err)
			return
		}

		// 6. Post-dispatch: daemon-exit triggers shutdown after response is sent
		if strings.TrimSpace(strings.SplitN(cmd, "\n", 2)[0]) == "daemon-exit" {
			return
		}
	}
}

// handleStream pushes frames at the requested interval until the client disconnects
// or sends @signal:exit. Format: "stream:<ms>\n<inner_cmd>".
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

	// Adjust daemon sampling rate to match stream interval
	prevInterval := collector.GetSampleInterval()
	collector.SetSampleInterval(int64(intervalMs))

	ctx, cancel := context.WithCancel(context.Background())
	defer func() {
		cancel()
		collector.SetSampleInterval(prevInterval) // restore on disconnect
	}()

	// Goroutine: detect @signal:exit or client disconnect.
	conn.SetReadDeadline(time.Time{}) // no deadline during stream
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
		return []byte(collector.PingInfo())
	case "daemon-version":
		return []byte(collector.Version())
	case "monitor":
		return jsonBytes(s.collector.GetSnapshot())
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
	case "sample-interval":
		parts := strings.SplitN(cmd, "\n", 2)
		if len(parts) < 2 {
			return []byte(`{"error":"usage: sample-interval\\n<ms>"}`)
		}
		ms, err := strconv.ParseInt(strings.TrimSpace(parts[1]), 10, 64)
		if err != nil {
			return []byte(fmt.Sprintf(`{"error":"invalid interval: %s"}`, strings.TrimSpace(parts[1])))
		}
		collector.SetSampleInterval(ms)
		return []byte(fmt.Sprintf(`{"status":"ok","interval_ms":%d}`, collector.GetSampleInterval()))
	case "watchdog-start":
		started := s.watchdog.Start()
		return []byte(fmt.Sprintf(`{"status":"ok","watchdog":true,"changed":%v}`, started))
	case "watchdog-stop":
		stopped := s.watchdog.Stop()
		return []byte(fmt.Sprintf(`{"status":"ok","watchdog":false,"changed":%v}`, stopped))
	case "watchdog-status":
		return []byte(fmt.Sprintf(`{"watchdog":%v}`, s.watchdog.IsRunning()))
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
