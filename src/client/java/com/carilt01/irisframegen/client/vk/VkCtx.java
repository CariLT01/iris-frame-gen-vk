package com.carilt01.irisframegen.client.vk;

import com.carilt01.irisframegen.client.VulkanWindow;

public class VkCtx {
    private final Instance instance;
    private final Device device;
    private final PhysDevice physDevice;
    private final Surface surface;
    private final SwapChain swapChain;
    private final PipelineCache pipelineCache;

    public VkCtx(VulkanWindow vkWindow) {
        this.instance = new Instance(true);
        this.physDevice = PhysDevice.createPhysicalDevice(instance, null);
        this.device = new Device(this.physDevice);
        this.surface = new Surface(instance, physDevice, vkWindow);
        this.swapChain = new SwapChain(vkWindow, device, surface, 3, true);
        this.pipelineCache = new PipelineCache(device);
    }

    public void cleanup() {
        this.surface.cleanup(this.instance);
        this.device.cleanup();
        this.physDevice.cleanup();
        this.instance.cleanup();
    }

    public Device getDevice() {
        return this.device;
    }

    public PhysDevice getPhysDevice() {
        return this.physDevice;
    }

    public SwapChain getSwapChain() {
        return swapChain;
    }

    public Surface getSurface() {
        return surface;
    }

    public PipelineCache getPipelineCache() {
        return pipelineCache;
    }
}
