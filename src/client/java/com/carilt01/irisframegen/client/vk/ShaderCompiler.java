package com.carilt01.irisframegen.client.vk;

import io.netty.buffer.ByteBuf;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;

public class ShaderCompiler {

    private ShaderCompiler() {
        // Utility class
    }

    public static byte[] compileShader(String shaderCode, int shaderType) {
        long compiler = 0;
        long options = 0;
        byte[] compiledShader;

        try {
            compiler = Shaderc.shaderc_compiler_initialize();
            options = Shaderc.shaderc_compile_options_initialize();
            if (VkUtils.USE_DEBUG_SHADERS) {
                Shaderc.shaderc_compile_options_set_generate_debug_info(options);
                Shaderc.shaderc_compile_options_set_optimization_level(options, 0);
                Shaderc.shaderc_compile_options_set_source_language(options, Shaderc.shaderc_source_language_glsl);
            }

            long result = Shaderc.shaderc_compile_into_spv(
                    compiler, shaderCode, shaderType, "shader.glsl", "main", options
            );

            if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
                throw new RuntimeException("Shader compilation failed: " + Shaderc.shaderc_result_get_error_message(result));
            }

            ByteBuffer buffer = Shaderc.shaderc_result_get_bytes(result);
            compiledShader = new byte[buffer.remaining()];
            buffer.get(compiledShader);
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }

        return compiledShader;
    }
}
