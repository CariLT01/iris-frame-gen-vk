package com.carilt01.irisframegen.client.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.LongBuffer;

import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineCache;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineCache;

public class PipelineCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineCache.class);

    private final long vkPipelineCache;

    public PipelineCache(Device device) {
        LOGGER.debug("Creating pipeline cache");

        try (var stack = MemoryStack.stackPush()) {
            var createInfo = VkPipelineCacheCreateInfo.calloc(stack)
                    .sType$Default();

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreatePipelineCache(device.getVkDevice(), createInfo, null, lp),
                    "Failed to create pipeline cache");
            vkPipelineCache = lp.get(0);
        }
    }

    public void cleanup(Device device) {
        LOGGER.debug("Destroying pipeline cache");
        vkDestroyPipelineCache(device.getVkDevice(), vkPipelineCache, null);
    }

    public long getVkPipelineCache() {
        return vkPipelineCache;
    }
}
