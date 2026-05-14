package com.carilt01.irisframegen.client.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkMemoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

public class VkUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(VkUtils.class);
    public static final int MAX_IN_FLIGHT = 2;

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
}
