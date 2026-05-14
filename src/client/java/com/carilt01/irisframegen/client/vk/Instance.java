package com.carilt01.irisframegen.client.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.carilt01.irisframegen.client.vk.VkUtils.vkCheck;
import static org.lwjgl.glfw.GLFW.GLFW_NO_ERROR;
import static org.lwjgl.glfw.GLFW.glfwGetError;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3;

public class Instance {

    private static final String VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation";

    private static final String PORTABILITY_EXTENSION = "VK_KHR_portability_enumeration";

    public static final int MESSAGE_SEVERITY_BITMASK = VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
    public static final int MESSAGE_TYPE_BITMASK = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
    private static final String DBG_CALL_BACK_PREF = "VkDebugUtilsCallback, {}";

    private static Logger LOGGER = LoggerFactory.getLogger(Instance.class);

    private VkDebugUtilsMessengerCreateInfoEXT debugUtils;
    private VkInstance vkInstance = null;
    private long vkDebugHandle = 0L;

    public Instance(boolean validate) {
        try (var stack = MemoryStack.stackPush()) {
            ByteBuffer appShortName = stack.UTF8("Iris Frame Gen");
            var appInfo = VkApplicationInfo.calloc()
                    .sType$Default()
                    .pApplicationName(appShortName)
                    .applicationVersion(1)
                    .pEngineName(appShortName)
                    .engineVersion(0)
                    .apiVersion(VK_API_VERSION_1_3);

            // Validation layers

            List<String> validationLayers = getSupportedValidationLayers();
            int numValidationLayers = validationLayers.size();
            boolean supportsValidation = validate;
            if (validate && numValidationLayers == 0) {
                supportsValidation = false;
                LOGGER.warn("Requested validation but no validation layers are available");
            }

            LOGGER.info("Validation: {}", supportsValidation);
            // set required layer

            PointerBuffer requiredLayers = null;
            if (supportsValidation) {
                requiredLayers = stack.mallocPointer(numValidationLayers);
                for (int i = 0; i < numValidationLayers; i++) {
                    LOGGER.info("Using validation layer [{}]", validationLayers.get(i));
                    requiredLayers.put(i, stack.ASCII(validationLayers.get(i)));
                }
            }

            Set<String> extensions = getInstanceExtensions();
            boolean usePortability = extensions.contains(PORTABILITY_EXTENSION) &&
                    VkUtils.getOS() == OSType.MACOS;

            // glfw extensions
            PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (glfwExtensions == null) {
                PointerBuffer description = stack.mallocPointer(1);
                int errorCode = glfwGetError(description);

                if (errorCode != GLFW_NO_ERROR) {
                    String errorDescription = description.getStringUTF8(0);
                    throw new RuntimeException("Failed to find GLFW surface extensions: " + errorDescription);
                } else {
                    throw new RuntimeException("Failed to find GLFW surface extensions: unknown error");
                }

            }

            // add debugging extensions
            var additionalExtensions = new ArrayList<ByteBuffer>();
            if (supportsValidation) {
                additionalExtensions.add(stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
            }
            if (usePortability) {
                additionalExtensions.add(stack.UTF8(PORTABILITY_EXTENSION));
            }
            int numAdditionalExtensions = additionalExtensions.size();
            PointerBuffer requiredExtensions = stack.mallocPointer(glfwExtensions.remaining() + numAdditionalExtensions);
            requiredExtensions.put(glfwExtensions);
            for (int i = 0; i < numAdditionalExtensions; i++) {
                requiredExtensions.put(additionalExtensions.get(i));
            }
            requiredExtensions.flip();

            // debug ext callback
            long extension = 0L;
            if (supportsValidation) {
                debugUtils = createDebugCallBack();
                extension = debugUtils.address();
            };

            var instanceInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(extension)
                    .pApplicationInfo(appInfo)
                    .ppEnabledLayerNames(requiredLayers)
                    .ppEnabledExtensionNames(requiredExtensions);
            if (usePortability) {
                instanceInfo.flags(0x00000001); // VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
            }

            PointerBuffer pInstance = stack.mallocPointer(1);
            vkCheck(vkCreateInstance(instanceInfo, null, pInstance), "Error creating instance");
            vkInstance = new VkInstance(pInstance.get(0), instanceInfo);

            vkDebugHandle = VK_NULL_HANDLE;
            if (supportsValidation) {
                LongBuffer longBuff = stack.mallocLong(1);
                vkCheck(vkCreateDebugUtilsMessengerEXT(vkInstance, debugUtils, null, longBuff), "Error creating debug utils");
                vkDebugHandle = longBuff.get(0);
            }
        }
    }

    private static VkDebugUtilsMessengerCreateInfoEXT createDebugCallBack() {
        return VkDebugUtilsMessengerCreateInfoEXT.calloc()
                .sType$Default()
                .messageSeverity(MESSAGE_SEVERITY_BITMASK)
                .messageType(MESSAGE_TYPE_BITMASK)
                .pfnUserCallback((messageSeverity, messageType, pCallbackData, pUserData) -> {
                    VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
                    if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0) {
                        LOGGER.info(DBG_CALL_BACK_PREF, callbackData.pMessageString());
                    } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
                        LOGGER.warn(DBG_CALL_BACK_PREF, callbackData.pMessageString());
                    } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
                        LOGGER.error(DBG_CALL_BACK_PREF, callbackData.pMessageString());
                    } else {
                        LOGGER.debug(DBG_CALL_BACK_PREF, callbackData.pMessageString());
                    }
                    return VK_FALSE;
                });
    }

    private Set<String> getInstanceExtensions() {
        Set<String> instanceExtensions = new HashSet<>();
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer numExtensionsBuf = stack.callocInt(1);
            vkEnumerateInstanceExtensionProperties((String) null, numExtensionsBuf, null);
            int numExtensions = numExtensionsBuf.get(0);
            LOGGER.info("Instance supports {} extensions", numExtensions);

            var instanceExtensionsProps = VkExtensionProperties.calloc(numExtensions, stack);
            vkEnumerateInstanceExtensionProperties((String) null, numExtensionsBuf, instanceExtensionsProps);
            for (int i = 0; i < numExtensions; i++) {
                VkExtensionProperties props = instanceExtensionsProps.get(i);
                String extensionName =  props.extensionNameString();
                instanceExtensions.add(extensionName);
                LOGGER.info("Supported instance extension: {}", extensionName);
            }
        }
        return instanceExtensions;
    }

    private List<String> getSupportedValidationLayers() {
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer numLayersArr = stack.callocInt(1);
            vkEnumerateInstanceLayerProperties(numLayersArr, null);
            int numLayers = numLayersArr.get(0);
            LOGGER.info("Instance supports [{}] layers", numLayers);

            var propsBuf = VkLayerProperties.calloc(numLayers, stack);
            vkEnumerateInstanceLayerProperties(numLayersArr, propsBuf);
            List<String> supportedLayers = new ArrayList<>();
            for (int i = 0; i < numLayers; i++) {
                VkLayerProperties props = propsBuf.get(i);
                String layerName = props.layerNameString();
                supportedLayers.add(layerName);
                LOGGER.info("Supported layer: {}", layerName);
            }
            // main validation layer
            List<String> layersToUse = new ArrayList<>();
            if (supportedLayers.contains(VALIDATION_LAYER)) {
                layersToUse.add(VALIDATION_LAYER);
            }
            return layersToUse;
        }
    }

    public void cleanup() {
        LOGGER.info("Destroying Vulkan instance");
        if (vkDebugHandle != VK_NULL_HANDLE) {
            vkDestroyDebugUtilsMessengerEXT(vkInstance, vkDebugHandle, null);
        }
        vkDestroyInstance(vkInstance, null);
        if (debugUtils != null) {
            debugUtils.pfnUserCallback().free();
            debugUtils.free();
        }
    }

    public VkInstance getVkInstance() {
        return this.vkInstance;
    }
}
