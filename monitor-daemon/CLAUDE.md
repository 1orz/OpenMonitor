# monitor-daemon

Go 编写的 Android 系统性能数据采集服务。零外部依赖，TCP 帧协议对外暴露实时 Snapshot。

**定位**：纯指标采集器 + 控制端点。进程管理已移至 App 侧（2026-04-12）。

## 项目结构

```
monitor-daemon/
├── cmd/
│   ├── daemon/main.go      # daemon 入口
│   └── client/main.go      # CLI 调试工具（mcli）
├── daemon/daemon.go         # Daemonize: stdio 脱离 + Setsid + PID 文件
├── proto/proto.go           # 帧协议：4字节大端长度 + UTF-8 payload（上限 1MB）
├── collector/
│   ├── collector.go         # Snapshot 模型 + 采样调度（200ms 系统 / 500ms FPS）
│   ├── cpu.go               # /proc/stat 差分 + /sys/.../scaling_cur_freq
│   ├── gpu.go               # Adreno /sys/class/kgsl + Mali fallback（需 root）
│   ├── thermal.go           # thermal_zone 类型关键字匹配 + fallback zone0
│   ├── memory.go            # /proc/meminfo（MemTotal + MemAvailable）
│   ├── battery.go           # sysfs 优先 → dumpsys battery fallback
│   ├── fps.go               # dumpsys SurfaceFlinger --timestats，PerfDog jank 算法
│   └── logger.go            # 运行时可调日志级别（debug/info/warning/error）
├── server/
│   ├── server.go            # TCP 服务：request-response + stream 推送 + heartbeat
│   └── watchdog.go          # App 进程存活监控，5s 间隔 pidof + am broadcast 重启
├── go.mod                   # module: monitor-daemon，go 1.21，零外部依赖
├── build.sh                 # 交叉编译脚本（自动检测设备）
└── test_client.py           # Python 测试客户端
```

## 启动参数

```
-addr           TCP 监听地址（默认 0.0.0.0:9876）
-pprof-addr     pprof HTTP 地址（如 0.0.0.0:6060），空则禁用
-data-dir       PID / 日志文件目录（默认 /data/local/tmp）
--no-detach     前台运行（开发用）
```

## TCP 命令

### 数据查询

| 命令 | 响应 | 说明 |
|------|------|------|
| `ping` | `{"status":"pong","version":"...","commit":"...","runner":"root","pid":N,"started_at":"...","uptime_s":N}` | 心跳 + 身份信息 |
| `daemon-version` | 同 ping | 别名 |
| `monitor` | Snapshot JSON（见下方） | 返回缓存快照，不触发 I/O |

### 运行时控制

| 命令 | 响应 | 说明 |
|------|------|------|
| `log-level\n<level>` | `{"status":"ok","level":"..."}` | debug / info / warning / error |
| `heartbeat-timeout\n<seconds>` | `{"status":"ok","timeout_s":N}` | 心跳超时（0=禁用），超时无 ping 则自动退出 |
| `clear-log` | `{"status":"ok"}` | 截断 daemon.log |
| `daemon-exit` | `{"status":"exiting"}` | 100ms 后优雅退出 |

### Watchdog

| 命令 | 响应 | 说明 |
|------|------|------|
| `watchdog-start` | `{"status":"ok","watchdog":true,"changed":bool}` | 启动 App 进程监控（5s 间隔 `pidof`，死亡则 `am broadcast` 重启） |
| `watchdog-stop` | `{"status":"ok","watchdog":false,"changed":bool}` | 停止监控 |
| `watchdog-status` | `{"watchdog":bool}` | 查询状态 |

### 流式推送

```
stream:<interval_ms>\n<cmd>    → 按间隔持续推送 dispatch(cmd) 结果
```

- 最小间隔 100ms，首帧立即发送
- 客户端发 `@signal:exit` 或断连则停止
- 典型用法：`stream:1000\nmonitor`

## 数据模型（Snapshot）

`monitor` 命令返回：

```json
{
  "fps": 115.3,
  "jank": 0,
  "big_jank": 0,
  "fps_layer": "layerName = SurfaceView[com.tencent.lolm/...]#14149",
  "fps_source": "timestats",

  "cpu_load": [46.6, 27.7, 5.8, 3.8, 4.7, 33.3, 31.8, 0.9],
  "cpu_freq": [1804, 1804, 1401, 1497, 1497, 729, 729, 672],
  "cpu_temp": 58.4,

  "gpu_freq": 389,
  "gpu_load": 61,

  "memory_total_mb": 23121,
  "memory_avail_mb": 13726,

  "battery": {
    "current_ma": -2840,
    "voltage_mv": 4050,
    "temp": 38.5,
    "capacity": 72,
    "status": "Discharging",
    "power_mw": 11502
  },

  "runner": "root",
  "timestamp_ms": 1772881175268
}
```

