package com.carilt01.irisframegen.client.vk;

import com.carilt01.irisframegen.client.GlState;
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
import java.util.concurrent.locks.LockSupport;

import static com.carilt01.irisframegen.client.vk.VkUtils.*;
import static org.lwjgl.vulkan.KHRExternalMemoryCapabilities.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT_KHR;
import static org.lwjgl.vulkan.KHRExternalMemoryWin32.vkGetMemoryWin32HandleKHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;
import static org.lwjgl.vulkan.VK11.VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK13.*;

public class Render {
    private final VkCtx vkCtx;
    private static final Logger LOGGER = LoggerFactory.getLogger(Render.class);

    private final CmdBuffer[] cmdBuffers;
    private final CmdPool[] cmdPools;

    private final CmdBuffer[] cmdBuffersCopy;
    private final CmdPool[] cmdPoolsCopy;
    private final Fence copyFence;

    private final Fence[] fences;
    private final Queue.GraphicsQueue graphQueue;
    private final Semaphore[] presCompleteSemphs;
    private final Queue.PresentQueue presentQueue;
    private final Semaphore[] renderCompleteSemphs;
    private ScnRenderer scnRenderer;
    private int currentFrame;



    private final ModelsCache modelsCache;

    private SharedBufferData colorBuffer;
    private SharedBufferData depthBuffer;

    private final Semaphore glRenderComplete;
    private final long glRenderCompleteSemphAdd;

    private final Object resourceLock = new Object();

    private static final int WIDTH = 854;
    private static final int HEIGHT = 480;

    public Render(VulkanWindow vkWindow) {
        currentFrame = 0;
        vkCtx = new VkCtx(vkWindow);

        graphQueue = new Queue.GraphicsQueue(vkCtx, 0);
        presentQueue = new Queue.PresentQueue(vkCtx, 0);

        cmdPools = new CmdPool[VkUtils.MAX_IN_FLIGHT];
        cmdBuffers = new CmdBuffer[VkUtils.MAX_IN_FLIGHT];
        cmdBuffersCopy = new CmdBuffer[MAX_IN_FLIGHT];
        cmdPoolsCopy = new CmdPool[MAX_IN_FLIGHT];
        fences = new Fence[VkUtils.MAX_IN_FLIGHT];
        presCompleteSemphs = new Semaphore[VkUtils.MAX_IN_FLIGHT];
        int numSwapChainImages = vkCtx.getSwapChain().getNumImages();
        renderCompleteSemphs = new Semaphore[numSwapChainImages];
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            cmdPools[i] = new CmdPool(vkCtx, graphQueue.getQueueFamilyIndex(), false);
            cmdBuffers[i] = new CmdBuffer(vkCtx, cmdPools[i], true, true);
            cmdPoolsCopy[i] = new CmdPool(vkCtx, graphQueue.getQueueFamilyIndex(), false);
            cmdBuffersCopy[i] = new CmdBuffer(vkCtx, cmdPoolsCopy[i], true, true);
            presCompleteSemphs[i] = new Semaphore(vkCtx, false);
            fences[i] = new Fence(vkCtx, true);
        }
        copyFence = new Fence(vkCtx, false);
        for (int i = 0; i < numSwapChainImages; i++) {
            renderCompleteSemphs[i] = new Semaphore(vkCtx, false);
        }


        modelsCache = new ModelsCache();

        glRenderComplete = new Semaphore(vkCtx, true);
        glRenderCompleteSemphAdd = glRenderComplete.getExportHandle(vkCtx);

        this.createImageSamplers();

