#!/usr/bin/env bash
# ==============================================================================
# setup-aarch64-build-env.sh
#
# Sets up a complete Android build environment on Linux aarch64 (e.g. Termux proot).
# Google only ships x86_64 Linux SDK tools, so this script:
#   1. Installs JDK 17 + Go (official aarch64 binaries)
#   2. Installs Android SDK (cmdline-tools, platforms, build-tools, NDK)
#   3. Replaces x86_64 build-tools with community aarch64 static binaries
#   4. Replaces x86_64 NDK clang/lld with thin wrappers → Termux native compiler
#   5. Installs native CMake + ninja
#
# Prerequisites:
#   - Termux proot-distro (Ubuntu) on aarch64
#   - Termux packages: clang, lld, binutils (for llvm-ar, llvm-strip, etc.)
#   - curl, unzip, git
#
# Usage:
#   bash scripts/setup-aarch64-build-env.sh
#
# After setup, build with:
#   export JAVA_HOME=$HOME/tools/jdk-17.0.2
#   export ANDROID_HOME=$HOME/android-sdk
#   export PATH=$JAVA_HOME/bin:$HOME/tools/go/bin:$PATH
#   ./gradlew assembleDebug --no-daemon
# ==============================================================================
set -euo pipefail

TOOLS_DIR="$HOME/tools"
SDK_DIR="$HOME/android-sdk"
TERMUX_BIN="/data/data/com.termux/files/usr/bin"
TERMUX_APT_OPTS="-o Dir::Etc=$TERMUX_BIN/../etc/apt -o Dir::State=$TERMUX_BIN/../var/lib/apt -o Dir::Cache=$TERMUX_BIN/../var/cache/apt"

msg()  { printf "\n\033[1;32m==> %s\033[0m\n" "$1"; }
err()  { printf "\033[1;31mERROR: %s\033[0m\n" "$1" >&2; exit 1; }

# ── Checks ───────────────────────────────────────────────────────────────────
[ "$(uname -m)" = "aarch64" ] || err "This script is for aarch64 only"
command -v curl >/dev/null   || err "curl is required"
command -v unzip >/dev/null  || err "unzip is required"
[ -x "$TERMUX_BIN/clang" ]  || err "Termux clang is required (pkg install clang)"
[ -x "$TERMUX_BIN/lld" ]    || err "Termux lld is required (pkg install lld)"

mkdir -p "$TOOLS_DIR" "$SDK_DIR"

# ── 1. JDK 17 ───────────────────────────────────────────────────────────────
if [ ! -d "$TOOLS_DIR/jdk-17.0.2" ]; then
  msg "Installing JDK 17"
  curl -fSL -o /tmp/jdk17.tar.gz \
    "https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_linux-aarch64_bin.tar.gz"
  tar xzf /tmp/jdk17.tar.gz -C "$TOOLS_DIR"
  rm /tmp/jdk17.tar.gz
fi
export JAVA_HOME="$TOOLS_DIR/jdk-17.0.2"
export PATH="$JAVA_HOME/bin:$PATH"
java -version 2>&1 | head -1

# ── 2. Go ────────────────────────────────────────────────────────────────────
if [ ! -d "$TOOLS_DIR/go" ]; then
  msg "Installing Go"
  curl -fSL -o /tmp/go.tar.gz "https://go.dev/dl/go1.23.4.linux-arm64.tar.gz"
  tar xzf /tmp/go.tar.gz -C "$TOOLS_DIR"
  rm /tmp/go.tar.gz
fi
export PATH="$TOOLS_DIR/go/bin:$PATH"
go version

# ── 3. Android SDK (cmdline-tools) ──────────────────────────────────────────
if [ ! -d "$SDK_DIR/cmdline-tools/latest" ]; then
  msg "Installing Android cmdline-tools"
  curl -fSL -o /tmp/cmdline-tools.zip \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  mkdir -p /tmp/cmdline-tmp
  unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tmp
  mkdir -p "$SDK_DIR/cmdline-tools"
  mv /tmp/cmdline-tmp/cmdline-tools "$SDK_DIR/cmdline-tools/latest"
  rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tmp
fi

export ANDROID_HOME="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$PATH"

# Accept licenses & install components
yes | sdkmanager --licenses >/dev/null 2>&1 || true
msg "Installing SDK platforms, build-tools, NDK"
sdkmanager "platforms;android-36" "platforms;android-35" \
           "build-tools;36.0.0" "platform-tools" \
           "ndk;28.2.13676358" 2>&1 | grep -E "Installing|done" || true

