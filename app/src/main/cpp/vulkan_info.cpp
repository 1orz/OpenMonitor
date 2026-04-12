#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string>
#include <sstream>
#include <vector>
#include <cstring>

#define LOG_TAG "vulkan-info"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Vulkan types and constants (avoid requiring vulkan.h)
typedef uint32_t VkFlags;
typedef uint32_t VkBool32;
typedef uint64_t VkDeviceSize;
typedef void* VkInstance;
typedef void* VkPhysicalDevice;

struct VkApplicationInfo {
    uint32_t sType; // VK_STRUCTURE_TYPE_APPLICATION_INFO = 0
    const void* pNext;
    const char* pApplicationName;
    uint32_t applicationVersion;
    const char* pEngineName;
    uint32_t engineVersion;
    uint32_t apiVersion;
};

struct VkInstanceCreateInfo {
    uint32_t sType; // VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO = 1
    const void* pNext;
    VkFlags flags;
    const VkApplicationInfo* pApplicationInfo;
    uint32_t enabledLayerCount;
    const char* const* ppEnabledLayerNames;
    uint32_t enabledExtensionCount;
    const char* const* ppEnabledExtensionNames;
};

struct VkPhysicalDeviceLimits {
    uint32_t maxImageDimension1D;
    uint32_t maxImageDimension2D;
    uint32_t maxImageDimension3D;
    uint32_t maxImageDimensionCube;
    uint32_t maxImageArrayLayers;
    uint32_t maxTexelBufferElements;
    uint32_t maxUniformBufferRange;
    uint32_t maxStorageBufferRange;
    uint32_t maxPushConstantsSize;
    uint32_t maxMemoryAllocationCount;
    uint32_t maxSamplerAllocationCount;
    VkDeviceSize bufferImageGranularity;
    VkDeviceSize sparseAddressSpaceSize;
    uint32_t maxBoundDescriptorSets;
    uint32_t maxPerStageDescriptorSamplers;
    uint32_t maxPerStageDescriptorUniformBuffers;
    uint32_t maxPerStageDescriptorStorageBuffers;
    uint32_t maxPerStageDescriptorSampledImages;
    uint32_t maxPerStageDescriptorStorageImages;
    uint32_t maxPerStageDescriptorInputAttachments;
    uint32_t maxPerStageResources;
    uint32_t maxDescriptorSetSamplers;
    uint32_t maxDescriptorSetUniformBuffers;
    uint32_t maxDescriptorSetUniformBuffersDynamic;
    uint32_t maxDescriptorSetStorageBuffers;
    uint32_t maxDescriptorSetStorageBuffersDynamic;
    uint32_t maxDescriptorSetSampledImages;
    uint32_t maxDescriptorSetStorageImages;
    uint32_t maxDescriptorSetInputAttachments;
    uint32_t maxVertexInputAttributes;
    uint32_t maxVertexInputBindings;
    uint32_t maxVertexInputAttributeOffset;
    uint32_t maxVertexInputBindingStride;
    uint32_t maxVertexOutputComponents;
    uint32_t maxTessellationGenerationLevel;
    uint32_t maxTessellationPatchSize;
    uint32_t maxTessellationControlPerVertexInputComponents;
    uint32_t maxTessellationControlPerVertexOutputComponents;
    uint32_t maxTessellationControlPerPatchOutputComponents;
    uint32_t maxTessellationControlTotalOutputComponents;
    uint32_t maxTessellationEvaluationInputComponents;
    uint32_t maxTessellationEvaluationOutputComponents;
    uint32_t maxGeometryShaderInvocations;
    uint32_t maxGeometryInputComponents;
    uint32_t maxGeometryOutputComponents;
    uint32_t maxGeometryOutputVertices;
    uint32_t maxGeometryTotalOutputComponents;
    uint32_t maxFragmentInputComponents;
    uint32_t maxFragmentOutputAttachments;
    uint32_t maxFragmentDualSrcAttachments;
    uint32_t maxFragmentCombinedOutputResources;
    uint32_t maxComputeSharedMemorySize;
    uint32_t maxComputeWorkGroupCount[3];
    uint32_t maxComputeWorkGroupInvocations;
    uint32_t maxComputeWorkGroupSize[3];
    uint32_t subPixelPrecisionBits;
    uint32_t subTexelPrecisionBits;
    uint32_t mipmapPrecisionBits;
    uint32_t maxDrawIndexedIndexValue;
    uint32_t maxDrawIndirectCount;
    float maxSamplerLodBias;
    float maxSamplerAnisotropy;
    uint32_t maxViewports;
    uint32_t maxViewportDimensions[2];
    float viewportBoundsRange[2];
    uint32_t viewportSubPixelBits;
    // remaining fields omitted for brevity
    char _padding[256];
};

