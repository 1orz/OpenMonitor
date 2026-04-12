package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"net"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"monitor-daemon/proto"
)

func main() {
	addr := flag.String("addr", "127.0.0.1:9876", "daemon address (host:port)")
	watch := flag.Bool("watch", false, "stream updates at interval (Ctrl+C to quit)")
	interval := flag.Duration("interval", time.Second, "stream interval (e.g. 500ms, 2s)")
	flag.Usage = func() {
		fmt.Fprintf(os.Stderr, "Usage: %s [flags] <command> [arg]\n\n", os.Args[0])
		fmt.Fprintf(os.Stderr, "Flags:\n")
		flag.PrintDefaults()
		fmt.Fprintf(os.Stderr, "\nCommands:\n")
		fmt.Fprintf(os.Stderr, "  ping\n")
		fmt.Fprintf(os.Stderr, "  daemon-version\n")
		fmt.Fprintf(os.Stderr, "  monitor\n")
		fmt.Fprintf(os.Stderr, "\nExamples:\n")
		fmt.Fprintf(os.Stderr, "  %s monitor\n", os.Args[0])
		fmt.Fprintf(os.Stderr, "  %s -addr 192.168.1.100:9876 monitor\n", os.Args[0])
		fmt.Fprintf(os.Stderr, "  %s -watch monitor\n", os.Args[0])
		fmt.Fprintf(os.Stderr, "  %s -watch -interval 500ms monitor\n", os.Args[0])
	}
	flag.Parse()

	args := flag.Args()
	if len(args) < 1 {
		flag.Usage()
		os.Exit(1)
	}

	cmd := args[0]
	if len(args) >= 2 {
		cmd = cmd + "\n" + strings.Join(args[1:], " ")
	}

	conn, err := net.Dial("tcp", *addr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "connect %s: %v\n", *addr, err)
		os.Exit(1)
	}
	defer conn.Close()

	if *watch {
		runWatch(conn, cmd, *interval)
	} else {
		runOnce(conn, cmd)
	}
}

func runOnce(conn net.Conn, cmd string) {
	resp, err := proto.SendCmd(conn, cmd)
	if err != nil {
		fmt.Fprintf(os.Stderr, "send: %v\n", err)
		os.Exit(1)
	}
	printJSON(resp)
}

func runWatch(conn net.Conn, cmd string, interval time.Duration) {
	ms := int(interval.Milliseconds())
	if ms < 100 {
		ms = 100
	}
	streamCmd := fmt.Sprintf("stream:%d\n%s", ms, cmd)
	if err := proto.WriteFrame(conn, []byte(streamCmd)); err != nil {
		fmt.Fprintf(os.Stderr, "stream start: %v\n", err)
		os.Exit(1)
	}

	// Ctrl+C: send exit signal to daemon then quit.
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, os.Interrupt, syscall.SIGTERM)
	go func() {
		<-sigCh
		proto.WriteFrame(conn, []byte("@signal:exit"))
		time.Sleep(100 * time.Millisecond)
		fmt.Print("\033[?25h") // restore cursor
		os.Exit(0)
	}()

	fmt.Print("\033[?25l") // hide cursor
	defer fmt.Print("\033[?25h")

	for {
		frame, err := proto.ReadFrame(conn)
		if err != nil {
			fmt.Fprintf(os.Stderr, "\nstream ended: %v\n", err)
			return
		}
		fmt.Print("\033[2J\033[H")
		fmt.Printf("  monitor-daemon  %s  interval:%v  Ctrl+C to quit\n\n",
			time.Now().Format("15:04:05.000"), interval)
		printJSON(frame)
	}
}

func printJSON(data []byte) {
	var v any
	if json.Unmarshal(data, &v) == nil {
		out, _ := json.MarshalIndent(v, "", "  ")
		fmt.Println(string(out))
	} else {
		fmt.Println(string(data))
	}
}
