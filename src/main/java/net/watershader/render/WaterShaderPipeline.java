package net.watershader.render;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.watershader.config.WaterShaderConfig;
import net.watershader.math.ReflectionMath;
import net.watershader.math.WaterEnvironmentMath;
import net.watershader.math.WaterPlaneFinder;
import net.watershader.mixin.GameRendererAccessor;
import net.watershader.shader.ShaderManager;
import net.watershader.shader.ShaderProgram;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLContext;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Set;

/**
 * Coordinates the 3-pass Water Shader rendering pipeline (Main, Stencil, Reflection) and
 * composites the final water refraction/reflection image onto the screen backbuffer.
 */
public final class WaterShaderPipeline {
    private static final WaterPlaneFinder WATER_PLANE_FINDER = new WaterPlaneFinder();
    private static final WorldWaterProbe WORLD_WATER_PROBE = new WorldWaterProbe();

    private static WaterShaderConfig config;
    private static ShaderManager shaderManager;
    private static FboManager fboManager;
    private static Logger logger;
    private static Minecraft activeClient;
    private static WeakReference<World> lastWorldRef = new WeakReference<>(null);

    private static boolean initialized = false;
    private static boolean userEnabled = true;
    private static boolean amphetamineDetected = false;
    private static boolean capOpenGL14 = false;
    private static boolean debugStencilView = false;

    private static final Set<Integer> EXCLUDED_BLOCK_IDS = new HashSet<>();
    private static final Set<String> EXCLUDED_BLOCK_KEYS = new HashSet<>();

    private static WaterRenderPass currentPass = WaterRenderPass.NONE;
    private static long lastFrameNs = 0L;
    private static double lastFrameDtSeconds = 1.0 / 60.0;
    private static float shaderTimer = 0.0F;
    private static boolean waterVisibleInMainPass = false;
    private static boolean skippedWaterPassesThisFrame = false;
    private static boolean reflectionRenderedThisFrame = false;
    private static boolean waterDepthCopiedThisFrame = false;
    private static boolean handDepthCopiedThisFrame = false;
    private static boolean weatherRenderedThisFrame = false;
    private static boolean renderingIsolatedWeather = false;
    private static double envRainAnimSeconds = 0.0;

    private static final double[] TMP_THIRD_PERSON_OFFSET = new double[3];
    private static final double[] TMP_CAMERA_EYE_POS = new double[3];

    private static final FloatBuffer MATRIX_READ_BUF = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer MATRIX_WRITE_BUF = BufferUtils.createFloatBuffer(16);
    private static final ByteBuffer COLOR_MASK_BUF = BufferUtils.createByteBuffer(16);

    private static final float[] TMP_MV = new float[16];
    private static final float[] TMP_INV_MV = new float[16];
    private static final float[] TMP_PROJ = new float[16];
    private static final float[] TMP_VP = new float[16];
    private static final float[] INVERSE_VIEW = new float[16];
    private static final float[] MAIN_MV = new float[16];
    private static final float[] MAIN_PROJ = new float[16];
    private static final float[] MAIN_INV_VP = new float[16];
    private static final float[] MAIN_NEAR_FAR = new float[]{0.05F, 512.0F};
    private static final float[] TMP_CLIP_PLANE_WORLD = new float[]{0.0F, 1.0F, 0.0F, 0.0F};
    private static final float[] CLIP_PLANE_EYE = new float[4];
    private static final float[] SAVED_SKY_MV = new float[16];
    private static final float[] SAVED_SKY_PROJ = new float[16];
    private static boolean savedSkyMatricesValid = false;
    private static boolean mainInvVpValid = false;

    private static boolean envHasCeiling = false;
    private static int envCameraSkyLight = 15;
    private static float envSkyAmbient = 1.0F;
    private static float envRain = 0.0F;
    private static float envThunder = 0.0F;
    private static float envReflBrightness = 1.0F;
    private static float envTransparencySkyLight = 1.0F;
    private static boolean envShouldRenderReflSky = true;
    private static boolean envCameraEyeInWater = false;
    private static float envUnderwaterFactor = 0.0F;
    private static float envUnderwaterBrightness = 1.0F;
    private static float envCamXMod640 = 0.0F;
    private static float envCamZMod640 = 0.0F;

    private static boolean savedColorMaskValid = false;
    private static boolean savedColorMaskR = true;
    private static boolean savedColorMaskG = true;
    private static boolean savedColorMaskB = true;
    private static boolean savedColorMaskA = true;
    private static boolean savedShadeModelValid = false;
    private static int savedShadeModel = GL11.GL_FLAT;
    private static boolean activeLayer1BoundShader = false;
    private static boolean activeLayer0StencilColorMasked = false;
    private static boolean activeLayer0StencilShader = false;

    private WaterShaderPipeline() {}

    public static void init(WaterShaderConfig cfg, ShaderManager mgr, Logger log) {
        if (fboManager != null) {
            fboManager.close();
        }
        config = cfg;
        shaderManager = mgr;
        logger = log;
        fboManager = new FboManager(log);
        amphetamineDetected = FabricLoader.getInstance().isModLoaded("amphetamine");
        ContextCapabilities caps = GLContext.getCapabilities();
        capOpenGL14 = caps != null && caps.OpenGL14;
        lastWorldRef = new WeakReference<>(null);
        rebuildExcludeCache(cfg);
        WATER_PLANE_FINDER.reset();
        lastFrameNs = System.nanoTime();
        lastFrameDtSeconds = 1.0 / 60.0;
        shaderTimer = 0.0F;
        userEnabled = true;
        debugStencilView = false;
        waterVisibleInMainPass = false;
        skippedWaterPassesThisFrame = false;
        reflectionRenderedThisFrame = false;
        waterDepthCopiedThisFrame = false;
        handDepthCopiedThisFrame = false;
        weatherRenderedThisFrame = false;
        renderingIsolatedWeather = false;
        envRainAnimSeconds = 0.0;
        envUnderwaterFactor = 0.0F;
        currentPass = WaterRenderPass.NONE;
        initialized = true;
        log.info(
                "[WaterShader] Pipeline initialized (water_mode={}, aa={}, refl_div={}, amphetamine={})",
                cfg.getWaterMode(),
                cfg.getAntiAliasing(),
                cfg.getReflectionResolution(),
                amphetamineDetected);
    }

