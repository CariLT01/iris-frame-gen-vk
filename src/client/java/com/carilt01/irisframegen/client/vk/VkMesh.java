package com.carilt01.irisframegen.client.vk;

public record VkMesh(String id, VkBuffer verticesBuffer, VkBuffer indicesBuffer, int numIndices) {
    public void cleanup(VkCtx vkCtx) {
        verticesBuffer.cleanup(vkCtx);
        indicesBuffer.cleanup(vkCtx);
    }
}
