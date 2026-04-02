# monitor-daemon

Go 编写的 Android 系统监控后台服务，以 TCP 帧协议对外暴露实时性能数据。

## 项目结构

```
monitor-daemon/
├── cmd/
│   ├── daemon/main.go      # daemon 入口（-addr / -log / --no-detach flags）
│   └── client/main.go      # CLI 调试工具（-addr / -watch / -interval）
├── daemon/daemon.go         # stdio 脱离 + PID 文件管理
├── proto/proto.go           # 帧协议：WriteFrame / ReadFrame / SendCmd
├── collector/
│   ├── collector.go         # Snapshot 模型 + 采样调度（1s 系统 / 500ms FPS）
│   ├── battery.go           # 电池：sysfs 优先，fallback dumpsys battery
│   ├── cpu.go               # /proc/stat + /sys/.../cpufreq/scaling_cur_freq
│   ├── gpu.go               # /sys/class/kgsl（Adreno）/ mali（Mali），需 root
│   ├── memory.go            # /proc/meminfo
│   ├── thermal.go           # /sys/class/thermal/thermal_zone*/temp，类型关键字匹配
│   └── fps.go               # dumpsys SurfaceFlinger --timestats，500ms 采样
├── server/server.go         # TCP 服务：request-response + stream 推送
├── go.mod                   # module: monitor-daemon，go 1.21，零外部依赖
├── build.sh                 # 构建脚本（见下方构建说明）
└── test_client.py           # Python 测试客户端
```

## 通信协议

**帧格式**：`[4字节大端无符号整数长度][UTF-8 JSON/文本 payload]`，上限 1 MB。

Go 封装在 `proto` 包：
```go
proto.WriteFrame(w, payload)        // 写一帧
proto.ReadFrame(r) ([]byte, error)  // 读一帧
proto.SendCmd(conn, cmd) ([]byte, error) // 发命令收响应（一次性）
```

**持久连接**：服务端对每条连接循环读命令，客户端可复用同一条 TCP 连接发多条命令。

## 命令参考

### 普通请求-响应

```
ping              → "pong"
daemon-version    → {"version":"1.0.0","protocol":"...","port":9876}
monitor           → Snapshot JSON（见数据模型）
daemon-exit       → {"status":"exiting"}  (triggers graceful process shutdown)
```

### 流式推送

```
stream:<interval_ms>\n<cmd>
```

客户端发一次，服务端按 `interval_ms`（最小 100ms）持续推帧，直到：
- 客户端发送 `@signal:exit`
- TCP 连接断开（写超时 5s 或读错误）

示例：
```
stream:1000\nmonitor     → 每秒推一帧完整 Snapshot
stream:500\nmonitor      → 500ms 推一帧
```

**退出**：客户端发 `@signal:exit` 帧，服务端停止推送并关闭连接。

## 数据模型

`monitor` 命令返回 `Snapshot`（全量）：

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

  "timestamp_ms": 1772881175268
}
```

字段说明：
- `cpu_load`：每核负载 %，按核心序号排列（不含聚合行）
- `cpu_freq`：每核当前频率 MHz
- `battery.current_ma`：**负数 = 放电，正数 = 充电**，root 模式下才有值（HyperOS 等厂商锁死 sysfs）
- `battery.power_mw`：实时功耗 mW = abs(current_ma) × voltage_mv / 1000，current_ma 为 0 时此字段也为 0
- `gpu_freq` / `gpu_load`：需 root 权限读取 `/sys/class/kgsl`，否则为 0

## 权限说明

| 数据项 | shell 权限 | root 权限 |
|--------|-----------|----------|
| CPU 负载 / 频率 | ✅ | ✅ |
| CPU 温度 | ✅ | ✅ |
| 内存 | ✅ | ✅ |
| FPS | ✅ | ✅ |
| 电池容量 / 温度 / 电压 / 状态 | ✅（dumpsys） | ✅（sysfs） |
| 电池电流 / 功率 | ❌（HyperOS 锁死） | ✅（sysfs current_now） |
| GPU 频率 / 负载 | ❌ | ✅ |

**电池电流备注**：Android App 进程可通过 `BatteryManager.getIntProperty(BATTERY_PROPERTY_CURRENT_NOW)` 无 root 获取；native daemon 进程无法调用 Android API，在 HyperOS 等锁定 sysfs 的系统上只能留 0。OpenMonitor App 集成时应在 App 侧补充此字段。

## 采样频率

| 数据项 | 采样间隔 | 方式 |
|--------|---------|------|
| CPU 负载 / 频率 / 温度 | 1s | sysfs |
| GPU 频率 / 负载 | 1s | sysfs |
| 内存 | 1s | /proc/meminfo |
| 电池 | 1s | sysfs → dumpsys battery fallback |
| FPS | 500ms | dumpsys SurfaceFlinger --timestats（EMA 平滑，α=0.4） |

FPS 采集注意事项：
- 启动时执行 `--timestats -enable` + `-clear` 重置历史快照，避免 prevCounts 建立在静止数据上导致 delta 恒为 0
- 自动跟踪前台应用（`dumpsys window displays` 获取 mFocusedApp）
- 无活跃帧时 FPS 每次衰减 5，平滑归零

## 构建与部署

```bash
# 构建全部目标
./build.sh [device_serial]

