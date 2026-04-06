package com.cloudorz.openmonitor.feature.hardware

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun VulkanInfoScreen(vulkanInfoJson: String) {
    val vk = remember(vulkanInfoJson) { parseVulkanJson(vulkanInfoJson) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // GPU Identity
        item {
            VkSectionCard("GPU 标识") {
                val device = vk.device
                VkRow("名称", device.deviceName)
                VkRow("供应商", device.vendorStr)
                VkRow("设备 ID", device.deviceIdHex)
                VkRow("设备类型", device.deviceType)
                VkRow("实例 API", vk.instanceApiVersion)
                VkRow("设备 API", device.apiVersion)
                VkRow("驱动版本", device.driverVersionStr)
                VkRow("设备扩展", "${vk.deviceExtensionsCount}")
                VkRow("实例扩展", "${vk.instanceExtensionsCount}")
            }
        }

        // Memory
        item {
            VkSectionCard("内存") {
                VkRow("设备本地（共享）", vk.memory.totalDeviceLocal)
                vk.memory.heaps.forEachIndexed { i, heap ->
                    VkRow("堆 $i", "${heap.sizeHuman}${if (heap.deviceLocal) " (设备本地)" else ""}")
                }
            }
        }

        // Limits
        item {
            VkSectionCard("限制") {
                val lim = vk.limits
                VkRow("最大 1D 图像", lim.maxImageDimension1D)
                VkRow("最大 2D 图像", "${lim.maxImageDimension2D} x ${lim.maxImageDimension2D}")
                VkRow("最大 3D 图像", "${lim.maxImageDimension3D} x ${lim.maxImageDimension3D} x ${lim.maxImageDimension3D}")
                VkRow("最大立方体图像", "${lim.maxImageDimensionCube} x ${lim.maxImageDimensionCube}")
                VkRow("最大数组层", lim.maxImageArrayLayers)
                VkRow("Uniform 缓冲区范围", formatBytes(lim.maxUniformBufferRange.toLong()))
                VkRow("存储缓冲区范围", formatBytes(lim.maxStorageBufferRange.toLong()))
                VkRow("最大各向异性", "%.0f".format(lim.maxSamplerAnisotropy))
                VkRow("最大计算调用", lim.maxComputeWorkGroupInvocations)
                VkRow("最大内存分配", lim.maxMemoryAllocationCount)
                VkRow("最大采样器分配", lim.maxSamplerAllocationCount)
            }
        }

        // Core Features
        item {
            VkSectionCard("核心功能") {
                vk.features.forEach { (name, value) ->
                    VkRow(name, if (value) "是" else "否")
                }
            }
        }

        // Queues
        item {
            VkSectionCard("队列") {
                vk.queues.forEachIndexed { i, queue ->
                    VkRow("队列 $i 标志", queue.flags)
                    VkRow("队列 $i 数量", queue.count)
                    VkRow("队列 $i 时间戳位数", queue.timestampBits)
                    if (i < vk.queues.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Device Extensions
        if (vk.deviceExtensions.isNotEmpty()) {
            item {
                VkSectionCard("设备扩展 (${vk.deviceExtensionsCount})") {
                    vk.deviceExtensions.forEach { ext ->
                        Text(
                            text = ext,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }

        // Instance Extensions
        if (vk.instanceExtensions.isNotEmpty()) {
            item {
                VkSectionCard("实例扩展 (${vk.instanceExtensionsCount})") {
                    vk.instanceExtensions.forEach { ext ->
                        Text(
                            text = ext,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ── Shared UI Components ────────────────────────────────────────────────────

@Composable
private fun VkSectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun VkRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.55f),
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> "%.1f KB".format(bytes / (1L shl 10).toDouble())
        else -> "$bytes B"
    }
}

// ── JSON Parsing ────────────────────────────────────────────────────────────

private data class VulkanData(
    val instanceApiVersion: String = "",
    val instanceExtensionsCount: Int = 0,
    val instanceExtensions: List<String> = emptyList(),
    val deviceExtensionsCount: Int = 0,
    val deviceExtensions: List<String> = emptyList(),
    val device: VkDevice = VkDevice(),
    val limits: VkLimits = VkLimits(),
    val features: List<Pair<String, Boolean>> = emptyList(),
    val memory: VkMemory = VkMemory(),
    val queues: List<VkQueue> = emptyList(),
)

private data class VkDevice(
    val deviceName: String = "",
    val vendorStr: String = "",
    val deviceIdHex: String = "",
    val deviceType: String = "",
    val apiVersion: String = "",
    val driverVersionStr: String = "",
)

private data class VkLimits(
    val maxImageDimension1D: String = "0",
    val maxImageDimension2D: String = "0",
    val maxImageDimension3D: String = "0",
    val maxImageDimensionCube: String = "0",
    val maxImageArrayLayers: String = "0",
    val maxUniformBufferRange: Int = 0,
    val maxStorageBufferRange: Int = 0,
    val maxSamplerAnisotropy: Float = 0f,
    val maxComputeWorkGroupInvocations: String = "0",
    val maxMemoryAllocationCount: String = "0",
    val maxSamplerAllocationCount: String = "0",
)

private data class VkMemory(
    val totalDeviceLocal: String = "",
    val heaps: List<VkHeap> = emptyList(),
)

private data class VkHeap(
    val sizeHuman: String = "",
    val deviceLocal: Boolean = false,
)

private data class VkQueue(
    val flags: String = "",
    val count: String = "0",
    val timestampBits: String = "0",
)

private fun parseVulkanJson(json: String): VulkanData {
    return try {
        val root = JSONObject(json)

        val instApiVer = root.optString("instanceApiVersion", "")
        val instExtCount = root.optInt("instanceExtensionsCount", 0)
        val instExtsStr = root.optString("instanceExtensions", "")
        val instExts = if (instExtsStr.isNotBlank()) instExtsStr.split(" ").filter { it.isNotBlank() } else emptyList()

        val devExtCount = root.optInt("deviceExtensionsCount", 0)
        val devExtsStr = root.optString("deviceExtensions", "")
        val devExts = if (devExtsStr.isNotBlank()) devExtsStr.split(" ").filter { it.isNotBlank() }.sorted() else emptyList()

        // Device
        val deviceObj = root.optJSONObject("device")
        val device = if (deviceObj != null) VkDevice(
            deviceName = deviceObj.optString("deviceName", ""),
            vendorStr = deviceObj.optString("vendorStr", ""),
            deviceIdHex = deviceObj.optString("deviceIdHex", ""),
            deviceType = deviceObj.optString("deviceType", ""),
            apiVersion = deviceObj.optString("apiVersion", ""),
            driverVersionStr = deviceObj.optString("driverVersionStr", ""),
        ) else VkDevice()

        // Limits
        val limitsObj = deviceObj?.optJSONObject("limits")
        val limits = if (limitsObj != null) VkLimits(
            maxImageDimension1D = limitsObj.optString("maxImageDimension1D", "0"),
            maxImageDimension2D = limitsObj.optString("maxImageDimension2D", "0"),
            maxImageDimension3D = limitsObj.optString("maxImageDimension3D", "0"),
            maxImageDimensionCube = limitsObj.optString("maxImageDimensionCube", "0"),
            maxImageArrayLayers = limitsObj.optString("maxImageArrayLayers", "0"),
            maxUniformBufferRange = limitsObj.optInt("maxUniformBufferRange", 0),
            maxStorageBufferRange = limitsObj.optInt("maxStorageBufferRange", 0),
            maxSamplerAnisotropy = limitsObj.optDouble("maxSamplerAnisotropy", 0.0).toFloat(),
            maxComputeWorkGroupInvocations = limitsObj.optString("maxComputeWorkGroupInvocations", "0"),
            maxMemoryAllocationCount = limitsObj.optString("maxMemoryAllocationCount", "0"),
            maxSamplerAllocationCount = limitsObj.optString("maxSamplerAllocationCount", "0"),
        ) else VkLimits()

        // Features
        val featuresObj = root.optJSONObject("features")
        val features = mutableListOf<Pair<String, Boolean>>()
        if (featuresObj != null) {
            val featureNames = mapOf(
                "robustBufferAccess" to "健壮缓冲区访问",
                "samplerAnisotropy" to "采样器各向异性",
                "geometryShader" to "几何着色器",
                "tessellationShader" to "曲面细分着色器",
                "multiViewport" to "多视口",
                "dualSrcBlend" to "双源混合",
                "depthClamp" to "深度钳位",
                "wideLines" to "宽线条",
                "largePoints" to "大点",
            )
            for ((key, label) in featureNames) {
                if (featuresObj.has(key)) {
                    features.add(label to featuresObj.optBoolean(key, false))
                }
            }
        }

        // Memory
        val memoryObj = root.optJSONObject("memory")
        val memory = if (memoryObj != null) {
            val heapsArr = memoryObj.optJSONArray("heaps")
            val heaps = mutableListOf<VkHeap>()
            if (heapsArr != null) {
                for (i in 0 until heapsArr.length()) {
                    val h = heapsArr.optJSONObject(i)
                    if (h != null) {
                        heaps.add(VkHeap(
                            sizeHuman = h.optString("sizeHuman", ""),
                            deviceLocal = h.optBoolean("deviceLocal", false),
                        ))
                    }
                }
            }
            VkMemory(
                totalDeviceLocal = memoryObj.optString("totalDeviceLocal", ""),
                heaps = heaps,
            )
        } else VkMemory()

        // Queues
        val queuesArr = root.optJSONArray("queues")
        val queues = mutableListOf<VkQueue>()
        if (queuesArr != null) {
            for (i in 0 until queuesArr.length()) {
                val q = queuesArr.optJSONObject(i)
                if (q != null) {
                    queues.add(VkQueue(
                        flags = q.optString("flags", ""),
                        count = q.optString("count", "0"),
                        timestampBits = q.optString("timestampBits", "0"),
                    ))
                }
            }
        }

        VulkanData(
            instanceApiVersion = instApiVer,
            instanceExtensionsCount = instExtCount,
            instanceExtensions = instExts,
            deviceExtensionsCount = devExtCount,
            deviceExtensions = devExts,
            device = device,
            limits = limits,
            features = features,
            memory = memory,
            queues = queues,
        )
    } catch (e: Exception) {
        VulkanData()
    }
}
