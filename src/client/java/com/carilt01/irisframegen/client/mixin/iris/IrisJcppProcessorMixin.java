package com.carilt01.irisframegen.client.mixin.iris;

import com.carilt01.irisframegen.client.iris.IrisShaderPreprocessor;
import com.carilt01.irisframegen.client.iris.IrisShaderState;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@Mixin(IncludeGraph.class)
public class IrisJcppProcessorMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(IrisJcppProcessorMixin.class);

    @Inject(method="readFile", at=@At("HEAD"), cancellable = true)
    private static String injectRead(Path path, CallbackInfoReturnable<String> cir) throws IOException {

        LOGGER.info("IncludeGraph mixin reading: {}", path)
        ;
        String source = Files.readString(path);

        // basically random
        String pickedSourceName = path.normalize().toString().replace("\\", "/");
        LOGGER.info("Source name: {}", pickedSourceName);

        String preprocessedShader = IrisShaderPreprocessor.preprocessShader(source, pickedSourceName, IrisShaderState.shaderZipFile);

        cir.setReturnValue(preprocessedShader);
        return preprocessedShader;
    }
}
