package net.watershader.shader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ShaderSourceLoaderTest {
    private static final Logger LOGGER = LogManager.getLogger("watershader-test");

    @Test
    void loadsAllRequiredBundledShaders(@TempDir Path tempDir) {
        Path overrideDir = tempDir.resolve("shaders");
        ShaderSourceLoader loader = new ShaderSourceLoader(overrideDir);
        loader.ensureOverrideDirectory(LOGGER);
        assertTrue(Files.isRegularFile(overrideDir.resolve("README.txt")));

        for (String name : ShaderManager.REQUIRED_SHADERS) {
            ShaderSourceLoader.LoadedShaderSource loaded = loader.load(name);
            assertEquals(name, loaded.name());
            assertTrue(loaded.vertexSource().contains("main"), () -> name + ".vert missing main");
            assertTrue(loaded.fragmentSource().contains("main"), () -> name + ".frag missing main");
            assertTrue(loaded.vertexOrigin().startsWith("resource:/"));
            assertTrue(loaded.fragmentOrigin().startsWith("resource:/"));
        }
    }

    @Test
    void externalOverrideTakesPrecedencePerStage(@TempDir Path tempDir) throws IOException {
        Path overrideDir = tempDir.resolve("shaders");
        Files.createDirectories(overrideDir);
        Path customFrag = overrideDir.resolve("water.frag");
        Files.writeString(customFrag, "void main() { gl_FragColor = vec4(1.0); }\n", StandardCharsets.UTF_8);

        ShaderSourceLoader loader = new ShaderSourceLoader(overrideDir);
        ShaderSourceLoader.LoadedShaderSource loaded = loader.load("water");

        assertTrue(loaded.vertexOrigin().startsWith("resource:/"));
        assertTrue(loaded.fragmentOrigin().startsWith("override:"));
        assertTrue(loaded.fragmentSource().contains("gl_FragColor = vec4(1.0);"));
    }

    @Test
    void missingShaderThrowsDescriptiveException(@TempDir Path tempDir) {
        ShaderSourceLoader loader = new ShaderSourceLoader(tempDir);
        ShaderCompileException ex = assertThrows(ShaderCompileException.class, () -> loader.load("nonexistent"));
        assertTrue(ex.getMessage().contains("nonexistent"));
    }

    @Test
    void formatNumberedSourceIncludesLineNumbers() {
        String numbered = ShaderSourceLoader.formatNumberedSource("lineA\nlineB");
        assertTrue(numbered.contains("   1 | lineA"));
        assertTrue(numbered.contains("   2 | lineB"));
    }
}