        LOGGER.info("Completed early graphics pipeline initialization. GL Render Complete Semph at: 0x{}", glRenderCompleteSemphAdd);
    }

    private void createImageSamplers() {
        this.colorBuffer = new SharedBufferData(null, null, createImageSampler());
        this.depthBuffer = new SharedBufferData(null, null, createImageSampler());
    }

    public void completeLateInit() {
        LOGGER.info("Completing late graphics pipeline initialization");
        scnRenderer = new ScnRenderer(vkCtx, colorBuffer, depthBuffer);
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



    public int getOldLayout(boolean initialized) {
        return initialized ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED;
    }

    public void render() {

        // wait GL to finish rendering
        LockSupport.park();
        // once finished, continue

        var cmdBufferCopy = cmdBuffersCopy[currentFrame];
        var cmdPoolCopy = cmdPoolsCopy[currentFrame];

        recordingStart(cmdPoolCopy, cmdBufferCopy);



        synchronized (resourceLock) {

            // depthbuffer copy first, it is cleared after postprocessing


            // colorbuffer

            colorBuffer.imageView().transitionLayout(cmdBufferCopy, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            depthBuffer.imageView().transitionLayout(cmdBufferCopy, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            colorBuffer.setInitialized(true);;
            depthBuffer.setInitialized(true);
            colorBuffer.imageView().copy(colorBuffer.getLocalImageView(), cmdBufferCopy, WIDTH, HEIGHT);
            depthBuffer.imageView().copy(depthBuffer.getLocalImageView(), cmdBufferCopy, WIDTH, HEIGHT);
            colorBuffer.imageView().transitionLayout(cmdBufferCopy, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            depthBuffer.imageView().transitionLayout(cmdBufferCopy, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            colorBuffer.getLocalImageView().transitionLayout(cmdBufferCopy, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            depthBuffer.getLocalImageView().transitionLayout(cmdBufferCopy, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);



        }

        recordingStop(cmdBufferCopy);
        submitCommands(cmdBufferCopy, copyFence);

        // wait for the fence
        //.info("Treying to wait for the copy fence");
        vkCheck(vkWaitForFences(vkCtx.getDevice().getVkDevice(), copyFence.getVkFence(), true, Long.MAX_VALUE),
        "Failed to wait for fence");
        //LOGGER.info("GPU work is complete");
        // resume GL thread
        LockSupport.unpark(GlState.GLThread);
        //LOGGER.info("Thread has been unparked");



        SwapChain swapChain = vkCtx.getSwapChain();

        //LOGGER.info("Waiting for swapchain fence");
        waitForFence(currentFrame);
        //LOGGER.info("Swapchain fence complete");

        int imageIndex = swapChain.acquireNextImage(vkCtx.getDevice(), presCompleteSemphs[currentFrame]);
        //LOGGER.info("Acquired swapchain image");
        if (imageIndex < 0) {
            return;
        }

        var cmdPool = cmdPools[currentFrame];
        var cmdBuffer = cmdBuffers[currentFrame];

        recordingStart(cmdPool, cmdBuffer);

        long descriptorSets = scnRenderer.getPipeline().getDescriptorSets();
        //LOGGER.info("Descriptor set add 0x{}", descriptorSets);
        scnRenderer.render(vkCtx, cmdBuffer, imageIndex, modelsCache, descriptorSets);

        recordingStop(cmdBuffer);

        submit(cmdBuffer, currentFrame, imageIndex);

        swapChain.presentImage(presentQueue, renderCompleteSemphs[imageIndex], imageIndex);

        currentFrame = (currentFrame + 1) % VkUtils.MAX_IN_FLIGHT;

        //LOGGER.info("Image present complete");

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
            VkSemaphoreSubmitInfo.Buffer waitSemphs = VkSemaphoreSubmitInfo.calloc(1, stack);
            waitSemphs.get(0).sType$Default()
                    .stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .semaphore(presCompleteSemphs[currentFrame].getVkSemaphore());
            /* waitSemphs.get(1).sType$Default()
                    .stageMask(VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT)
                    .semaphore(glRenderComplete.getVkSemaphore()); */
            VkSemaphoreSubmitInfo.Buffer signalSemphs = VkSemaphoreSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .stageMask(VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT)
                    .semaphore(renderCompleteSemphs[imageIndex].getVkSemaphore());

            // LOGGER.info("gl render complete semph at: 0x{}", glRenderComplete.getVkSemaphore());

            graphQueue.submit(cmds, waitSemphs, signalSemphs, fence);
        }
    }

    private void submitCommands(CmdBuffer cmdBuffer, Fence waitFence) {
        try (var stack = MemoryStack.stackPush()) {
            waitFence.reset(vkCtx);

            var submitInfo = VkSubmitInfo.calloc(stack)
                            .sType$Default();

            PointerBuffer commandBuffers = stack.pointers(cmdBuffer.getVkCommandBuffer());

            submitInfo.waitSemaphoreCount(0)
                    .pWaitSemaphores(null)
                    .pWaitDstStageMask(null)
                    .pCommandBuffers(commandBuffers)
                    .pSignalSemaphores(null);

            vkCheck(vkQueueSubmit(graphQueue.getVkQueue(), submitInfo, waitFence.getVkFence()),
                    "Failed to submit to queue");

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

    public long getImportHandleForImage(int width, int height, int mipLevels, int format, long[] outSize,
                                        ImageBufferType type) {
        if (format ==0) {
            throw new IllegalArgumentException("Format cannot be 0");
        }

        synchronized (resourceLock) {
            colorBuffer.setInitialized(false);
            depthBuffer.setInitialized(false);

            try (var stack = MemoryStack.stackPush()) {
                var externalInfo = VkExternalMemoryImageCreateInfo.calloc(stack)
                        .sType$Default()
                        .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT_KHR);

                int attachmentBit = 0;
                switch (type) {
                    case DEPTH -> attachmentBit = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
                    case COLOR -> attachmentBit = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
                }

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
                        .usage(VK_IMAGE_USAGE_SAMPLED_BIT | attachmentBit | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                        .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);

                LongBuffer lp1 = stack.mallocLong(1);
                vkCheck(vkCreateImage(vkCtx.getDevice().getVkDevice(), createInfo, null, lp1),
                        "Failed to create image");

                long vkImage = lp1.get(0);
                LOGGER.info("VkImage address should be at: 0x{}", vkImage);

                // create a copy of it

                var createInfoLocal = VkImageCreateInfo.calloc(stack)
                        .sType$Default()
                        .imageType(VK_IMAGE_TYPE_2D)
                        .format(format)
                        .extent(it -> it.set(width, height, 1))
                        .mipLevels(1)
                        .arrayLayers(1)
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .tiling(VK_IMAGE_TILING_OPTIMAL)
                        .usage(VK_IMAGE_USAGE_SAMPLED_BIT | attachmentBit | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                        .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);

                LongBuffer lp2 = stack.mallocLong(1);
                vkCheck(vkCreateImage(vkCtx.getDevice().getVkDevice(), createInfoLocal, null, lp2),
                        "Failed to create local image");
                long vkImageLocal = lp2.get(0);

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

                // get export pointer
                var handleInfo = VkMemoryGetWin32HandleInfoKHR.calloc(stack)
                        .sType$Default()
                        .memory(vkMemory)
                        .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT_KHR);

                PointerBuffer pHandle = stack.mallocPointer(1);
                vkCheck(vkGetMemoryWin32HandleKHR(vkCtx.getDevice().getVkDevice(), handleInfo, pHandle),
                        "Failed to export vk memory handle");

                outSize[0] = memReq.size();

                // allocate for local image
                memReq = VkMemoryRequirements.malloc(stack);
                vkGetImageMemoryRequirements(vkCtx.getDevice().getVkDevice(), vkImageLocal, memReq);

                dedicatedInfo = VkMemoryDedicatedAllocateInfo.calloc(stack)
                        .sType$Default()
                        .image(vkImageLocal);

                allocInfo = VkMemoryAllocateInfo.calloc(stack)
                        .sType$Default()
                        .pNext(dedicatedInfo.address())
                        .allocationSize(memReq.size())
                        .memoryTypeIndex(memoryTypeFromProperties(vkCtx, memReq.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

                vkCheck(vkAllocateMemory(vkCtx.getDevice().getVkDevice(), allocInfo, null, lp),
                        "Failed to allocate memory for local handle image");

                vkMemory = lp.get(0);

                vkCheck(vkBindImageMemory(vkCtx.getDevice().getVkDevice(), vkImageLocal, vkMemory, 0),
                        "Failed to bind image memory");

                LOGGER.info("Created image is address: 0x{}", vkImage);


                int aspectMaxBit = 0;
                switch (type) {
                    case DEPTH -> aspectMaxBit = VK_IMAGE_ASPECT_DEPTH_BIT;
                    case COLOR -> aspectMaxBit = VK_IMAGE_ASPECT_COLOR_BIT;
                }

                ImageView sharedImageView = new ImageView(vkCtx.getDevice(), vkImage, new ImageView.ImageViewData()
                        .format(format).aspectMask(aspectMaxBit), VK_IMAGE_LAYOUT_UNDEFINED, type);

                ImageView copiedImageView = new ImageView(vkCtx.getDevice(), vkImageLocal, new ImageView.ImageViewData()
                        .format(format).aspectMask(aspectMaxBit), VK_IMAGE_LAYOUT_UNDEFINED, type);

                if (type == ImageBufferType.COLOR) {
                    colorBuffer.setImageView(sharedImageView);
                    colorBuffer.setLocalImageView(copiedImageView);
                } else if (type == ImageBufferType.DEPTH) {
                    depthBuffer.setImageView(sharedImageView);
                    depthBuffer.setLocalImageView(copiedImageView);
                }


                return pHandle.get(0);

            }
        }
    }

    public long getGlRenderCompleteSemphAdd() {
        return glRenderCompleteSemphAdd;
    }
}
