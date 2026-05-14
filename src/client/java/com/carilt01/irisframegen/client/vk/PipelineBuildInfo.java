package com.carilt01.irisframegen.client.vk;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;

public class PipelineBuildInfo {

    private final int colorFormat;
    private final ShaderModule[] shaderModules;
    private final VkPipelineVertexInputStateCreateInfo vi;

    private final long sampler;
    private final ImageView sharedImageView;

    public PipelineBuildInfo(ShaderModule[] shaderModules, VkPipelineVertexInputStateCreateInfo vi, int colorFormat, long sampler, ImageView sharedImageView) {
        this.shaderModules = shaderModules;
        this.colorFormat = colorFormat;
        this.vi = vi;
        this.sampler = sampler;
        this.sharedImageView = sharedImageView;
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

    public ImageView getSharedImageView() {
        return sharedImageView;
    }

    public long getSampler() {
        return sampler;
    }
}
