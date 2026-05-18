package com.carilt01.irisframegen.client.vk;

import com.carilt01.irisframegen.client.GlState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.LongBuffer;
import java.util.Arrays;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

public class ScnRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScnRenderer.class);

    private final VkClearValue clrValueColor;
    private VkRenderingAttachmentInfo.Buffer[] attInfoColor;
    private VkRenderingInfo[] renderInfo;

    private final Pipeline pipeline;

    public ScnRenderer(VkCtx vkCtx, SharedBufferData colorBuffer, SharedBufferData depthBuffer, SharedBufferData motionBuffer) {
        clrValueColor = VkClearValue.calloc().color(
                c -> c.float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1.0f));
        attInfoColor = createColorAttachmentInfo(vkCtx, clrValueColor);
        renderInfo = createRenderInfo(vkCtx, attInfoColor);


        ShaderModule[] shaderModules = createShaderModules(vkCtx);
        pipeline = createPipeline(vkCtx, shaderModules, colorBuffer, depthBuffer, motionBuffer);
        Arrays.asList(shaderModules).forEach(s -> s.cleanup(vkCtx));
    }

    private static VkRenderingAttachmentInfo.Buffer[] createColorAttachmentInfo(VkCtx vkCtx, VkClearValue clearValue) {
        SwapChain swapChain = vkCtx.getSwapChain();
        int numImages = swapChain.getNumImages();
        var result = new VkRenderingAttachmentInfo.Buffer[numImages];

        for (int i = 0; i < numImages; i++) {
            var attachments = VkRenderingAttachmentInfo.calloc(1)
                    .sType$Default()
                    .imageView(swapChain.getImageView(i).getVkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .clearValue(clearValue);
            result[i] = attachments;
        }
        return result;
    }

    private static VkRenderingInfo[] createRenderInfo(VkCtx vkCtx, VkRenderingAttachmentInfo.Buffer[] colorAttachments) {
        SwapChain swapChain = vkCtx.getSwapChain();
        int numImages = swapChain.getNumImages();
        var result = new VkRenderingInfo[numImages];
        try (var stack = MemoryStack.stackPush()) {
            VkExtent2D extent = swapChain.getSwapChainExtent();
            var renderArea = VkRect2D.calloc(stack).extent(extent);
            for (int i = 0; i < numImages; i++) {
                var renderingInfo = VkRenderingInfo.calloc()
                        .sType$Default()
                        .renderArea(renderArea)
                        .layerCount(1)
                        .pColorAttachments(colorAttachments[i]);
                result[i] =  renderingInfo;
            }
        }
        return result;
    }

    public void render(VkCtx vkCtx, CmdBuffer cmdBuffer, int imageIndex, ModelsCache modelsCache,
                       long descriptorSets) {


        try (var stack = MemoryStack.stackPush()) {
            SwapChain swapChain = vkCtx.getSwapChain();

            long swapChainImage = swapChain.getImageView(imageIndex).getVkImage();
            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            // LOGGER.info("Swap chain image: 0x{} call before", swapChainImage);

            VkUtils.imageBarrier(stack, cmdHandle, swapChainImage,
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);

            vkCmdBeginRendering(cmdHandle, renderInfo[imageIndex]);
            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());
            LongBuffer descriptorSetsBuf = stack.longs(descriptorSets);


            vkCmdBindDescriptorSets(cmdBuffer.getVkCommandBuffer(), VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipelineLayout(),
                    0, descriptorSetsBuf, null);

            VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
            int width = swapChainExtent.width();
            int height = swapChainExtent.height();
            var viewport = VkViewport.calloc(1, stack)
                            .x(0)
                                    .y(height)
                                            .height(-height)
                                                    .width(width)
                                                            .minDepth(0.0f)
                                                                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            var scissor = VkRect2D.calloc(1, stack)
                            .extent(it -> it.width(width).height(height))
                                    .offset(it -> it.x(0).y(0));
            vkCmdSetScissor(cmdHandle, 0, scissor);

            LongBuffer offsets = stack.mallocLong(1).put(0, 0L);
            LongBuffer vertexBuffer = stack.mallocLong(1);
            var vulkanModels = modelsCache.getModelsMap().values();
            for (VkModel vkModel : vulkanModels) {
                for (VkMesh vkMesh : vkModel.getVulkanMeshList()) {
                    vertexBuffer.put(0, vkMesh.verticesBuffer().getBuffer());
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                    vkCmdBindIndexBuffer(cmdHandle, vkMesh.indicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);
                    vkCmdDrawIndexed(cmdHandle, vkMesh.numIndices(), 1, 0, 0, 0);
                }
            }

            vkCmdEndRendering(cmdHandle);


            // LOGGER.info("Swap chain image: 0x{}", swapChainImage);
            VkUtils.imageBarrier(stack, cmdHandle, swapChainImage,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_PIPELINE_STAGE_2_NONE,
                    VK_IMAGE_ASPECT_COLOR_BIT);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        Arrays.asList(renderInfo).forEach(VkRenderingInfo::free);
        Arrays.asList(attInfoColor).forEach(VkRenderingAttachmentInfo.Buffer::free);
        clrValueColor.free();
        pipeline.cleanup(vkCtx);
    }

    private static ShaderModule[] createShaderModules(VkCtx vkCtx) {
        byte[] vertexShaderContent = ShaderCompiler.compileShader(ShadersSource.VERTEX_SHADER, Shaderc.shaderc_glsl_vertex_shader);
        byte[] fragmentShaderContent = ShaderCompiler.compileShader(ShadersSource.FRAGMENT_SHADER, Shaderc.shaderc_glsl_fragment_shader);

        return new ShaderModule[]{
                new ShaderModule(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, vertexShaderContent),
                new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, fragmentShaderContent)
        };
    }

    private static Pipeline createPipeline(VkCtx vkCtx, ShaderModule[] shaderModules,
                                           SharedBufferData colorBufferData, SharedBufferData depthBufferData,
                                           SharedBufferData motionBufferData) {
        var vtxBuffStruct = new VtxBufferStruct();
        var buildInfo = new PipelineBuildInfo(shaderModules, vtxBuffStruct.getVi(),
                vkCtx.getSurface().getSurfaceFormat().imageFormat(), colorBufferData, depthBufferData, motionBufferData);
        var pipeline = new Pipeline(vkCtx, buildInfo);
        vtxBuffStruct.cleanup();
        return pipeline;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }
}


