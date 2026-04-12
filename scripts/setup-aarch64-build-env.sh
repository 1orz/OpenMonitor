#!/usr/bin/env bash
# ==============================================================================
# setup-aarch64-build-env.sh
#
# Sets up a complete Android build environment on Linux aarch64 (Termux proot).
# Google only ships x86_64 Linux SDK tools, so this script replaces them with
# community-built aarch64 alternatives. The project source code stays clean —
# all platform-specific workarounds live here.
#
# Prerequisites: Termux proot-distro (Ubuntu) with packages:
#   pkg install clang lld ndk-sysroot binutils-is-llvm
#
# Usage:
#   bash scripts/setup-aarch64-build-env.sh
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
TOOLS_DIR="$HOME/tools"
SDK_DIR="$HOME/android-sdk"
TERMUX_BIN="/data/data/com.termux/files/usr/bin"
TERMUX_APT="$TERMUX_BIN/apt -o Dir::Etc=$TERMUX_BIN/../etc/apt -o Dir::State=$TERMUX_BIN/../var/lib/apt -o Dir::Cache=$TERMUX_BIN/../var/cache/apt"

msg()  { printf "\n\033[1;32m==> %s\033[0m\n" "$1"; }
err()  { printf "\033[1;31mERROR: %s\033[0m\n" "$1" >&2; exit 1; }

[ "$(uname -m)" = "aarch64" ] || err "This script is for aarch64 only"
command -v curl  >/dev/null || err "curl required"
command -v unzip >/dev/null || err "unzip required"
[ -x "$TERMUX_BIN/clang" ] || err "Termux clang required (pkg install clang)"
[ -x "$TERMUX_BIN/lld" ]   || err "Termux lld required (pkg install lld)"

mkdir -p "$TOOLS_DIR" "$SDK_DIR"

# ── 1. JDK 17 ───────────────────────────────────────────────────────────────
if [ ! -d "$TOOLS_DIR/jdk-17.0.2" ]; then
  msg "Installing JDK 17"
  curl -fSL -o /tmp/jdk17.tar.gz \
    "https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_linux-aarch64_bin.tar.gz"
  tar xzf /tmp/jdk17.tar.gz -C "$TOOLS_DIR" && rm /tmp/jdk17.tar.gz
fi
export JAVA_HOME="$TOOLS_DIR/jdk-17.0.2"
export PATH="$JAVA_HOME/bin:$PATH"
msg "JDK: $(java -version 2>&1 | head -1)"

# ── 2. Go ────────────────────────────────────────────────────────────────────
if [ ! -d "$TOOLS_DIR/go" ]; then
  msg "Installing Go"
  curl -fSL -o /tmp/go.tar.gz "https://go.dev/dl/go1.23.4.linux-arm64.tar.gz"
  tar xzf /tmp/go.tar.gz -C "$TOOLS_DIR" && rm /tmp/go.tar.gz
fi
export PATH="$TOOLS_DIR/go/bin:$PATH"
msg "Go: $(go version)"

# ── 3. Android SDK ──────────────────────────────────────────────────────────
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

yes | sdkmanager --licenses >/dev/null 2>&1 || true
msg "Installing SDK components"
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools" \
           "ndk;28.2.13676358" 2>&1 | grep -E "Installing|done" || true

# ── 4. Replace x86_64 build-tools with aarch64 binaries ─────────────────────
msg "Replacing build-tools with aarch64 native binaries"
TOOLS_URL="https://github.com/1orz/android-sdk-tools/releases/download/35.0.2-aarch64/android-sdk-tools-aarch64.zip"
if ! file "$SDK_DIR/build-tools/36.0.0/aapt2" 2>/dev/null | grep -q "aarch64"; then
  curl -fSL -o /tmp/sdk-tools-aarch64.zip "$TOOLS_URL"
  TMP_EXTRACT=/tmp/sdk-tools-aarch64
  mkdir -p "$TMP_EXTRACT"
  unzip -o /tmp/sdk-tools-aarch64.zip -d "$TMP_EXTRACT"
  for bin in aapt aapt2 aidl zipalign dexdump split-select; do
    [ -f "$TMP_EXTRACT/$bin" ] && cp "$TMP_EXTRACT/$bin" "$SDK_DIR/build-tools/36.0.0/$bin"
  done
  chmod +x "$SDK_DIR/build-tools/36.0.0/"*
  rm -rf /tmp/sdk-tools-aarch64.zip "$TMP_EXTRACT"
fi

# ── 5. Native CMake + ninja ─────────────────────────────────────────────────
CMAKE_VER="3.31.6"
if [ ! -f "$TOOLS_DIR/cmake-${CMAKE_VER}-linux-aarch64/bin/cmake" ]; then
  msg "Installing CMake $CMAKE_VER"
  curl -fSL -o /tmp/cmake.tar.gz \
    "https://github.com/Kitware/CMake/releases/download/v${CMAKE_VER}/cmake-${CMAKE_VER}-linux-aarch64.tar.gz"
  tar xzf /tmp/cmake.tar.gz -C "$TOOLS_DIR" && rm /tmp/cmake.tar.gz
fi

