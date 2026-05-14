package com.carilt01.irisframegen.client;

import com.carilt01.irisframegen.client.vk.MeshData;
import com.carilt01.irisframegen.client.vk.ModelData;
import com.carilt01.irisframegen.client.vk.Render;
import com.carilt01.irisframegen.client.vk.VkCtx;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkExternalMemoryImageCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;

import java.util.ArrayList;
import java.util.List;

import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT;
import static org.lwjgl.vulkan.VK11.VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO;

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
                -1.0f, -1.0f, 0.0f,
                3.0f, -1.0f, 0.0f,
                -1.0f,  3.0f, 0.0f
        },
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

    public Render getRender() {
        return render;
    }
}
