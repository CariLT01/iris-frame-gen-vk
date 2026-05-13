package com.carilt01.irisframegen.client;

import com.carilt01.irisframegen.IrisFrameGeneration;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class VkEarlyInit implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        IrisFrameGeneration.LOGGER.info("Prelaunch entry point");
    }
}
