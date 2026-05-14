package com.carilt01.irisframegen.client.vk;

public class ShadersSource {
    public static final String VERTEX_SHADER = """
#version 450

layout(location = 0) in vec3 iPos;

void main() {
    gl_Position = vec4(iPos, 1.0);
}
            """;

    public static final String FRAGMENT_SHADER = """
#version 450

layout(binding = 0) uniform sampler2D screenTexture;
layout(location = 0) out vec4 FragColor;

void main() {

    ivec2 texSize = textureSize(screenTexture, 0);
    vec2 uv = gl_FragCoord.xy / texSize;

    FragColor = texture(screenTexture, uv);
}
            """;
}
