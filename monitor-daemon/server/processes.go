package server

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"
)

// ProcessEntry is the full process info returned by the "processes" command.
type ProcessEntry struct {
	PID          int     `json:"pid"`
	PPID         int     `json:"ppid"`
	Name         string  `json:"name"`
	State        string  `json:"state"`
	User         string  `json:"user"`
	CPUPercent   float64 `json:"cpu_percent"`
	RSSKB        int64   `json:"rss_kb"`
	SwapKB       int64   `json:"swap_kb"`
	ShrKB        int64   `json:"shr_kb"`
	Cmdline      string  `json:"cmdline"`
	OomAdj       int     `json:"oom_adj"`
	OomScore     int     `json:"oom_score"`
	OomScoreAdj  int     `json:"oom_score_adj"`
	CGroup       string  `json:"cgroup"`
	CpuSet       string  `json:"cpu_set"`
	CpusAllowed  string  `json:"cpus_allowed"`
	CtxtSwitches int64   `json:"ctxt_switches"`
}

// ThreadEntry is the thread info returned by the "threads/<pid>" command.
type ThreadEntry struct {
	TID        int     `json:"tid"`
	Name       string  `json:"name"`
	CPUPercent float64 `json:"cpu_percent"`
}

// procTicks holds utime+stime ticks for a process or thread.
type procTicks struct {
	utime, stime uint64
}

// rawProcData holds the raw file contents read for a single process.
type rawProcData struct {
	status      map[string]string
	cmdline     string
	oomAdj      string
	oomScore    string
	oomScoreAdj string
	cgroup      string
	statmShared int64 // shared pages from /proc/<pid>/statm field[2]
}

// readTotalCPUTicks reads /proc/stat and returns the sum of all CPU ticks across all cores.
func readTotalCPUTicks() uint64 {
	data, err := os.ReadFile("/proc/stat")
	if err != nil {
		return 0
	}
	for _, line := range strings.SplitN(string(data), "\n", 3) {
		if strings.HasPrefix(line, "cpu ") {
			var total uint64
			for _, f := range strings.Fields(line)[1:] {
				v, _ := strconv.ParseUint(f, 10, 64)
				total += v
			}
			return total
		}
	}
	return 0
}

// readStatTicks reads utime+stime from /proc/<pid>/stat (or /proc/<pid>/task/<tid>/stat).
// The comm field may contain spaces and parentheses, so we find the last ')' to locate field 3.
func readStatTicks(statPath string) (utime, stime uint64) {
	data, err := os.ReadFile(statPath)
	if err != nil {
		return 0, 0
	}
	s := string(data)
	lastParen := strings.LastIndex(s, ")")
	if lastParen < 0 || lastParen+1 >= len(s) {
		return 0, 0
	}
	// After last ')': " state ppid pgrp session tty_nr tpgid flags minflt cminflt majflt cmajflt utime stime ..."
	// 0-indexed: [0]=state [1]=ppid ... [11]=utime [12]=stime
	fields := strings.Fields(s[lastParen+1:])
	if len(fields) < 13 {
		return 0, 0
	}
	utime, _ = strconv.ParseUint(fields[11], 10, 64)
	stime, _ = strconv.ParseUint(fields[12], 10, 64)
	return utime, stime
}

// readProcStatus parses /proc/<pid>/status into a key→value map.
func readProcStatus(pid int) map[string]string {
	data, err := os.ReadFile(fmt.Sprintf("/proc/%d/status", pid))
	if err != nil {
		return nil
	}
	m := make(map[string]string, 32)
	for _, line := range strings.Split(string(data), "\n") {
		if i := strings.IndexByte(line, ':'); i > 0 {
			m[strings.TrimSpace(line[:i])] = strings.TrimSpace(line[i+1:])
		}
	}
	return m
}

// readProcFile reads a small /proc file and returns its trimmed content (first line only).
func readProcFile(path string) string {
	data, err := os.ReadFile(path)
	if err != nil {
		return ""
	}
	s := strings.TrimRight(string(data), "\n\r ")
	if i := strings.IndexByte(s, '\n'); i >= 0 {
		return s[:i]
	}
	return s
}

