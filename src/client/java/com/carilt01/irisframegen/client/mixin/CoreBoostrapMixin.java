package com.carilt01.irisframegen.client.mixin;

import com.carilt01.irisframegen.IrisFrameGeneration;
import com.carilt01.irisframegen.client.Engine;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.Proxy;

@Mixin(MinecraftClient.class)
public class CoreBoostrapMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(IrisFrameGeneration.MOD_ID);

    @Inject(method = "<init>", at = @At("HEAD"))
    private static void beforeInit(String accessToken, Proxy proxy, CallbackInfo ci) {
        LOGGER.info("Core boostrap mixin");

    }
}
