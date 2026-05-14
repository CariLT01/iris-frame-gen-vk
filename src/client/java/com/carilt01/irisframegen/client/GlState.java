package com.carilt01.irisframegen.client;

import static org.lwjgl.opengl.EXTSemaphore.glGenSemaphoresEXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT;

public class GlState {

    public static int glSemph;

    public static void importSemaphore(long address) {
        int glSemaphore = glGenSemaphoresEXT();
        glImportSemaphoreWin32HandleEXT(glSemaphore, GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, address);
    }
}
