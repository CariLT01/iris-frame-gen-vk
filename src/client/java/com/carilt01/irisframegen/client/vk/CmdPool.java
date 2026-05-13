package com.carilt01.irisframegen.client.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.LongBuffer;

import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.VK10.*;

public class CmdPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(CmdPool.class);

    private final long vkCommandPool;

    public CmdPool(VkCtx vkCtx, int queueFamilyIndex, boolean supportReset) {
        LOGGER.info("Creating command pool");
        try (var stack = MemoryStack.stackPush()) {
            var cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .queueFamilyIndex(queueFamilyIndex);
            if (supportReset) {
                cmdPoolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            }

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateCommandPool(vkCtx.getDevice().getVkDevice(), cmdPoolInfo, null, lp),
                    "Failed to create command pool");

            vkCommandPool = lp.get(0);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        vkDestroyCommandPool(vkCtx.getDevice().getVkDevice(), vkCommandPool, null);
    }

    public long getVkCommandPool() {
        return vkCommandPool;
    }

    public void reset(VkCtx vkCtx) {
        vkResetCommandPool(vkCtx.getDevice().getVkDevice(), vkCommandPool, 0);
    }
}