# Write local.properties
echo "sdk.dir=$SDK_DIR" > "$(dirname "$0")/../local.properties"

# ── 4. Replace x86_64 build-tools with aarch64 static binaries ──────────────
msg "Replacing build-tools with aarch64 native binaries"
BUILD_TOOLS_URL="https://github.com/lzhiyong/android-sdk-tools/releases/download/35.0.2/android-sdk-tools-static-aarch64.zip"
if ! file "$SDK_DIR/build-tools/36.0.0/aapt2" 2>/dev/null | grep -q "aarch64"; then
  curl -fSL -o /tmp/sdk-tools-aarch64.zip "$BUILD_TOOLS_URL"
  TMP_EXTRACT=/tmp/sdk-tools-aarch64
  mkdir -p "$TMP_EXTRACT"
  unzip -o /tmp/sdk-tools-aarch64.zip -d "$TMP_EXTRACT"
  for bin in aapt aapt2 aidl zipalign dexdump split-select; do
    [ -f "$TMP_EXTRACT/build-tools/$bin" ] && cp "$TMP_EXTRACT/build-tools/$bin" "$SDK_DIR/build-tools/36.0.0/$bin"
  done
  chmod +x "$SDK_DIR/build-tools/36.0.0/"*
  rm -rf /tmp/sdk-tools-aarch64.zip "$TMP_EXTRACT"
fi

# Also replace aapt2 in AGP's Maven cache (AGP downloads its own aapt2 jar)
msg "Patching AGP cached aapt2"
AAPT2_JAR=$(find "$HOME/.gradle/caches/modules-2" -name "aapt2-*-linux.jar" 2>/dev/null | head -1)
if [ -n "$AAPT2_JAR" ]; then
  TMP_JAR=/tmp/aapt2-jar-patch
  mkdir -p "$TMP_JAR"
  cp "$SDK_DIR/build-tools/36.0.0/aapt2" "$TMP_JAR/aapt2"
  (cd "$TMP_JAR" && jar uf "$AAPT2_JAR" aapt2)
  rm -rf "$HOME/.gradle/caches/"*/transforms/
  rm -rf "$TMP_JAR"
fi

# ── 5. Native CMake + ninja ─────────────────────────────────────────────────
CMAKE_VER="3.31.6"
if [ ! -f "$TOOLS_DIR/cmake-${CMAKE_VER}-linux-aarch64/bin/cmake" ]; then
  msg "Installing CMake $CMAKE_VER (aarch64)"
  curl -fSL -o /tmp/cmake.tar.gz \
    "https://github.com/Kitware/CMake/releases/download/v${CMAKE_VER}/cmake-${CMAKE_VER}-linux-aarch64.tar.gz"
  tar xzf /tmp/cmake.tar.gz -C "$TOOLS_DIR"
  rm /tmp/cmake.tar.gz
fi

# Install into SDK cmake directory (Gradle expects cmake in SDK)
msg "Configuring SDK CMake"
SDK_CMAKE="$SDK_DIR/cmake/3.22.1"
mkdir -p "$SDK_CMAKE/bin"
cp "$TOOLS_DIR/cmake-${CMAKE_VER}-linux-aarch64/bin/cmake" "$SDK_CMAKE/bin/"
cp "$TOOLS_DIR/cmake-${CMAKE_VER}-linux-aarch64/bin/ctest" "$SDK_CMAKE/bin/"
cp "$TOOLS_DIR/cmake-${CMAKE_VER}-linux-aarch64/bin/cpack" "$SDK_CMAKE/bin/"
rm -rf "$SDK_CMAKE/share"
cp -r "$TOOLS_DIR/cmake-${CMAKE_VER}-linux-aarch64/share" "$SDK_CMAKE/share"

# Ninja from Termux
msg "Installing ninja"
NINJA_DEB=$(find /tmp -maxdepth 1 -name "ninja_*.deb" 2>/dev/null | head -1)
if [ -z "$NINJA_DEB" ]; then
  (cd /tmp && $TERMUX_BIN/apt $TERMUX_APT_OPTS download ninja 2>/dev/null)
  NINJA_DEB=$(find /tmp -maxdepth 1 -name "ninja_*.deb" | head -1)
