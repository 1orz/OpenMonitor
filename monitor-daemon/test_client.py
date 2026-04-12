#!/usr/bin/env python3
"""Quick test client for monitor-daemon protocol.
Usage:
  python3 test_client.py                          # default host, all commands
  python3 test_client.py 192.168.5.220            # specific host, all commands
  python3 test_client.py 192.168.5.220 monitor-fps  # specific host + command
"""

import socket
import struct
import json
import sys

# First arg is host if it contains a dot, otherwise treat as command
_args = sys.argv[1:]
HOST = _args.pop(0) if _args and '.' in _args[0] else "192.168.5.220"
PORT = 9876


def send_cmd(sock, cmd: str) -> dict | str:
    data = cmd.encode()
    sock.sendall(struct.pack(">I", len(data)) + data)
    length = struct.unpack(">I", _recv_exact(sock, 4))[0]
    body = _recv_exact(sock, length)
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        return body.decode()


def _recv_exact(sock, n: int) -> bytes:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("connection closed")
        buf += chunk
    return buf


COMMANDS = ["ping", "daemon-version", "monitor-mini", "monitor-system", "monitor-fps"]

print(f"Connecting to {HOST}:{PORT}")
with socket.create_connection((HOST, PORT), timeout=5) as s:
    cmds = _args if _args else COMMANDS
    for cmd in cmds:
        resp = send_cmd(s, cmd)
        print(f"\n=== {cmd} ===")
        if isinstance(resp, dict):
            print(json.dumps(resp, indent=2, ensure_ascii=False))
        else:
            print(resp)
