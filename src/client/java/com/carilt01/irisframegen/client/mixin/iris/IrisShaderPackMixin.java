package com.carilt01.irisframegen.client.mixin.iris;

import com.carilt01.irisframegen.client.iris.IrisShaderState;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.zip.ZipFile;

@Mixin(ShaderPack.class)
public class IrisShaderPackMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(IrisShaderPackMixin.class);

    @Inject(method="readProperties", at=@At("HEAD"), cancellable = true)
    private static String readPropertiesInject(Path shaderPath, String name, CallbackInfoReturnable<String> cir) throws IOException {


        try {

            // resolve root directory
            FileSystem ifs = shaderPath.getFileSystem();
            LOGGER.info("Type: {}", ifs);

            Path shaderZipPath = Path.of(ifs.toString());
            IrisShaderState.shaderZipPath = shaderZipPath;
            if (IrisShaderState.shaderZipFile == null) {
                LOGGER.info("Opening shader pack zip file");
                IrisShaderState.shaderZipFile = new ZipFile(shaderZipPath.toFile());
            }


            String initialFile = Files.readString(shaderPath.resolve(name), StandardCharsets.ISO_8859_1);


            initialFile = "\n\niris.features.required = CUSTOM_IMAGES\nimage.irisFrameGenOUTmotion = irisFrameGenOUTmotion RG RG32F FLOAT true true 1 1 1 1\n\n" + initialFile;


            LOGGER.info("Modified file is: {}", initialFile);

            Path absShaderPath = Minecraft.getInstance().gameDirectory.toPath().resolve(Path.of(shaderPath.toString()));

            LOGGER.info("Shader pack directory: {}", absShaderPath.toAbsolutePath());

            cir.setReturnValue(initialFile);

            return initialFile;
        } catch (IOException e) {
            LOGGER.info("[Iris Frame Gen] Failed to load shader.properties file: ", e);
            return null;
        }
    }
}
