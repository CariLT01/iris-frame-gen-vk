package com.carilt01.irisframegen.client.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.VK10.*;

public class Pipeline {

    private static final Logger LOGGER = LoggerFactory.getLogger(Pipeline.class);

    private final long vkPipeline;
    private final long vkPipelineLayout;
    private long descriptorSets;
    private long descriptorSetLayout;

    public Pipeline(VkCtx vkCtx, PipelineBuildInfo buildInfo) {
        LOGGER.debug("Creating pipeline");
        Device device = vkCtx.getDevice();
        try (var stack = MemoryStack.stackPush()) {
            LongBuffer lp = stack.mallocLong(1);

            ByteBuffer main = stack.UTF8("main");

            ShaderModule[] shaderModules = buildInfo.getShaderModules();
            int numModules = shaderModules.length;
            var shaderStages = VkPipelineShaderStageCreateInfo.calloc(numModules, stack);
            for (int i = 0; i < numModules; i++) {
                ShaderModule shaderModule = shaderModules[i];
                shaderStages.get(i)
                        .sType$Default()
                        .stage(shaderModule.getShaderStage())
                        .module(shaderModule.getHandle())
                        .pName(main);
            }

            var assemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            var viewportStateCreateInfo = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewportCount(1)
                    .scissorCount(1);

            var rasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .cullMode(VK_CULL_MODE_NONE)
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .lineWidth(1.0f);

            var multisampleStateCreateInfo = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            VkPipelineDynamicStateCreateInfo vkPipelineDynamicStateCreateInfo =
                    VkPipelineDynamicStateCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                            .pDynamicStates(stack.ints(
                                    VK_DYNAMIC_STATE_VIEWPORT,
                                    VK_DYNAMIC_STATE_SCISSOR
                            ));

            var blendAttState = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(false);
            var colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(blendAttState);

            IntBuffer colorFormats = stack.mallocInt(1);
            colorFormats.put(0, buildInfo.getColorFormat());
            var rendCreateInfo = VkPipelineRenderingCreateInfo.calloc(stack)
                    .sType$Default()
                    .colorAttachmentCount(1)
                    .pColorAttachmentFormats(colorFormats);

            initDescriptorSets(vkCtx, buildInfo.getSampler(), buildInfo.getSharedImageView());

            LOGGER.info("Layout pointer, 0x{}", descriptorSetLayout);

            var pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .pSetLayouts(stack.longs(descriptorSetLayout))
                    .setLayoutCount(1)
                    .sType$Default();

            vkCheck(vkCreatePipelineLayout(device.getVkDevice(), pPipelineLayoutCreateInfo, null, lp),
                    "Failed to create pipeline layout");
            vkPipelineLayout = lp.get(0);

            var createInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .renderPass(VK_NULL_HANDLE)
                    .pStages(shaderStages)
                    .pVertexInputState(buildInfo.getVi())
                    .pInputAssemblyState(assemblyStateCreateInfo)
                    .pViewportState(viewportStateCreateInfo)
                    .pRasterizationState(rasterizationStateCreateInfo)
                    .pColorBlendState(colorBlendState)
                    .pMultisampleState(multisampleStateCreateInfo)
                    .pDynamicState(vkPipelineDynamicStateCreateInfo)
                    .layout(vkPipelineLayout)
                    .pNext(rendCreateInfo);

            vkCheck(vkCreateGraphicsPipelines(device.getVkDevice(), vkCtx.getPipelineCache().getVkPipelineCache(), createInfo, null, lp),
                    "Failed to create graphics pipeline");
            vkPipeline = lp.get(0);
        }
    }

    private void initDescriptorSets(VkCtx vkCtx, long sampler, ImageView sharedImageView) {
        try (var stack = MemoryStack.stackPush()) {
            var binding = VkDescriptorSetLayoutBinding.calloc(stack)
                    .binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
            bindings.put(0, binding);

            var createInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pBindings(bindings);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateDescriptorSetLayout(vkCtx.getDevice().getVkDevice(), createInfo, null, lp),
                    "Failed to create description set layout");

            long descriptionSetLayout = lp.get(0);

            descriptorSetLayout = descriptionSetLayout;
            LOGGER.info("Descriptor set layout at: 0x{}", descriptorSetLayout);

            LOGGER.info("Description layout pointer: 0x{}", descriptionSetLayout);

            // pool
            var poolSize = VkDescriptorPoolSize.calloc(stack)
                    .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.put(0, poolSize);

            var poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .maxSets(1)
                    .pPoolSizes(poolSizes);

            vkCheck(vkCreateDescriptorPool(vkCtx.getDevice().getVkDevice(), poolInfo, null, lp),
                    "Failed to create descriptor pool");

            long descriptorPool = lp.get(0);

            // allocate set
            var allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(stack.longs(descriptionSetLayout));

            vkCheck(vkAllocateDescriptorSets(vkCtx.getDevice().getVkDevice(), allocInfo, lp),
                    "Failed to allocate descriptor sets");

            descriptorSets = lp.get(0);
            LOGGER.info("Description sets at: 0x{}", descriptorSets);

            VkDescriptorImageInfo.Buffer imageInfos = VkDescriptorImageInfo.calloc(1, stack);
            imageInfos.get(0)
                    .sampler(sampler)
                    .imageView(sharedImageView.getVkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            VkWriteDescriptorSet write = VkWriteDescriptorSet.calloc(stack)
                    .sType$Default()
                    .dstSet(descriptorSets)
                    .dstBinding(0)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(imageInfos);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(1, stack);
            writes.put(0, write);

            vkUpdateDescriptorSets(vkCtx.getDevice().getVkDevice(), writes, null);

            LOGGER.info("Descriptor sets created");
        }

    }

    public void cleanup(VkCtx vkCtx) {
        LOGGER.debug("Destroying pipeline");
        VkDevice vkDevice = vkCtx.getDevice().getVkDevice();
        vkDestroyPipelineLayout(vkDevice, vkPipelineLayout, null);
        vkDestroyPipeline(vkDevice, vkPipeline, null);
    }

    public long getVkPipeline() {
        return vkPipeline;
    }

    public long getVkPipelineLayout() {
        return vkPipelineLayout;
    }

    public long getDescriptorSets() {
        return descriptorSets;
    }
}
