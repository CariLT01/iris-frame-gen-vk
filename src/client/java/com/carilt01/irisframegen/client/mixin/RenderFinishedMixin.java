package com.carilt01.irisframegen.client.mixin;

import com.carilt01.irisframegen.client.GlState;
import com.carilt01.irisframegen.client.VkState;
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
import static org.lwjgl.opengl.GL11C.glFinish;

@Mixin(GameRenderer.class)
public class RenderFinishedMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderFinishedMixin.class);

    @Inject(method="render", at=@At("TAIL"))
    private void renderLevelFinished(final DeltaTracker deltaTracker, final boolean advanceGameTime, CallbackInfo ci) {
        // TODO: replace glFinish with something more efficient
        glFinish(); // expensive, but necessary at the moment to prevent flickering
        VkState.resumeEngineThread();
    }
}