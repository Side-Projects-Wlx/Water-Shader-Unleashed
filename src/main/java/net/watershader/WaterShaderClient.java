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
 * Client entrypoint for Water Shader. Loads config early and compiles GPU shaders
 * once Minecraft finishes creating its OpenGL display context.
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
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configPath = configDir.resolve(WaterShaderConfig.CONFIG_FILE_NAME);
        config = WaterShaderConfig.loadOrCreate(configPath, LOGGER);
        LOGGER.info("Water Shader initialized client config; awaiting OpenGL context creation.");
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
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve(WaterShaderConfig.CONFIG_FILE_NAME);
            config = WaterShaderConfig.loadOrCreate(configPath, LOGGER);
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

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(WaterShaderConfig.CONFIG_FILE_NAME);
    }

    public static ShaderManager getShaderManager() {
        return shaderManager;
    }
}
