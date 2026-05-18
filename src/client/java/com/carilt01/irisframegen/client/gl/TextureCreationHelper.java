package com.carilt01.irisframegen.client.gl;

import com.carilt01.irisframegen.client.GlState;
import com.carilt01.irisframegen.client.VkState;
import com.carilt01.irisframegen.client.vk.ImageBufferType;
import com.carilt01.irisframegen.client.vk.TextureFormatVk;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectWin32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;

import static org.lwjgl.opengl.EXTMemoryObjectWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT;
import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_BASE_LEVEL;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LEVEL;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL30C.GL_RG32F;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8_SINT;

public class TextureCreationHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextureCreationHelper.class);

    public static TextureFormatVk textureFormatToVk(TextureFormat format) {
        switch (format) {
            case TextureFormat.DEPTH32 -> {
                return TextureFormatVk.DEPTH32;
            }
            case TextureFormat.RED8 -> {
                return TextureFormatVk.RED8;
            }
            case TextureFormat.RED8I -> {
                return TextureFormatVk.RED8I;
            }
            case TextureFormat.RGBA8 -> {
                return TextureFormatVk.RGBA8;
            }
        }

        throw new IllegalArgumentException("Unknown texture format: " + format);
    }

    public static int textureFormatToVkConstant(TextureFormatVk colorFormat) {
        switch (colorFormat) {
            case TextureFormatVk.RGBA8 -> {
                return VK_FORMAT_R8G8B8A8_UNORM;
            }
            case TextureFormatVk.DEPTH32 -> {
                return VK_FORMAT_D32_SFLOAT;
            }
            case TextureFormatVk.RED8 -> {
                return VK_FORMAT_R8_UNORM;
            }
            case TextureFormatVk.RED8I -> {
                return VK_FORMAT_R8_SINT;
            }
            case TextureFormatVk.RG32F -> {
                return VK_FORMAT_R32G32_SFLOAT;
            }
            default -> {
                return -1;
            }
        }
    }

    private static TextureFormat textureFormatVkToTextureFormat(TextureFormatVk formatVk) {
        return switch (formatVk) {
            case RED8 -> TextureFormat.RED8;
            case RED8I -> TextureFormat.RED8I;
            case DEPTH32 -> TextureFormat.DEPTH32;
            case RGBA8 -> TextureFormat.RGBA8;
            default -> throw new IllegalArgumentException("Texture format of " + formatVk + " cannot be converted");
        };
    }


    private static int textureFormatToGlInternal(TextureFormatVk format) {
        switch (format) {
            case RGBA8:   return GL_RGBA8;
            case DEPTH32: return GL_DEPTH_COMPONENT32F;
            case RED8:    return GL_R8;
            case RED8I:   return GL_R8I;
            case RG32F:   return GL_RG32F;
            default:      return -1;
        }
    }

    public static int importTextureFromHandle(long handle, long size,
                                               int mipLevels, int w, int h,
                                               TextureFormatVk format) {
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

    public static void createTextureMojang(int width, int height, TextureFormatVk format, int mipLevels,
                                           int usage, String label, int depthOrLayers, ImageBufferType type, CallbackInfoReturnable<GpuTexture> cir) {



        VkState.checkCompatible();

        LOGGER.info("Detected main framebuffer, creating vk texture: label: {}", label);
        LOGGER.info("Format ordinal: {}, format hashcode: {}, format: {}", format.ordinal(), format.hashCode(), format);
        int vkFormat = TextureCreationHelper.textureFormatToVkConstant(format);
        LOGGER.info("Vk format: {}", vkFormat);
        long[] outputSize = new long[1];
        long importHandle = VkState.getEngine().getRender().getImportHandleForImage(width, height, mipLevels, vkFormat, outputSize, type);

        LOGGER.info("Image import handle is: {}, output size: {}", importHandle, outputSize[0]);

        int generatedTexture = importTextureFromHandle(importHandle, outputSize[0],
                mipLevels, width, height, format);

        try {
            Constructor<GlTexture> ctor = GlTexture.class.getDeclaredConstructor(
                    int.class, String.class, TextureFormat.class,
                    int.class, int.class, int.class, int.class, int.class
            );
            ctor.setAccessible(true);

            TextureFormat officialFormat = textureFormatVkToTextureFormat(format);

            GlTexture customTexture = ctor.newInstance(
                    usage, label, officialFormat, width, height, depthOrLayers,
                    1,
                    generatedTexture
            );

            GlState.importSemaphore(VkState.getEngine().getRender().getGlRenderCompleteSemphAdd());

            GlState.createSyncObject();

            cir.setReturnValue(customTexture);
        } catch (Exception e) {
            glDeleteTextures(generatedTexture);
            throw new RuntimeException("Failed to initialize custom GLTexture: " + e);
        }
    }
}
