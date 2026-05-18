package com.carilt01.irisframegen.client.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
public class RenderTypeMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderTypeMixin.class);

    @Inject(method="<init>", at=@At("HEAD"))
    private static void renderTypeCreate(final String name, final RenderSetup state, CallbackInfo ci) {
        //LOGGER.info("Render type name: {}", name);
    }
}
