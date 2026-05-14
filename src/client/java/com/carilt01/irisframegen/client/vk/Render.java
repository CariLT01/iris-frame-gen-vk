package com.carilt01.irisframegen.client.vk;

import com.carilt01.irisframegen.client.VulkanWindow;
import com.mojang.blaze3d.textures.TextureFormat;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.List;

import static com.carilt01.irisframegen.client.vk.VkUtils.memoryTypeFromProperties;
import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.KHRExternalMemoryCapabilities.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT_KHR;
import static org.lwjgl.vulkan.KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;
import static org.lwjgl.vulkan.VK11.VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK13.VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT;

public class Render {
    private final VkCtx vkCtx;
    private static final Logger LOGGER = LoggerFactory.getLogger(Render.class);

    private final CmdBuffer[] cmdBuffers;
    private final CmdPool[] cmdPools;
    private final Fence[] fences;
    private final Queue.GraphicsQueue graphQueue;
    private final Semaphore[] presCompleteSemphs;
    private final Queue.PresentQueue presentQueue;
    private final Semaphore[] renderCompleteSemphs;
    private ScnRenderer scnRenderer;
    private int currentFrame;

    private ImageView sharedImageView = null;

    private final ModelsCache modelsCache;
    private final long sampler;

    private final Semaphore glRenderComplete;
    private final long glRenderCompleteSemphAdd;

    public Render(VulkanWindow vkWindow) {
        currentFrame = 0;
        vkCtx = new VkCtx(vkWindow);

        graphQueue = new Queue.GraphicsQueue(vkCtx, 0);
        presentQueue = new Queue.PresentQueue(vkCtx, 0);

        cmdPools = new CmdPool[VkUtils.MAX_IN_FLIGHT];
        cmdBuffers = new CmdBuffer[VkUtils.MAX_IN_FLIGHT];
        fences = new Fence[VkUtils.MAX_IN_FLIGHT];
        presCompleteSemphs = new Semaphore[VkUtils.MAX_IN_FLIGHT];
        int numSwapChainImages = vkCtx.getSwapChain().getNumImages();
        renderCompleteSemphs = new Semaphore[numSwapChainImages];
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            cmdPools[i] = new CmdPool(vkCtx, graphQueue.getQueueFamilyIndex(), false);
            cmdBuffers[i] = new CmdBuffer(vkCtx, cmdPools[i], true, true);
            presCompleteSemphs[i] = new Semaphore(vkCtx, false);
            fences[i] = new Fence(vkCtx, true);
        }
        for (int i = 0; i < numSwapChainImages; i++) {
            renderCompleteSemphs[i] = new Semaphore(vkCtx, false);
        }
        sampler = createImageSampler();

        modelsCache = new ModelsCache();

