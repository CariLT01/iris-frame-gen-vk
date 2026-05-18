package com.carilt01.irisframegen.client.iris;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

public class IrisShaderState {

    private static Map<String, Set<String>> fileContentToName = new HashMap<>();
    public static Path shaderZipPath = null;
    public static @Nullable ZipFile shaderZipFile = null;

    public static void resetShaderState() {
        fileContentToName.clear();
    }

    public static void addShaderLink(String shaderContent, String fileName) {
        if (!fileContentToName.containsKey(fileName)) {
            fileContentToName.put(shaderContent, new HashSet<>());
        }
        fileContentToName.get(shaderContent).add(fileName);
    }

    public static @Nullable Set<String> getShaderNames(String shaderContent) {
        return fileContentToName.get(shaderContent);
    }
}
