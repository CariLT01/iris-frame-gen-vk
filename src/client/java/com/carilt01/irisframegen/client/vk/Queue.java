package com.carilt01.irisframegen.client.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;

import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.vkQueueSubmit2;

public class Queue {
    private final int queueFamilyIndex;
    private final VkQueue vkQueue;

    private static final Logger LOGGER = LoggerFactory.getLogger(Queue.class);

    public Queue(VkCtx vkCtx, int queueFamilyIndex, int queueIndex) {
        LOGGER.info("Creating queue");
        this.queueFamilyIndex = queueFamilyIndex;
        try (var stack = MemoryStack.stackPush()) {
            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(vkCtx.getDevice().getVkDevice(), queueFamilyIndex, queueIndex, pQueue);
            long queue = pQueue.get(0);
            vkQueue = new VkQueue(queue, vkCtx.getDevice().getVkDevice());
        }
    }

    public int getQueueFamilyIndex() {
        return this.queueFamilyIndex;
    }

    public VkQueue getVkQueue() {
        return this.vkQueue;
    }

    public void waitForIdle() {
        vkQueueWaitIdle(this.vkQueue);
    }

    public static class GraphicsQueue extends Queue {
        public GraphicsQueue(VkCtx vkCtx, int queueIndex) {
            super(vkCtx, getGraphicsQueueFamilyIndex(vkCtx), queueIndex);
        }

        private static int getGraphicsQueueFamilyIndex(VkCtx vkCtx) {
            int index = -1;
            var queuePropsBuf = vkCtx.getPhysDevice().getVkQueueFamilyProps();
            int numQueueFamilies = queuePropsBuf.capacity();
            for (int i = 0; i < numQueueFamilies; i++) {
                VkQueueFamilyProperties props = queuePropsBuf.get(i);
                boolean graphicsQueue = (props.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0;
                if (graphicsQueue) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                throw new RuntimeException("Failed to get graphics queue family index");
            }
            return index;
        }
    }

    public static class PresentQueue extends Queue {
        public PresentQueue(VkCtx vkCtx, int queueIndex) {
            super(vkCtx, getPresentQueueFamilyIndex(vkCtx), queueIndex);
        }

        private static int getPresentQueueFamilyIndex(VkCtx vkCtx) {
            int index = -1;
            try (var stack = MemoryStack.stackPush()) {
                var queuePropsBuf = vkCtx.getPhysDevice().getVkQueueFamilyProps();
                int numQueueFamilies = queuePropsBuf.capacity();
                IntBuffer intBuf = stack.mallocInt(1);
                for (int i = 0; i < numQueueFamilies; i++) {
                    KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(vkCtx.getPhysDevice().getVkPhysicalDevice(),
                            i, vkCtx.getSurface().getVkSurface(), intBuf);
                    boolean supportsPresentation = intBuf.get(0) == VK_TRUE;
                    if (supportsPresentation) {
                        index = i;
                        break;
                    }
                }
            }
            if (index < 0) {
                throw new RuntimeException("Failed to get queue presentation family index");
            }

            return index;
        }
    }

    public void submit(VkCommandBufferSubmitInfo.Buffer commandBuffers, VkSemaphoreSubmitInfo.Buffer waitSemaphores,
                       VkSemaphoreSubmitInfo.Buffer signalSemaphores, Fence fence) {
        try (var stack = MemoryStack.stackPush()) {

            var submitInfo = VkSubmitInfo2.calloc(1, stack)
                    .sType$Default()
                    .pCommandBufferInfos(commandBuffers)
                    .pSignalSemaphoreInfos(signalSemaphores);
            if (waitSemaphores != null) {
                submitInfo.pWaitSemaphoreInfos(waitSemaphores);
            }
            long fenceHandle = fence != null ? fence.getVkFence() : VK_NULL_HANDLE;

            // LOGGER.info("Fence handle: 0x{}", fenceHandle);

            vkCheck(vkQueueSubmit2(vkQueue, submitInfo, fenceHandle), "Failed to submit command to queue");

        }
    }

}
