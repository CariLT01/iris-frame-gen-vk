package com.carilt01.irisframegen.client.vk;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkExportSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreGetWin32HandleInfoKHR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.LongBuffer;

import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.KHRExternalSemaphoreCapabilities.VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT_KHR;
import static org.lwjgl.vulkan.KHRExternalSemaphoreWin32.vkGetSemaphoreWin32HandleKHR;
import static org.lwjgl.vulkan.VK10.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK10.vkDestroySemaphore;

public class Semaphore {

    private static final Logger LOGGER = LoggerFactory.getLogger(Semaphore.class);

    private final long vkSemaphore;

    public Semaphore(VkCtx vkCtx, boolean exportable) {
        try (var stack = MemoryStack.stackPush()) {
            VkExportSemaphoreCreateInfo exportInfo = null;
            if (exportable) {
                exportInfo = VkExportSemaphoreCreateInfo.calloc(stack)
                        .sType$Default()
                        .handleTypes(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT_KHR);
            }

            VkSemaphoreCreateInfo semaphoreCreateInfo;
            if (!exportable) {
                semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            } else {
                semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack).sType$Default().pNext(exportInfo);
            }

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateSemaphore(vkCtx.getDevice().getVkDevice(), semaphoreCreateInfo, null, lp),
                    "Failed to create semaphore");
            vkSemaphore = lp.get(0);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        vkDestroySemaphore(vkCtx.getDevice().getVkDevice(), vkSemaphore, null);
    }

    public long getVkSemaphore() {
        return vkSemaphore;
    }

    public long getExportHandle(VkCtx vkCtx) {
        try (var stack = MemoryStack.stackPush()) {

            LOGGER.info("vk semaphore address: 0x{}", vkSemaphore);

            var getHandleInfo = VkSemaphoreGetWin32HandleInfoKHR.calloc(stack)
                    .sType$Default()
                    .semaphore(vkSemaphore)
                    .handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_WIN32_BIT_KHR);

            PointerBuffer pb = stack.mallocPointer(1);
            vkGetSemaphoreWin32HandleKHR(vkCtx.getDevice().getVkDevice(), getHandleInfo, pb);

            long handle = pb.get(0);
            LOGGER.info("Handle: 0x{}", handle);
            return handle;
        }

    }
}
