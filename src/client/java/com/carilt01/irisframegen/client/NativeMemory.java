package com.carilt01.irisframegen.client;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * A facade that mirrors the most frequently used methods of
 * {@link MemoryUtil}. Every method directly delegates to the
 * official LWJGL implementation.
 * <p>
 * Use this class when you want a single point of access
 * without exposing LWJGL internals.
 */
public final class NativeMemory {

    private NativeMemory() { /* static utility class */ }

    // -------------------------------------------------------------------
    //  Pointer buffer allocation & freeing
    // -------------------------------------------------------------------

    public static PointerBuffer memAllocPointer(int size) {
        return MemoryUtil.memAllocPointer(size);
    }

    public static void memFree(PointerBuffer pointerBuffer) {
        MemoryUtil.memFree(pointerBuffer);
    }

    /**
     * Frees native memory at the given address.
     * Uses {@link MemoryUtil#nmemFree(long)} which is available
     * even when the convenience overload {@code memFree(long)} is missing.
     */
    public static void memFree(long address) {
        MemoryUtil.nmemFree(address);
    }

    // -------------------------------------------------------------------
    //  Float buffer allocation & wrapping
    // -------------------------------------------------------------------

    public static FloatBuffer memAllocFloat(int size) {
        return MemoryUtil.memAllocFloat(size);
    }

    public static FloatBuffer memFloatBuffer(long address, int capacity) {
        return MemoryUtil.memFloatBuffer(address, capacity);
    }

    public static void memFree(FloatBuffer floatBuffer) {
        MemoryUtil.memFree(floatBuffer);
    }

    // -------------------------------------------------------------------
    //  Int buffer allocation & wrapping
    // -------------------------------------------------------------------

    public static IntBuffer memAllocInt(int size) {
        return MemoryUtil.memAllocInt(size);
    }

    public static IntBuffer memIntBuffer(long address, int capacity) {
        return MemoryUtil.memIntBuffer(address, capacity);
    }

    public static void memFree(IntBuffer intBuffer) {
        MemoryUtil.memFree(intBuffer);
    }

    // -------------------------------------------------------------------
    //  Byte buffer allocation & wrapping
    // -------------------------------------------------------------------

    public static ByteBuffer memAlloc(int size) {
        return MemoryUtil.memAlloc(size);
    }

    public static ByteBuffer memByteBuffer(long address, int capacity) {
        return MemoryUtil.memByteBuffer(address, capacity);
    }

    public static void memFree(ByteBuffer byteBuffer) {
        MemoryUtil.memFree(byteBuffer);
    }

    // -------------------------------------------------------------------
    //  Null pointer constant (from LWJGL)
    // -------------------------------------------------------------------

    public static final long NULL = MemoryUtil.NULL;
}