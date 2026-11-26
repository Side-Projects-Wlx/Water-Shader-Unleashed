package net.watershader;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.watershader.config.WaterShaderConfig;
import net.watershader.render.WaterShaderPipeline;
import net.watershader.shader.ShaderManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/**
 * Client entrypoint for Water Shader. Binds the GlassConfigAPI 3 configuration facade
 * early and compiles GPU shaders once Minecraft finishes creating its OpenGL display context.
 */
public final class WaterShaderClient implements ClientModInitializer {
    public static final String MOD_ID = "watershader";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static Minecraft minecraft;
    private static WaterShaderConfig config;
    private static ShaderManager shaderManager;
    private static boolean glInitialized;

    @Override
    public void onInitializeClient() {
        config = new WaterShaderConfig();
        LOGGER.info(
            "Water Shader bound GlassConfigAPI 3 config (water_mode={}, aa={}, refl_div={}, items={}, clouds={}, player={}, v3={}, water_alpha={}, fall_alpha={}); awaiting OpenGL context creation.",
            config.getWaterMode(),
            config.getAntiAliasing(),
            config.getReflectionResolution(),
            config.isReflectiveItems(),
            config.isReflectiveClouds(),
            config.isReflectPlayer(),
            config.isRenderV3(),
            config.getWaterSurfaceTransparency(),
            config.getWaterfallTransparency()
        );
    }

    /**
     * Called once after {@code Minecraft.init()} finishes creating the OpenGL context.
     */
    public static void onGlContextReady(Minecraft client) {
        minecraft = client;
        if (glInitialized) {
            return;
        }
        glInitialized = true;

        if (config == null) {
            config = new WaterShaderConfig();
        }

        Path overrideDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("shaders");
        shaderManager = new ShaderManager(overrideDir, LOGGER);
        shaderManager.compileAll();
        WaterShaderPipeline.init(config, shaderManager, LOGGER);
    }

    /**
     * Releases offscreen GPU buffers and compiled shader programs when closing the client.
     */
    public static void shutdown() {
        WaterShaderPipeline.close();
        if (shaderManager != null) {
            shaderManager.close();
            shaderManager = null;
        }
        minecraft = null;
        glInitialized = false;
    }

    public static Minecraft getMinecraft() {
        return minecraft;
    }

    public static WaterShaderConfig getConfig() {
        return config;
    }

    public static ShaderManager getShaderManager() {
        return shaderManager;
    }

    /**
     * Hot-reloads all GLSL programs from {@code config/watershader/shaders/} (or bundled resources)
     * on the active OpenGL thread. Keeps existing working shaders active if compilation fails.
     */
    public static boolean reloadShaders() {
        if (!glInitialized || shaderManager == null) {
            return false;
        }
        return shaderManager.compileAll();
    }
}
