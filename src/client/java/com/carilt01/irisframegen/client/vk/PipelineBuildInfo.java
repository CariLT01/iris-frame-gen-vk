package com.carilt01.irisframegen.client.vk;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;

public class PipelineBuildInfo {

    private final int colorFormat;
    private final ShaderModule[] shaderModules;
    private final VkPipelineVertexInputStateCreateInfo vi;

    private final SharedBufferData colorBufferData;
    private final SharedBufferData depthBufferData;

    public PipelineBuildInfo(ShaderModule[] shaderModules, VkPipelineVertexInputStateCreateInfo vi, int colorFormat,
                             SharedBufferData colorBufferData, SharedBufferData depthBufferData) {
        this.shaderModules = shaderModules;
        this.colorFormat = colorFormat;
        this.vi = vi;
        this.colorBufferData = colorBufferData;
        this.depthBufferData = depthBufferData;
    }

    public int getColorFormat() {
        return colorFormat;
    }

    public ShaderModule[] getShaderModules() {
        return shaderModules;
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return vi;
    }

    public SharedBufferData getColorBufferData() {
        return colorBufferData;
    }

    public SharedBufferData getDepthBufferData() {
        return depthBufferData;
    }
}