    public static void close() {
        currentPass = WaterRenderPass.NONE;
        activeClient = null;
        lastWorldRef = new WeakReference<>(null);
        WORLD_WATER_PROBE.bind(null);
        WaterLayer1Capture.abortChunkRebuildIfActive();
        WaterLayer1Mesh.resetCapabilityCache();
        userEnabled = true;
        debugStencilView = false;
        waterVisibleInMainPass = false;
        skippedWaterPassesThisFrame = false;
        reflectionRenderedThisFrame = false;
        waterDepthCopiedThisFrame = false;
        handDepthCopiedThisFrame = false;
        weatherRenderedThisFrame = false;
        renderingIsolatedWeather = false;
        envUnderwaterFactor = 0.0F;
        capOpenGL14 = false;
        savedShadeModelValid = false;
        if (fboManager != null) {
            fboManager.close();
        }
        WATER_PLANE_FINDER.reset();
        initialized = false;
    }

    public static boolean shouldReplaceUnderwaterOverlay() {
        return isActive()
                && fboManager != null
                && fboManager.isValid()
                && !debugStencilView
                && (config == null || config.isUnderwaterEffect());
    }

    public static boolean shouldReplaceVanillaRain() {
        return currentPass == WaterRenderPass.MAIN
                && !envHasCeiling
                && fboManager != null
                && fboManager.hasWeatherAttachment()
                && !debugStencilView;
    }

    public static boolean shouldReplaceWaterSplashes() {
        return currentPass == WaterRenderPass.MAIN
                && fboManager != null
                && fboManager.hasWeatherAttachment()
                && !debugStencilView;
    }

    public static boolean shouldReplaceWaterBubbles() {
        return currentPass == WaterRenderPass.MAIN
                && fboManager != null
                && fboManager.isValid()
                && !debugStencilView;
    }

    public static void renderModernWaterBubbles(net.minecraft.entity.Entity camera, float tickDelta) {
        if (!shouldReplaceWaterBubbles() || camera == null || activeClient == null) {
            return;
        }
        if (!ModernWeatherRenderer.hasBubbleParticles(activeClient)) {
            return;
        }
        float wcR = (config != null) ? config.getWaterColorR() : 0.0F;
        float wcG = (config != null) ? config.getWaterColorG() : 0.5F;
        float wcB = (config != null) ? config.getWaterColorB() : 0.35F;
        ModernWeatherRenderer.renderModernWaterBubbles(
                activeClient,
                camera,
                tickDelta,
                envThunder,
                wcR,
                wcG,
                wcB,
                fboManager.getRainSplashTexture());
    }

    public static boolean isRenderingIsolatedWeather() {
        return renderingIsolatedWeather;
    }

