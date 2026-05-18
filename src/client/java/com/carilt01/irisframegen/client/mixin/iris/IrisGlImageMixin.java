package com.carilt01.irisframegen.client.mixin.iris;

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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlImage.class)
public class IrisGlImageMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(IrisGlImageMixin.class);

    @Inject(method="<init>", at=@At("TAIL"))
    private static void initInject(String name, String samplerName, TextureType target, PixelFormat format, InternalTextureFormat internalFormat, PixelType pixelType, boolean clear, int width, int height, int depth, CallbackInfo ci) {
        LOGGER.info("GL IMG MIXIN: Name: {}", name);
    }
}
