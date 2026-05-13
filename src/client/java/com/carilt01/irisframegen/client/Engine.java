package com.carilt01.irisframegen.client;

import com.carilt01.irisframegen.client.vk.MeshData;
import com.carilt01.irisframegen.client.vk.ModelData;
import com.carilt01.irisframegen.client.vk.Render;
import com.carilt01.irisframegen.client.vk.VkCtx;

import java.util.ArrayList;
import java.util.List;

public class Engine {

    private final Render render;
    private final VulkanWindow vkWindow;

    public Engine() {
        vkWindow = new VulkanWindow();
        render = new Render(vkWindow);
    }

    private void init() {
        var modelId = "TriangleModel";
        MeshData meshData = new MeshData("triangle-mesh", new float[]{
                -0.5f, -0.5f, 0.0f,
                0.0f, 0.5f, 0.0f,
                0.5f, -0.5f, 0.0f},
                new int[]{0, 1, 2});
        List<MeshData> meshDataList = new ArrayList<>();
        meshDataList.add(meshData);
        ModelData modelData = new ModelData(modelId, meshDataList);
        List<ModelData> models = new ArrayList<>();
        models.add(modelData);

        render.init(models);
    }

    public void run() {
        init();
        while(!vkWindow.shouldClose()) {
            vkWindow.pollEvents();
            render.render();
        }
    }
}
