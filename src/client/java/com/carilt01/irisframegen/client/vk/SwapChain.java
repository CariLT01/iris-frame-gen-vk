package com.carilt01.irisframegen.client.vk;

import com.carilt01.irisframegen.client.VulkanWindow;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.VK10.*;

public class SwapChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwapChain.class);

    private final ImageView[] imageViews;
    private final int numImages;
    private final VkExtent2D swapChainExtent;
    private long vkSwapChain;

    public SwapChain(VulkanWindow vkWindow, Device device, Surface surface, int requestedImage, boolean vsync) {
        try (var stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR surfaceCaps = surface.getSurfaceCaps();

            int reqImages = calcNumImages(surfaceCaps, requestedImage);
            swapChainExtent = calcSwapChainExtent(vkWindow, surfaceCaps);

            Surface.SurfaceFormat surfaceFormat = surface.getSurfaceFormat();
            var vkSwapChainCreateInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .surface(surface.getVkSurface())
                    .minImageCount(reqImages)
                    .imageFormat(surfaceFormat.imageFormat())
                    .imageColorSpace(surfaceFormat.colorSpace())
                    .imageExtent(swapChainExtent)
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .preTransform(surfaceCaps.currentTransform())
                    .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .clipped(true);

            if (vsync) {
                vkSwapChainCreateInfo.presentMode(KHRSurface.VK_PRESENT_MODE_FIFO_KHR);
            } else {
                vkSwapChainCreateInfo.presentMode(KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR);
            }

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(KHRSwapchain.vkCreateSwapchainKHR(device.getVkDevice(), vkSwapChainCreateInfo, null, lp), "Failed to create swapchain");
            vkSwapChain = lp.get(0);

            imageViews = createImageViews(stack, device, vkSwapChain, surfaceFormat.imageFormat());
            numImages = imageViews.length;
        }
    }

    private static int calcNumImages(VkSurfaceCapabilitiesKHR surfaceCaps, int requestedImages) {
        int maxImages = surfaceCaps.maxImageCount();
        int minImages = surfaceCaps.minImageCount();
        int result = minImages;
        if (maxImages != 0) {
            result = Math.min(requestedImages, maxImages);
        }
        result = Math.max(result, minImages);
        LOGGER.info("Requested {} images, got {} images, surface capabilities: min {} max {}",
                requestedImages, result, minImages, maxImages);

        return result;
    }

    private static VkExtent2D calcSwapChainExtent(VulkanWindow vkWindow, VkSurfaceCapabilitiesKHR surfaceCaps) {
        var result = VkExtent2D.calloc();
        if (surfaceCaps.currentExtent().width() == 0xFFFFFFFF) {
            // surface undefined
            int width = Math.min(vkWindow.getWidth(), surfaceCaps.maxImageExtent().width());
            width = Math.max(width, surfaceCaps.minImageExtent().width());

            int height = Math.min(vkWindow.getHeight(), surfaceCaps.maxImageExtent().height());
            height = Math.max(height, surfaceCaps.minImageExtent().height());

            result.width(width);
            result.height(height);
        } else {
            // surface already defined
            result.set(surfaceCaps.currentExtent());
        }

        return result;
    }

    private static ImageView[] createImageViews(MemoryStack stack, Device device, long swapchain, int format) {
        IntBuffer ip = stack.mallocInt(1);
        vkCheck(KHRSwapchain.vkGetSwapchainImagesKHR(device.getVkDevice(), swapchain, ip, null),
                "Failed to get the number of images");
        int numImages = ip.get(0);

        LongBuffer swapChainImages = stack.mallocLong(numImages);
        vkCheck(KHRSwapchain.vkGetSwapchainImagesKHR(device.getVkDevice(), swapchain, ip, swapChainImages),
                "Failed to get surface images");

        var result = new ImageView[numImages];
        var imageViewData = new ImageView.ImageViewData().format(format).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
        for (int i = 0; i < numImages; i++) {
            result[i] = new ImageView(device, swapChainImages.get(i), imageViewData, VK_IMAGE_LAYOUT_UNDEFINED, ImageBufferType.COLOR);
        }
        return result;
    }

    public int acquireNextImage(Device device, Semaphore imageAqSem) {
        int imageIndex;
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer ip = stack.mallocInt(1);
            int err = KHRSwapchain.vkAcquireNextImageKHR(device.getVkDevice(), vkSwapChain, ~0L,
                    imageAqSem.getVkSemaphore(), 0L, ip);
            if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                return -1;
            } else if (err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                // Not optimal, but can still be used
            } else if (err != VK_SUCCESS) {
                throw new RuntimeException("Failed to acquire image: " + err);
            }
            imageIndex = ip.get(0);
        }
        return imageIndex;
    }

    public boolean presentImage(Queue queue, Semaphore renderCompleteSemph, int imageIndex) {
        boolean resize = false;
        try (var stack = MemoryStack.stackPush()) {
            VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack)
                    .sType$Default()
                    .pWaitSemaphores(stack.longs(renderCompleteSemph.getVkSemaphore()))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(vkSwapChain))
                    .pImageIndices(stack.ints(imageIndex));

            int err = KHRSwapchain.vkQueuePresentKHR(queue.getVkQueue(), present);
            if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                resize = true;
            } else if (err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                // not optimal, can still be used
            } else if (err != VK_SUCCESS) {
                throw new RuntimeException("Failed to present: " + err);
            }
        }
        return resize;
    }

    public void cleanup(Device device) {
        swapChainExtent.free();
        Arrays.asList(imageViews).forEach(i -> i.cleanup(device));
        KHRSwapchain.vkDestroySwapchainKHR(device.getVkDevice(), vkSwapChain, null);
    }

    public ImageView getImageView(int i) {
        return imageViews[i];
    }

    public int getNumImages() {
        return numImages;
    }

    public VkExtent2D getSwapChainExtent() {
        return swapChainExtent;
    }



}