struct VkPhysicalDeviceSparseProperties {
    VkBool32 residencyStandard2DBlockShape;
    VkBool32 residencyStandard2DMultisampleBlockShape;
    VkBool32 residencyStandard3DBlockShape;
    VkBool32 residencyAlignedMipSize;
    VkBool32 residencyNonResidentStrict;
};

struct VkPhysicalDeviceProperties {
    uint32_t apiVersion;
    uint32_t driverVersion;
    uint32_t vendorID;
    uint32_t deviceID;
    uint32_t deviceType;
    char deviceName[256];
    uint8_t pipelineCacheUUID[16];
    VkPhysicalDeviceLimits limits;
    VkPhysicalDeviceSparseProperties sparseProperties;
};

struct VkPhysicalDeviceFeatures {
    VkBool32 robustBufferAccess;
    VkBool32 fullDrawIndexUint32;
    VkBool32 imageCubeArray;
    VkBool32 independentBlend;
    VkBool32 geometryShader;
    VkBool32 tessellationShader;
    VkBool32 sampleRateShading;
    VkBool32 dualSrcBlend;
    VkBool32 logicOp;
    VkBool32 multiDrawIndirect;
    VkBool32 drawIndirectFirstInstance;
    VkBool32 depthClamp;
    VkBool32 depthBiasClamp;
    VkBool32 fillModeNonSolid;
    VkBool32 depthBounds;
    VkBool32 wideLines;
    VkBool32 largePoints;
    VkBool32 alphaToOne;
    VkBool32 multiViewport;
    VkBool32 samplerAnisotropy;
    // ... more fields
    char _padding[256];
};

struct VkExtensionProperties {
    char extensionName[256];
    uint32_t specVersion;
};

struct VkMemoryType {
    VkFlags propertyFlags;
    uint32_t heapIndex;
};

struct VkMemoryHeap {
    VkDeviceSize size;
    VkFlags flags;
};

struct VkPhysicalDeviceMemoryProperties {
    uint32_t memoryTypeCount;
    VkMemoryType memoryTypes[32];
    uint32_t memoryHeapCount;
    VkMemoryHeap memoryHeaps[16];
};

struct VkQueueFamilyProperties {
    VkFlags queueFlags;
    uint32_t queueCount;
    uint32_t timestampValidBits;
    uint32_t minImageTransferGranularity[3];
};

// Function pointer types
typedef uint32_t (*PFN_vkEnumerateInstanceVersion)(uint32_t*);
typedef uint32_t (*PFN_vkCreateInstance)(const VkInstanceCreateInfo*, const void*, VkInstance*);
typedef void (*PFN_vkDestroyInstance)(VkInstance, const void*);
typedef uint32_t (*PFN_vkEnumeratePhysicalDevices)(VkInstance, uint32_t*, VkPhysicalDevice*);
typedef void (*PFN_vkGetPhysicalDeviceProperties)(VkPhysicalDevice, VkPhysicalDeviceProperties*);
typedef void (*PFN_vkGetPhysicalDeviceFeatures)(VkPhysicalDevice, VkPhysicalDeviceFeatures*);
typedef void (*PFN_vkGetPhysicalDeviceMemoryProperties)(VkPhysicalDevice, VkPhysicalDeviceMemoryProperties*);
typedef void (*PFN_vkGetPhysicalDeviceQueueFamilyProperties)(VkPhysicalDevice, uint32_t*, VkQueueFamilyProperties*);
typedef uint32_t (*PFN_vkEnumerateDeviceExtensionProperties)(VkPhysicalDevice, const char*, uint32_t*, VkExtensionProperties*);
typedef uint32_t (*PFN_vkEnumerateInstanceExtensionProperties)(const char*, uint32_t*, VkExtensionProperties*);

static std::string formatSize(uint64_t bytes) {
    char buf[64];
    if (bytes >= (1ULL << 30))
        snprintf(buf, sizeof(buf), "%.2f GB", bytes / (double)(1ULL << 30));
    else if (bytes >= (1ULL << 20))
        snprintf(buf, sizeof(buf), "%.2f MB", bytes / (double)(1ULL << 20));
    else if (bytes >= (1ULL << 10))
        snprintf(buf, sizeof(buf), "%.2f KB", bytes / (double)(1ULL << 10));
    else
        snprintf(buf, sizeof(buf), "%llu B", (unsigned long long)bytes);
    return buf;
}

