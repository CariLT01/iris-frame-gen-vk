package com.carilt01.irisframegen.client.vk;

public class ShadersSource {
    public static final String VERTEX_SHADER = """
#version 450

layout(location = 0) in vec3 iPos;

void main() {
    gl_Position = vec4(iPos, 1.0);
}
            """;

    // TODO: fix real double-gamma correct bug
    // TODO: fix colors being a tiny bit off
    public static final String FRAGMENT_SHADER = """
#version 450

layout(binding = 0) uniform sampler2D screenTexture;
layout(binding = 1) uniform sampler2D depthTexture;
layout(binding = 2) uniform sampler2D motionTexture;
layout(location = 0) out vec4 FragColor;

void main() {

    ivec2 texSize = textureSize(screenTexture, 0);
    vec2 uv = gl_FragCoord.xy / texSize;
    uv.y = 1.0 - uv.y;

    vec3 screenColor = pow(texture(screenTexture, uv).rgb, vec3(2.2));
    float d = texture(depthTexture, uv).r;
    float vis = 1.0 - d;
    
    vec2 motionColor = texture(motionTexture, uv).rg;

    FragColor = vec4(motionColor, 0.0, 1.0);
}
            """;
}
