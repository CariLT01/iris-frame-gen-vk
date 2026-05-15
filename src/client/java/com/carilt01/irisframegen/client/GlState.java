package com.carilt01.irisframegen.client;

import static org.lwjgl.opengl.EXTSemaphore.glDeleteSemaphoresEXT;
import static org.lwjgl.opengl.EXTSemaphore.glGenSemaphoresEXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.GL_HANDLE_TYPE_OPAQUE_WIN32_EXT;
import static org.lwjgl.opengl.EXTSemaphoreWin32.glImportSemaphoreWin32HandleEXT;
import static org.lwjgl.opengl.GL32.*;

public class GlState {

    public static int glSemph = 0;
    public static long glRenderFinishedSync = 0L;

    public static boolean colorBufferInitialized = false;
    public static boolean depthBufferInitialized = false;

    public static void importSemaphore(long address) {
        if (glSemph != 0) {
            glDeleteSemaphoresEXT(glSemph);
        }
        glSemph = glGenSemaphoresEXT();
        glImportSemaphoreWin32HandleEXT(glSemph, GL_HANDLE_TYPE_OPAQUE_WIN32_EXT, address);
    }

    public static void createSyncObject() {
        glRenderFinishedSync = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    public static void waitRenderComplete() {
        if (glRenderFinishedSync == 0L) {
            return;
        }
        glClientWaitSync(glRenderFinishedSync, GL_SYNC_FLUSH_COMMANDS_BIT, GL_TIMEOUT_IGNORED);
    }

    public static long getGlRenderFinishedSync() {
        return glRenderFinishedSync;
    }
}
