<div align="center">

<img src="icon/openmonitor_logo.svg" width="128" height="128" alt="OpenMonitor Logo" />

# OpenMonitor

**基于 Jetpack Compose & Material 3 的强大开源 Android 系统监控工具**

实时监控 CPU、GPU、电池、帧率、温度、内存和进程 — 支持 ROOT、Shizuku 和 ADB 模式。

[![Release](https://img.shields.io/github/v/release/1orz/OpenMonitor?include_prereleases&logo=github&label=Release)](https://github.com/1orz/OpenMonitor/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen?logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)
[![Stars](https://img.shields.io/github/stars/1orz/OpenMonitor?style=flat&logo=github&label=Stars)](https://github.com/1orz/OpenMonitor/stargazers)
[![License](https://img.shields.io/github/license/1orz/OpenMonitor?label=License)](https://github.com/1orz/OpenMonitor/blob/main/LICENSE)

[**下载**](https://github.com/1orz/OpenMonitor/releases/latest) · [**问题反馈**](https://github.com/1orz/OpenMonitor/issues) · [**QQ频道(中文)**](https://pd.qq.com/s/ft7tc3xae) · [**QQ群(中文)**](https://qm.qq.com/cgi-bin/qm/qr?k=Kx6t5VIe6vwKSyk6gsf2CGCABO3V7qfe&jump_from=webapi&authKey=Qq+pLqZq/1Px+1g/r49xi2QDzcCfxxiH6kK5c2qdTNMvnggkmbbq6Aptw8oKruyz) · [**Telegram(中/英)**](https://t.me/OpenMonitor) · [**官网**](https://om.cloudorz.com/)

[**English**](README.md) | **简体中文** | [**日本語**](README_ja.md)

---

</div>

## Star 趋势

<div align="center">

[![Star History Chart](https://api.star-history.com/svg?repos=1orz/OpenMonitor&type=Date)](https://star-history.com/#1orz/OpenMonitor&Date)

</div>

---

## 特性亮点

- 实时监控 CPU、GPU、电池、帧率、内存、温度和进程
- 多种权限模式：ROOT（Magisk / KernelSU / APatch）、Shizuku、ADB 和基础模式
- 悬浮窗覆盖层，跨应用实时监控
- 快捷设置磁贴和桌面小组件，一键访问
- 帧率录制，支持卡顿检测、会话回放、图片/CSV 导出
- 完整硬件参数，内置 900+ SoC 数据库，Vulkan 和 OpenGL 枚举
- 硬件级密钥认证和设备完整性验证
- Material 3 动态主题，支持跟随系统 / 浅色 / 深色 / AMOLED 四种模式
- Rust 特权守护进程，高性能低开销数据采集
- 支持英语、简体中文、繁体中文、日语四种语言

---

## 目录

- [快速开始](#快速开始)
- [权限模式](#权限模式)
- [功能详解](#功能详解)
  - [总览仪表盘](#总览仪表盘)
  - [CPU 分析](#cpu-分析)
  - [电池](#电池)
  - [帧率录制](#帧率录制)
  - [进程管理](#进程管理)
  - [硬件信息](#硬件信息)
  - [悬浮窗监控](#悬浮窗监控)
  - [密钥认证](#密钥认证)
  - [快捷磁贴与桌面小组件](#快捷磁贴与桌面小组件)
- [架构](#架构)
- [技术栈](#技术栈)
- [从源码构建](#从源码构建)
- [隐私政策](#隐私政策)
- [参与贡献](#参与贡献)
- [许可证](#许可证)

---

## 快速开始

1. **下载** — 从 [GitHub Releases](https://github.com/1orz/OpenMonitor/releases/latest) 下载最新 APK 并安装。
2. **启动** — 首次打开会显示隐私协议，阅读并同意后继续。
3. **选择权限模式** — 应用会自动检测可用的权限框架（ROOT、Shizuku、ADB），并推荐最佳选项。如果没有特权访问，基础模式也可以提供核心监控功能。
4. **开始探索** — 底部导航栏分为三个标签：
   - **功能** — 访问所有监控模块（硬件信息、电池、帧率、进程、悬浮窗、密钥认证等）
   - **总览** — 实时仪表盘，一目了然所有关键指标
   - **设置** — 配置主题、语言、权限模式、触觉反馈等

---

## 权限模式

OpenMonitor 会自适应设备可用的权限，并优雅降级：

| 模式 | 工作方式 | 可用功能 |
|:-----|:---------|:---------|
| **ROOT** | 通过 Magisk / KernelSU / APatch 获取完整特权。Rust 守护进程通过 libsu 自动启动。 | 全部功能 — 帧率、杀进程、GPU 频率、完整温度、悬浮窗、电池电流/功率 |
| **Shizuku** | 通过 Shizuku 管理器委托权限。守护进程以 Shizuku UserService 运行。 | 帧率监控、进程管理、增强指标 |
| **ADB** | 通过 `adb shell` 手动启动服务端，无需安装额外应用。 | 与 Shizuku 相同，适用于无 ROOT 的设备 |
| **基础** | 不需要任何特殊权限。 | CPU 负载/频率、内存、电池基础信息（来自可读的 sysfs） |

> 可随时在 **设置 > 系统 > 权限模式** 中切换。

---

## 功能详解

### 总览仪表盘

总览页面提供设备实时状态的全面快照：

- **CPU** — 总负载百分比（颜色编码：绿色 < 50%、黄色 < 80%、红色 >= 80%）、平均频率（MHz）、温度、在线/总核心数，以及每个核心的迷你状态指示
- **内存** — 弧形仪表盘显示使用率，物理 RAM 已用/总量、可用内存、Swap 使用量、ZRam 压缩比
- **GPU** — 型号名称、当前/最大频率、负载百分比进度条、当前调度器
- **SoC 信息** — 设备名称、厂商 Logo、SoC 型号、制程工艺标签、架构、ABI、CPU 簇配置
- **缓存** — L1D / L1I / L2 / L3 缓存大小、ARM NEON 支持状态
- **系统信息卡片** — 功率（W）、电池电量和电压、温度、Android 版本、系统运行时间

所有指标实时更新，仪表盘旨在让你快速了解设备整体状态。

### CPU 分析

详细的逐核 CPU 分析，可从总览或硬件信息进入：

- **簇视图** — 按簇（小核 / 大核 / 超大核）分组，显示每簇的调度器、频率范围（最小–最大 MHz）和平均负载
- **逐核状态** — 每个核心显示编号、微架构名称、当前负载百分比、频率（MHz）、在线/离线状态
- **SoC 详情** — 硬件 ID、架构、ABI、调度策略、完整簇配置和频率范围
- **缓存层级** — 每核/每簇的 L1D / L1I / L2 / L3 大小

### 电池

深度电池分析，支持历史记录追踪：

- **状态卡片** — 当前电量百分比、电压（V）、温度（带颜色编码的健康指示）、预估剩余/充电时间
- **历史图表** — 折线图显示电池电量随时间变化，可通过筛选按钮选择时间范围
- **放电分析** — 选定时间段内的趋势可视化，帮助了解耗电模式
- **应用耗电排行** — 按应用显示电池消耗，附带应用图标、预估耗电百分比和毫安时（需要使用情况统计权限 — 未授权时应用会提示你开启）
- **CSV 导出** — 通过分享按钮导出电池历史数据为 CSV 文件

### 帧率录制

专业级帧率监控和会话管理：

**开始录制**
1. 进入 **功能 > 帧率录制**
2. 打开 **帧率悬浮窗** 开关，启用悬浮窗叠加层
3. 打开你要测试的游戏或应用 — 悬浮窗会显示实时帧率
4. 测试完成后，返回 OpenMonitor 结束会话

**会话列表**
- 所有会话以卡片形式展示，显示应用图标、应用名、平均帧率、时长和时间戳
- 帧率质量颜色编码：绿色（>= 50 fps）、黄色（>= 30 fps）、红色（< 30 fps）
- 长按进入多选模式，支持批量删除
- 点击编辑图标可重命名会话

**会话详情与分析**

点击某个会话可打开完整的分析视图：

- **设备信息** — CPU 平台、设备型号、操作系统版本
- **会话信息头** — 应用图标、名称、版本、包名、屏幕分辨率、时间戳、时长
- **六项关键指标**：
  - 最大 / 最小 / 平均帧率（颜色编码）
  - 帧率方差（标准差）
  - 流畅度（>= 45 fps 帧的占比）
  - 5% Low（最低 5% 分位数）
  - 最高电池温度
  - 平均功耗（W）
- **应用切换时间线** — 水平可滚动条，显示录制期间前台应用切换和时间段
- **交互式图表**（通过设置图标切换）：
  - 帧率与温度（CPU 温度、GPU 温度、CPU/GPU 使用率）
  - 帧时间（最大帧时间，毫秒）
  - CPU/GPU 使用率（总 CPU、GPU、各簇负载）
  - CPU/GPU 频率（各簇频率、GPU 频率）
  - 卡顿图表（每帧卡顿和大卡顿增量）
  - 功耗/电量（功率 W、电量 %）
  - 电池电流（mA，最大/最小/平均值）
  - 温度（CPU、GPU、电池）
- 每个图表支持 **显示/隐藏单独数据系列** 以聚焦分析
- 所有图表支持 **滚动和缩放**

**导出选项**
- **保存为 PNG** — 生成包含所有图表、设备信息和带二维码品牌横幅的可分享图片
- **保存为 CSV** — 导出原始数据为电子表格格式，用于外部分析

### 进程管理

实时进程监控和管理功能：

- **搜索** — 通过搜索栏按名称过滤进程
- **过滤模式** — 在「全部进程」和「仅应用」（已安装应用）之间切换
- **排序选项** — 按 CPU %、内存（MB）、名称（字母序）或 PID 排序
- **进程列表** — 每条记录显示应用图标（或默认图标）、进程名、PID、CPU 使用率（颜色编码：绿色 < 5%、黄色 5–20%、红色 > 20%）和内存占用（MB）
- **进程详情** — 点击任意进程可查看详细信息
- **进程计数** — 顶部显示当前过滤后的进程总数

> 在 ROOT 和 Shizuku 模式下可使用进程终止功能。

### 硬件信息

全面的设备硬件参数：

- **处理器** — 厂商 Logo、SoC 名称、制程工艺标签、核心数、64 位标签、各簇 CPU 配置和频率范围、调度器、架构、ABI。可跳转至 CPU 分析页面。
- **GPU** — 型号/渲染器名称、芯片 ID、厂商、GMEM 大小、着色器核心数、总线宽度、L2 缓存（Mali GPU）。提供 **Vulkan 特性** 和 **OpenGL ES 特性** 查看入口。
- **显示** — 分辨率、刷新率、PPI、物理尺寸（英寸/毫米）、宽高比、密度等级、广色域、HDR 能力、面板名称。列出所有支持的分辨率和刷新率。
- **内存** — RAM 大小标签（取整为市场容量）、使用进度条（总量/已用/可用）。ZRam 部分显示压缩比（如可用）。
- **存储** — 市场容量、存储类型（如 UFS 3.1）、使用进度条、分区明细及使用空间、文件系统类型、块大小。

**Vulkan 信息页面** — GPU 能力深度查看：
- 设备身份（名称、厂商、设备 ID、类型、API 版本、驱动版本）
- 内存堆及 device-local 标记
- 限制参数（最大图像尺寸、计算调用数、缓冲区范围、各向异性）
- 核心特性（robust buffer access、几何/细分着色器、多视口等）
- 队列族及标志位和时间戳位数
- 完整的设备和实例扩展列表

### 悬浮窗监控

始终置顶的悬浮监控覆盖层：

- **多种监控项** — 可分别开关帧率、CPU、内存、GPU 和温度的悬浮显示
- **权限处理** — 如果未授予悬浮窗权限，会显示卡片引导你前往系统设置开启
- **快速访问** — 也可通过快捷设置磁贴一键切换

悬浮窗在所有应用上方保持可见，非常适合游戏或跑分时使用。

### 密钥认证

基于硬件的设备安全验证：

- **信任横幅** — 清晰显示证书链是否受信任
- **启动状态** — 已验证 / 自签名 / 失败状态，附引导加载程序锁定状态（颜色编码图标）
- **认证信息** — 认证版本、安全级别（TEE / StrongBox / 软件）、Keymaster 版本、挑战值、唯一标识
- **吊销列表** — 设备证书吊销状态，支持网络获取，显示条目数和缓存过期时间
- **证书链** — 逐证书详情，包括主体、颁发者、有效期和吊销状态及原因
- **授权列表** — 逐字段展示，带 HW/SW 标签区分硬件或软件级别
- **选项菜单：**
  - 切换「使用 Attest Key」/「使用 StrongBox」/「Shizuku 模式」
  - 重置持久化 Attest Key
  - 保存证书链为 `.p7b` 文件
  - 从文件加载证书链

### 快捷磁贴与桌面小组件

**快捷设置磁贴**
- 在快捷设置面板中添加 "Monitor" 磁贴
- 点击即可开关悬浮监控覆盖层
- 显示激活/未激活状态

**桌面小组件**
- 展示三项关键指标：CPU 负载 %、CPU 温度、内存使用率 %
- 自动刷新；点击任意指标即可打开 OpenMonitor
- 轻量级 — 直接读取 `/proc/stat`、`/sys/class/thermal/`、`/proc/meminfo`

---

## 架构

```
┌──────────────────────────────────────────────────────────────┐
│                           app                                │
│    MainActivity · Splash · Agreement · Settings · Nav3       │
├──────────┬──────────┬──────────┬──────────┬──────────────────┤
│ feature- │ feature- │ feature- │ feature- │    feature-      │
│ overview │ battery  │   fps    │ process  │    hardware      │
├──────────┼──────────┼──────────┴──────────┼──────────────────┤
│ feature- │ feature- │    feature-key-     │     service      │
│  float   │   cpu    │    attestation      │                  │
├──────────┴──────────┴─────────────────────┴──────────────────┤
│  core-ui   │  core-data  │  core-model  │   core-common      │
├────────────┼─────────────┼──────────────┼────────────────────┤
│            │core-database│core-daemon-api│                   │
├────────────┴─────────────┴──────────────┴────────────────────┤
│              daemon-rust (Rust 特权守护进程)                   │
│      AF_UNIX IPC · sysfs 轮询 · SurfaceFlinger FPS          │
└──────────────────────────────────────────────────────────────┘
```

### Rust 特权守护进程 (`daemon-rust`)

轻量级 Rust 二进制文件，以提升的权限运行，采集普通应用无法访问的系统数据：

- **双 fork 守护进程化** — AF_UNIX 套接字 IPC
- **长度前缀 JSON** 通信协议
- **多线程采样** — CPU 频率/负载、温度区域、GPU 状态、电池电流/功率、SurfaceFlinger 帧率
- **APK v2 证书锁定** — 客户端身份认证
- **启动模式** — ROOT (libsu)、Shizuku (UserService)、ADB (CLI)

### 原生层

- **cpuinfo-bridge** — [cpuinfo](https://github.com/pytorch/cpuinfo) 的 JNI 桥接，获取详细 CPU 拓扑和缓存信息
- **vulkan-info** — 通过 JNI 枚举 Vulkan 能力

---

## 技术栈

| 类别 | 技术 | 版本 |
|:-----|:-----|:-----|
| 语言 | Kotlin | 2.3 |
| UI | Jetpack Compose + Material 3 | BOM 2026.03 |
| 导航 | Navigation3 (runtime + ui) | 1.1.0 |
| 依赖注入 | Dagger Hilt | 2.59.2 |
| 数据库 | Room | 2.8.4 |
| 异步 | Kotlin Coroutines | 1.10.2 |
| Root 访问 | libsu | 6.0.0 |
| 权限 | Shizuku API | 13.1.5 |
| 后台 | WorkManager | 2.11.2 |
| 图表 | Vico | 3.1.0 |
| 主题 | Material Kolor | 4.1.1 |
| 分析 | Firebase Analytics | BOM 34.12.0 |
| 守护进程 | Rust (daemon-rust) | Edition 2021 |
| 原生 | cpuinfo + Vulkan Info | arm64-v8a |
| Markdown | multiplatform-markdown-renderer | 0.40.2 |

---

## 从源码构建

### 前置条件

- JDK 17+
- Android SDK，compile SDK 36
- CMake 3.22.1（Gradle 自动安装）
- NDK（用于原生 cpuinfo 和 Vulkan 模块）

### 构建

```bash
git clone https://github.com/1orz/OpenMonitor-src.git
cd OpenMonitor-src
./gradlew assembleDebug
```

APK 将生成在 `app/build/outputs/apk/debug/`。

### aarch64 Linux（Termux）

在 ARM64 Linux 设备上构建：

```bash
bash scripts/setup-aarch64-build-env.sh
./gradlew assembleDebug
```

安装脚本会自动处理所有平台特定的工具链适配（aapt2、CMake、NDK wrapper）。

---

## 隐私政策

- **所有监控数据保存在设备本地** — 不上传系统指标
- **Firebase Analytics** 为可选且匿名
- **所有操作为只读** — OpenMonitor 不会修改你的系统
- **完全开源** — 你可以审查每一行代码

详见完整的 [隐私政策](https://om.cloudorz.com/)。

---

## 参与贡献

欢迎贡献！请在 [GitHub](https://github.com/1orz/OpenMonitor/issues) 上提交 Issue 和 Pull Request。

---

## 许可证

详见 [LICENSE](LICENSE) 文件。

---

<div align="center">

**由 Kotlin、Rust 和 Compose 驱动**

</div>