SDK_CMAKE="$SDK_DIR/cmake/3.22.1"
mkdir -p "$SDK_CMAKE/bin"
cp "$TOOLS_DIR/cmake-${CMAKE_VER}-linux-aarch64/bin/cmake" "$SDK_CMAKE/bin/"
cp "$TOOLS_DIR/cmake-${CMAKE_VER}-linux-aarch64/bin/ctest" "$SDK_CMAKE/bin/"
cp "$TOOLS_DIR/cmake-${CMAKE_VER}-linux-aarch64/bin/cpack" "$SDK_CMAKE/bin/"
rm -rf "$SDK_CMAKE/share"
cp -r "$TOOLS_DIR/cmake-${CMAKE_VER}-linux-aarch64/share" "$SDK_CMAKE/share"

# Ninja
msg "Installing ninja"
NINJA_DIR="$TOOLS_DIR/ninja-aarch64"
if [ ! -f "$NINJA_DIR/bin/ninja" ]; then
  mkdir -p "$NINJA_DIR"
  (cd /tmp && $TERMUX_APT download ninja libandroid-spawn 2>/dev/null || true)
  for deb in /tmp/ninja_*.deb /tmp/libandroid-spawn_*.deb; do
    [ -f "$deb" ] && dpkg-deb -x "$deb" "$NINJA_DIR"
  done
fi
NINJA_BIN="$NINJA_DIR/data/data/com.termux/files/usr/bin/ninja"
NINJA_LIB="$NINJA_DIR/data/data/com.termux/files/usr/lib"
cat > "$SDK_CMAKE/bin/ninja" << WRAPPER
#!/bin/sh
export LD_LIBRARY_PATH=$NINJA_LIB:\$LD_LIBRARY_PATH
exec $NINJA_BIN "\$@"
WRAPPER
chmod +x "$SDK_CMAKE/bin/ninja"

# ── 6. NDK clang/lld wrappers ───────────────────────────────────────────────
setup_ndk_wrappers() {
  local NDK_DIR="$1"
  local NDK_BIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin"
  [ -d "$NDK_BIN" ] || return

  local CLANG_VER
  CLANG_VER=$(ls "$NDK_BIN"/clang-[0-9]* 2>/dev/null | head -1 | grep -oP 'clang-\K[0-9]+')
  [ -z "$CLANG_VER" ] && return

  local RESDIR="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/lib/clang/$CLANG_VER"
  msg "Patching NDK $(basename "$NDK_DIR") (clang-$CLANG_VER)"

  # Backup original x86_64 clang
  [ -f "$NDK_BIN/clang-${CLANG_VER}.x86_64.bak" ] || \
    mv "$NDK_BIN/clang-$CLANG_VER" "$NDK_BIN/clang-${CLANG_VER}.x86_64.bak" 2>/dev/null || true

  # IMPORTANT: remove symlinks first (clang++ → clang → clang-N),
  # otherwise writing to clang++ follows the chain and overwrites clang-N
  rm -f "$NDK_BIN/clang" "$NDK_BIN/clang++" "$NDK_BIN/clang-$CLANG_VER"

  # C compiler
  for f in "clang-$CLANG_VER" "clang"; do
    cat > "$NDK_BIN/$f" << WRAPPER
#!/bin/sh
exec $TERMUX_BIN/clang -resource-dir=$RESDIR "\$@"
WRAPPER
    chmod +x "$NDK_BIN/$f"
  done

  # C++ compiler — must translate -static-libstdc++ (NDK-specific flag that
  # vanilla clang misinterprets as -Bstatic -lc++_shared which doesn't exist)
  cat > "$NDK_BIN/clang++" << 'OUTER'
#!/bin/sh
args=""
for arg in "$@"; do
  case "$arg" in
    -static-libstdc++) args="$args -nostdlib++ -lc++_static -lc++abi" ;;
    *) args="$args $arg" ;;
  esac
done
exec CLANGXX_PLACEHOLDER -resource-dir=RESDIR_PLACEHOLDER $args
OUTER
  sed -i "s|RESDIR_PLACEHOLDER|$RESDIR|;s|CLANGXX_PLACEHOLDER|$TERMUX_BIN/clang++|" "$NDK_BIN/clang++"
  chmod +x "$NDK_BIN/clang++"

  # lld and llvm-* tools
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

for ndk_dir in "$SDK_DIR"/ndk/*/; do
  [ -d "$ndk_dir" ] && setup_ndk_wrappers "$ndk_dir"
done

# ── 7. Write local.properties + user gradle.properties ──────────────────────
msg "Writing local.properties"
cat > "$PROJECT_DIR/local.properties" << EOF
sdk.dir=$SDK_DIR
EOF

# aapt2 override must be a Gradle property (not local.properties)
# Write to user-level ~/.gradle/gradle.properties so it doesn't pollute the project
GRADLE_USER_PROPS="$HOME/.gradle/gradle.properties"
mkdir -p "$HOME/.gradle"
if ! grep -q "aapt2FromMavenOverride" "$GRADLE_USER_PROPS" 2>/dev/null; then
  echo "android.aapt2FromMavenOverride=$SDK_DIR/build-tools/36.0.0/aapt2" >> "$GRADLE_USER_PROPS"
  msg "Added aapt2 override to $GRADLE_USER_PROPS"
fi

# ── Done ─────────────────────────────────────────────────────────────────────
msg "Setup complete!"
cat << 'BANNER'

Add to your shell profile (~/.bashrc or ~/.zshrc):

  export JAVA_HOME=$HOME/tools/jdk-17.0.2
  export ANDROID_HOME=$HOME/android-sdk
  export PATH=$JAVA_HOME/bin:$HOME/tools/go/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

Then build:

  ./gradlew assembleDebug --no-daemon

BANNER
