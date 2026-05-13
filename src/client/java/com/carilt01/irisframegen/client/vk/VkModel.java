package com.carilt01.irisframegen.client.vk;

import java.util.ArrayList;
import java.util.List;

public class VkModel {
    private final String id;
    private final List<VkMesh> vulkanMeshList;

    public VkModel(String id) {
        this.id = id;
        vulkanMeshList = new ArrayList<>();
    }
    public void cleanup(VkCtx vkCtx) {
        vulkanMeshList.forEach(mesh -> mesh.cleanup(vkCtx));
    }

    public String getId() {
        return id;
    }

    public List<VkMesh> getVulkanMeshList() {
        return vulkanMeshList;
    }
}
