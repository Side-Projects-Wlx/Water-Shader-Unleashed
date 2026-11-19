package net.watershader.shader;

import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves GLSL vertex and fragment shader sources from the external override
 * directory first, falling back per-file to bundled mod resources.
 */
public final class ShaderSourceLoader {
    public static final String RESOURCE_ROOT = "assets/watershader/shaders/";

    private final Path overrideDir;
    private final ClassLoader classLoader;

    public ShaderSourceLoader(Path overrideDir) {
        this(overrideDir, ShaderSourceLoader.class.getClassLoader());
    }

    public ShaderSourceLoader(Path overrideDir, ClassLoader classLoader) {
        this.overrideDir = overrideDir;
        this.classLoader = classLoader;
    }

    /**
     * Ensures the external shader override folder exists and contains a brief README.
     */
    public void ensureOverrideDirectory(Logger logger) {
        try {
            Files.createDirectories(overrideDir);
            Path readme = overrideDir.resolve("README.txt");
            if (Files.notExists(readme)) {
                String text = String.join(
                    System.lineSeparator(),
                    "Water Shader external GLSL override folder.",
                    "Place any modified .vert or .frag file here (e.g. water.frag, water.vert,",
                    "transparency.vert, transparency.frag, color_white.vert, color_white.frag,",
                    "color_black.vert, color_black.frag) and restart the game to override",
                    "the bundled mod shader for that stage.",
                    ""
                );
                Files.writeString(readme, text, StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            logger.warn("Could not initialize shader override directory at {}", overrideDir, ex);
        }
    }

    /**
     * Loads vertex and fragment sources for {@code name}.
     */
    public LoadedShaderSource load(String name) {
        ResolvedStage vert = loadStage(name, ".vert");
        ResolvedStage frag = loadStage(name, ".frag");
        return new LoadedShaderSource(name, vert.source(), vert.origin(), frag.source(), frag.origin());
    }

    private ResolvedStage loadStage(String name, String extension) {
        String fileName = name + extension;
        Path overrideFile = overrideDir.resolve(fileName);
        if (Files.isRegularFile(overrideFile)) {
            try {
                return new ResolvedStage(Files.readString(overrideFile, StandardCharsets.UTF_8), "override:" + overrideFile);
            } catch (IOException ex) {
                throw new ShaderCompileException(name, extension, "Failed reading override file " + overrideFile, ex);
            }
        }

        String resourcePath = RESOURCE_ROOT + fileName;
        try (InputStream in = classLoader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new ShaderCompileException(name, extension, "Shader resource not found: " + resourcePath);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new ResolvedStage(text, "resource:/" + resourcePath);
        } catch (IOException ex) {
            throw new ShaderCompileException(name, extension, "Failed reading bundled resource " + resourcePath, ex);
        }
    }

    /**
     * Formats GLSL source with 1-based line numbers for compiler error logs.
     */
    public static String formatNumberedSource(String source) {
        String[] lines = source.split("\\R", -1);
        StringBuilder sb = new StringBuilder(source.length() + lines.length * 6);
        for (int i = 0; i < lines.length; i++) {
            sb.append(String.format("%4d | %s%n", i + 1, lines[i]));
        }
        return sb.toString();
    }

    private record ResolvedStage(String source, String origin) {
    }

    public record LoadedShaderSource(
        String name,
        String vertexSource,
        String vertexOrigin,
        String fragmentSource,
        String fragmentOrigin
    ) {
    }
}
