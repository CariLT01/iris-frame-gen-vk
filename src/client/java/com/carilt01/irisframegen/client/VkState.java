package com.carilt01.irisframegen.client;

import org.lwjgl.opengl.GL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VkState {

    private static final Logger LOGGER = LoggerFactory.getLogger(VkState.class);

    private static Engine engine = null;
    private static Thread engineThread = null;

    private static final Object lock = new Object();
    private static boolean signaled = false;

    public static void checkCompatible() {
        boolean hasMemoryObject = GL.getCapabilities().GL_EXT_memory_object;
        boolean hasMemoryObjectWin32 = GL.getCapabilities().GL_EXT_memory_object_win32;

        if (!hasMemoryObject || !hasMemoryObjectWin32) {
            throw new RuntimeException("Unsupported OpenGL extensions; cannot use iris frame gen mod. Please uninstall.");
        }
    }

    public static void initialize() {

        LOGGER.info("Vulkan backend is initializing");

        engineThread = new Thread(() -> {
            engine = new Engine();
            LOGGER.info("Waiting for READY signal...");
            synchronized (lock) {
                while (!signaled) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                LOGGER.info("Received signal READY");
            }
            engine.getRender().completeLateInit();
            engine.run();
        });
        engineThread.setName("IrisFrameGen/VkThread");
        engineThread.setPriority(7);
        engineThread.setDaemon(true);
        engineThread.start();
    }

    public static Engine getEngine() {
        if (engine == null) {
            throw new RuntimeException("Engine does not exist");
        }
        return engine;
    }

    public static void signalReady() {
        synchronized (lock) {
            LOGGER.info("Signaling READY state");
            signaled = true;
            lock.notify();
        }
    }
}
