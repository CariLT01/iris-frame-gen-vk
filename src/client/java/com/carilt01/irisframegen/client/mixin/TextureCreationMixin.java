package com.carilt01.irisframegen.client.mixin;

import com.carilt01.irisframegen.client.GlDeviceInterface;
import com.carilt01.irisframegen.client.GlState;
import com.carilt01.irisframegen.client.VkState;
import com.carilt01.irisframegen.client.gl.TextureCreationHelper;
import com.carilt01.irisframegen.client.vk.ImageBufferType;
import com.carilt01.irisframegen.client.vk.TextureFormatVk;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

import static org.lwjgl.opengl.EXTMemoryObject.glCreateMemoryObjectsEXT;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public abstract class TextureCreationMixin implements GlDeviceInterface {

    // Shadow must match the bytecode exactly
    @Shadow
    public abstract GpuTexture createTexture(Supplier<String> label, int usage, TextureFormat format, int width, int height, int depth, int mips);

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(TextureCreationMixin.class);






    @Inject(method = "createTexture", at = @At(value = "HEAD", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_texImage2D(...)V"), cancellable = true)
    private void iris_frame_gen$onTextureCreate(
            Supplier<String> labelSupplier, // Bytecode wants Supplier
            int usage,
            TextureFormat format,
            int width,
            int height,
            int depthOrLayers,
            int mipLevels,
            CallbackInfoReturnable<GpuTexture> cir
    ) {
        String label = labelSupplier.get();

        if (!label.startsWith("minecraft:") && !label.startsWith("entity:")) {
            LOGGER.info("Other framebuffer texture: {}", label);
        }

        if (!VkState.getSignaled()) {

            TextureFormatVk formatVk = TextureCreationHelper.textureFormatToVk(format);

            // ONLY hijack the specific texture we need for Frame Gen
            if ("Main / Color".equals(label)) {
                TextureCreationHelper.createTextureMojang(width, height, formatVk, mipLevels, usage, label, depthOrLayers, ImageBufferType.COLOR, cir);
                GlState.colorBufferInitialized = true;
            } else if ("Main / Depth".equals(label)) {
                TextureCreationHelper.createTextureMojang(width, height, formatVk, mipLevels, usage, label, depthOrLayers, ImageBufferType.DEPTH, cir);
                GlState.depthBufferInitialized = true;
            } else {
                // LOGGER.info("other: {}", label);
            }

            if (GlState.colorBufferInitialized && GlState.depthBufferInitialized && GlState.motionBufferInitialized) {
                VkState.signalReady();
            }

            GlState.GLThread = Thread.currentThread();
        }


    }

    // Update your interface implementation to handle the conversion
    @Override
    public GpuTexture iris_frame_generation$callCreateTexture(@Nullable String label, int usage, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels) {
        // Wrap the String in a Supplier to satisfy the Shadowed method
        return this.createTexture(() -> label, usage, format, width, height, depthOrLayers, mipLevels);
    }
}