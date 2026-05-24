package com.carilt01.irisframegen.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class FSR3Native {

    private static File tempDir = null;

    private static final Logger LOGGER = LoggerFactory.getLogger(FSR3Native.class);

    static {
        try {

            tempDir = Files.createTempDirectory("iris_natives_").toFile();
            tempDir.deleteOnExit();


            unpackResource("/natives/ffx_backend_vk_x64.dll");
            unpackResource("/natives/ffx_opticalflow_x64.dll");
            unpackResource("/natives/ffx_frameinterpolation_x64.dll");
            unpackResource("/natives/ffx_fsr3upscaler_x64.dll");
            unpackResource("/natives/ffx_fsr3_x64.dll");
            unpackResource("/natives/AMDfsr3.dll");

            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                com.sun.jna.NativeLibrary.addSearchPath("ffx_fsr3_x64", tempDir.getAbsolutePath());
            }

            System.load(new File(tempDir, "ffx_backend_vk_x64.dll").getAbsolutePath());
            System.load(new File(tempDir, "ffx_opticalflow_x64.dll").getAbsolutePath());
            System.load(new File(tempDir, "ffx_frameinterpolation_x64.dll").getAbsolutePath());
            System.load(new File(tempDir, "ffx_fsr3upscaler_x64.dll").getAbsolutePath());
            System.load(new File(tempDir, "ffx_fsr3_x64.dll").getAbsolutePath());
            System.load(new File(tempDir, "AMDfsr3.dll").getAbsolutePath());
        } catch (Throwable e) {
            throw new RuntimeException("Failed to extract and load native library: " + e);
        }

    }

    private static void unpackResource(String resourcePath) throws IOException {
        String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        File targetFile = new File(tempDir, fileName);
        targetFile.deleteOnExit();

        if (!targetFile.exists()) {
            try (InputStream in = FSR3Native.class.getResourceAsStream(resourcePath);
                 FileOutputStream out = new FileOutputStream(targetFile)) {
                if (in == null) throw new RuntimeException("File not found in JAR: " + resourcePath);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    public static native long init(long vkDevice, long vkQueue, long vkPhysicalDevice, long vkInstance);

    public static native void setSwapchain(long context, long vkSwapchain);

    public static native void dispatch(
            long context,
            long colorImage,
            long depthImage,
            long motionVectors,
            long outputImage,
            int width,
            int height,
            float deltaTime
    );

    public static native void destroy(long context);

    public static native void present(long context);
}