# 手动构建
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build -o monitor-daemon ./cmd/daemon
GOOS=android GOARCH=arm64 CGO_ENABLED=0 go build -o mcli ./cmd/client
go build -o mcli-mac ./cmd/client   # macOS 本机调试用

# 推送到设备
adb -s <serial> push monitor-daemon /data/local/tmp/monitor-daemon
adb -s <serial> shell chmod 755 /data/local/tmp/monitor-daemon

# 启动（root，推荐）— 自动 daemon 化，立即返回 shell
adb -s <serial> shell su -c /data/local/tmp/monitor-daemon

# 启动（shell，无 GPU 和电池电流）
adb -s <serial> shell /data/local/tmp/monitor-daemon

# 启动（开发模式，前台运行，日志输出到 stdout）
./monitor-daemon --no-detach

# 停止（通过 TCP 命令优雅退出）
./mcli-mac daemon-exit

# 端口转发（macOS 本机调试）
adb -s <serial> forward tcp:9876 tcp:9876
```

## CLI 工具（mcli / mcli-mac）

```bash
# 单次查询
./mcli-mac monitor
./mcli-mac ping
./mcli-mac daemon-version

# 流式监控（每秒刷新）
./mcli-mac -watch monitor
./mcli-mac -watch -interval 500ms monitor

# 连接远端
./mcli-mac -addr 192.168.x.x:9876 -watch monitor
```

## OpenMonitor App 集成规划

daemon 作为可选的高权限数据源，App 通过 TCP 连接本机 127.0.0.1:9876 获取数据，并在以下场景补充 daemon 的盲区：

| 数据项 | 集成方式 |
|--------|---------|
| 电池电流 / 功率 | App 侧用 `BatteryManager.getIntProperty(BATTERY_PROPERTY_CURRENT_NOW)` 补充 |
| GPU 数据（无 root） | App 侧通过 OpenGL ES / Vulkan API 估算，或展示 N/A |
| daemon 可用性检测 | 启动时 `ping`，超时则降级到纯 App 数据源 |

**推荐集成模式**：
1. App 启动时尝试连接 daemon（`ping`，1s 超时）
2. 连接成功：发 `stream:1000\nmonitor`，接收推帧，合并 App 侧电池电流后更新 UI
3. 连接失败：完全走 App 自有采集路径（现有 DataSource 体系）
4. daemon 断连：自动降级，不影响 App 正常功能

**协议实现参考**（Kotlin）：
```kotlin
// 帧读写与 proto/proto.go 完全一致
fun writeFrame(os: OutputStream, payload: ByteArray) {
    val len = ByteBuffer.allocate(4).putInt(payload.size).array()
    os.write(len)
    os.write(payload)
}

fun readFrame(is: InputStream): ByteArray {
    val lenBuf = ByteArray(4)
    is.readFully(lenBuf)
    val len = ByteBuffer.wrap(lenBuf).int
    val buf = ByteArray(len)
    is.readFully(buf)
    return buf
}
```