// readCgroup reads /proc/<pid>/cgroup and returns the cpuset path (e.g. "foreground"),
// falling back to the full first line if cpuset is not found.
func readCgroup(pid int) string {
	data, err := os.ReadFile(fmt.Sprintf("/proc/%d/cgroup", pid))
	if err != nil {
		return ""
	}
	// Each line: "<id>:<subsystems>:<path>"
	// Prefer the cpuset line as it is most informative (foreground/background/restricted).
	firstLine := ""
	for _, line := range strings.Split(strings.TrimRight(string(data), "\n"), "\n") {
		parts := strings.SplitN(line, ":", 3)
		if len(parts) < 3 {
			continue
		}
		if firstLine == "" {
			firstLine = line
		}
		if strings.Contains(parts[1], "cpuset") {
			return line
		}
	}
	return firstLine
}

// readStatmShared reads /proc/<pid>/statm and returns the shared-pages field (index 2).
func readStatmShared(pid int) int64 {
	data, err := os.ReadFile(fmt.Sprintf("/proc/%d/statm", pid))
	if err != nil {
		return 0
	}
	fields := strings.Fields(string(data))
	if len(fields) < 3 {
		return 0
	}
	v, _ := strconv.ParseInt(fields[2], 10, 64)
	return v * 4 // pages → kB (assuming 4 kB pages)
}

// readRawProcData reads all supplementary files for a process concurrently.
func readRawProcData(pid int) rawProcData {
	base := fmt.Sprintf("/proc/%d", pid)
	return rawProcData{
		status:      readProcStatus(pid),
		cmdline:     readProcFile(base + "/cmdline"),
		oomAdj:      readProcFile(base + "/oom_adj"),
		oomScore:    readProcFile(base + "/oom_score"),
		oomScoreAdj: readProcFile(base + "/oom_score_adj"),
		cgroup:      readCgroup(pid),
		statmShared: readStatmShared(pid),
	}
}

// parseKBField parses a /proc/status value like "1234 kB" and returns the kB value.
func parseKBField(s string) int64 {
	if s == "" {
		return 0
	}
	v, _ := strconv.ParseInt(strings.Fields(s)[0], 10, 64)
	return v
}

// androidUIDToUser maps Android numeric UIDs to human-readable names.
func androidUIDToUser(uid int) string {
	switch uid {
	case 0:
		return "root"
	case 1000:
		return "system"
	case 1001:
		return "radio"
	case 2000:
		return "shell"
	case 9999:
		return "nobody"
	default:
		if uid >= 10000 && uid < 20000 {
			return fmt.Sprintf("u0_a%d", uid-10000)
		}
		return strconv.Itoa(uid)
	}
}

