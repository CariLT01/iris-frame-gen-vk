package com.carilt01.irisframegen.client.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.LongBuffer;

import static com.carilt01.irisframegen.client.vk.VkUtils.transitionImageLayout;
import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.VK10.*;

public class ImageView {
    public static class ImageViewData {
        private int aspectMask;
        private int baseArrayLayer;
        private int format;
        private int layerCount;
        private int mipLevels;
        private int viewType;

        public ImageViewData() {
            this.baseArrayLayer = 0;
            this.layerCount = 1;
            this.mipLevels = 1;
            this.viewType = VK_IMAGE_VIEW_TYPE_2D;
        }

        public ImageView.ImageViewData aspectMask(int aspectMask) {
            this.aspectMask = aspectMask;
            return this;
        }

        public ImageView.ImageViewData baseArrayLayer(int baseArrayLayer) {
            this.baseArrayLayer = baseArrayLayer;
            return this;
        }

        public ImageView.ImageViewData format(int format) {
            this.format = format;
            return this;
        }

        public ImageView.ImageViewData layerCount(int layerCount) {
            this.layerCount = layerCount;
            return this;
        }

        public ImageView.ImageViewData mipLevels(int mipLevels) {
            this.mipLevels = mipLevels;
            return this;
        }

        public ImageView.ImageViewData viewType(int viewType) {
            this.viewType = viewType;
            return this;
        }
    }

    private int aspectMask;
    private int mipLevels;
    private final long vkImage;
    private final long vkImageView;
    private int layout;
    public final ImageBufferType imageType;

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageView.class);

    public ImageView(Device device, long vkImage, ImageViewData imageViewData, int layout,
                     ImageBufferType imageBufferType) {
        this.aspectMask = imageViewData.aspectMask;
        this.mipLevels = imageViewData.mipLevels;
        this.vkImage = vkImage;
        this.layout = layout;
        this.imageType = imageBufferType;

        LOGGER.info("creating image view for: 0x{}", vkImage);

        try (var stack = MemoryStack.stackPush()) {
            LongBuffer lp = stack.mallocLong(1);
            var viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(vkImage)
                    .viewType(imageViewData.viewType)
                    .format(imageViewData.format)
                    .subresourceRange(it -> it
                            .aspectMask(aspectMask)
                            .baseMipLevel(0)
                            .levelCount(mipLevels)
                            .baseArrayLayer(imageViewData.baseArrayLayer)
                            .layerCount(imageViewData.layerCount));

            vkCheck(vkCreateImageView(device.getVkDevice(), viewCreateInfo, null, lp),
                    "Failed to create image view");
            vkImageView = lp.get(0);
        }
    }

    public void cleanup(Device device) {
        vkDestroyImageView(device.getVkDevice(), vkImageView, null);
    }

    public int getAspectMask() {
        return aspectMask;
    }

    public int getMipLevels() {
        return mipLevels;
    }

    public long getVkImageView() {
        return vkImageView;
    }



    public long getVkImage() {

        return vkImage;
    }

    public void transitionLayout(CmdBuffer cmdBuffer, int newLayout) {
        transitionImageLayout(cmdBuffer.getVkCommandBuffer(), this.getVkImage(), this.layout, newLayout, imageType);
        this.layout = newLayout;
    }

    public void copy(ImageView destination, CmdBuffer cmdBuf,
                     int width, int height) {
        try (var stack = MemoryStack.stackPush()) {
            this.transitionLayout(cmdBuf, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
            destination.transitionLayout(cmdBuf, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);

            VkImageCopy.Buffer copyRegion = VkImageCopy.calloc(1, stack);
            copyRegion.srcSubresource()
                    .aspectMask(VkUtils.getAspectMaskFromBufferType(this.imageType))
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);

            copyRegion.dstSubresource()
                    .aspectMask(VkUtils.getAspectMaskFromBufferType(this.imageType))
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);

            copyRegion.extent()
                    .width(width)
                    .height(height)
                    .depth(1);


            vkCmdCopyImage(cmdBuf.getVkCommandBuffer(),
                    this.getVkImage(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    destination.getVkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    copyRegion);
        }

    }
}
