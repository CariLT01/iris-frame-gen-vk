package com.carilt01.irisframegen.client.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceProperties2;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceQueueFamilyProperties2;

public class PhysDevice {
    private final VkExtensionProperties.Buffer vkDeviceExtensions;
    private final VkPhysicalDeviceMemoryProperties vkMemoryProperties;
    private final VkPhysicalDevice vkPhysicalDevice;
    private final VkPhysicalDeviceFeatures vkPhysicalDeviceFeatures;
    private final VkPhysicalDeviceProperties2 vkPhysicalDeviceProperties;
    private final VkQueueFamilyProperties.Buffer vkQueueFamilyProps;

    private static final Logger LOGGER = LoggerFactory.getLogger(PhysDevice.class);

    protected static final Set<String> REQUIRED_EXTENSIONS;

    static {
        REQUIRED_EXTENSIONS = new HashSet<>();
        REQUIRED_EXTENSIONS.add(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME);
    }

    private PhysDevice(VkPhysicalDevice vkPhysicalDevice) {
        try (var stack = MemoryStack.stackPush()) {
            this.vkPhysicalDevice = vkPhysicalDevice;

            IntBuffer intBuffer = stack.mallocInt(1);

            // get physical properties
            vkPhysicalDeviceProperties = VkPhysicalDeviceProperties2.calloc().sType$Default();
            vkGetPhysicalDeviceProperties2(vkPhysicalDevice, vkPhysicalDeviceProperties);

            // Gget device extensions
            vkCheck(vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, (String) null, intBuffer, null),
                    "Failed to get device extensions");
            vkDeviceExtensions = VkExtensionProperties.calloc(intBuffer.get(0));
            vkCheck(vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, (String) null, intBuffer, vkDeviceExtensions),
                    "Failed to get extension properties");

            // Get queue family props
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, null);
            vkQueueFamilyProps = VkQueueFamilyProperties.calloc(intBuffer.get(0));
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, vkQueueFamilyProps);

            vkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc();
            vkGetPhysicalDeviceFeatures(vkPhysicalDevice, vkPhysicalDeviceFeatures);

            // get memory information
            vkMemoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
            vkGetPhysicalDeviceMemoryProperties(vkPhysicalDevice, vkMemoryProperties);
        }
    }

    public static PhysDevice createPhysicalDevice(Instance instance, String prefDeviceName) {
        LOGGER.info("Selecting physical devices");
        PhysDevice result = null;
        try (var stack = MemoryStack.stackPush()) {
            PointerBuffer pPhysicalDevices = getPhysicalDevice(instance, stack);
            int numDevices = pPhysicalDevices.capacity();

            var physDevices = new ArrayList<PhysDevice>();
            for (int i = 0; i < numDevices; i++) {
                var vkPhysicalDevice = new VkPhysicalDevice(pPhysicalDevices.get(i), instance.getVkInstance());
                var physDevice = new PhysDevice(vkPhysicalDevice);

                String deviceName = physDevice.getDeviceName();
                if (!physDevice.hasGraphicsQueueFamily()) {
                    LOGGER.info("Device {} does not support graphics queue", deviceName);
                    physDevice.cleanup();
                    continue;
                }

                if (!physDevice.supportsExtensions(REQUIRED_EXTENSIONS)) {
                    LOGGER.info("Device {} does not support required extensions", deviceName);
                    physDevice.cleanup();
                    continue;
                }

                if (prefDeviceName != null && prefDeviceName.equals(deviceName)) {
                    result = physDevice;
                    break;
                }

                if (physDevice.vkPhysicalDeviceProperties.properties().deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
                    physDevices.addFirst(physDevice);
                } else {
                    physDevices.add(physDevice);
                }
            }

            result = result == null && !physDevices.isEmpty() ? physDevices.removeFirst() : result;
            physDevices.forEach(PhysDevice::cleanup);
            if (result == null) {
                throw new RuntimeException("No suitable physical devices found, supporting vulkan and required extensions");
            }
            LOGGER.info("Selected device: {}", result.getDeviceName());
        }

        return result;
    }

    protected static PointerBuffer getPhysicalDevice(Instance instance, MemoryStack stack) {
        PointerBuffer pPhysicalDevices;
        IntBuffer intBuffer = stack.mallocInt(1);
        vkCheck(vkEnumeratePhysicalDevices(instance.getVkInstance(), intBuffer, null),
                "Failed to get the number of physical devices");
        int numDevices = intBuffer.get(0);
        LOGGER.info("detected {} physical devices", numDevices);

        pPhysicalDevices = stack.mallocPointer(numDevices);
        vkCheck(vkEnumeratePhysicalDevices(instance.getVkInstance(), intBuffer, pPhysicalDevices),
                "Failed to get physical devices");
        return pPhysicalDevices;
    }

    public void cleanup() {
        LOGGER.info("Destroying physical device: {}", getDeviceName());
        vkMemoryProperties.free();
        vkPhysicalDeviceFeatures.free();
        vkQueueFamilyProps.free();
        vkDeviceExtensions.free();
        vkPhysicalDeviceProperties.free();
    }

    public boolean supportsExtensions(Set<String> extensions) {
        var copyExtensions = new HashSet<>(extensions);
        int numExtensions = vkDeviceExtensions != null ? vkDeviceExtensions.capacity() : 0;
        for (int i = 0; i < numExtensions; i++) {
            String extensionName = vkDeviceExtensions.get(i).extensionNameString();
            copyExtensions.remove(extensionName);
        }
        boolean result = copyExtensions.isEmpty();
        if (!result) {
            LOGGER.info("At least {} extensions not supported by device", copyExtensions.size());
        }
        return result;
    }

    private boolean hasGraphicsQueueFamily() {
        boolean result = false;
        int numQueueFamilies = vkQueueFamilyProps != null ? vkQueueFamilyProps.capacity() : 0;
        for (int i = 0; i < numQueueFamilies; i++) {
            VkQueueFamilyProperties familyProps = vkQueueFamilyProps.get(i);
            if ((familyProps.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                result = true;
                break;
            }
        }
        return result;
    }

    // getters
    public String getDeviceName() {
        return vkPhysicalDeviceProperties.properties().deviceNameString();
    }

    public VkPhysicalDeviceMemoryProperties getVkMemoryProperties() {
        return vkMemoryProperties;
    }

    public VkPhysicalDevice getVkPhysicalDevice() {
        return vkPhysicalDevice;
    }

    public VkPhysicalDeviceFeatures getVkPhysicalDeviceFeatures() {
        return vkPhysicalDeviceFeatures;
    }

    public VkPhysicalDeviceProperties2 getVkPhysicalDeviceProperties() {
        return vkPhysicalDeviceProperties;
    }

    public VkQueueFamilyProperties.Buffer getVkQueueFamilyProps() {
        return vkQueueFamilyProps;
    }



}
