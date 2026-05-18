package com.carilt01.irisframegen.client.mixin;

import com.carilt01.irisframegen.client.StackTracePrint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "com.mojang.blaze3d.opengl.GlRenderPass")
public class GlslRenderMixin {
    @Inject(method="drawIndexed", at=@At("HEAD"))
    private void injectDrawIndexed(CallbackInfo ci) {
        StackTracePrint.findMyCaller();
    }
}