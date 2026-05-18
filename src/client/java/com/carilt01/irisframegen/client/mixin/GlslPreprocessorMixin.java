package com.carilt01.irisframegen.client.mixin;

import com.carilt01.irisframegen.client.StackTracePrint;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(GlslPreprocessor.class)
public class GlslPreprocessorMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlslPreprocessorMixin.class);

    @Inject(method="process", at=@At("RETURN"))
    private void injectPreprocess(final String source, CallbackInfoReturnable<List<String>> cir) {

        List<String> finalSources = cir.getReturnValue();

        for (String finSource : finalSources) {
            if (!finSource.contains("gl_Position")) {
                //LOGGER.info("not a vertex shader");
                return;
            }
            //LOGGER.info("Preprocess source: {}", finSource);
        }

        StackTracePrint.findMyCaller();


    }
}
