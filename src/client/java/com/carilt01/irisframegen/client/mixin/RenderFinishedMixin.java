package com.carilt01.irisframegen.client.mixin;

import com.carilt01.irisframegen.client.GlState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.EXTSemaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.EXTSemaphore.glSignalSemaphoreEXT;

@Mixin(GameRenderer.class)
public class RenderFinishedMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderFinishedMixin.class);

    @Inject(method="renderLevel", at=@At("TAIL"))
    private void renderLevelFinished(final DeltaTracker deltaTracker, CallbackInfo ci) {
        LOGGER.info("signaling!!!!!!");
        EXTSemaphore.glSignalSemaphoreEXT(GlState.glSemph, new int[0], new int[0], new int[0]);
    }
}
