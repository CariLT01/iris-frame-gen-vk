package com.carilt01.irisframegen.client.vk;

import com.carilt01.irisframegen.client.VulkanWindow;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBufferSubmitInfo;
import org.lwjgl.vulkan.VkSemaphoreSubmitInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

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
    private final ScnRenderer scnRenderer;
    private int currentFrame;

    private final ModelsCache modelsCache;

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
            presCompleteSemphs[i] = new Semaphore(vkCtx);
            fences[i] = new Fence(vkCtx, true);
        }
        for (int i = 0; i < numSwapChainImages; i++) {
            renderCompleteSemphs[i] = new Semaphore(vkCtx);
        }
        scnRenderer = new ScnRenderer(vkCtx);
        modelsCache = new ModelsCache();
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

        scnRenderer.render(vkCtx, cmdBuffer, imageIndex, modelsCache);

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
            VkSemaphoreSubmitInfo.Buffer waitSemphs = VkSemaphoreSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .semaphore(presCompleteSemphs[currentFrame].getVkSemaphore());
            VkSemaphoreSubmitInfo.Buffer signalSemphs = VkSemaphoreSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .stageMask(VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT)
                    .semaphore(renderCompleteSemphs[imageIndex].getVkSemaphore());
            graphQueue.submit(cmds, waitSemphs, signalSemphs, fence);
        }
    }

    public void init(List<ModelData> models) {
        LOGGER.debug("Loading {} models", models.size());
        modelsCache.loadModels(vkCtx, models, cmdPools[0], graphQueue);
        LOGGER.debug("Loaded {} models", models.size());
    }
}
