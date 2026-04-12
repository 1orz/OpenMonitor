package collector

import (
	"bufio"
	"os"
	"strconv"
	"strings"
)

// sysFile keeps a sysfs file open for efficient repeated reads.
// Uses Seek(0)+Read instead of open/read/close per sample cycle.
type sysFile struct {
	f   *os.File
	buf []byte
}

func openSysFile(path string) *sysFile {
	f, err := os.Open(path)
	if err != nil {
		return nil
	}
	return &sysFile{f: f, buf: make([]byte, 128)}
}

func (s *sysFile) readInt() (int, bool) {
	if s == nil {
		return 0, false
	}
	if _, err := s.f.Seek(0, 0); err != nil {
		return 0, false
	}
	n, err := s.f.Read(s.buf)
	if err != nil || n == 0 {
		return 0, false
	}
	v, err := strconv.Atoi(strings.TrimSpace(string(s.buf[:n])))
	return v, err == nil
}

func (s *sysFile) readString() string {
	if s == nil {
		return ""
	}
	if _, err := s.f.Seek(0, 0); err != nil {
		return ""
	}
	n, _ := s.f.Read(s.buf)
	if n == 0 {
		return ""
	}
	return strings.TrimSpace(string(s.buf[:n]))
}

func (s *sysFile) close() {
	if s != nil && s.f != nil {
		s.f.Close()
	}
}

// procFile keeps a /proc file open for efficient repeated scanner-based reads.
// Supports Seek(0) to re-read fresh kernel-generated content each cycle.
type procFile struct {
	f *os.File
}

func openProcFile(path string) *procFile {
	f, err := os.Open(path)
	if err != nil {
		return nil
	}
	return &procFile{f: f}
}

func (p *procFile) newScanner() *bufio.Scanner {
	if p == nil {
		return nil
	}
	p.f.Seek(0, 0)
	return bufio.NewScanner(p.f)
}

func (p *procFile) close() {
	if p != nil && p.f != nil {
		p.f.Close()
	}
}
