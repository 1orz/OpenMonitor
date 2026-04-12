#!/bin/bash
set -e

DAEMON=monitor-daemon
CLIENT=mcli
DEST_DIR=/data/local/tmp
PORT=9876

cd "$(dirname "$0")"

GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
LDFLAGS="-s -w -X monitor-daemon/collector.GitCommit=${GIT_COMMIT}"

# Output paths
JNILIB_DIR="../app/src/main/jniLibs/arm64-v8a"
ASSETS_DIR="../app/src/main/assets/daemon"

echo ">>> Building daemon for Android arm64 (commit=$GIT_COMMIT)..."
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build -ldflags "$LDFLAGS" -o "$DAEMON" ./cmd/daemon

# Package as native lib (like Shizuku: libshizuku.so → libmonitor-daemon.so)
mkdir -p "$JNILIB_DIR"
cp "$DAEMON" "$JNILIB_DIR/libmonitor-daemon.so"

# Keep commit file in assets for version checking
mkdir -p "$ASSETS_DIR"
echo "$GIT_COMMIT" > "$ASSETS_DIR/daemon-commit.txt"
# Remove old asset binary if present
rm -f "$ASSETS_DIR/$DAEMON"

echo ">>> Updated: $JNILIB_DIR/libmonitor-daemon.so + $ASSETS_DIR/daemon-commit.txt"

echo ">>> Building client for Android arm64..."
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build -ldflags "$LDFLAGS" -o "$CLIENT" ./cmd/client

echo ">>> Building client for macOS (native)..."
go build -ldflags "$LDFLAGS" -o mcli-mac ./cmd/client

# Select target device
if [ -n "$1" ]; then
    DEVICE="$1"
else
    DEVICES=()
    while IFS= read -r line; do DEVICES+=("$line"); done < <(adb devices | awk -F'\t' 'NR>1 && $2=="device" {print $1}')
    if [ ${#DEVICES[@]} -eq 0 ]; then
        echo ""; echo ">>> No devices connected, skipping push."
        echo "    Built: $DAEMON, $CLIENT, mcli-mac"
        exit 0
    elif [ ${#DEVICES[@]} -eq 1 ]; then
        DEVICE="${DEVICES[0]}"
    else
        echo ""
        echo ">>> Multiple devices detected:"
        for i in "${!DEVICES[@]}"; do
            model=$(adb -s "${DEVICES[$i]}" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
            echo "    [$i] ${DEVICES[$i]}  ($model)"
        done
        printf ">>> Select device [0-%d]: " $((${#DEVICES[@]}-1))
        read -r choice
        if [[ ! "$choice" =~ ^[0-9]+$ ]] || [ "$choice" -ge ${#DEVICES[@]} ]; then
            echo "Invalid choice"; exit 1
        fi
        DEVICE="${DEVICES[$choice]}"
    fi
fi

echo ""
echo ">>> Pushing to device $DEVICE..."
adb -s "$DEVICE" push "$DAEMON" "$DEST_DIR/$DAEMON"
adb -s "$DEVICE" push "$CLIENT" "$DEST_DIR/$CLIENT"
adb -s "$DEVICE" shell chmod 755 "$DEST_DIR/$DAEMON" "$DEST_DIR/$CLIENT"

echo ""
echo ">>> Done"
echo ""
echo "    Start (root):   adb -s $DEVICE shell su -c $DEST_DIR/$DAEMON"
echo "    Start (shell):  adb -s $DEVICE shell $DEST_DIR/$DAEMON"
echo "    Start (dev):    ./$DAEMON --no-detach"
echo "    Stop:           ./mcli-mac daemon-exit"
echo "    Forward port:   adb -s $DEVICE forward tcp:$PORT tcp:$PORT"
echo ""
echo "    On device:      $DEST_DIR/$CLIENT ping"
echo "    On macOS:       ./mcli-mac ping"
