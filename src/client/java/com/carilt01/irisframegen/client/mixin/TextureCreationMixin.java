package com.carilt01.irisframegen.client.mixin;

import com.carilt01.irisframegen.client.GlDeviceInterface;
import com.carilt01.irisframegen.client.GlState;
import com.carilt01.irisframegen.client.VkState;
import com.mojang.blaze3d.opengl.GlBackend;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectWin32;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.lang.reflect.Constructor;
import java.nio.IntBuffer;
import java.util.function.Supplier;

import static org.lwjgl.opengl.EXTMemoryObject.glCreateMemoryObjectsEXT;
import static org.lwjgl.opengl.EXTMemoryObjectWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.vulkan.VK10.*;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public abstract class TextureCreationMixin implements GlDeviceInterface {

    // Shadow must match the bytecode exactly
    @Shadow
    public abstract GpuTexture createTexture(Supplier<String> label, int usage, TextureFormat format, int width, int height, int depth, int mips);

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(TextureCreationMixin.class);


    @Unique
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


    @Unique
    private static int textureFormatToGlInternal(TextureFormat format) {
        switch (format) {
            case RGBA8:   return GL_RGBA8;
            case DEPTH32: return GL_DEPTH_COMPONENT32F;
            case RED8:    return GL_R8;
            case RED8I:   return GL_R8I;
            default:      return -1;
        }
    }

    @Unique
    private static int importTextureFromHandle(long handle, long size,
                                                int mipLevels, int w, int h,
                                               TextureFormat format) {
        int[] memoryObjects = new int[1];
        EXTMemoryObject.glCreateMemoryObjectsEXT(memoryObjects);
        int memoryObject = memoryObjects[0];

        EXTMemoryObjectWin32.glImportMemoryWin32HandleEXT(memoryObject, size, GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, handle);


        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        int glTextureFormat = textureFormatToGlInternal(format);

        EXTMemoryObject.glTexStorageMem2DEXT(GL_TEXTURE_2D, mipLevels, glTextureFormat, w, h, memoryObject, 0);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);   // no mipmaps
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);


        glBindTexture(GL_TEXTURE_2D, 0);

        return textureId;


    }

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

        // ONLY hijack the specific texture we need for Frame Gen
        if ("Main / Color".equals(label)) {

            VkState.checkCompatible();

            LOGGER.info("Detected main framebuffer, creating vk texture");
            LOGGER.info("Format ordinal: {}, format hashcode: {}, format: {}", format.ordinal(), format.hashCode(), format);
            int vkFormat = textureFormatToVkConstant(format);
            LOGGER.info("Vk format: {}", vkFormat);
            long[] outputSize = new long[1];
            long importHandle = VkState.getEngine().getRender().getImportHandleForImage(width, height, mipLevels, vkFormat, outputSize);

            LOGGER.info("Image import handle is: {}, output size: {}", importHandle, outputSize[0]);

            int generatedTexture = importTextureFromHandle(importHandle, outputSize[0],
                    mipLevels, width, height, format);

            try {
                Constructor<GlTexture> ctor = GlTexture.class.getDeclaredConstructor(
                        int.class, String.class, TextureFormat.class,
                        int.class, int.class, int.class, int.class, int.class
                );
                ctor.setAccessible(true);
                GlTexture customTexture = ctor.newInstance(
                        usage, label, format, width, height, depthOrLayers,
                        1,
                        generatedTexture
                );

                VkState.signalReady();

                GlState.importSemaphore(VkState.getEngine().getRender().getGlRenderCompleteSemphAdd());

                cir.setReturnValue(customTexture);
            } catch (Exception e) {
                glDeleteTextures(generatedTexture);
                throw new RuntimeException("Failed to initialize custom GLTexture: " + e);
            }


        }
    }

    // Update your interface implementation to handle the conversion
    @Override
    public GpuTexture iris_frame_generation$callCreateTexture(@Nullable String label, int usage, TextureFormat format, int width, int height, int depthOrLayers, int mipLevels) {
        // Wrap the String in a Supplier to satisfy the Shadowed method
        return this.createTexture(() -> label, usage, format, width, height, depthOrLayers, mipLevels);
    }
}