package com.carilt01.irisframegen.client;

import net.fabricmc.api.ClientModInitializer;

public class IrisFrameGenerationClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {

		if (!IrisFrameGenerationConfig.ENABLED) {
			return;
		}
		VkState.initialize();

	}
}