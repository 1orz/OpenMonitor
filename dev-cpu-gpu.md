
  ---
  DevCheck 硬件识别方案分析

  一、SoC 芯片识别（核心机制）

  DevCheck 使用 两级数据源：

  1. assets/socs.json — SoC 型号数据库（990 条）

  以硬件代号为 key，存储 SoC 营销名和参数：

  {
    "SM8750": {"VENDOR":"Qualcomm®", "NAME":"Snapdragon™ 8 Elite", "FAB":"3 nm", "CPU":"6 x Oryon\n2 x Oryon", ...},
    "SM8475": {"VENDOR":"Qualcomm®", "NAME":"Snapdragon™ 8+ Gen 1", "FAB":"4 nm", "CPU":"4 x Cortex-A510\n3 x Cortex-A710\n1 x
  Cortex-X2", ...},
    "MSM8953": {"VENDOR":"Qualcomm®", "NAME":"Snapdragon™ 625", "FAB":"14 nm", ...}
  }

  查找逻辑 (ig0.java):
  1. 获取硬件标识符 uu.l() (如 "SM8750") 和 uu.m() (如 "walt")
  2. 遍历 JSON 所有 key，检查硬件标识是否 contains(key)
  3. 选择最长匹配 key（避免 "SM" 误匹配 "SM8750"）
  4. 第一轮用 uu.l() 匹配，不中则用 uu.m() 再试

  2. 硬件标识获取链 (uu.l(), lines 375-465)

  6级 fallback：
  ro.vendor.soc.model.part_name  →  "SM8750"
  ro.unicpu.model
  ro.vendor.soc.model_name
  ro.soc.model                   →  特殊处理 Exynos
  /proc/cpuinfo Hardware 字段    →  "Qualcomm Technologies, Inc. walt"
  ro.board.platform              →  "walt"
  Build.BOARD                    →  最终兜底

  3. SoC 厂商分类 (uu.r(), line 848-871)

  通过 ro.soc.manufacturer 关键词匹配：
  - Qualcomm: qualcomm/QTI/msm/sm/sdm/qcom
  - MediaTek: mediatek/mt/mtk
  - Samsung: samsung/exynos/universal/s5e
  - HiSilicon: kirin/hisilicon/huawei
  - Google: google
  - 还有 Unisoc/Rockchip/Realtek/AMLogic/JLQ/Intel/Marvell

  ---
  二、CPU 核心型号识别（MIDR 解码）

  uu.o(int midr) (lines 873-1161) 通过解析 ARM MIDR (Main ID Register) 来识别 CPU 核心微架构：

  MIDR 格式: [implementer:8][variant:4][arch:4][partnum:12][revision:4]

  - Implementer 0x41 (ARM): Cortex-A5/A7/A8/A9/A12/A15/A17/A32/A34/A35/A53/A55/A57/A65/A72/A73/A75/A76/A77/A78/A510/A520/A710/A7
  15/A720/A725/X1/X2/X3/X4/X925...
  - Implementer 0x51 (Qualcomm): Scorpion → Krait 200/300/400 → Kryo Gold/Silver → Kryo 670/680 → Oryon / Oryon Phoenix L /
  Oryon Prime
  - Implementer 0x53 (Samsung): Exynos M1/M2/M3/M4/M5
  - Implementer 0x48 (HiSilicon): TaiShan v110/V120/V121
  - Implementer 0x4E (Nvidia): Denver/Denver2/Carmel

  通过 /proc/cpuinfo 每个核心的 CPU implementer 和 CPU part 字段读取 MIDR，配合
  /sys/devices/system/cpu/cpu{i}/cpufreq/cpuinfo_min_freq + cpuinfo_max_freq 获取频率范围。

  ---
  三、设备营销名识别

  assets/databases/devices.db — SQLite 数据库（50,698 条）

  Schema: CREATE TABLE devices (name, device, model)

  示例数据：
  OnePlus 13  | OP5D0DL1 | PJZ110
  Galaxy S25  | SC-51F   | SC-51F

  三级查询 (g10.A(), lines 25-44):
  // 1. device + model 精确匹配
  WHERE device LIKE ? AND model LIKE ?
  // 2. model 匹配
  WHERE model LIKE ?
  // 3. device 匹配
  WHERE device LIKE ?

  使用 Build.DEVICE 和 Build.MODEL 作为输入参数。

  ---
  四、GPU 识别

  完全依赖 JNI native 代码（GpuBridge.java）：

  native AdrenoInfo nGetAdrenoInfo();  // Adreno 专用
  native MaliInfo nGetMaliInfo();       // Mali 专用
  native long[] nativeGetMaxCuAndFreq(); // 通用 CU + 频率

  Adreno（读取 /dev/kgsl-3d0 设备节点）:
  - chipId: "830v2" — GPU 芯片版本
  - gmemSize: on-chip GMEM 大小 (如 12MB)
  - 需要访问 /dev/kgsl-3d0（root 权限才有完整数据）

  Mali（读取 /sys/ 或 driver 接口）:
  - gpuName: GPU 名称
  - numShaderCores: 着色器核心数
  - numL2bytes, numL2slices, numBusBits

  Vulkan: 独立 native 库 vulkan_info，调用 Vulkan API 枚举设备能力
  OpenGL: 通过 EGL 上下文获取 GL_VENDOR/GL_RENDERER/GL_VERSION

  ---
  五、SoC 厂商 Logo

  uu.p(String socName, Context context) (lines 1163-1278)

  通过 SoC 名称关键词匹配，返回对应 vendor logo drawable：

  ┌───────────────────┬──────────────────────────────────┐
  │      关键词       │               Logo               │
  ├───────────────────┼──────────────────────────────────┤
  │ napdragon/ualcomm │ ic_snapdragon.png                │
  ├───────────────────┼──────────────────────────────────┤
  │ xynos/amsung      │ Samsung Exynos logo (dark/light) │
  ├───────────────────┼──────────────────────────────────┤
  │ irin/hisilicon    │ Kirin logo (dark/light)          │
  ├───────────────────┼──────────────────────────────────┤
  │ ensor/oogle       │ Google Tensor logo               │
  ├───────────────────┼──────────────────────────────────┤
  │ ediatek           │ MediaTek logo                    │
  ├───────────────────┼──────────────────────────────────┤
  │ iaomi             │ Xiaomi XRING logo                │
  ├───────────────────┼──────────────────────────────────┤
  │ nisoc             │ Unisoc logo                      │
  ├───────────────────┼──────────────────────────────────┤
  │ ntel              │ Intel logo                       │
  ├───────────────────┼──────────────────────────────────┤
  │ amd/AMD           │ AMD logo                         │
  ├───────────────────┼──────────────────────────────────┤
  │ ockchip           │ Rockchip logo                    │
  ├───────────────────┼──────────────────────────────────┤
  │ arvell            │ Marvell logo                     │
  ├───────────────────┼──────────────────────────────────┤
  │ amlogic           │ AMLogic logo                     │
  ├───────────────────┼──────────────────────────────────┤
  │ vidia             │ Nvidia logo                      │
  ├───────────────────┼──────────────────────────────────┤
  │ realtek/rtd       │ Realtek logo                     │
  ├───────────────────┼──────────────────────────────────┤
  │ jlq               │ JLQ logo                         │
  └───────────────────┴──────────────────────────────────┘

  所有 logo 都是内置 PNG/vector drawable，支持 dark/light 模式着色。

  ---
  六、总结：DevCheck 方案 vs 之前看的 SOCS repo

  ┌─────────────┬───────────────────────────────┬──────────────────────────────────┐
  │    维度     │       DevCheck 内置方案       │         liuzho/SOCS repo         │
  ├─────────────┼───────────────────────────────┼──────────────────────────────────┤
  │ SoC 数据库  │ socs.json 990条，key=硬件代号 │ 590个独立JSON文件，key=URL编码名 │
  ├─────────────┼───────────────────────────────┼──────────────────────────────────┤
  │ 设备数据库  │ devices.db SQLite 50698条     │ 无                               │
  ├─────────────┼───────────────────────────────┼──────────────────────────────────┤
  │ CPU核心识别 │ MIDR 寄存器解码，精确到微架构 │ 仅存"6 x Oryon" 粗粒度描述       │
  ├─────────────┼───────────────────────────────┼──────────────────────────────────┤
  │ GPU         │ JNI native 实时读取硬件       │ 无                               │
  ├─────────────┼───────────────────────────────┼──────────────────────────────────┤
  │ Logo        │ 15+ 内置 vendor logo          │ 无                               │
  ├─────────────┼───────────────────────────────┼──────────────────────────────────┤
  │ 匹配方式    │ contains + 最长匹配           │ 精确 key 查找                    │
  └─────────────┴───────────────────────────────┴──────────────────────────────────┘

  结论: DevCheck 的 socs.json（990条）比 SOCS repo 更实用——key 直接用硬件代号（SM8750），配合 uu.l() 属性链获取的值直接 contains
   匹配。我们应该直接使用 DevCheck 的 socs.json 而不是 SOCS repo 的文件。

  要继续实现 SoC 数据库集成吗？我建议：
  1. 将 DevCheck 的 socs.json（990条）作为基础数据
  2. 实现类似 uu.l() 的硬件标识获取链
  3. CPU 核心通过 MIDR 解码（我们已有 cpuinfo JNI）
  4. 厂商 logo 用 Material vector icon 替代