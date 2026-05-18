package com.carilt01.irisframegen.client;

public class FSR3Native {

    static {
        System.loadLibrary("amd_fsr3natives.dll");
    }

    public static native long init(long vkDevice, long vkQueue);

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
}
