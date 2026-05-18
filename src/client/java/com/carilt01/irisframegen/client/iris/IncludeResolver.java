package com.carilt01.irisframegen.client.iris;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class IncludeResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncludeResolver.class);

    private static final Pattern INCLUDE_PATTERN =
            Pattern.compile("#include\\s+\"([^\"]+)\"");


    private static String resolveRecursive(String source, Path root, Set<String> visited, ZipFile file) throws Exception {
        Matcher matcher = INCLUDE_PATTERN.matcher(source);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String includePath = matcher.group(1);

            // Normalize path (remove leading slash)
            Path fullPath = root.resolve(includePath.startsWith("/")
                    ? includePath.substring(1)
                    : includePath).normalize();

            String key = fullPath.toString();

            // Prevent cyclic includes
            if (visited.contains(key)) {
                matcher.appendReplacement(result, "");
                continue;
            }

            visited.add(key);

            ZipEntry entry = file.getEntry(String.valueOf(fullPath).replace("\\", "/"));
            if (entry == null) {
                throw new FileNotFoundException("Cannot find file: " + fullPath + " as specified in #include directive");
            }

            String fileSource = "";
            try (InputStream is = file.getInputStream(entry)) {
                fileSource = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            String includedSource = fileSource;

            // Recursive resolve
            String resolved = resolveRecursive(includedSource, root, visited, file);

            // Escape backslashes/dollars for regex replacement
            resolved = Matcher.quoteReplacement(resolved);

            matcher.appendReplacement(result, resolved);
        }

        matcher.appendTail(result);
        return result.toString();
    }

    public static String resolve(String shaderSource, ZipFile file) throws Exception {
        String resolved = resolveRecursive(shaderSource, Path.of("shaders"), new HashSet<>(), file);
        //LOGGER.info("Resolved source: {}", resolved);
        return resolved;
    }

}