fi
if [ -n "$NINJA_DEB" ]; then
  NINJA_TMP=/tmp/ninja-extract
  mkdir -p "$NINJA_TMP"
  dpkg-deb -x "$NINJA_DEB" "$NINJA_TMP"
  # Also get libandroid-spawn dep
  (cd /tmp && $TERMUX_BIN/apt $TERMUX_APT_OPTS download libandroid-spawn 2>/dev/null)
  SPAWN_DEB=$(find /tmp -maxdepth 1 -name "libandroid-spawn_*.deb" | head -1)
  [ -n "$SPAWN_DEB" ] && dpkg-deb -x "$SPAWN_DEB" "$NINJA_TMP"

  cat > "$SDK_CMAKE/bin/ninja" << WRAPPER
#!/bin/sh
export LD_LIBRARY_PATH=$NINJA_TMP/$TERMUX_BIN/../lib:\$LD_LIBRARY_PATH
exec $NINJA_TMP/$TERMUX_BIN/ninja "\$@"
WRAPPER
  chmod +x "$SDK_CMAKE/bin/ninja"
fi

# ── 6. NDK clang/lld wrappers → Termux native compiler ──────────────────────
setup_ndk_wrappers() {
  local NDK_DIR="$1"
  local NDK_BIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin"
  local NDK_SYSROOT="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/sysroot"

  [ -d "$NDK_BIN" ] || return

  # Detect clang version in this NDK
  local CLANG_VER
  CLANG_VER=$(ls "$NDK_BIN"/clang-[0-9]* 2>/dev/null | head -1 | grep -oP 'clang-\K[0-9]+')
  [ -z "$CLANG_VER" ] && return

  local RESDIR="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/lib/clang/$CLANG_VER"

  msg "Patching NDK wrappers: $(basename "$NDK_DIR") (clang-$CLANG_VER)"

  # Backup & replace clang
  [ -f "$NDK_BIN/clang-${CLANG_VER}.x86_64.bak" ] || \
    mv "$NDK_BIN/clang-$CLANG_VER" "$NDK_BIN/clang-${CLANG_VER}.x86_64.bak" 2>/dev/null || true

  # Remove symlinks, create fresh scripts
  rm -f "$NDK_BIN/clang" "$NDK_BIN/clang++" "$NDK_BIN/clang-$CLANG_VER"

  # C compiler
  for f in "clang-$CLANG_VER" "clang"; do
    cat > "$NDK_BIN/$f" << WRAPPER
#!/bin/sh
exec $TERMUX_BIN/clang -resource-dir=$RESDIR "\$@"
WRAPPER
    chmod +x "$NDK_BIN/$f"
  done

  # C++ compiler (translates -static-libstdc++ to NDK-compatible flags)
  cat > "$NDK_BIN/clang++" << 'OUTER'
#!/bin/sh
WRAPPER_RESDIR=RESDIR_PLACEHOLDER
WRAPPER_CLANGXX=CLANGXX_PLACEHOLDER
args=""
for arg in "$@"; do
  case "$arg" in
    -static-libstdc++) args="$args -nostdlib++ -lc++_static -lc++abi" ;;
    *) args="$args $arg" ;;
  esac
done
exec $WRAPPER_CLANGXX -resource-dir=$WRAPPER_RESDIR $args
OUTER
  sed -i "s|RESDIR_PLACEHOLDER|$RESDIR|;s|CLANGXX_PLACEHOLDER|$TERMUX_BIN/clang++|" "$NDK_BIN/clang++"
  chmod +x "$NDK_BIN/clang++"

  # Replace lld and llvm-* tools
  for tool in lld llvm-ar llvm-strip llvm-objcopy llvm-nm llvm-readelf llvm-objdump llvm-ranlib; do
    [ -f "$NDK_BIN/$tool" ] || continue
    file "$NDK_BIN/$tool" 2>/dev/null | grep -qE "x86-64|symbolic link" || continue
    rm -f "$NDK_BIN/$tool"
    cat > "$NDK_BIN/$tool" << WRAPPER
#!/bin/sh
exec $TERMUX_BIN/$tool "\$@"
WRAPPER
    chmod +x "$NDK_BIN/$tool"
  done
}

# Apply to all installed NDK versions
for ndk_dir in "$SDK_DIR"/ndk/*/; do
  [ -d "$ndk_dir" ] && setup_ndk_wrappers "$ndk_dir"
done

# ── Done ─────────────────────────────────────────────────────────────────────
msg "Setup complete!"
echo ""
echo "Add to your shell profile:"
echo "  export JAVA_HOME=$TOOLS_DIR/jdk-17.0.2"
echo "  export ANDROID_HOME=$SDK_DIR"
echo '  export PATH=$JAVA_HOME/bin:'"$TOOLS_DIR"'/go/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH'
echo ""
echo "Then build:"
echo "  ./gradlew assembleDebug --no-daemon"
