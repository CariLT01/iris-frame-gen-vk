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

    @Inject(method="readProperties", at=@At("HEAD"))
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


            try (FileSystem fs = FileSystems.newFileSystem(shaderZipPath, Map.of())) {
                Path root = fs.getPath("/");
                try (var stream = Files.walk(root)) {
                    stream.filter(Files::isRegularFile).forEach((file) -> {
                        try {
                            LOGGER.info("Reading: {}", file.toAbsolutePath());
                            String fileContent = Files.readString(file.toAbsolutePath());
                            IrisShaderState.addShaderLink(fileContent, file.toString());
                            LOGGER.info("Adding shader link: {}: {}", file.toString(), fileContent.hashCode());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (IOException e) {
                    LOGGER.error("[Iris Frame Gen]: Failed to parse file contents: ", e);
                }
            }


            String initialFile = Files.readString(shaderPath.resolve(name), StandardCharsets.ISO_8859_1);


            initialFile = "\n\nimage.irisFrameGen__motionVectorsBuffer = irisFrameGen__motionVectorsOutBuf RG32F RG32F RG32F true true 1 1 1 1\n\n" + initialFile;


            //LOGGER.info("Modified file is: {}", initialFile);

            Path absShaderPath = Minecraft.getInstance().gameDirectory.toPath().resolve(Path.of(shaderPath.toString()));

            LOGGER.info("Shader pack directory: {}", absShaderPath.toAbsolutePath());



            return initialFile;
        } catch (IOException e) {
            LOGGER.info("[Iris Frame Gen] Failed to load shader.properties file: ", e);
            return null;
        }
    }
}
