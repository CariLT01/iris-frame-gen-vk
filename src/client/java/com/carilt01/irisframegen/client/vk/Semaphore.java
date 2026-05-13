package com.carilt01.irisframegen.client.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

import java.nio.LongBuffer;

import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.VK10.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK10.vkDestroySemaphore;

public class Semaphore {
    private final long vkSemaphore;

    public Semaphore(VkCtx vkCtx) {
        try (var stack = MemoryStack.stackPush()) {
            var semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack).sType$Default();

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
}
