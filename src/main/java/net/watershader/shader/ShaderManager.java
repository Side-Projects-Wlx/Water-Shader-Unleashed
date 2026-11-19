package net.watershader.shader;

import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads, compiles, and manages the Water Shader GPU programs after the OpenGL context is created.
 */
public final class ShaderManager implements AutoCloseable {
    public static final List<String> REQUIRED_SHADERS = List.of(
        "water",
        "transparency",
        "color_white",
        "color_black"
    );

    private final ShaderSourceLoader sourceLoader;
    private final Logger logger;
    private final Map<String, ShaderProgram> programs = new LinkedHashMap<>();
    private boolean ready;

    public ShaderManager(Path overrideDir, Logger logger) {
        this.sourceLoader = new ShaderSourceLoader(overrideDir);
        this.logger = logger;
        this.sourceLoader.ensureOverrideDirectory(logger);
    }

    /**
     * Compiles all required Water Shader GLSL programs on the active OpenGL context.
     */
    public boolean compileAll() {
        close();
        String glVersion = GL11.glGetString(GL11.GL_VERSION);
        String glRenderer = GL11.glGetString(GL11.GL_RENDERER);
        String glVendor = GL11.glGetString(GL11.GL_VENDOR);
        logger.info("Initializing Water Shader programs on OpenGL '{}' ({} / {})", glVersion, glVendor, glRenderer);

        boolean allSucceeded = true;
        for (String shaderName : REQUIRED_SHADERS) {
            try {
                ShaderSourceLoader.LoadedShaderSource source = sourceLoader.load(shaderName);
                ShaderProgram program = ShaderProgram.compile(source, logger);
                programs.put(shaderName, program);
                logger.info(
                    "Compiled shader '{}' -> program={} (vert={}, frag={})",
                    shaderName,
                    program.getProgramId(),
                    source.vertexOrigin(),
                    source.fragmentOrigin()
                );
            } catch (RuntimeException ex) {
                allSucceeded = false;
                logger.error("Failed to load/compile shader '{}': {}", shaderName, ex.getMessage(), ex);
            }
        }

        ready = allSucceeded && programs.size() == REQUIRED_SHADERS.size();
        if (ready) {
            logger.info("All {} Water Shader programs compiled cleanly.", programs.size());
        } else {
            logger.error("Shader compilation finished with errors ({} / {} compiled).", programs.size(), REQUIRED_SHADERS.size());
        }
        return ready;
    }

    public boolean isReady() {
        return ready;
    }

    public Map<String, ShaderProgram> getPrograms() {
        return Collections.unmodifiableMap(programs);
    }

    public ShaderProgram getProgram(String name) {
        return programs.get(name);
    }

    @Override
    public void close() {
        for (ShaderProgram program : programs.values()) {
            program.close();
        }
        programs.clear();
        ready = false;
    }
}