字段说明：
- `cpu_load`：每核负载 %（不含聚合行），`/proc/stat` 两次采样差分计算
- `cpu_freq`：每核 MHz，读 `/sys/devices/system/cpu/cpu[0-15]/cpufreq/scaling_cur_freq`
- `cpu_temp`：°C，thermal_zone 类型匹配（见下方）
- `gpu_freq` / `gpu_load`：需 root，否则 null
- `battery.current_ma`：**负数=放电，正数=充电**，root 模式读 sysfs current_now；HyperOS 锁死时为 null
- `battery.power_mw`：abs(current_ma) × voltage_mv / 1000
- `runner`：`"root"` 或 `"shell"`
- 所有指标字段为 pointer，采集不到时 JSON null

## 后台采集

### 系统采样（默认 200ms，可调）

每周期读取以下数据源（collector/collector.go sampleSystem）：

| 数据 | 文件 / 路径 | 说明 |
|------|-------------|------|
| CPU 负载 | `/proc/stat` | 解析 cpu 行各 tick 字段，与前次差分计算每核 load% |
| CPU 频率 | `/sys/devices/system/cpu/cpu{0-15}/cpufreq/scaling_cur_freq` | kHz → MHz，遇到首个缺失核停止 |
| CPU 温度 | `/sys/class/thermal/thermal_zone*/temp` | 启动时探测 type 文件匹配关键字，缓存路径 |
| GPU 频率 | `/sys/class/kgsl/kgsl-3d0/gpuclk` → `devfreq/cur_freq` → `/sys/kernel/gpu/gpu_clock` → Mali 路径 | sync.Once 探测，root only |
| GPU 负载 | `/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage` → 多 fallback | 同上 |
| 内存 | `/proc/meminfo` | 仅取 MemTotal + MemAvailable，kB → MB |
| 电池 | sysfs `/sys/class/power_supply/{battery,Battery,bms,BAT0,BAT1}/` | current_now / voltage_now / temp / capacity / status |
| 电池 fallback | `dumpsys battery` | sysfs 不可用时降级，无 current_ma |

**温度路径探测关键字**（thermal.go）：`cpu`, `soc`, `tsens_tz_sensor`, `cpu-1-0` ~ `cpu-1-3`, `thermal_zone0`, `skin`, `quiet-therm`。无匹配则 fallback zone0。

**GPU 路径**（gpu.go）：Adreno (`/sys/class/kgsl/kgsl-3d0/`) 优先，Mali (`/sys/devices/platform/mali.0/`) 备选，启动时 sync.Once 探测一次并缓存。

### FPS 采样（固定 500ms）

每周期执行的外部命令（collector/fps.go）：

| 命令 | 频率 | 超时 | 用途 |
|------|------|------|------|
| `dumpsys SurfaceFlinger --timestats -dump` | 每 500ms | 5s | 解析各 layer totalFrames，差分算 FPS |
| `dumpsys activity activities` | 每 500ms | 5s | 获取 mResumedActivity → 前台包名 |
| `dumpsys window displays` | 每 500ms（fallback） | 5s | 获取 mFocusedApp → 前台包名 |
| `dumpsys SurfaceFlinger --latency <layer>` | 按需（delta=0 或 jank 检测时） | 5s | 逐帧 actualPresentTime，PerfDog jank 算法 |
| `dumpsys SurfaceFlinger --timestats -enable/-clear` | 启动 + 恢复 | 5s | 重置 timestats 状态 |

**FPS 计算**：rawFps = delta_frames / delta_seconds，直出无平滑。

**Jank 检测**（PerfDog 算法）：
- Jank：frameTime > 2 × avg(prev2) AND > 2 × 16.67ms
- BigJank：frameTime > 3 × avg(prev2) AND > 3 × 16.67ms

**Layer 选择**：粘性跟踪当前 layer，仅在另一 layer delta > 2 倍时切换，优先 BLAST layer。

**恢复机制**：连续 6 次采集失败 或 距上次 enable > 10 分钟 → 重新 enable + clear timestats。

## 权限矩阵

