package com.carilt01.irisframegen.client.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;

public class Device {
    private final VkDevice vkDevice;

    private static final Logger LOGGER = LoggerFactory.getLogger(Device.class);

    public Device(PhysDevice physDevice) {
        LOGGER.info("Creating VkDevice");

        try (var stack = MemoryStack.stackPush()) {
            PointerBuffer reqExtensions = createReqExtensions(physDevice, stack);

            // Enable all queue families
            var queuePropsBuff = physDevice.getVkQueueFamilyProps();
            int numQueueFamilies = queuePropsBuff.capacity();
            var queueCreationInfoBuf = VkDeviceQueueCreateInfo.calloc(numQueueFamilies, stack);
            for (int i = 0; i < numQueueFamilies; i++) {
                FloatBuffer priorities = stack.callocFloat(queuePropsBuff.get(i).queueCount());
                queueCreationInfoBuf.get(i)
                        .sType$Default()
                        .queueFamilyIndex(i)
                        .pQueuePriorities(priorities);
            }
            // setup required features
            var features13 = VkPhysicalDeviceVulkan13Features.calloc(stack)
                    .sType$Default()
                    .dynamicRendering(true)
                    .synchronization2(true);

            var features2 = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
            features2.pNext(features13.address());

            var deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(features2.address())
                    .ppEnabledExtensionNames(reqExtensions)
                    .pQueueCreateInfos(queueCreationInfoBuf);

            PointerBuffer pp = stack.mallocPointer(1);
            vkCheck(vkCreateDevice(physDevice.getVkPhysicalDevice(), deviceCreateInfo, null, pp),
                    "Failed to create device");
            vkDevice = new VkDevice(pp.get(0), physDevice.getVkPhysicalDevice(), deviceCreateInfo);



        }
    }

    private static PointerBuffer createReqExtensions(PhysDevice physDevice, MemoryStack stack) {
        Set<String> deviceExtensions = getDeviceExtensions(physDevice);
        boolean usePortability = deviceExtensions.contains(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME) && VkUtils.getOS() == OSType.MACOS;
        var extsList = new ArrayList<ByteBuffer>();
        for (String extension : PhysDevice.REQUIRED_EXTENSIONS) {
            extsList.add(stack.ASCII(extension));
        }
        if (usePortability) {
            extsList.add(stack.ASCII(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME));
        }
        PointerBuffer requiredExtensions = stack.mallocPointer(extsList.size());
        extsList.forEach(requiredExtensions::put);
        requiredExtensions.flip();
        return requiredExtensions;
    }

    private static Set<String> getDeviceExtensions(PhysDevice physDevice) {
        Set<String> deviceExtensions = new HashSet<>();
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer numExtensionsBuf = stack.callocInt(1);
            vkEnumerateDeviceExtensionProperties(physDevice.getVkPhysicalDevice(), (String) null, numExtensionsBuf, null);
            int numExtensions = numExtensionsBuf.get(0);
            LOGGER.info("Device supports {} extensions", numExtensions);

            try (var propsBuf = VkExtensionProperties.calloc(numExtensions)) {
                vkEnumerateDeviceExtensionProperties(physDevice.getVkPhysicalDevice(), (String) null, numExtensionsBuf, propsBuf);
                for (int i = 0; i < numExtensions; i++) {
                    VkExtensionProperties props = propsBuf.get(i);
                    String extensionName = props.extensionNameString();
                    deviceExtensions.add(extensionName);
                    LOGGER.info("Supports {}", extensionName);
                }
            }
        }
        return deviceExtensions;
    }

    public void cleanup() {
        vkDestroyDevice(vkDevice, null);
    }

    public VkDevice getVkDevice() {
        return vkDevice;
    }

    public void waitIdle() {
        vkDeviceWaitIdle(vkDevice);
    }

}
