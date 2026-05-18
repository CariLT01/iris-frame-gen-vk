package com.carilt01.irisframegen.client.mixin.iris;

import com.carilt01.irisframegen.client.GlState;
import com.carilt01.irisframegen.client.VkState;
import com.carilt01.irisframegen.client.gl.TextureCreationHelper;
import com.carilt01.irisframegen.client.vk.ImageBufferType;
import com.carilt01.irisframegen.client.vk.TextureFormatVk;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.PixelFormat;
import net.irisshaders.iris.gl.texture.PixelType;
import net.irisshaders.iris.gl.texture.TextureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(GlImage.class)
public class IrisGlImageMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(IrisGlImageMixin.class);

    @Redirect(method="<init>", at=@At(
            value="INVOKE",
            target = "Lnet/irisshaders/iris/gl/IrisRenderSystem;createTexture(I)I"
    ))
    private static int redirectCreateTexture(int glType,
                                      String name,
                                      String samplerName,
                                      TextureType target,
                                      PixelFormat format,
                                      InternalTextureFormat internalFormat,
                                      PixelType pixelType,
                                      boolean clear,
                                      int width,
                                      int height,
                                      int depth) {
        if (!Objects.equals(name, "irisFrameGenOUTmotion")) {
            return IrisRenderSystem.createTexture(target.getGlType());
        } else {
            LOGGER.info("Found texture: {}", name);
        }

        LOGGER.info("Creating texture for Iris motion vectors");
        long[] outBuf = new long[1];
        int vkConstantFormat = TextureCreationHelper.textureFormatToVkConstant(TextureFormatVk.RG32F);

        long importHandle = VkState.getEngine().getRender().getImportHandleForImage(width, height, 1, vkConstantFormat, outBuf, ImageBufferType.MOTION);
        int generatedTexture = TextureCreationHelper.importTextureFromHandle(importHandle, outBuf[0], 1, width, height, TextureFormatVk.RG32F);
        LOGGER.info("Texture for motion vectors ID is: {}" ,generatedTexture);

        GlState.motionBufferInitialized = true;
        if (GlState.colorBufferInitialized && GlState.depthBufferInitialized) {
            VkState.signalReady();
        }

        return generatedTexture;

    }


    @Inject(method="setup", at=@At("HEAD"))
    private static void injectSetup(int texture, int width, int height, int depth, CallbackInfo ci) {
        LOGGER.info("GL IMG MIXIN: TEXTURE: {}", texture);
    }
}
