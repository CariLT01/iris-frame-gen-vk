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

layout(location = 0) out vec4 FragColor;

void main() {
    FragColor = vec4(1.0, 0.0, 0.0, 1.0);
}
            """;
}