// listProcesses enumerates all processes from /proc and returns their info with current CPU%.
// Uses two samples 200 ms apart to compute per-process CPU utilization.
func listProcesses() []ProcessEntry {
	// Enumerate all numeric /proc entries (PIDs).
	entries, err := os.ReadDir("/proc")
	if err != nil {
		return []ProcessEntry{}
	}
	pids := make([]int, 0, len(entries))
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		pid, err := strconv.Atoi(e.Name())
		if err != nil {
			continue
		}
		pids = append(pids, pid)
	}

	// Sample 1: system-wide total + per-process ticks.
	totalTicks1 := readTotalCPUTicks()
	ticks1 := make(map[int]procTicks, len(pids))
	for _, pid := range pids {
		u, s := readStatTicks(fmt.Sprintf("/proc/%d/stat", pid))
		ticks1[pid] = procTicks{u, s}
	}

	// Read all supplementary files concurrently during the sleep.
	raw := make(map[int]rawProcData, len(pids))
	var mu sync.Mutex
	var wg sync.WaitGroup
	for _, pid := range pids {
		wg.Add(1)
		go func(p int) {
			defer wg.Done()
			r := readRawProcData(p)
			mu.Lock()
			raw[p] = r
			mu.Unlock()
		}(pid)
	}

	time.Sleep(200 * time.Millisecond)
	wg.Wait()

	// Sample 2: system-wide total + per-process ticks.
	totalTicks2 := readTotalCPUTicks()
	ticks2 := make(map[int]procTicks, len(pids))
	for _, pid := range pids {
		u, s := readStatTicks(fmt.Sprintf("/proc/%d/stat", pid))
		ticks2[pid] = procTicks{u, s}
	}

	totalDelta := totalTicks2 - totalTicks1
	if totalDelta == 0 {
		totalDelta = 1
	}

	result := make([]ProcessEntry, 0, len(pids))
	for _, pid := range pids {
		r := raw[pid]
		if r.status == nil {
			continue // process disappeared
		}

		// CPU%.
		t1, t2 := ticks1[pid], ticks2[pid]
		processDelta := (t2.utime + t2.stime) - (t1.utime + t1.stime)
		cpuPercent := float64(processDelta) / float64(totalDelta) * 100.0

		// Basic fields from /proc/<pid>/status.
		name := r.status["Name"]
		stateStr := r.status["State"]
		state := "?"
		if len(stateStr) > 0 {
			state = string(stateStr[0])
		}
		ppid, _ := strconv.Atoi(r.status["PPid"])

		uid := 0
		if uidField := r.status["Uid"]; uidField != "" {
			if fields := strings.Fields(uidField); len(fields) > 0 {
				uid, _ = strconv.Atoi(fields[0])
			}
		}
		user := androidUIDToUser(uid)

		rssKB := parseKBField(r.status["VmRSS"])
		swapKB := parseKBField(r.status["VmSwap"])

		// Context switches (voluntary only).
		ctxtSwitches, _ := strconv.ParseInt(r.status["voluntary_ctxt_switches"], 10, 64)

		// Cmdline: null-separated args → space-separated, trimmed.
		cmdline := strings.TrimRight(strings.ReplaceAll(r.cmdline, "\x00", " "), " ")

		oomAdj, _ := strconv.Atoi(r.oomAdj)
		oomScore, _ := strconv.Atoi(r.oomScore)
		oomScoreAdj, _ := strconv.Atoi(r.oomScoreAdj)

		cpuSet := r.status["Cpus_allowed_list"]

		result = append(result, ProcessEntry{
			PID:          pid,
			PPID:         ppid,
			Name:         name,
			State:        state,
			User:         user,
			CPUPercent:   cpuPercent,
			RSSKB:        rssKB,
			SwapKB:       swapKB,
			ShrKB:        r.statmShared,
			Cmdline:      cmdline,
			OomAdj:       oomAdj,
			OomScore:     oomScore,
			OomScoreAdj:  oomScoreAdj,
			CGroup:       r.cgroup,
			CpuSet:       cpuSet,
			CpusAllowed:  cpuSet,
			CtxtSwitches: ctxtSwitches,
		})
	}

	return result
}

// listThreads enumerates all threads of pid from /proc/<pid>/task and returns their info
// with current CPU% computed from two samples 200 ms apart.
func listThreads(pid int) []ThreadEntry {
	taskDir := fmt.Sprintf("/proc/%d/task", pid)
	entries, err := os.ReadDir(taskDir)
	if err != nil {
		return []ThreadEntry{}
	}

	tids := make([]int, 0, len(entries))
	for _, e := range entries {
		tid, err := strconv.Atoi(e.Name())
		if err != nil {
			continue
		}
		tids = append(tids, tid)
	}
	if len(tids) == 0 {
		return []ThreadEntry{}
	}

	// Sample 1.
	totalTicks1 := readTotalCPUTicks()
	ticks1 := make(map[int]procTicks, len(tids))
	for _, tid := range tids {
		u, s := readStatTicks(fmt.Sprintf("%s/%d/stat", taskDir, tid))
		ticks1[tid] = procTicks{u, s}
	}

	time.Sleep(200 * time.Millisecond)

	// Sample 2.
	totalTicks2 := readTotalCPUTicks()
	ticks2 := make(map[int]procTicks, len(tids))
	for _, tid := range tids {
		u, s := readStatTicks(fmt.Sprintf("%s/%d/stat", taskDir, tid))
		ticks2[tid] = procTicks{u, s}
	}

	totalDelta := totalTicks2 - totalTicks1
	if totalDelta == 0 {
		totalDelta = 1
	}

	result := make([]ThreadEntry, 0, len(tids))
	for _, tid := range tids {
		t1, t2 := ticks1[tid], ticks2[tid]
		delta := (t2.utime + t2.stime) - (t1.utime + t1.stime)
		cpuPercent := float64(delta) / float64(totalDelta) * 100.0

		name := readProcFile(fmt.Sprintf("%s/%d/comm", taskDir, tid))

		result = append(result, ThreadEntry{
			TID:        tid,
			Name:       name,
			CPUPercent: cpuPercent,
		})
	}

	return result
}

// doKillProcess sends SIGKILL to the given PID.
func doKillProcess(pid int) error {
	return syscall.Kill(pid, syscall.SIGKILL)
}
