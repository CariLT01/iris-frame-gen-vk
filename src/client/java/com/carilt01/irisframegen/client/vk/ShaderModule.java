package com.carilt01.irisframegen.client.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;

public class ShaderModule {

    private final long handle;
    private final int shaderStage;

    public ShaderModule(VkCtx vkCtx, int shaderStage, byte[] spvContent) {
        handle = createShaderModule(vkCtx, spvContent);
        this.shaderStage = shaderStage;
    }

    private static long createShaderModule(VkCtx vkCtx, byte[] code) {
        try (var stack = MemoryStack.stackPush()) {
            ByteBuffer pCode = stack.malloc(code.length).put(0, code);

            var moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(pCode);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateShaderModule(vkCtx.getDevice().getVkDevice(), moduleCreateInfo, null, lp),
                    "Failed to create shader module");

            return lp.get(0);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        vkDestroyShaderModule(vkCtx.getDevice().getVkDevice(), handle, null);
    }

    public long getHandle() {
        return handle;
    }

    public int getShaderStage() {
        return shaderStage;
    }
}