    public static void applyWeatherBlendFunc() {
        if (capOpenGL14) {
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    /**
     * Renders snow (via vanilla's {@code renderSnow} with {@code Biome.canRain()} suppressed) and
     * modern rain streaks into {@code weatherTex} so falling precipitation never bends over water.
     */
    public static void renderWeatherIsolated(Minecraft client, float tickDelta, Runnable vanillaSnowRunner) {
        if (currentPass != WaterRenderPass.MAIN || client == null || client.world == null) {
            vanillaSnowRunner.run();
            return;
        }
        boolean allowRainAndSnow = !envHasCeiling;
        float rainGrad = allowRainAndSnow
                ? WaterEnvironmentMath.clamp01(client.world.getRainGradient(tickDelta))
                : 0.0F;
        boolean hasSplashes = ModernWeatherRenderer.hasSplashParticles(client);
        if (rainGrad <= 0.005F && !hasSplashes) {
            return;
        }
        if (fboManager == null || !fboManager.hasWeatherAttachment() || debugStencilView) {
            vanillaSnowRunner.run();
            return;
        }
        if (!fboManager.beginWeatherPass()) {
            vanillaSnowRunner.run();
            return;
        }
        renderingIsolatedWeather = true;
        try {
            if (allowRainAndSnow && rainGrad > 0.005F) {
                vanillaSnowRunner.run();
            }
            float wcR = (config != null) ? config.getWaterColorR() : 0.0F;
            float wcG = (config != null) ? config.getWaterColorG() : 0.5F;
            float wcB = (config != null) ? config.getWaterColorB() : 0.35F;
            ModernWeatherRenderer.renderModernRainAndSplashes(
                    client,
                    tickDelta,
                    allowRainAndSnow ? rainGrad : 0.0F,
                    envThunder,
                    envRainAnimSeconds,
                    hasSplashes,
                    wcR,
                    wcG,
                    wcB,
                    fboManager.getRainStreakTexture(),
                    fboManager.getRainSplashTexture());
            weatherRenderedThisFrame = true;
        } finally {
            renderingIsolatedWeather = false;
            fboManager.endWeatherPass();
        }
    }

    public static void onAfterLayer1() {
        boolean needWaterDepth = envCameraEyeInWater
                || envUnderwaterFactor > 0.005F
                || (!envHasCeiling && envRain > 0.005F);
        if (currentPass == WaterRenderPass.MAIN
                && needWaterDepth
                && !waterDepthCopiedThisFrame
                && fboManager != null
                && fboManager.isValid()) {
            fboManager.copyMainDepthToWater();
            waterDepthCopiedThisFrame = true;
        }
    }

    public static void markWaterVisibleThisPass() {
        if (currentPass == WaterRenderPass.MAIN) {
            waterVisibleInMainPass = true;
        }
    }

    public static WaterRenderPass getCurrentPass() {
        return currentPass;
    }

    public static boolean isAmphetamineDetected() {
        return amphetamineDetected;
    }

    public static boolean isDebugStencilView() {
        return debugStencilView;
    }

    public static boolean toggleDebugStencil() {
        debugStencilView = !debugStencilView;
        return debugStencilView;
    }

    public static boolean isUserEnabled() {
        return userEnabled;
    }

    public static boolean toggleUserEnabled() {
        userEnabled = !userEnabled;
        if (!userEnabled) {
            debugStencilView = false;
            envUnderwaterFactor = 0.0F;
        }
        return userEnabled;
    }

    /**
     * Returns {@code true} when Water Shader is initialized and enabled in config ({@code water_mode > 0}),
     * independent of the live F9 quick toggle so chunk Layer 1 capture stays warm for instant ON/OFF toggling
     * while remaining completely idle (zero GPU VBOs) when {@code water_mode == 0}.
     */
    public static boolean isConfiguredEnabled() {
        return initialized
                && config != null
                && shaderManager != null
                && fboManager != null
                && config.getWaterMode() > 0
                && shaderManager.isReady();
    }

    public static boolean isActive() {
        return userEnabled && isConfiguredEnabled();
    }

    public static boolean isBlockExcluded(Block block) {
        if (block == null) {
            return false;
        }
        if (EXCLUDED_BLOCK_IDS.contains(block.id)) {
            return true;
        }
        String key = block.getTranslationKey();
        return key != null && EXCLUDED_BLOCK_KEYS.contains(key);
    }

    /**
     * Runs the active multi-pass Water Shader pipeline (or falls back to a single vanilla pass when
     * disabled, in 3D Anaglyph mode, when FBOs fail, or when the world/camera is unavailable).
     */
    public static void renderFramePasses(Minecraft client, float tickDelta, Runnable singlePassRender) {
        activeClient = client;
        if (!isActive()
                || client == null
                || client.world == null
                || client.camera == null
                || (client.options != null && client.options.anaglyph3d)) {
            currentPass = WaterRenderPass.NONE;
            singlePassRender.run();
            return;
        }

        WaterLayer1Capture.abortChunkRebuildIfActive();
        if (lastWorldRef.get() != client.world) {
            lastWorldRef = new WeakReference<>(client.world);
            WATER_PLANE_FINDER.reset();
            envUnderwaterFactor = 0.0F;
        }

        updateTimer();
        updateEnvironmentState(client, tickDelta);
        updateWaterPlane(client, tickDelta);

        boolean withReflection = config.getWaterMode() >= 2;
        if (!fboManager.ensureReady(
                client.displayWidth,
                client.displayHeight,
                config.getAntiAliasing(),
                config.getReflectionResolution(),
                withReflection)) {
            currentPass = WaterRenderPass.NONE;
            singlePassRender.run();
            return;
        }

        try {
            waterVisibleInMainPass = false;
            skippedWaterPassesThisFrame = false;
            reflectionRenderedThisFrame = false;
            waterDepthCopiedThisFrame = false;
            handDepthCopiedThisFrame = false;
            weatherRenderedThisFrame = false;

            // Pass 0: Main scene -> FBO 0 (colorMap) with transparency shader on Layer 1
            executePass(WaterRenderPass.MAIN, () -> fboManager.bindMain(), singlePassRender);

            if (!waterVisibleInMainPass && !debugStencilView && !envCameraEyeInWater && envUnderwaterFactor <= 0.005F) {
                skippedWaterPassesThisFrame = true;
            } else {
                // Pass 1: Stencil pass -> FBO 1 (stencilMap) with normal-encoded water classification
                executePass(WaterRenderPass.STENCIL, () -> fboManager.bindStencil(), singlePassRender);

                // Pass 2: Mirrored oblique-clipped reflection -> FBO 2 (reflectedColorMap)
                if (withReflection && !envHasCeiling && !debugStencilView) {
                    executePass(WaterRenderPass.REFLECTION, () -> fboManager.bindReflection(), singlePassRender);
                    reflectionRenderedThisFrame = true;
                }
            }

            // Final composite onto screen backbuffer before InGameHud & Screens draw
            fboManager.unbind();
            drawFullscreenComposite(client.displayWidth, client.displayHeight);
        } finally {
            currentPass = WaterRenderPass.NONE;
            if (fboManager != null) {
                fboManager.unbind();
            }
            ShaderProgram.unbindActive();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glFrontFace(GL11.GL_CCW);
        }
    }

    public static int getActiveViewportWidth(int fallbackDisplayWidth) {
        if (fboManager == null || !fboManager.isValid()) {
            return fallbackDisplayWidth;
        }
        return switch (currentPass) {
            case MAIN -> fboManager.getMainWidth();
            case STENCIL -> fboManager.getStencilWidth();
            case REFLECTION -> fboManager.getReflectionWidth();
            case NONE -> fallbackDisplayWidth;
        };
    }

    public static int getActiveViewportHeight(int fallbackDisplayHeight) {
        if (fboManager == null || !fboManager.isValid()) {
            return fallbackDisplayHeight;
        }
        return switch (currentPass) {
            case MAIN -> fboManager.getMainHeight();
            case STENCIL -> fboManager.getStencilHeight();
            case REFLECTION -> fboManager.getReflectionHeight();
            case NONE -> fallbackDisplayHeight;
        };
    }

    /**
     * Applies pass-specific transforms immediately after {@code GameRenderer.renderWorld(tickDelta, eye)}
     * sets camera projection and ModelView, BEFORE {@code Frustum.getInstance()} captures them.
     */
    public static void afterCameraSetup(Minecraft client, float tickDelta) {
        if (currentPass == WaterRenderPass.MAIN) {
            readGlMatrix(GL11.GL_PROJECTION_MATRIX, MAIN_PROJ);
            readGlMatrix(GL11.GL_MODELVIEW_MATRIX, MAIN_MV);
            ReflectionMath.extractNearFar(MAIN_PROJ, MAIN_NEAR_FAR);
            mainInvVpValid = ReflectionMath.computeInvViewProj(MAIN_INV_VP, MAIN_PROJ, MAIN_MV, TMP_VP);
            return;
        }

        if (currentPass == WaterRenderPass.STENCIL) {
            readGlMatrix(GL11.GL_MODELVIEW_MATRIX, TMP_MV);
            ReflectionMath.inverseOrth(INVERSE_VIEW, TMP_MV);
            return;
        }

        if (currentPass != WaterRenderPass.REFLECTION || client == null || client.camera == null) {
            return;
        }

        LivingEntity cam = client.camera;
        double camY = cam.lastTickY + (cam.y - cam.lastTickY) * tickDelta;
        double waterToOrigin = WATER_PLANE_FINDER.computeWaterHeightOffset(camY);
        float planeH = WaterPlaneFinder.computeClipPlaneH(waterToOrigin);

        readGlMatrix(GL11.GL_PROJECTION_MATRIX, SAVED_SKY_PROJ);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glScalef(1.0F, -1.0F, 1.0F);
        readGlMatrix(GL11.GL_MODELVIEW_MATRIX, SAVED_SKY_MV);
        GL11.glPopMatrix();
        savedSkyMatricesValid = true;

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glTranslatef(0.0F, (float) waterToOrigin, 0.0F);
        GL11.glScalef(1.0F, -1.0F, 1.0F);
        GL11.glFrontFace(GL11.GL_CW);

        readGlMatrix(GL11.GL_MODELVIEW_MATRIX, TMP_MV);
        readGlMatrix(GL11.GL_PROJECTION_MATRIX, TMP_PROJ);
        if (ReflectionMath.isPerspectiveProjection(TMP_PROJ)) {
            ReflectionMath.inverseOrth(TMP_INV_MV, TMP_MV);
            TMP_CLIP_PLANE_WORLD[3] = planeH;
            ReflectionMath.transformPlaneTransposeInverse(
                    CLIP_PLANE_EYE,
                    TMP_INV_MV,
                    TMP_CLIP_PLANE_WORLD);
            if (ReflectionMath.modifyProjectionMatrix(TMP_PROJ, CLIP_PLANE_EYE)) {
                MATRIX_WRITE_BUF.clear();
                MATRIX_WRITE_BUF.put(TMP_PROJ);
                MATRIX_WRITE_BUF.flip();
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glLoadMatrix(MATRIX_WRITE_BUF);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
            }
        }
    }

    public static void onGlClear(int mask) {
        if (currentPass == WaterRenderPass.STENCIL && (mask & GL11.GL_COLOR_BUFFER_BIT) != 0) {
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        } else if (currentPass == WaterRenderPass.REFLECTION
                && !envShouldRenderReflSky
                && (mask & GL11.GL_COLOR_BUFFER_BIT) != 0) {
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        }
        GL11.glClear(mask);
    }

    public static boolean shouldRenderSky() {
        if (currentPass == WaterRenderPass.STENCIL) {
            return false;
        }
        if (currentPass == WaterRenderPass.REFLECTION) {
            return envShouldRenderReflSky;
        }
        return true;
    }

    public static void onBeforeRenderSky() {
        if (currentPass != WaterRenderPass.REFLECTION || !savedSkyMatricesValid) {
            return;
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        MATRIX_WRITE_BUF.clear();
        MATRIX_WRITE_BUF.put(SAVED_SKY_PROJ);
        MATRIX_WRITE_BUF.flip();
        GL11.glLoadMatrix(MATRIX_WRITE_BUF);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        MATRIX_WRITE_BUF.clear();
        MATRIX_WRITE_BUF.put(SAVED_SKY_MV);
        MATRIX_WRITE_BUF.flip();
        GL11.glLoadMatrix(MATRIX_WRITE_BUF);
    }

    public static void onAfterRenderSky() {
        if (currentPass != WaterRenderPass.REFLECTION || !savedSkyMatricesValid) {
            return;
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }

    public static boolean shouldUpdateTargetedOrCompileChunks() {
        return currentPass == WaterRenderPass.NONE || currentPass == WaterRenderPass.MAIN;
    }

    public static boolean shouldRenderParticlesAndOverlays() {
        return currentPass == WaterRenderPass.NONE || currentPass == WaterRenderPass.MAIN;
    }

    public static boolean shouldRenderEntities() {
        if (currentPass == WaterRenderPass.REFLECTION) {
            return config != null && (config.isReflectiveItems() || config.isReflectPlayer());
        }
        return true;
    }

    /**
     * Filters individual entities during Pass 2 ({@code REFLECTION}) so {@code reflective_items 0}
     * hides mobs/items even when {@code reflect_player 1} is enabled, and vice versa.
     */
    public static boolean shouldRenderEntityInCurrentPass(net.minecraft.entity.Entity entity, Minecraft client) {
        if (currentPass != WaterRenderPass.REFLECTION || config == null) {
            return true;
        }
        boolean isCameraEntity = client != null && entity != null && entity == client.camera;
        return shouldRenderReflectedEntity(isCameraEntity, config.isReflectPlayer(), config.isReflectiveItems());
    }

    public static boolean shouldRenderReflectedEntity(
            boolean isCameraEntity,
            boolean reflectPlayer,
            boolean reflectiveItems) {
        return isCameraEntity ? reflectPlayer : reflectiveItems;
    }

    public static void onBeforeRenderEntities(Minecraft client) {
        if (currentPass == WaterRenderPass.STENCIL) {
            bindColorBlackForOverlayPass();
        } else if (currentPass == WaterRenderPass.REFLECTION && config != null && client != null && client.options != null) {
            client.options.thirdPerson = config.isReflectPlayer();
        }
    }

    public static void onAfterRenderEntities(Minecraft client, boolean prevThirdPerson) {
        if (currentPass == WaterRenderPass.STENCIL) {
            unbindColorBlackForOverlayPass();
        }
        if (currentPass == WaterRenderPass.REFLECTION && client != null && client.options != null) {
            client.options.thirdPerson = prevThirdPerson;
        }
    }

    public static boolean shouldRenderLayer1() {
        return currentPass != WaterRenderPass.REFLECTION;
    }

    public static boolean shouldRenderClouds() {
        if (currentPass == WaterRenderPass.STENCIL) {
            return false;
        }
        if (currentPass == WaterRenderPass.REFLECTION) {
            return config != null && config.isReflectiveClouds();
        }
        return true;
    }

    public static boolean shouldRenderFirstPersonHand() {
        return currentPass == WaterRenderPass.NONE
                || currentPass == WaterRenderPass.MAIN
                || currentPass == WaterRenderPass.STENCIL;
    }

    public static void onBeforeFirstPersonHand() {
        if (currentPass == WaterRenderPass.STENCIL) {
            bindColorBlackForOverlayPass();
        }
    }

    public static void onAfterFirstPersonHand() {
        if (currentPass == WaterRenderPass.MAIN
                && fboManager != null
                && fboManager.isValid()) {
            boolean needHandDepth = weatherRenderedThisFrame
                    || (config != null && config.isUnderwaterEffect()
                    && (envCameraEyeInWater || envUnderwaterFactor > 0.005F));
            if (needHandDepth) {
                fboManager.copyMainDepthToHand();
                handDepthCopiedThisFrame = true;
            }
        } else if (currentPass == WaterRenderPass.STENCIL) {
            unbindColorBlackForOverlayPass();
        }
    }

    /**
     * Handles shader/colorMask setup at {@code WorldRenderer.renderLastChunks(renderPass, delta)} HEAD.
     *
     * @return {@code true} if {@code amphetamine} is active on Layer 1 and the mixin should draw
     * captured {@link WaterLayer1Mesh} chunks cleanly instead of Amphetamine's Layer 1 pass.
     */
    public static boolean onWorldRenderLastChunksBegin(int renderPass) {
        activeLayer1BoundShader = false;
        activeLayer0StencilColorMasked = false;
        activeLayer0StencilShader = false;

        if (currentPass == WaterRenderPass.NONE || shaderManager == null || config == null) {
            return false;
        }

        if (renderPass == 0 && currentPass == WaterRenderPass.STENCIL) {
            if (amphetamineDetected) {
                saveAndDisableColorMask();
                activeLayer0StencilColorMasked = true;
            } else {
                bindColorBlackForOverlayPass();
                activeLayer0StencilShader = true;
            }
            return false;
        }

        if (renderPass == 1) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);

            if (currentPass == WaterRenderPass.MAIN) {
                ShaderProgram transparency = shaderManager.getProgram("transparency");
                if (transparency != null) {
                    // Force GL_SMOOTH even in Fast Graphics / Smooth Lighting OFF so per-vertex water
                    // boundary, inflow, and cave-entrance gradients interpolate cleanly across quads.
                    saveAndForceSmoothShadeModel();
                    // Disable fixed-function GL_ALPHA_TEST (GL_GREATER 0.1) during Layer 1 water rendering
                    // so smooth still-to-flowing alpha gradients (0.0 -> waterfall_transparency) never hit
                    // a hard 0.1 cutoff seam; non-water Layer 1 blocks enforce alpha <= 0.1 discard in GLSL.
                    GL11.glDisable(GL11.GL_ALPHA_TEST);
                    transparency.bind();
                    transparency.setUniform1i("colorMap", 0, logger);
                    transparency.setUniform1f("setting_v3", config.getRenderV3Float(), logger);
                    transparency.setUniform1f("water_transparency", config.getWaterSurfaceTransparency(), logger);
                    transparency.setUniform1f("waterfall_transparency", config.getWaterfallTransparency(), logger);
                    transparency.setUniform3f(
                            "waterfall_color",
                            config.getWaterfallColorR(),
                            config.getWaterfallColorG(),
                            config.getWaterfallColorB(),
                            logger);
                    transparency.setUniform3f(
                            "water_color",
                            config.getWaterColorR(),
                            config.getWaterColorG(),
                            config.getWaterColorB(),
                            logger);
                    transparency.setUniform1f("waterfall_fog", config.isWaterfallFog() ? 1.0F : 0.0F, logger);
                    transparency.setUniform1f("env_sky_light", envTransparencySkyLight, logger);
                    transparency.setUniform1f(
                            "still_refl_enabled",
                            WaterEnvironmentMath.computeStillReflEnabledUniform(config.getWaterMode(), envHasCeiling),
                            logger);
                    activeLayer1BoundShader = true;
                }
                return amphetamineDetected;
            }

            if (currentPass == WaterRenderPass.STENCIL) {
                ShaderProgram colorWhite = shaderManager.getProgram("color_white");
                if (colorWhite != null) {
                    saveAndForceSmoothShadeModel();
                    // Disable GL_ALPHA_TEST in stencil pass because alpha carries sky_exposure (0.0 in caves / on ice)
                    GL11.glDisable(GL11.GL_ALPHA_TEST);
                    colorWhite.bind();
                    colorWhite.setUniformMat4("inverse_view", INVERSE_VIEW, false, logger);
                    activeLayer1BoundShader = true;
                }
                GL11.glDisable(GL11.GL_BLEND);
                return amphetamineDetected;
            }
        }

        return false;
    }

    public static void onWorldRenderLastChunksEnd(int renderPass) {
        if (renderPass == 0) {
            if (activeLayer0StencilColorMasked) {
                restoreSavedColorMask();
                activeLayer0StencilColorMasked = false;
            }
            if (activeLayer0StencilShader) {
                unbindColorBlackForOverlayPass();
                activeLayer0StencilShader = false;
            }
            return;
        }

        if (renderPass == 1) {
            if (activeLayer1BoundShader) {
                ShaderProgram.unbindActive();
                restoreSavedShadeModel();
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
                activeLayer1BoundShader = false;
            }
            if (currentPass == WaterRenderPass.STENCIL) {
                GL11.glEnable(GL11.GL_BLEND);
            }
        }
    }

    private static void executePass(WaterRenderPass pass, Runnable bindTarget, Runnable frameRunner) {
        currentPass = pass;
        try {
            bindTarget.run();
            frameRunner.run();
        } finally {
            savedSkyMatricesValid = false;
            ShaderProgram.unbindActive();
            restoreSavedShadeModel();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glFrontFace(GL11.GL_CCW);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            restoreSavedColorMask();
        }
    }

    private static void drawFullscreenComposite(int displayWidth, int displayHeight) {
        int safeW = Math.max(1, displayWidth);
        int safeH = Math.max(1, displayHeight);

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT
                        | GL11.GL_DEPTH_BUFFER_BIT
                        | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glViewport(0, 0, safeW, safeH);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true, true, true, true);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0, safeW, 0.0, safeH, -1.0, 1.0);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            if (debugStencilView) {
                ShaderProgram.unbindActive();
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, fboManager.getStencilTexture());
                drawScreenQuad(safeW, safeH);
            } else {
                ShaderProgram water = shaderManager.getProgram("water");
                if (water != null) {
                    int stencilTex = skippedWaterPassesThisFrame
                            ? fboManager.getDummyBlackTexture()
                            : fboManager.getStencilTexture();
                    int reflTex = (skippedWaterPassesThisFrame || !reflectionRenderedThisFrame)
                            ? fboManager.getDummyBlackTexture()
                            : fboManager.getReflectionTexture();
                    boolean useCapturedDepth = waterDepthCopiedThisFrame
                            && (envCameraEyeInWater || envUnderwaterFactor > 0.005F || (!envHasCeiling && envRain > 0.005F));
                    int waterDepthTex = useCapturedDepth
                            ? fboManager.getWaterDepthTexture()
                            : fboManager.getDummyDepthTexture();
                    int handDepthTex = handDepthCopiedThisFrame
                            ? fboManager.getHandDepthTexture()
                            : fboManager.getDummyDepthTexture();
                    int weatherTex = weatherRenderedThisFrame
                            ? fboManager.getWeatherTexture()
                            : fboManager.getDummyTransparentTexture();

                    GL13.glActiveTexture(GL13.GL_TEXTURE5);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, weatherTex);
                    GL13.glActiveTexture(GL13.GL_TEXTURE4);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, handDepthTex);
                    GL13.glActiveTexture(GL13.GL_TEXTURE3);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, waterDepthTex);
                    GL13.glActiveTexture(GL13.GL_TEXTURE2);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, reflTex);
                    GL13.glActiveTexture(GL13.GL_TEXTURE1);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, stencilTex);
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, fboManager.getMainColorTexture());

                    water.bind();
                    water.setUniform1f("timer", shaderTimer, logger);
                    water.setUniform1i("colorMap", 0, logger);
                    water.setUniform1i("stencilMap", 1, logger);
                    water.setUniform1i("reflectedColorMap", 2, logger);
                    water.setUniform1i("waterDepthMap", 3, logger);
                    water.setUniform1i("handDepthMap", 4, logger);
                    water.setUniform1i("weatherMap", 5, logger);
                    water.setUniform3f(
                            "water_color",
                            config.getWaterColorR(),
                            config.getWaterColorG(),
                            config.getWaterColorB(),
                            logger);
                    // Normalize texelSize to baseline 2x display resolution so blur/feather radii stay
                    // visually identical across anti_aliasing = 1, 2, 3+
                    water.setUniform2f(
                            "texelSize",
                            0.5F / safeW,
                            0.5F / safeH,
                            logger);
                    water.setUniform1f("ripple_strength", config.getRippleStrength(), logger);
                    water.setUniform1f("fresnel_strength", config.getFresnelStrength(), logger);
                    water.setUniform1f("z_near", MAIN_NEAR_FAR[0], logger);
                    water.setUniform1f("z_far", MAIN_NEAR_FAR[1], logger);
                    water.setUniform1f("refl_brightness", (config.getWaterMode() >= 2) ? envReflBrightness : 0.0F, logger);
                    water.setUniform1f(
                            "rain_strength",
                            (!envHasCeiling && useCapturedDepth && mainInvVpValid) ? envRain : 0.0F,
                            logger);
                    water.setUniform1f("rain_blur_strength", !envHasCeiling ? envRain : 0.0F, logger);
                    water.setUniformMat4("inv_view_proj", MAIN_INV_VP, false, logger);
                    water.setUniform2f("rain_cam_xz", envCamXMod640, envCamZMod640, logger);
                    water.setUniform1f("weather_active", weatherRenderedThisFrame ? 1.0F : 0.0F, logger);
                    water.setUniform1f(
                            "camera_in_water",
                            WaterEnvironmentMath.computeCameraInWaterUniform(envCameraEyeInWater),
                            logger);
                    water.setUniform1f(
                            "is_underwater",
                            WaterEnvironmentMath.computeUnderwaterEffectUniform(
                                    config.isUnderwaterEffect(), envUnderwaterFactor),
                            logger);
                    water.setUniform1f("uw_brightness", envUnderwaterBrightness, logger);

                    drawScreenQuad(safeW, safeH);
                    water.unbind();
                }
            }
        } finally {
            ShaderProgram.unbindActive();
            GL13.glActiveTexture(GL13.GL_TEXTURE5);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);

            GL11.glPopAttrib();
        }
    }

    private static void drawScreenQuad(int width, int height) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(0.0F, 0.0F);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(width, 0.0F);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(width, height);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(0.0F, height);
        GL11.glEnd();
    }

    private static void bindColorBlackForOverlayPass() {
        if (shaderManager == null) {
            return;
        }
        ShaderProgram colorBlack = shaderManager.getProgram("color_black");
        if (colorBlack != null) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            colorBlack.bind();
            colorBlack.setUniform1i("colorMap", 0, logger);
        }
    }

    private static void unbindColorBlackForOverlayPass() {
        ShaderProgram.unbindActive();
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
    }

    private static void saveAndDisableColorMask() {
        COLOR_MASK_BUF.clear();
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, COLOR_MASK_BUF);
        savedColorMaskR = COLOR_MASK_BUF.get(0) != 0;
        savedColorMaskG = COLOR_MASK_BUF.get(1) != 0;
        savedColorMaskB = COLOR_MASK_BUF.get(2) != 0;
        savedColorMaskA = COLOR_MASK_BUF.get(3) != 0;
        savedColorMaskValid = true;
        GL11.glColorMask(false, false, false, false);
    }

    private static void restoreSavedColorMask() {
        if (savedColorMaskValid) {
            GL11.glColorMask(savedColorMaskR, savedColorMaskG, savedColorMaskB, savedColorMaskA);
            savedColorMaskValid = false;
        } else {
            GL11.glColorMask(true, true, true, true);
        }
    }

    private static void saveAndForceSmoothShadeModel() {
        if (!savedShadeModelValid) {
            savedShadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
            savedShadeModelValid = true;
        }
        GL11.glShadeModel(GL11.GL_SMOOTH);
    }

    private static void restoreSavedShadeModel() {
        if (savedShadeModelValid) {
            GL11.glShadeModel(savedShadeModel);
            savedShadeModelValid = false;
        }
    }

    private static void updateTimer() {
        long now = System.nanoTime();
        long dt = now - lastFrameNs;
        lastFrameNs = now;
        if (dt > 0L) {
            long clampedDt = Math.min(dt, 100_000_000L);
            lastFrameDtSeconds = clampedDt / 1_000_000_000.0;
            envRainAnimSeconds += lastFrameDtSeconds;
            if (envRainAnimSeconds > 10000.0) {
                envRainAnimSeconds -= 10000.0;
            }
            shaderTimer += (float) ((0.002 * clampedDt) / 13_000_000.0);
            if (shaderTimer >= 1.0F) {
                shaderTimer -= (float) Math.floor(shaderTimer);
            }
        }
    }

    private static void updateEnvironmentState(Minecraft client, float tickDelta) {
        World world = client.world;
        LivingEntity cam = client.camera;
        if (world == null || cam == null) {
            return;
        }
        envHasCeiling = world.dimension != null && (world.dimension.isNether || world.dimension.hasCeiling);
        float timeOfDay = world.getTime(tickDelta);
        envSkyAmbient = WaterEnvironmentMath.computeSkyAmbientLight(timeOfDay);
        envRain = WaterEnvironmentMath.clamp01(world.getRainGradient(tickDelta));
        envThunder = WaterEnvironmentMath.clamp01(world.getThunderGradient(tickDelta));

        envReflBrightness = WaterEnvironmentMath.computeReflectionBrightness(
                envRain, envThunder, envHasCeiling);
        envTransparencySkyLight = WaterEnvironmentMath.computeTransparencySkyLight(
                envRain, envThunder, envHasCeiling);

        envCameraEyeInWater = resolveCameraSubmersionAndEyePos(client, world, cam, tickDelta, TMP_CAMERA_EYE_POS);
        double camWorldX = TMP_CAMERA_EYE_POS[0];
        double camWorldZ = TMP_CAMERA_EYE_POS[2];
        envCamXMod640 = (float) (camWorldX - Math.floor(camWorldX / 640.0) * 640.0);
        envCamZMod640 = (float) (camWorldZ - Math.floor(camWorldZ / 640.0) * 640.0);
        envUnderwaterFactor = (config != null && config.isUnderwaterEffect())
                ? WaterEnvironmentMath.stepUnderwaterFactor(envUnderwaterFactor, envCameraEyeInWater, lastFrameDtSeconds)
                : 0.0F;

        envCameraSkyLight = sampleEffectiveCameraSkyLight(
                world,
                TMP_CAMERA_EYE_POS[0],
                TMP_CAMERA_EYE_POS[1],
                TMP_CAMERA_EYE_POS[2],
                envHasCeiling);
        envShouldRenderReflSky = WaterEnvironmentMath.shouldRenderReflectionSky(envHasCeiling, envCameraSkyLight);
        envUnderwaterBrightness = WaterEnvironmentMath.computeUnderwaterBrightness(
                envSkyAmbient, envRain, envCameraSkyLight, envHasCeiling);
    }

    private static boolean resolveCameraSubmersionAndEyePos(
            Minecraft client,
            World world,
            LivingEntity cam,
            float tickDelta,
            double[] outEyePos) {
        boolean isThirdPerson = client.options != null && client.options.thirdPerson && !cam.isSleeping();
        if (!isThirdPerson) {
            outEyePos[0] = cam.lastTickX + (cam.x - cam.lastTickX) * tickDelta;
            outEyePos[1] = cam.lastTickY + (cam.y - cam.lastTickY) * tickDelta + cam.getEyeHeight();
            outEyePos[2] = cam.lastTickZ + (cam.z - cam.lastTickZ) * tickDelta;
            return cam.isInFluid(Material.WATER);
        }

        double eyeAdjust = cam.standingEyeHeight - 1.62F;
        double baseX = cam.lastTickX + (cam.x - cam.lastTickX) * tickDelta;
        double baseY = cam.lastTickY + (cam.y - cam.lastTickY) * tickDelta - eyeAdjust;
        double baseZ = cam.lastTickZ + (cam.z - cam.lastTickZ) * tickDelta;

        double dist = 4.0;
        float yaw = cam.yaw;
        float pitch = cam.pitch;
        boolean debugCam = client.options.debugCamera;
        if (client.gameRenderer instanceof GameRendererAccessor gra) {
            float prevDist = gra.watershader$getPrevThirdPersonDistance();
            float curDist = gra.watershader$getThirdPersonDistance();
            dist = prevDist + (curDist - prevDist) * tickDelta;
            if (debugCam) {
                float prevYaw = gra.watershader$getPrevThirdPersonYaw();
                float curYaw = gra.watershader$getThirdPersonYaw();
                float prevPitch = gra.watershader$getPrevThirdPersonPitch();
                float curPitch = gra.watershader$getThirdPersonPitch();
                yaw = prevYaw + (curYaw - prevYaw) * tickDelta;
                pitch = prevPitch + (curPitch - prevPitch) * tickDelta;
            }
        }

        if (debugCam) {
            WaterEnvironmentMath.computeThirdPersonOffset(yaw + 180.0F, pitch, dist, TMP_THIRD_PERSON_OFFSET);
        } else {
            WaterEnvironmentMath.computeThirdPersonOffset(yaw, pitch, dist, TMP_THIRD_PERSON_OFFSET);
            double offX = TMP_THIRD_PERSON_OFFSET[0];
            double offY = TMP_THIRD_PERSON_OFFSET[1];
            double offZ = TMP_THIRD_PERSON_OFFSET[2];
            for (int i = 0; i < 8; i++) {
                float dx = ((i & 1) * 2 - 1) * 0.1F;
                float dy = (((i >> 1) & 1) * 2 - 1) * 0.1F;
                float dz = (((i >> 2) & 1) * 2 - 1) * 0.1F;
                HitResult hit = world.raycast(
                        Vec3d.createCached(baseX + dx, baseY + dy, baseZ + dz),
                        Vec3d.createCached(baseX - offX + dx + dz, baseY - offY + dy, baseZ - offZ + dz));
                if (hit != null) {
                    double hitDist = hit.pos.distanceTo(Vec3d.createCached(baseX, baseY, baseZ));
                    if (hitDist < dist) {
                        dist = hitDist;
                    }
                }
            }
            WaterEnvironmentMath.computeThirdPersonOffset(yaw, pitch, dist, TMP_THIRD_PERSON_OFFSET);
        }

        double camEyeX = baseX - TMP_THIRD_PERSON_OFFSET[0];
        double camEyeY = baseY - TMP_THIRD_PERSON_OFFSET[1];
        double camEyeZ = baseZ - TMP_THIRD_PERSON_OFFSET[2];
        outEyePos[0] = camEyeX;
        outEyePos[1] = camEyeY;
        outEyePos[2] = camEyeZ;

        int bx = MathHelper.floor(camEyeX);
        int by = MathHelper.floor(camEyeY);
        int bz = MathHelper.floor(camEyeZ);
        if (by < 0 || by > 127) {
            return false;
        }
        if (world.getMaterial(bx, by, bz) != Material.WATER) {
            return false;
        }
        int meta = world.getBlockMeta(bx, by, bz);
        boolean waterAbove = (by + 1 <= 127) && world.getMaterial(bx, by + 1, bz) == Material.WATER;
        return WaterEnvironmentMath.isCameraPointInWater(camEyeY, by, meta, true, waterAbove);
    }

    private static int sampleEffectiveCameraSkyLight(
            World world,
            double camX,
            double camY,
            double camZ,
            boolean hasCeiling) {
        if (hasCeiling) {
            return 0;
        }
        int bx = (int) Math.floor(camX);
        int by = Math.max(0, Math.min(127, (int) Math.floor(camY)));
        int bz = (int) Math.floor(camZ);
        int probeY = by;
        while (probeY <= 127 && world.getMaterial(bx, probeY, bz) == Material.WATER) {
            probeY++;
        }
        if (probeY > 127) {
            return 15;
        }
        return world.getBrightness(LightType.SKY, bx, probeY, bz);
    }

    public static boolean isOpaqueSolid(World world, int x, int y, int z) {
        if (world == null || y < 0 || y > 127) {
            return false;
        }
        int id = world.getBlockId(x, y, z);
        if (id <= 0 || id >= Block.BLOCKS.length) {
            return false;
        }
        Block block = Block.BLOCKS[id];
        return block != null && block.isFullCube() && block.material.blocksMovement();
    }

    private static void updateWaterPlane(Minecraft client, float tickDelta) {
        World world = client.world;
        LivingEntity cam = client.camera;
        if (world == null || cam == null) {
            return;
        }
        double camX = cam.lastTickX + (cam.x - cam.lastTickX) * tickDelta;
        double camY = cam.lastTickY + (cam.y - cam.lastTickY) * tickDelta;
        double camZ = cam.lastTickZ + (cam.z - cam.lastTickZ) * tickDelta;
        float yaw = cam.prevYaw + (cam.yaw - cam.prevYaw) * tickDelta;
        float pitch = cam.prevPitch + (cam.pitch - cam.prevPitch) * tickDelta;
        WORLD_WATER_PROBE.bind(world);
        try {
            WATER_PLANE_FINDER.updateSmoothed(
                    WORLD_WATER_PROBE,
                    camX,
                    camY,
                    camZ,
                    yaw,
                    pitch,
                    envCameraEyeInWater,
                    lastFrameDtSeconds);
        } finally {
            WORLD_WATER_PROBE.bind(null);
        }
    }

    private static final class WorldWaterProbe implements WaterPlaneFinder.BlockProbe {
        private World world;

        void bind(World world) {
            this.world = world;
        }

        @Override
        public boolean isStillWater(int x, int y, int z) {
            return isOpenStillWater(x, y, z)
                    && (isOpenStillWater(x + 1, y, z)
                    || isOpenStillWater(x - 1, y, z)
                    || isOpenStillWater(x, y, z + 1)
                    || isOpenStillWater(x, y, z - 1));
        }

        private boolean isOpenStillWater(int x, int y, int z) {
            if (this.world == null || y < 0 || y > 127) {
                return false;
            }
            return this.world.getMaterial(x, y, z) == Material.WATER
                    && this.world.getBlockMeta(x, y, z) == 0
                    && (y >= 127 || this.world.getMaterial(x, y + 1, z) != Material.WATER);
        }

        @Override
        public boolean isOpaqueSolid(int x, int y, int z) {
            return WaterShaderPipeline.isOpaqueSolid(this.world, x, y, z);
        }
    }

    private static void readGlMatrix(int matrixEnum, float[] dst) {
        MATRIX_READ_BUF.clear();
        GL11.glGetFloat(matrixEnum, MATRIX_READ_BUF);
        MATRIX_READ_BUF.get(dst, 0, 16);
    }

    private static void rebuildExcludeCache(WaterShaderConfig cfg) {
        EXCLUDED_BLOCK_IDS.clear();
        EXCLUDED_BLOCK_KEYS.clear();
        if (cfg == null) {
            return;
        }
        for (String entry : cfg.getExcludeList()) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                EXCLUDED_BLOCK_IDS.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                EXCLUDED_BLOCK_KEYS.add(trimmed);
            }
        }
    }
}