        glRenderComplete = new Semaphore(vkCtx, true);
        glRenderCompleteSemphAdd = glRenderComplete.getExportHandle(vkCtx);
        LOGGER.info("Completed early graphics pipeline initialization. GL Render Complete Semph at: 0x{}", glRenderCompleteSemphAdd);
    }

    public void completeLateInit() {
        LOGGER.info("Completing late graphics pipeline initialization");
        scnRenderer = new ScnRenderer(vkCtx, sampler, sharedImageView);
    }

    private long createImageSampler() {
        try (var stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo createInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK_FILTER_LINEAR)          // magnification filter
                    .minFilter(VK_FILTER_LINEAR)          // minification filter
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR) // if you had mips, but not needed
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .anisotropyEnable(false)              // optional, can be true if you want
                    .maxAnisotropy(1.0f)
                    .borderColor(VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK)
                    .unnormalizedCoordinates(false)       // false = UV in [0,1]
                    .compareEnable(false)
                    .compareOp(VK_COMPARE_OP_NEVER)
                    .minLod(0.0f)
                    .maxLod(1.0f)                         // only mip level 0, since we have 1 level
                    .mipLodBias(0.0f);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateSampler(vkCtx.getDevice().getVkDevice(), createInfo, null, lp),
                    "Failed to create vk sampler");
            return lp.get(0);

        }

    }



    public void cleanup() {
        vkCtx.getDevice().waitIdle();

        scnRenderer.cleanup(vkCtx);

        Arrays.asList(renderCompleteSemphs).forEach(i -> i.cleanup(vkCtx));
        Arrays.asList(presCompleteSemphs).forEach(i -> i.cleanup(vkCtx));
        Arrays.asList(fences).forEach(i -> i.cleanup(vkCtx));
        for (int i = 0; i < cmdPools.length; i++) {
            cmdBuffers[i].cleanup(vkCtx, cmdPools[i]);
            cmdPools[i].cleanup(vkCtx);
        }

        vkCtx.cleanup();
    }

    private void recordingStart(CmdPool cmdPool, CmdBuffer cmdBuffer) {
        cmdPool.reset(vkCtx);
        cmdBuffer.beginRecording();
    }

    private void recordingStop(CmdBuffer cmdBuffer) {
        cmdBuffer.endRecording();
    }

    private void transitionImageLayout(VkCommandBuffer cmdBuf, long image, int oldLayout, int newLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier barrier = VkImageMemoryBarrier.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .subresourceRange(it -> it
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0)
                            .levelCount(1)      // only one mip level
                            .baseArrayLayer(0)
                            .layerCount(1)
                    );

            // Set source and destination access masks based on layout transitions
            int srcAccessMask = 0;
            int dstAccessMask = 0;

            switch (oldLayout) {
                case VK_IMAGE_LAYOUT_UNDEFINED:
                    srcAccessMask = 0;
                    break;
                case VK_IMAGE_LAYOUT_GENERAL:
                    srcAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
                    break;
                // Add other layouts if needed (e.g., VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
            }

            switch (newLayout) {
                case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL:
                    dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
                    break;
                case VK_IMAGE_LAYOUT_GENERAL:
                    dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
                    break;
                // Add other layouts as needed
            }

            barrier.srcAccessMask(srcAccessMask);
            barrier.dstAccessMask(dstAccessMask);

            // Determine pipeline stages
            int srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            int dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;

            if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED) {
                srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            } else {
                srcStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT; // conservative
            }

            VkImageMemoryBarrier.Buffer memoryBarriers = VkImageMemoryBarrier.calloc(1, stack);
            memoryBarriers.put(0, barrier);

            vkCmdPipelineBarrier(cmdBuf,
                    srcStage, dstStage,
                    0,                 // dependencyFlags
                    null,              // memory barriers
                    null,              // buffer memory barriers
                    memoryBarriers);          // image memory barrier
        }
    }

    public void render() {
        SwapChain swapChain = vkCtx.getSwapChain();

        waitForFence(currentFrame);

        var cmdPool = cmdPools[currentFrame];
        var cmdBuffer = cmdBuffers[currentFrame];

        recordingStart(cmdPool, cmdBuffer);

        int imageIndex = swapChain.acquireNextImage(vkCtx.getDevice(), presCompleteSemphs[currentFrame]);
        if (imageIndex < 0) {
            return;
        }

        transitionImageLayout(cmdBuffer.getVkCommandBuffer(), sharedImageView.getVkImage(),
                VK_IMAGE_LAYOUT_UNDEFINED,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);


        long descriptorSets = scnRenderer.getPipeline().getDescriptorSets();
        //LOGGER.info("Descriptor set add 0x{}", descriptorSets);
        scnRenderer.render(vkCtx, cmdBuffer, imageIndex, modelsCache, descriptorSets);

        recordingStop(cmdBuffer);

        submit(cmdBuffer, currentFrame, imageIndex);

        swapChain.presentImage(presentQueue, renderCompleteSemphs[imageIndex], imageIndex);

        currentFrame = (currentFrame + 1) % VkUtils.MAX_IN_FLIGHT;

    }

    private void waitForFence(int currentFrame) {
        var fence = fences[currentFrame];
        fence.fenceWait(vkCtx);
    }

    private void submit(CmdBuffer cmdBuffer, int currentFrame, int imageIndex) {
        try (var stack = MemoryStack.stackPush()) {
            var fence = fences[currentFrame];
            fence.reset(vkCtx);
            var cmds = VkCommandBufferSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .commandBuffer(cmdBuffer.getVkCommandBuffer());
            VkSemaphoreSubmitInfo.Buffer waitSemphs = VkSemaphoreSubmitInfo.calloc(2, stack);
            waitSemphs.get(0).sType$Default()
                    .stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .semaphore(presCompleteSemphs[currentFrame].getVkSemaphore());
            waitSemphs.get(1).sType$Default()
                    .stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .semaphore(glRenderComplete.getVkSemaphore())
                    .value(0);
            VkSemaphoreSubmitInfo.Buffer signalSemphs = VkSemaphoreSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .stageMask(VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT)
                    .semaphore(renderCompleteSemphs[imageIndex].getVkSemaphore());

            LOGGER.info("gl render complete semph at: 0x{}", glRenderComplete.getVkSemaphore());

            graphQueue.submit(cmds, waitSemphs, signalSemphs, fence);
        }
    }

    public void init(List<ModelData> models) {
        LOGGER.debug("Loading {} models", models.size());
        modelsCache.loadModels(vkCtx, models, cmdPools[0], graphQueue);
        LOGGER.debug("Loaded {} models", models.size());
    }

    private int textureFormatToVkConstant(TextureFormat colorFormat) {
        switch (colorFormat) {
            case TextureFormat.RGBA8 -> {
                return VK_FORMAT_R8G8B8A8_UNORM;
            }
            case TextureFormat.DEPTH32 -> {
                return VK_FORMAT_D32_SFLOAT;
            }
            case TextureFormat.RED8 -> {
                return VK_FORMAT_R8_UNORM;
            }
            case TextureFormat.RED8I -> {
                return VK_FORMAT_R8_SINT;
            }
            default -> {
                return -1;
            }
        }
    }

    public long getImportHandleForImage(int width, int height, int mipLevels, int format, long[] outSize) {
        if (format ==0) {
            throw new IllegalArgumentException("Format cannot be 0");
        }

        try (var stack = MemoryStack.stackPush()) {



            var externalInfo = VkExternalMemoryImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT_KHR);

            var createInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(externalInfo)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(format)
                    .extent(it -> it.set(width, height, 1))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);

            LongBuffer lp1 = stack.mallocLong(1);
            vkCheck(vkCreateImage(vkCtx.getDevice().getVkDevice(), createInfo, null, lp1),
                    "Failed to create image");

            long vkImage = lp1.get(0);

            LongBuffer lp = stack.mallocLong(1);




            // memory requirements and export information
            var memReq = VkMemoryRequirements.malloc(stack);
            vkGetImageMemoryRequirements(vkCtx.getDevice().getVkDevice(), vkImage, memReq);

            var dedicatedInfo = VkMemoryDedicatedAllocateInfo.calloc(stack)
                    .sType$Default()
                    .image(vkImage);

            var exportInfo = VkExportMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT_KHR);
            dedicatedInfo.pNext(exportInfo.address());

            var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(dedicatedInfo.address())
                    .allocationSize(memReq.size())
                    .memoryTypeIndex(memoryTypeFromProperties(vkCtx, memReq.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

            vkCheck(vkAllocateMemory(vkCtx.getDevice().getVkDevice(), allocInfo, null, lp),
                    "Failed to allocate memory for external export handle image");

            long vkMemory = lp.get(0);

            vkCheck(vkBindImageMemory(vkCtx.getDevice().getVkDevice(), vkImage, vkMemory, 0),
                    "Failed to bind image memory");

            LOGGER.info("Created image is address: 0x{}", vkImage);

            sharedImageView = new ImageView(vkCtx.getDevice(), vkImage, new ImageView.ImageViewData()
                    .format(format).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT));

            var handleInfo = VkMemoryGetWin32HandleInfoKHR.calloc(stack)
                    .sType$Default()
                    .memory(vkMemory)
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT_KHR);

            PointerBuffer pHandle = stack.mallocPointer(1);
            vkCheck(vkGetMemoryWin32HandleKHR(vkCtx.getDevice().getVkDevice(), handleInfo, pHandle),
                    "Failed to export vk memory handle");

            outSize[0] = memReq.size();
            return pHandle.get(0);

        }



    }

    public long getGlRenderCompleteSemphAdd() {
        return glRenderCompleteSemphAdd;
    }
}
