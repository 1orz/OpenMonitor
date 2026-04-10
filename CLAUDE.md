# OpenMonitor - Claude Code 项目指南

## 项目概述

Android 系统监控工具，Kotlin/Compose，多模块 Gradle 项目。
- 模块：app, core/*(common, model, data, database, ui), feature/*(overview, battery, fps, process, cpu, float, hardware, key-attestation), service
- Go daemon（monitor-daemon，交叉编译 Android arm64）
- C++ JNI（cpuinfo-bridge + vulkan-info，CMake 构建）
- Shizuku AIDL IPC

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| UI | Jetpack Compose + Material 3 | BOM 2026.03.01 |
| 导航 | Navigation3 (runtime + ui) | 1.1.0 |
| DI | Dagger Hilt | 2.59.2 |
| 数据库 | Room | 2.8.4 |
| 异步 | Kotlin Coroutines | 1.10.2 |
| Root | libsu | 6.0.0 |
| 权限 | Shizuku API | 13.1.5 |
| 后台 | WorkManager | 2.11.2 |
| 图表 | Vico | 3.1.0 |
| 动态配色 | Material Kolor | 4.1.1 |
| 日志 | xLog | 1.11.1 |
| 分析 | Firebase Analytics | BOM 34.12.0 |
| Native | cpuinfo (BSD-2) + Vulkan Info | prebuilt arm64-v8a |

## UI 架构

- **主题**：Material 3 + Material Kolor 动态配色，支持 System/Light/Dark/AMOLED 四种模式
- **导航**：Navigation3 NavDisplay，左右滑动转场（transitionSpec slideInHorizontally/slideOutHorizontally）
- **设置组件**：`app/.../ui/component/SettingsComponents.kt` 提供 SettingsGroup / SettingsSwitchItem / SettingsDropdownItem / SettingsNavigateItem / ExpressiveSwitch，基于 M3 ListItem + Surface segmented 圆角
- **开源协议**：`assets/licenses/libraries.json` 维护库列表，license 文本从 GitHub 在线获取

## UI 字符串规范（强制）

**所有用户可见的 UI 字符串必须使用 i18n 资源，禁止硬编码。**

- Compose 中使用 `stringResource(R.string.xxx)`
- 字符串定义在 `app/src/main/res/values/strings.xml`（英文基准）
- 必须同步添加到四个语言文件：`values/`（EN）、`values-zh-rCN/`、`values-zh-rTW/`、`values-ja/`
- 应用名称 `OpenMonitor`、版本号、品牌名（Magisk、KernelSU 等）不需要翻译
- 违规示例（禁止）：`Text("检测中…")` `Text("Detecting…")`
- 合规示例：`Text(stringResource(R.string.settings_detecting))`

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
