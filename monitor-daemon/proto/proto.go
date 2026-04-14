package proto

import (
	"encoding/binary"
	"io"
	"net"
)

// WriteFrame writes a 4-byte big-endian length prefix followed by payload.
func WriteFrame(w io.Writer, payload []byte) error {
	if err := binary.Write(w, binary.BigEndian, uint32(len(payload))); err != nil {
		return err
	}
	_, err := w.Write(payload)
	return err
}

// ReadFrame reads a 4-byte big-endian length prefix then payload (limit 1 MB).
func ReadFrame(r io.Reader) ([]byte, error) {
	var msgLen uint32
	if err := binary.Read(r, binary.BigEndian, &msgLen); err != nil {
		return nil, err
	}
	if msgLen == 0 || msgLen > 1<<20 {
		return nil, io.ErrUnexpectedEOF
	}
	buf := make([]byte, msgLen)
	if _, err := io.ReadFull(r, buf); err != nil {
		return nil, err
	}
	return buf, nil
}

// SendCmd sends cmd over conn and returns the response bytes.
func SendCmd(conn net.Conn, cmd string) ([]byte, error) {
	if err := WriteFrame(conn, []byte(cmd)); err != nil {
		return nil, err
	}
	return ReadFrame(conn)
}
