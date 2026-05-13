package com.carilt01.irisframegen.client.vk;

import com.carilt01.irisframegen.client.VulkanWindow;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB;

public class Surface {

    private final VkSurfaceCapabilitiesKHR surfaceCaps;
    private final SurfaceFormat surfaceFormat;
    private final long vkSurface;

    private static final Logger LOGGER = LoggerFactory.getLogger(Surface.class);

    public record SurfaceFormat(int imageFormat, int colorSpace) {};

    public Surface(Instance instance, PhysDevice physDevice, VulkanWindow window) {
        LOGGER.info("Creating vulkan surface");
        try (var stack = MemoryStack.stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);
            GLFWVulkan.glfwCreateWindowSurface(instance.getVkInstance(), window.getHandle(), null, pSurface);
            vkSurface = pSurface.get(0);
            surfaceCaps = VkSurfaceCapabilitiesKHR.calloc();
            vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physDevice.getVkPhysicalDevice(),
                    vkSurface, surfaceCaps), "Failed to get surface capabilities");

            surfaceFormat = calcSurfaceFormat(physDevice, vkSurface);
        }
    }

    private static SurfaceFormat calcSurfaceFormat(PhysDevice physDevice, long vkSurface) {
        int imageFormat;
        int colorSpace;

        try (var stack = MemoryStack.stackPush()) {
            IntBuffer ip = stack.mallocInt(1);
            vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physDevice.getVkPhysicalDevice(),
                    vkSurface, ip, null), "Failed to get the number of surface formats");

            int numFormats = ip.get(0);
            if (numFormats <= 0) {
                throw new RuntimeException("No surface formats retrieved");
            }

            var surfaceFormats = VkSurfaceFormatKHR.calloc(numFormats, stack);
            vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physDevice.getVkPhysicalDevice(),
                    vkSurface, ip, surfaceFormats), "Failed to get surface formats");

            imageFormat = VK_FORMAT_B8G8R8A8_SRGB;
            colorSpace = surfaceFormats.get(0).colorSpace();
            for (int i = 0; i < numFormats; i++) {
                VkSurfaceFormatKHR surfaceFormatKHR = surfaceFormats.get(i);
                if (surfaceFormatKHR.format() == VK_FORMAT_B8G8R8A8_SRGB &&
                surfaceFormatKHR.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                    imageFormat = surfaceFormatKHR.format();
                    colorSpace = surfaceFormatKHR.colorSpace();
                    break;
                }
            }

            return new SurfaceFormat(imageFormat, colorSpace);
        }
    }

    public void cleanup(Instance instance) {
        surfaceCaps.free();
        KHRSurface.vkDestroySurfaceKHR(instance.getVkInstance(), vkSurface, null);
    }

    public VkSurfaceCapabilitiesKHR getSurfaceCaps() {
        return this.surfaceCaps;
    }

    public SurfaceFormat getSurfaceFormat() {
        return this.surfaceFormat;
    }

    public long getVkSurface() {
        return vkSurface;
    }

}
