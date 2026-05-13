package com.carilt01.irisframegen.client;

import net.fabricmc.api.ClientModInitializer;

public class IrisFrameGenerationClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Thread vkThread = new Thread(() -> {
			Engine engine = new Engine();
			engine.run();
		});
		vkThread.setName("IrisFrameGen-VkThread");
		vkThread.setDaemon(true);
		vkThread.setPriority(7);

		vkThread.start();

	}
}