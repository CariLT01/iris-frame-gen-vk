package com.carilt01.irisframegen.client.vk;

import com.carilt01.irisframegen.client.NativeMemory;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

import java.nio.LongBuffer;

import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.VK10.*;

public class VkBuffer {
    private final long allocatedSize;
    private final long buffer;
    private final long memory;
    private final PointerBuffer pb;
    private final long requestedSize;

    private long mappedMemory;

    public VkBuffer(VkCtx vkCtx, long size, int usage, int reqMask) {
        requestedSize = size;
        mappedMemory = 0L;
        try (var stack = MemoryStack.stackPush()) {
            Device device = vkCtx.getDevice();
            var bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateBuffer(device.getVkDevice(), bufferCreateInfo, null, lp), "Failed to create buffer");
            buffer = lp.get(0);

            var memReqs = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device.getVkDevice(), buffer, memReqs);

            var memAlloc = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(VkUtils.memoryTypeFromProperties(vkCtx, memReqs.memoryTypeBits(), reqMask));
            vkCheck(vkAllocateMemory(device.getVkDevice(), memAlloc, null, lp), "Failed to allocate memory");
            allocatedSize = memAlloc.allocationSize();
            memory = lp.get(0);
            pb = NativeMemory.memAllocPointer(1);
            vkCheck(vkBindBufferMemory(vkCtx.getDevice().getVkDevice(), buffer, memory, 0), "Failed to bind buffer memory");
        }

    }


    public void cleanup(VkCtx vkCtx) {
        NativeMemory.memFree(pb);
        VkDevice vkDevice = vkCtx.getDevice().getVkDevice();
        vkDestroyBuffer(vkDevice, buffer, null);
        vkFreeMemory(vkDevice, memory, null);
    }

    public long getBuffer() {
        return buffer;
    }

    public long getRequestedSize() {
        return requestedSize;
    }

    public long map(VkCtx vkCtx) {
        if (mappedMemory == 0L) {
            vkCheck(vkMapMemory(vkCtx.getDevice().getVkDevice(), memory, 0, allocatedSize, 0, pb), "Failed to map buffer");
            mappedMemory = pb.get(0);
        }
        return mappedMemory;
    }

    public void unmap(VkCtx vkCtx) {
        if (mappedMemory != 0L) {
            vkUnmapMemory(vkCtx.getDevice().getVkDevice(), memory);
            mappedMemory = 0L;
        }
    }
}
