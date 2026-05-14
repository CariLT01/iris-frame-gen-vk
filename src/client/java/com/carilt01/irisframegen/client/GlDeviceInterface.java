package com.carilt01.irisframegen.client;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import org.jspecify.annotations.Nullable;

public interface GlDeviceInterface {
    // Add methods you want to expose from GlDevice
    GpuTexture iris_frame_generation$callCreateTexture(@Nullable String label, final @GpuTexture.Usage int usage, final TextureFormat format, final int width, final int height, final int depthOrLayers, final int mipLevels);
}