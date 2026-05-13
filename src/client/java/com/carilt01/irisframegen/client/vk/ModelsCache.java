package com.carilt01.irisframegen.client.vk;

import com.carilt01.irisframegen.client.NativeMemory;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;

public class ModelsCache {

    private final Map<String, VkModel> modelsMap;

    public ModelsCache() {
        modelsMap = new HashMap<>();
    }

    public void loadModels(VkCtx vkCtx, List<ModelData> models, CmdPool cmdPool, Queue queue) {
        List<VkBuffer> stagingBuffersList = new ArrayList<>();

        var cmd = new CmdBuffer(vkCtx, cmdPool, true, true);
        cmd.beginRecording();

        for (ModelData modelData : models) {
            VkModel vkModel = new VkModel(modelData.id());
            modelsMap.put(vkModel.getId(), vkModel);

            // transform mesh data into gpu buffers
            for (MeshData meshData : modelData.meshes()) {
                TransferBuffer verticesBuffer = createVerticesBuffer(vkCtx, meshData);
                TransferBuffer indicesBuffer = createIndicesBuffer(vkCtx, meshData);
                stagingBuffersList.add(verticesBuffer.srcBuffer());
                stagingBuffersList.add(indicesBuffer.srcBuffer());
                verticesBuffer.recordTransferCommand(cmd);
                indicesBuffer.recordTransferCommand(cmd);

                VkMesh vkMesh = new VkMesh(meshData.id(), verticesBuffer.dstBuffer(),
                        indicesBuffer.dstBuffer(), meshData.indices().length);
                vkModel.getVulkanMeshList().add(vkMesh);
            }
        }

        cmd.endRecording();
        cmd.submitAndWait(vkCtx, queue);
        cmd.cleanup(vkCtx, cmdPool);

        stagingBuffersList.forEach(b -> b.cleanup(vkCtx));
    }

    public static TransferBuffer createVerticesBuffer(VkCtx vkCtx, MeshData meshData) {
        float[] positions = meshData.positions();
        int numElements = positions.length;
        int bufferSize = numElements * VkUtils.FLOAT_SIZE;

        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        long mappedMemory = srcBuffer.map(vkCtx);
        FloatBuffer data = NativeMemory.memFloatBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());

        int rows = positions.length / 3;
        for (int row = 0; row < rows; row++) {
            int startPos = row * 3;
            data.put(positions[startPos]);
            data.put(positions[startPos + 1]);
            data.put(positions[startPos + 2]);
        }

        srcBuffer.unmap(vkCtx);

        return new TransferBuffer(srcBuffer, dstBuffer);
    }

    private static TransferBuffer createIndicesBuffer(VkCtx vkCtx, MeshData meshData) {
        int[] indices = meshData.indices();
        int numIndices = indices.length;
        int bufferSize = numIndices * VkUtils.INT_SIZE;

        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        long mappedMemory = srcBuffer.map(vkCtx);
        IntBuffer data = NativeMemory.memIntBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());
        data.put(indices);
        srcBuffer.unmap(vkCtx);

        return new TransferBuffer(srcBuffer, dstBuffer);

    }

    public void cleanup(VkCtx vkCtx) {
        modelsMap.forEach((k, t) -> t.cleanup(vkCtx));
        modelsMap.clear();
    }

    public VkModel getModel(String id) {
        return modelsMap.get(id);
    }

    public Map<String, VkModel> getModelsMap() {
        return modelsMap;
    }
}
