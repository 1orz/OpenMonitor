# OpenMonitor - Claude Code 项目指南

## 项目概述

Android 系统监控工具，Kotlin/Compose，多模块 Gradle 项目。
- 模块：app, core/*, feature/*, service
- Go daemon（monitor-daemon，交叉编译 Android arm64）
- C++ JNI（cpuinfo-bridge，CMake 构建）
- Shizuku AIDL IPC

## 构建

标准 Android 项目，macOS / x86_64 Linux 直接构建：
```bash
./gradlew assembleDebug
```

## aarch64 Linux 构建（Termux proot）

Google 不提供 aarch64 Linux 的 SDK 工具。运行一次安装脚本即可：
```bash
bash scripts/setup-aarch64-build-env.sh
```

**原则：项目源码不包含任何平台特定的 workaround。** 所有 aarch64 适配（工具替换、wrapper 脚本、aapt2 override）全部在 setup 脚本中完成，写入 `local.properties`（git-ignored）。

### 工具替换方案

| 工具 | 替代 |
|------|------|
| build-tools (aapt2, aidl, zipalign) | [1orz/android-sdk-tools](https://github.com/1orz/android-sdk-tools) aarch64 静态二进制 |
| CMake | Kitware 官方 aarch64 发行版 |
| ninja | Termux 包 |
| NDK clang/lld/llvm-* | Wrapper → Termux 原生编译器 + NDK `-resource-dir` |

### NDK Wrapper 三个坑

1. **只加 `-resource-dir`，不加 `--sysroot`** — NDK CMake toolchain 自己会传
2. **clang++ 翻译 `-static-libstdc++`** — Termux clang 把它变成 `-Bstatic -lc++_shared`（不存在），要改为 `-nostdlib++ -lc++_static -lc++abi`
3. **先 rm symlink 再写文件** — NDK 链 `clang++ → clang → clang-N`，直接写 `clang++` 会顺着 symlink 覆盖 `clang-N`