| 数据 | shell | root |
|------|-------|------|
| CPU 负载 / 频率 / 温度 | ✅ | ✅ |
| 内存 | ✅ | ✅ |
| FPS / Jank | ✅ | ✅ |
| 电池容量 / 温度 / 电压 / 状态 | ✅（dumpsys fallback） | ✅（sysfs 直读） |
| 电池电流 / 功率 | ❌（HyperOS 锁 sysfs） | ✅ |
| GPU 频率 / 负载 | ❌ | ✅ |

## Daemon 存在的必要性

| 数据 | 为什么 App 无法替代 |
|------|---------------------|
| GPU 频率/负载 | sysfs 路径权限 0600，需 root 才能读 |
| FPS / Jank | `dumpsys SurfaceFlinger --timestats` 需 shell/root 权限执行 |
| 电池电流（sysfs） | HyperOS 等厂商锁死 sysfs current_now，App BatteryManager API 可补充但精度不同 |
| CPU/内存/温度 | App 也能读，但 daemon 200ms 高频采样对 App 主线程无影响，且数据通过 TCP 缓存推送更高效 |

**已移至 App 侧的功能**（2026-04-12）：进程列表（`ps` + `/proc` 直读）、进程 Kill（`ShellExecutor kill -9`）、线程列表（`/proc/<pid>/task/`）。移除原因：`processes` 命令占 daemon ~75% CPU（pprof 实测，每次调用 ~2700 次 /proc 文件读取 + 300 个临时 goroutine）。

## 性能概况（2026-04-12 pprof 实测，瘦身后）

移除进程管理后，daemon 仅剩系统采样 + FPS 采集：

| 采集器 | CPU 占比 | 主要开销 |
|--------|----------|----------|
| 系统采样（200ms） | ~4.8% | sysfs 文件 open/read syscall |
| FPS 采样（500ms） | ~1.2% | dumpsys 子进程 spawn |
| **合计** | **~6%** | **预计实际 3-4%（去除 GC 压力后）** |

**pprof 端点**：启动时加 `--pprof-addr 0.0.0.0:6060`，然后：
```bash
adb forward tcp:6060 tcp:6060
go tool pprof -http=:8080 'http://127.0.0.1:6060/debug/pprof/profile?seconds=10'
```

## 构建与部署

```bash
# 交叉编译（自动检测设备并推送）
./build.sh [device_serial]

# 手动编译
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build -o monitor-daemon ./cmd/daemon
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build -o mcli ./cmd/client
go build -o mcli-mac ./cmd/client   # macOS 本机调试

# 推送 + 启动
adb push monitor-daemon /data/local/tmp/monitor-daemon
adb shell chmod 755 /data/local/tmp/monitor-daemon
adb shell su -c /data/local/tmp/monitor-daemon          # root，自动 daemon 化
adb shell su -c "/data/local/tmp/monitor-daemon --pprof-addr 0.0.0.0:6060"  # 带 pprof

# 停止
./mcli-mac daemon-exit

# 端口转发
adb forward tcp:9876 tcp:9876
```

## CLI 工具（mcli）

```bash
./mcli-mac monitor                               # 单次查询
./mcli-mac ping                                   # 心跳
./mcli-mac -watch monitor                         # 流式（默认 1s）
./mcli-mac -watch -interval 500ms monitor         # 500ms 间隔
./mcli-mac -addr 192.168.x.x:9876 -watch monitor  # 远端
```

## App 集成

daemon 作为可选高权限数据源，App TCP 连接 127.0.0.1:9876：

1. 启动时 `ping`（1s 超时），成功则发 `stream:1000\nmonitor` 接收推帧
2. 失败或断连：自动降级到 App 自有 DataSource 体系
3. 电池电流：App 侧 `BatteryManager.getIntProperty(BATTERY_PROPERTY_CURRENT_NOW)` 补充
4. 进程管理：App 侧 ShellExecutor（跟随用户设置的权限模式）

**帧协议**（Kotlin，与 proto/proto.go 一致）：
```kotlin
fun writeFrame(os: OutputStream, payload: ByteArray) {
    os.write(ByteBuffer.allocate(4).putInt(payload.size).array())
    os.write(payload)
}

fun readFrame(`is`: InputStream): ByteArray {
    val lenBuf = ByteArray(4); `is`.readFully(lenBuf)
    val len = ByteBuffer.wrap(lenBuf).int
    val buf = ByteArray(len); `is`.readFully(buf)
    return buf
}
```
