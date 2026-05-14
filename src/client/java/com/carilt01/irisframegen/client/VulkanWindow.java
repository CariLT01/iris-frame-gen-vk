package com.carilt01.irisframegen.client;

import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkInstance;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO;

public class VulkanWindow {

    private final int width = 854;
    private final int height = 480;



    private long window;
    private VkInstance vkInstance;

    public VulkanWindow() {
        this.init();
    }

    private void init() {
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW init failed!");
        }

        if (!glfwVulkanSupported()) {
            throw new IllegalStateException("Vulkan is not supported on your system!");
        }

        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(width, height, "Vulkan Window", 0L, 0L);

        if (window == 0L) {
            throw new IllegalStateException("Failed to create window!");
        }
    }

    public long getHandle() {
        return this.window;
    }

    public void cleanup() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(window);
    }
}
