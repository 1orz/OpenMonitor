# OpenMonitor - Claude Code 项目指南

## 项目概述

Android 系统监控工具，使用 Kotlin/Compose 构建，包含：
- 多模块 Gradle 项目（app, core/*, feature/*, service）
- Go 编写的 monitor-daemon（交叉编译为 Android arm64）
- C++ JNI 原生库（cpuinfo-bridge，通过 CMake 构建）
- Shizuku AIDL IPC 接口

## 构建命令

```bash
export JAVA_HOME=$HOME/tools/jdk-17.0.2
export ANDROID_HOME=$HOME/android-sdk
export PATH=$JAVA_HOME/bin:$HOME/tools/go/bin:$PATH
./gradlew assembleDebug --no-daemon
```

## 在 aarch64 Linux（Termux proot）上构建

Google 不提供 Linux aarch64 版本的 Android SDK 工具。需要用社区方案替换。
完整安装脚本：`scripts/setup-aarch64-build-env.sh`

### 原理概述

| 工具 | 问题 | 替代方案 |
|------|------|---------|
| build-tools (aapt2, aidl, zipalign) | x86_64 ELF | [lzhiyong/android-sdk-tools](https://github.com/lzhiyong/android-sdk-tools) 静态编译 aarch64 二进制 |
| AGP Maven 缓存的 aapt2 | x86_64 ELF（在 jar 包内） | 用 `jar uf` 替换 jar 内二进制，然后清除 `~/.gradle/caches/*/transforms/` |
| CMake | SDK 自带的是 x86_64 | [Kitware 官方 aarch64 发行版](https://github.com/Kitware/CMake/releases) |
| ninja | SDK 自带的是 x86_64 | Termux 包 `ninja` |
| NDK clang/clang++/lld | x86_64 ELF | 薄 wrapper 脚本 → Termux 原生 `clang`/`lld`，加 NDK 的 `-resource-dir` |

### NDK Wrapper 关键细节

**clang/clang++ wrapper 只加 `-resource-dir`，不加 `--sysroot`**：NDK 的 CMake toolchain 已经会传 `--sysroot` 和 `--target`，重复添加会冲突。

**clang++ 必须翻译 `-static-libstdc++` 标志**：
- NDK 原版 clang 理解这个标志 → 链接 `libc++_static.a`
- Termux clang 不理解 → 错误地生成 `-Bstatic -lc++_shared`（找不到该库）
- Wrapper 必须将 `-static-libstdc++` 替换为 `-nostdlib++ -lc++_static -lc++abi`

**必须先删除 symlink 再创建 wrapper**：
NDK 的链接链 `clang++ → clang → clang-N`。如果直接 `cat > clang++`，shell 会跟着 symlink 覆盖 `clang-N`，导致 C 编译器也变成 C++ 模式。正确做法：`rm -f clang clang++ clang-N`，然后分别创建三个独立脚本。

### Wrapper 模板

```bash
# clang（C 编译器）
#!/bin/sh
exec /data/data/com.termux/files/usr/bin/clang -resource-dir=$NDK/lib/clang/$VER "$@"

# clang++（C++ 编译器，带 -static-libstdc++ 翻译）
#!/bin/sh
args=""
for arg in "$@"; do
  case "$arg" in
    -static-libstdc++) args="$args -nostdlib++ -lc++_static -lc++abi" ;;
    *) args="$args $arg" ;;
  esac
done
exec /data/data/com.termux/files/usr/bin/clang++ -resource-dir=$NDK/lib/clang/$VER $args

# lld / llvm-ar / llvm-strip 等
#!/bin/sh
exec /data/data/com.termux/files/usr/bin/<tool> "$@"
```
