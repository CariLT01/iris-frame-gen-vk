package com.carilt01.irisframegen.client.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

public class VkUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(VkUtils.class);
    public static final int MAX_IN_FLIGHT = 1;

    public static final int FLOAT_SIZE = 4;
    public static final int INT_SIZE = 4;

    public static final boolean USE_DEBUG_SHADERS = true;

    private VkUtils() {

    }

    public static OSType getOS() {
        OSType result;
        String os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        if ((os.indexOf("mac") >= 0) || (os.indexOf("darwin") >= 0)) {
            result = OSType.MACOS;
        } else if (os.indexOf("win") >= 0) {
            result = OSType.WINDOWS;
        } else if (os.indexOf("nux") >= 0) {
            result = OSType.LINUX;
        } else {
            result = OSType.OTHER;
        }

        return result;
    }

    public static void vkCheck(int err, String errMsg) {
        if (err != VK_SUCCESS) {
            String errCode = switch (err) {
                case VK_NOT_READY -> "VK_NOT_READY";
                case VK_TIMEOUT -> "VK_TIMEOUT";
                case VK_EVENT_SET -> "VK_EVENT_SET";
                case VK_EVENT_RESET -> "VK_EVENT_RESET";
                case VK_INCOMPLETE -> "VK_INCOMPLETE";
                case VK_ERROR_OUT_OF_HOST_MEMORY -> "VK_ERROR_OUT_OF_HOST_MEMORY";
                case VK_ERROR_OUT_OF_DEVICE_MEMORY -> "VK_ERROR_OUT_OF_DEVICE_MEMORY";
                case VK_ERROR_INITIALIZATION_FAILED -> "VK_ERROR_INITIALIZATION_FAILED";
                case VK_ERROR_DEVICE_LOST -> "VK_ERROR_DEVICE_LOST";
                case VK_ERROR_MEMORY_MAP_FAILED -> "VK_ERROR_MEMORY_MAP_FAILED";
                case VK_ERROR_LAYER_NOT_PRESENT -> "VK_ERROR_LAYER_NOT_PRESENT";
                case VK_ERROR_EXTENSION_NOT_PRESENT -> "VK_ERROR_EXTENSION_NOT_PRESENT";
                case VK_ERROR_FEATURE_NOT_PRESENT -> "VK_ERROR_FEATURE_NOT_PRESENT";
                case VK_ERROR_INCOMPATIBLE_DRIVER -> "VK_ERROR_INCOMPATIBLE_DRIVER";
                case VK_ERROR_TOO_MANY_OBJECTS -> "VK_ERROR_TOO_MANY_OBJECTS";
                case VK_ERROR_FORMAT_NOT_SUPPORTED -> "VK_ERROR_FORMAT_NOT_SUPPORTED";
                case VK_ERROR_FRAGMENTED_POOL -> "VK_ERROR_FRAGMENTED_POOL";
                case VK_ERROR_UNKNOWN -> "VK_ERROR_UNKNOWN";
                default -> "Not mapped";
            };

            LOGGER.error("VK CHECK FAILED: {}: {}: [{}]", errMsg, errCode, err);
            throw new RuntimeException(errMsg + ": " + errCode + " [" + err + "]");
        }
    }

    public static void imageBarrier(MemoryStack stack, VkCommandBuffer cmdHandle, long image, int oldLayout, int newLayout,
                                    long srcStage, long dstStage, long srcAccess, long dstAccess, int aspectMask) {

        // 1. Allocate the barrier buffer explicitly
        VkImageMemoryBarrier2.Buffer barrierBuffer = VkImageMemoryBarrier2.calloc(1, stack);
        VkImageMemoryBarrier2 barrier = barrierBuffer.get(0);

        // 2. Set EVERYTHING explicitly without the fluent API to ensure order
        barrier.sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER_2);
        barrier.pNext(0); // CRITICAL: Ensure pNext isn't garbage
        barrier.srcStageMask(srcStage);
        barrier.srcAccessMask(srcAccess);
        barrier.dstStageMask(dstStage);
        barrier.dstAccessMask(dstAccess);
        barrier.oldLayout(oldLayout);
        barrier.newLayout(newLayout);
        barrier.srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
        barrier.dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
        barrier.image(image); // Set this RIGHT BEFORE the range

        // 3. Manually set the range fields
        barrier.subresourceRange()
                .aspectMask(aspectMask)
                .baseMipLevel(0)
                .levelCount(VK_REMAINING_MIP_LEVELS)
                .baseArrayLayer(0)
                .layerCount(VK_REMAINING_ARRAY_LAYERS);

        // 4. Create Dependency Info
        VkDependencyInfo depInfo = VkDependencyInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEPENDENCY_INFO)
                .pImageMemoryBarriers(barrierBuffer); // Pass the BUFFER, not the single object

        vkCmdPipelineBarrier2(cmdHandle, depInfo);
    }

    public static int memoryTypeFromProperties(VkCtx vkCtx, int typeBits, int reqMask) {
        int result = -1;
        VkMemoryType.Buffer memoryTypes = vkCtx.getPhysDevice().getVkMemoryProperties().memoryTypes();
        for (int i = 0; i < VK_MAX_MEMORY_TYPES; i++) {
            if ((typeBits & 1) == 1 && (memoryTypes.get(i).propertyFlags() & reqMask) == reqMask) {
                result = i;
                break;
            }
            typeBits >>= 1;

        }
        if (result < 0) {
            throw new RuntimeException("Failed to find memory type");
        }
        return result;
    }

    public static void transitionImageLayout(VkCommandBuffer cmdBuf, long image,
                                             int oldLayout, int newLayout,
                                             ImageBufferType type) {
        int aspectMask = (type == ImageBufferType.DEPTH)
                ? VK_IMAGE_ASPECT_DEPTH_BIT
                : VK_IMAGE_ASPECT_COLOR_BIT;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier barrier = VkImageMemoryBarrier.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .subresourceRange(it -> it
                            .aspectMask(aspectMask)
                            .baseMipLevel(0)
                            .levelCount(1)
                            .baseArrayLayer(0)
                            .layerCount(1)
                    );

            // ----- Source access mask & stage (based on oldLayout) -----
            int srcAccessMask = 0;
            int srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT; // default for UNDEFINED

            switch (oldLayout) {
                case VK_IMAGE_LAYOUT_UNDEFINED:
                    srcAccessMask = 0;
                    srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                    break;
                case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL:
                    srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
                    srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                    break;
                case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL:
                    srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
                    srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                    break;
                case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL:
                    srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
                    srcStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                    break;
                case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL:
                    srcAccessMask = VK_ACCESS_SHADER_READ_BIT;
                    srcStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                    break;
                case VK_IMAGE_LAYOUT_GENERAL:
                    srcAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
                    srcStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                    break;
                // Add other layouts you use (e.g., DEPTH_ATTACHMENT_OPTIMAL, PRESENT_SRC_KHR…)
                default:
                    // Conservative fallback
                    srcAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
                    srcStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
            }

            // ----- Destination access mask & stage (based on newLayout) -----
            int dstAccessMask = 0;
            int dstStage = VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;

            switch (newLayout) {
                case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL:
                    dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
                    dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                    break;
                case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL:
                    dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
                    dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                    break;
                case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL:
                    dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
                    dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                    break;
                case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL:
                    dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
                    dstStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                    break;
                case VK_IMAGE_LAYOUT_GENERAL:
                    dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
                    dstStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                    break;
                // Add others as needed
                default:
                    dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
                    dstStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
            }

            barrier.srcAccessMask(srcAccessMask);
            barrier.dstAccessMask(dstAccessMask);

            VkImageMemoryBarrier.Buffer buf = VkImageMemoryBarrier.calloc(1, stack);
            buf.put(0, barrier);

            // Submit the barrier
            vkCmdPipelineBarrier(cmdBuf,
                    srcStage, dstStage,
                    0,                 // dependencyFlags
                    null,              // memory barriers
                    null,              // buffer memory barriers
                    buf
            );
        }
    }

    public static int getAspectMaskFromBufferType(ImageBufferType type) {
        int aspectMaxBit = 0;
        switch (type) {
            case DEPTH -> aspectMaxBit = VK_IMAGE_ASPECT_DEPTH_BIT;
            case COLOR -> aspectMaxBit = VK_IMAGE_ASPECT_COLOR_BIT;
            case MOTION -> aspectMaxBit = VK_IMAGE_ASPECT_COLOR_BIT;
        }

        return aspectMaxBit;
    }
}