static std::string deviceTypeStr(uint32_t type) {
    switch (type) {
        case 0: return "Other";
        case 1: return "Integrated GPU";
        case 2: return "Discrete GPU";
        case 3: return "Virtual GPU";
        case 4: return "CPU";
        default: return "Unknown";
    }
}

static std::string queueFlagsStr(uint32_t flags) {
    std::string s;
    if (flags & 0x01) { if (!s.empty()) s += ", "; s += "graphics"; }
    if (flags & 0x02) { if (!s.empty()) s += ", "; s += "compute"; }
    if (flags & 0x04) { if (!s.empty()) s += ", "; s += "transfer"; }
    if (flags & 0x08) { if (!s.empty()) s += ", "; s += "sparse"; }
    if (flags & 0x10) { if (!s.empty()) s += ", "; s += "protected"; }
    return s.empty() ? "none" : s;
}

static std::string vkVersionStr(uint32_t ver) {
    char buf[32];
    snprintf(buf, sizeof(buf), "%u.%u.%u", (ver >> 22) & 0x3FF, (ver >> 12) & 0x3FF, ver & 0xFFF);
    return buf;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_cloudorz_openmonitor_core_data_datasource_VulkanInfoBridge_nGetVulkanInfoJson(JNIEnv *env, jclass) {
    void *libvulkan = dlopen("libvulkan.so", RTLD_NOW);
    if (!libvulkan) {
        LOGE("Failed to load libvulkan.so");
        return env->NewStringUTF("{}");
    }

    auto vkEnumerateInstanceVersion = (PFN_vkEnumerateInstanceVersion)dlsym(libvulkan, "vkEnumerateInstanceVersion");
    auto vkCreateInstance = (PFN_vkCreateInstance)dlsym(libvulkan, "vkCreateInstance");
    auto vkDestroyInstance = (PFN_vkDestroyInstance)dlsym(libvulkan, "vkDestroyInstance");
    auto vkEnumeratePhysicalDevices = (PFN_vkEnumeratePhysicalDevices)dlsym(libvulkan, "vkEnumeratePhysicalDevices");
    auto vkGetPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)dlsym(libvulkan, "vkGetPhysicalDeviceProperties");
    auto vkGetPhysicalDeviceFeatures = (PFN_vkGetPhysicalDeviceFeatures)dlsym(libvulkan, "vkGetPhysicalDeviceFeatures");
    auto vkGetPhysicalDeviceMemoryProperties = (PFN_vkGetPhysicalDeviceMemoryProperties)dlsym(libvulkan, "vkGetPhysicalDeviceMemoryProperties");
    auto vkGetPhysicalDeviceQueueFamilyProperties = (PFN_vkGetPhysicalDeviceQueueFamilyProperties)dlsym(libvulkan, "vkGetPhysicalDeviceQueueFamilyProperties");
    auto vkEnumerateDeviceExtensionProperties = (PFN_vkEnumerateDeviceExtensionProperties)dlsym(libvulkan, "vkEnumerateDeviceExtensionProperties");
    auto vkEnumerateInstanceExtensionProperties = (PFN_vkEnumerateInstanceExtensionProperties)dlsym(libvulkan, "vkEnumerateInstanceExtensionProperties");

    if (!vkCreateInstance || !vkDestroyInstance || !vkEnumeratePhysicalDevices ||
        !vkGetPhysicalDeviceProperties || !vkGetPhysicalDeviceFeatures) {
        LOGE("Failed to resolve Vulkan functions");
        dlclose(libvulkan);
        return env->NewStringUTF("{}");
    }

    // Instance API version
    uint32_t instanceApiVersion = (1 << 22) | (0 << 12); // 1.0.0 fallback
    if (vkEnumerateInstanceVersion) {
        vkEnumerateInstanceVersion(&instanceApiVersion);
    }

    // Create instance
    VkApplicationInfo appInfo = {};
    appInfo.sType = 0; // VK_STRUCTURE_TYPE_APPLICATION_INFO
    appInfo.pApplicationName = "OpenMonitor";
    appInfo.apiVersion = instanceApiVersion;

    VkInstanceCreateInfo createInfo = {};
    createInfo.sType = 1; // VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO
    createInfo.pApplicationInfo = &appInfo;

    VkInstance instance = nullptr;
    if (vkCreateInstance(&createInfo, nullptr, &instance) != 0) {
        LOGE("vkCreateInstance failed");
        dlclose(libvulkan);
        return env->NewStringUTF("{}");
    }

    // Instance extensions
    uint32_t instExtCount = 0;
    std::vector<VkExtensionProperties> instExts;
    std::string instExtsStr;
    if (vkEnumerateInstanceExtensionProperties) {
        vkEnumerateInstanceExtensionProperties(nullptr, &instExtCount, nullptr);
        if (instExtCount > 0) {
            instExts.resize(instExtCount);
            vkEnumerateInstanceExtensionProperties(nullptr, &instExtCount, instExts.data());
            for (uint32_t i = 0; i < instExtCount; i++) {
                if (i > 0) instExtsStr += " ";
                instExtsStr += instExts[i].extensionName;
            }
        }
    }

    // Enumerate physical devices
    uint32_t deviceCount = 0;
    vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);

    std::ostringstream json;
    json << "{";
    json << "\"instanceApiVersion\":\"" << vkVersionStr(instanceApiVersion) << "\"";
    json << ",\"instanceExtensionsCount\":" << instExtCount;
    json << ",\"instanceExtensions\":\"" << instExtsStr << "\"";

    if (deviceCount > 0) {
        std::vector<VkPhysicalDevice> devices(deviceCount);
        vkEnumeratePhysicalDevices(instance, &deviceCount, devices.data());

        VkPhysicalDevice device = devices[0];

        // Properties
        VkPhysicalDeviceProperties props = {};
        vkGetPhysicalDeviceProperties(device, &props);

        json << ",\"device\":{";
        json << "\"deviceName\":\"" << props.deviceName << "\"";
        json << ",\"vendorId\":" << props.vendorID;

        // Vendor string
        std::string vendorStr;
        switch (props.vendorID) {
            case 0x1002: vendorStr = "AMD"; break;
            case 0x10DE: vendorStr = "NVIDIA"; break;
            case 0x13B5: vendorStr = "ARM"; break;
            case 0x5143: vendorStr = "Qualcomm"; break;
            case 0x8086: vendorStr = "Intel"; break;
            case 0x1010: vendorStr = "ImgTec"; break;
            default:
                char vbuf[16];
                snprintf(vbuf, sizeof(vbuf), "0x%04X", props.vendorID);
                vendorStr = vbuf;
        }
        json << ",\"vendorStr\":\"" << vendorStr << "\"";

        char devIdHex[16];
        snprintf(devIdHex, sizeof(devIdHex), "0x%08X", props.deviceID);
        json << ",\"deviceId\":" << props.deviceID;
        json << ",\"deviceIdHex\":\"" << devIdHex << "\"";
        json << ",\"deviceType\":\"" << deviceTypeStr(props.deviceType) << "\"";
        json << ",\"apiVersion\":\"" << vkVersionStr(props.apiVersion) << "\"";
        json << ",\"driverVersion\":" << props.driverVersion;

        // Driver version (Qualcomm format: maj.min.patch encoded)
        char drvVer[32];
        uint32_t dv = props.driverVersion;
        if (props.vendorID == 0x5143) {
            // Qualcomm: major.minor.patch
            snprintf(drvVer, sizeof(drvVer), "%u.%u.%u", (dv >> 22) & 0x3FF, (dv >> 12) & 0x3FF, dv & 0xFFF);
        } else {
            snprintf(drvVer, sizeof(drvVer), "%u.%u.%u", (dv >> 22) & 0x3FF, (dv >> 12) & 0x3FF, dv & 0xFFF);
        }
        json << ",\"driverVersionStr\":\"" << drvVer << "\"";

        // Limits
        json << ",\"limits\":{";
        json << "\"maxImageDimension1D\":" << props.limits.maxImageDimension1D;
        json << ",\"maxImageDimension2D\":" << props.limits.maxImageDimension2D;
        json << ",\"maxImageDimension3D\":" << props.limits.maxImageDimension3D;
        json << ",\"maxImageDimensionCube\":" << props.limits.maxImageDimensionCube;
        json << ",\"maxImageArrayLayers\":" << props.limits.maxImageArrayLayers;
        json << ",\"maxUniformBufferRange\":" << props.limits.maxUniformBufferRange;
        json << ",\"maxStorageBufferRange\":" << props.limits.maxStorageBufferRange;
        json << ",\"maxPushConstantsSize\":" << props.limits.maxPushConstantsSize;
        json << ",\"maxMemoryAllocationCount\":" << props.limits.maxMemoryAllocationCount;
        json << ",\"maxSamplerAllocationCount\":" << props.limits.maxSamplerAllocationCount;
        json << ",\"maxComputeWorkGroupInvocations\":" << props.limits.maxComputeWorkGroupInvocations;
        json << ",\"maxSamplerAnisotropy\":" << props.limits.maxSamplerAnisotropy;
        json << "}";

        json << "}"; // device

        // Features
        VkPhysicalDeviceFeatures features = {};
        vkGetPhysicalDeviceFeatures(device, &features);
        json << ",\"features\":{";
        json << "\"robustBufferAccess\":" << (features.robustBufferAccess ? "true" : "false");
        json << ",\"samplerAnisotropy\":" << (features.samplerAnisotropy ? "true" : "false");
        json << ",\"geometryShader\":" << (features.geometryShader ? "true" : "false");
        json << ",\"tessellationShader\":" << (features.tessellationShader ? "true" : "false");
        json << ",\"multiViewport\":" << (features.multiViewport ? "true" : "false");
        json << ",\"dualSrcBlend\":" << (features.dualSrcBlend ? "true" : "false");
        json << ",\"depthClamp\":" << (features.depthClamp ? "true" : "false");
        json << ",\"wideLines\":" << (features.wideLines ? "true" : "false");
        json << ",\"largePoints\":" << (features.largePoints ? "true" : "false");
        json << "}";

        // Memory
        VkPhysicalDeviceMemoryProperties memProps = {};
        vkGetPhysicalDeviceMemoryProperties(device, &memProps);
        json << ",\"memory\":{";
        json << "\"heapCount\":" << memProps.memoryHeapCount;
        json << ",\"heaps\":[";
        uint64_t totalDeviceLocal = 0;
        for (uint32_t i = 0; i < memProps.memoryHeapCount; i++) {
            if (i > 0) json << ",";
            json << "{\"size\":" << memProps.memoryHeaps[i].size;
            json << ",\"sizeHuman\":\"" << formatSize(memProps.memoryHeaps[i].size) << "\"";
            json << ",\"deviceLocal\":" << ((memProps.memoryHeaps[i].flags & 1) ? "true" : "false");
            json << "}";
            if (memProps.memoryHeaps[i].flags & 1) totalDeviceLocal += memProps.memoryHeaps[i].size;
        }
        json << "],\"totalDeviceLocal\":\"" << formatSize(totalDeviceLocal) << "\"";
        json << "}";

        // Queue families
        uint32_t queueFamilyCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(device, &queueFamilyCount, nullptr);
        std::vector<VkQueueFamilyProperties> queueFamilies(queueFamilyCount);
        vkGetPhysicalDeviceQueueFamilyProperties(device, &queueFamilyCount, queueFamilies.data());
        json << ",\"queues\":[";
        for (uint32_t i = 0; i < queueFamilyCount; i++) {
            if (i > 0) json << ",";
            json << "{\"flags\":\"" << queueFlagsStr(queueFamilies[i].queueFlags) << "\"";
            json << ",\"count\":" << queueFamilies[i].queueCount;
            json << ",\"timestampBits\":" << queueFamilies[i].timestampValidBits;
            json << "}";
        }
        json << "]";

        // Device extensions
        uint32_t devExtCount = 0;
        std::string devExtsStr;
        if (vkEnumerateDeviceExtensionProperties) {
            vkEnumerateDeviceExtensionProperties(device, nullptr, &devExtCount, nullptr);
            if (devExtCount > 0) {
                std::vector<VkExtensionProperties> devExts(devExtCount);
                vkEnumerateDeviceExtensionProperties(device, nullptr, &devExtCount, devExts.data());
                for (uint32_t i = 0; i < devExtCount; i++) {
                    if (i > 0) devExtsStr += " ";
                    devExtsStr += devExts[i].extensionName;
                }
            }
        }
        json << ",\"deviceExtensionsCount\":" << devExtCount;
        json << ",\"deviceExtensions\":\"" << devExtsStr << "\"";
    }

    json << "}";

    vkDestroyInstance(instance, nullptr);
    dlclose(libvulkan);

    return env->NewStringUTF(json.str().c_str());
}
