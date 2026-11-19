package net.watershader.render;

import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Manages the offscreen Framebuffer Objects (color, water stencil, planar reflection) and a 1x1
 * black fallback texture used when reflections are disabled ({@code water_mode == 1}).
 *
 * <p>Fixes original Water Shader v4c bugs: resize checks use logical OR across width/height/config,
 * old FBO/RBO/texture handles are deleted before reallocation and on shutdown, and {@code anti_aliasing}
 * actually scales FBO 0 and FBO 1 cleanly inside {@code GL_MAX_TEXTURE_SIZE}.
 */
public final class FboManager {
    private enum Backend {
        GL30_CORE,
        ARB,
        EXT,
        UNSUPPORTED
    }

    /**
     * Pure computed target dimensions for the shader FBOs.
     */
    public record FboDimensions(int colorWidth, int colorHeight, int reflectionWidth, int reflectionHeight) {
    }

    private static final class FboTarget {
        int fboId;
        int colorTexId;
        int depthRbId;
        int width;
        int height;

        boolean isAllocated() {
            return fboId != 0 && colorTexId != 0 && depthRbId != 0;
        }
    }

    private final Logger logger;
    private final FboTarget mainTarget = new FboTarget();
    private final FboTarget stencilTarget = new FboTarget();
    private final FboTarget reflectionTarget = new FboTarget();

    private Backend backend = Backend.UNSUPPORTED;
    private boolean initialized = false;
    private boolean valid = false;
    private boolean capOpenGL12 = false;
    private boolean capOpenGL14 = false;
    private boolean capArbShadow = false;
    private boolean capArbMirroredRepeat = false;

    private int dummyBlackTexId = 0;
    private int dummyTransparentTexId = 0;
    private int dummyDepthTexId = 0;
    private int weatherTexId = 0;
    private int rainStreakTexId = 0;
    private int rainSplashTexId = 0;
    private int waterDepthTexId = 0;
    private int handDepthTexId = 0;
    private int mainDepthFormat = GL14.GL_DEPTH_COMPONENT24;

    private int lastDisplayW = -1;
    private int lastDisplayH = -1;
    private int lastAa = -1;
    private int lastReflDiv = -1;
    private boolean lastWithReflection = false;

    public FboManager(Logger logger) {
        this.logger = logger;
    }

    public static FboDimensions computeDimensions(
            int displayWidth,
            int displayHeight,
            int antiAliasing,
            int reflectionDivisor,
            int maxTextureSize) {
        int safeW = Math.max(1, displayWidth);
        int safeH = Math.max(1, displayHeight);
        int safeAa = Math.max(1, antiAliasing);
        int safeDiv = Math.max(1, reflectionDivisor);

        long rawColorW = (long) safeW * safeAa;
        long rawColorH = (long) safeH * safeAa;
        if (maxTextureSize > 0 && (rawColorW > maxTextureSize || rawColorH > maxTextureSize)) {
            double scale = Math.min((double) maxTextureSize / rawColorW, (double) maxTextureSize / rawColorH);
            rawColorW = Math.max(1L, (long) Math.floor(rawColorW * scale));
            rawColorH = Math.max(1L, (long) Math.floor(rawColorH * scale));
        }

        long rawReflW = Math.max(1L, safeW / safeDiv);
        long rawReflH = Math.max(1L, safeH / safeDiv);
        if (maxTextureSize > 0 && (rawReflW > maxTextureSize || rawReflH > maxTextureSize)) {
            double scale = Math.min((double) maxTextureSize / rawReflW, (double) maxTextureSize / rawReflH);
            rawReflW = Math.max(1L, (long) Math.floor(rawReflW * scale));
            rawReflH = Math.max(1L, (long) Math.floor(rawReflH * scale));
        }

        return new FboDimensions(
                (int) rawColorW,
                (int) rawColorH,
                (int) rawReflW,
                (int) rawReflH);
    }

    public static boolean needsRecreate(
            boolean currentlyValid,
            int prevDisplayW,
            int prevDisplayH,
            int prevAa,
            int prevReflDiv,
            boolean prevWithReflection,
            int newDisplayW,
            int newDisplayH,
            int newAa,
            int newReflDiv,
            boolean newWithReflection) {
        return !currentlyValid
                || prevDisplayW != newDisplayW
                || prevDisplayH != newDisplayH
                || prevAa != newAa
                || prevReflDiv != newReflDiv
                || prevWithReflection != newWithReflection;
    }

    public static boolean isDepthCopyValid(boolean fboValid, int texId, int width, int height) {
        return fboValid && texId != 0 && width > 0 && height > 0;
    }

    /**
     * Ensures all active FBOs exist and match current window and config dimensions.
     *
     * @return {@code true} if FBOs are ready for rendering.
     */
    public boolean ensureReady(
            int displayWidth,
            int displayHeight,
            int antiAliasing,
            int reflectionDivisor,
            boolean withReflection) {
        int safeAa = Math.max(1, antiAliasing);
        int safeDiv = Math.max(1, reflectionDivisor);
        if (!needsRecreate(
                this.valid,
                this.lastDisplayW,
                this.lastDisplayH,
                this.lastAa,
                this.lastReflDiv,
                this.lastWithReflection,
                displayWidth,
                displayHeight,
                safeAa,
                safeDiv,
                withReflection)) {
            return true;
        }

        if (!this.initialized) {
            detectBackend();
            this.initialized = true;
        }
        if (this.backend == Backend.UNSUPPORTED) {
            return false;
        }

        deleteFramebuffers();
        ensureDummyBlackTexture();
        ensureDummyTransparentTexture();
        ensureDummyDepthTexture();
        ensureRainStreakTexture();
        ensureRainSplashTexture();

        int maxTex = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        FboDimensions dims = computeDimensions(displayWidth, displayHeight, safeAa, safeDiv, maxTex);

        boolean ok = createTarget(this.mainTarget, dims.colorWidth(), dims.colorHeight(), "main_color", true)
                && createTarget(this.stencilTarget, dims.colorWidth(), dims.colorHeight(), "water_stencil", false)
                && (!withReflection || createTarget(
                this.reflectionTarget,
                dims.reflectionWidth(),
                dims.reflectionHeight(),
                "reflection_color",
                false));

        if (ok) {
            this.weatherTexId = createWeatherColorTexture(dims.colorWidth(), dims.colorHeight());
            this.waterDepthTexId = createDepthTexture(dims.colorWidth(), dims.colorHeight());
            this.handDepthTexId = createDepthTexture(dims.colorWidth(), dims.colorHeight());
            if (this.weatherTexId == 0 || this.waterDepthTexId == 0 || this.handDepthTexId == 0) {
                this.logger.error("[WaterShader] Failed to allocate weather/depth textures ({}x{})", dims.colorWidth(), dims.colorHeight());
                ok = false;
            } else {
                // Verify weatherTexId completes mainTarget cleanly when swapped into attachment 0
                bindTarget(this.mainTarget);
                glAttachColorTex(this.weatherTexId);
                boolean weatherComplete = queryFboStatus() == completeStatusConstant();
                glAttachColorTex(this.mainTarget.colorTexId);
                if (!weatherComplete) {
                    this.logger.warn("[WaterShader] Weather color attachment incomplete; disabling isolated weather texture");
                    GL11.glDeleteTextures(this.weatherTexId);
                    this.weatherTexId = 0;
                }
            }
        }

        // Ensure default screen FBO is active after creation
        unbind();

        if (!ok) {
            deleteFramebuffers();
            this.valid = false;
            return false;
        }

        this.lastDisplayW = displayWidth;
        this.lastDisplayH = displayHeight;
        this.lastAa = safeAa;
        this.lastReflDiv = safeDiv;
        this.lastWithReflection = withReflection;
        this.valid = true;
        return true;
    }

    public boolean isValid() {
        return this.valid;
    }

    public boolean hasWeatherAttachment() {
        return this.valid && this.mainTarget.isAllocated() && this.weatherTexId != 0;
    }

    public void bindMain() {
        bindTarget(this.mainTarget);
    }

    public void bindStencil() {
        bindTarget(this.stencilTarget);
    }

    public void bindReflection() {
        bindTarget(this.reflectionTarget);
    }

    public void unbind() {
        glBindFbo(0);
    }

    public int getMainColorTexture() {
        return this.mainTarget.colorTexId;
    }

    public int getStencilTexture() {
        return this.stencilTarget.colorTexId;
    }

    public int getReflectionTexture() {
        return (this.reflectionTarget.isAllocated() && this.lastWithReflection)
                ? this.reflectionTarget.colorTexId
                : getDummyBlackTexture();
    }

    public int getDummyBlackTexture() {
        ensureDummyBlackTexture();
        return this.dummyBlackTexId;
    }

    public int getDummyTransparentTexture() {
        ensureDummyTransparentTexture();
        return this.dummyTransparentTexId;
    }

    public int getDummyDepthTexture() {
        ensureDummyDepthTexture();
        return this.dummyDepthTexId;
    }

    public int getWeatherTexture() {
        return this.weatherTexId != 0 ? this.weatherTexId : getDummyTransparentTexture();
    }

    public int getRainStreakTexture() {
        ensureRainStreakTexture();
        return this.rainStreakTexId;
    }

    public int getRainSplashTexture() {
        ensureRainSplashTexture();
        return this.rainSplashTexId;
    }

    public int getWaterDepthTexture() {
        return this.waterDepthTexId != 0 ? this.waterDepthTexId : getDummyDepthTexture();
    }

    public int getHandDepthTexture() {
        return this.handDepthTexId != 0 ? this.handDepthTexId : getDummyDepthTexture();
    }

    public boolean beginWeatherPass() {
        if (!this.valid || !this.mainTarget.isAllocated() || this.weatherTexId == 0) {
            return false;
        }
        bindTarget(this.mainTarget);
        glAttachColorTex(this.weatherTexId);
        GL11.glViewport(0, 0, this.mainTarget.width, this.mainTarget.height);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDepthMask(false);
        return true;
    }

    public void endWeatherPass() {
        GL11.glDepthMask(true);
        if (!this.mainTarget.isAllocated()) {
            return;
        }
        bindTarget(this.mainTarget);
        glAttachColorTex(this.mainTarget.colorTexId);
        GL11.glViewport(0, 0, this.mainTarget.width, this.mainTarget.height);
    }

    public void copyMainDepthToWater() {
        copyBoundDepthTo(this.waterDepthTexId, this.mainTarget.width, this.mainTarget.height);
    }

    public void copyMainDepthToHand() {
        copyBoundDepthTo(this.handDepthTexId, this.mainTarget.width, this.mainTarget.height);
    }

    private void copyBoundDepthTo(int texId, int width, int height) {
        if (!isDepthCopyValid(this.valid, texId, width, height)) {
            return;
        }
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTex2d = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex2d);
        GL13.glActiveTexture(prevActive);
    }

    public int getMainWidth() {
        return this.mainTarget.width;
    }

    public int getMainHeight() {
        return this.mainTarget.height;
    }

    public int getStencilWidth() {
        return this.stencilTarget.width;
    }

    public int getStencilHeight() {
        return this.stencilTarget.height;
    }

    public int getReflectionWidth() {
        return this.reflectionTarget.width;
    }

    public int getReflectionHeight() {
        return this.reflectionTarget.height;
    }

    public void close() {
        deleteFramebuffers();
        if (this.dummyBlackTexId != 0) {
            GL11.glDeleteTextures(this.dummyBlackTexId);
            this.dummyBlackTexId = 0;
        }
        if (this.dummyTransparentTexId != 0) {
            GL11.glDeleteTextures(this.dummyTransparentTexId);
            this.dummyTransparentTexId = 0;
        }
        if (this.dummyDepthTexId != 0) {
            GL11.glDeleteTextures(this.dummyDepthTexId);
            this.dummyDepthTexId = 0;
        }
        if (this.rainStreakTexId != 0) {
            GL11.glDeleteTextures(this.rainStreakTexId);
            this.rainStreakTexId = 0;
        }
        if (this.rainSplashTexId != 0) {
            GL11.glDeleteTextures(this.rainSplashTexId);
            this.rainSplashTexId = 0;
        }
        this.lastDisplayW = -1;
        this.lastDisplayH = -1;
        this.lastAa = -1;
        this.lastReflDiv = -1;
        this.lastWithReflection = false;
        this.valid = false;
    }

    private void detectBackend() {
        ContextCapabilities caps = GLContext.getCapabilities();
        this.capOpenGL12 = caps != null && caps.OpenGL12;
        this.capOpenGL14 = caps != null && caps.OpenGL14;
        this.capArbShadow = caps != null && caps.GL_ARB_shadow;
        this.capArbMirroredRepeat = caps != null && caps.GL_ARB_texture_mirrored_repeat;
        if (caps != null && caps.OpenGL30) {
            this.backend = Backend.GL30_CORE;
        } else if (caps != null && caps.GL_ARB_framebuffer_object) {
            this.backend = Backend.ARB;
        } else if (caps != null && caps.GL_EXT_framebuffer_object) {
            this.backend = Backend.EXT;
        } else {
            this.backend = Backend.UNSUPPORTED;
            this.logger.error("[WaterShader] No framebuffer object support detected (GL30/ARB_fbo/EXT_fbo missing)");
        }
        if (this.backend != Backend.UNSUPPORTED) {
            this.logger.info("[WaterShader] Using FBO backend: {}", this.backend);
        }
    }

    private boolean createTarget(FboTarget target, int width, int height, String label, boolean recordFormat) {
        if (tryCreateTarget(target, width, height, GL14.GL_DEPTH_COMPONENT24)) {
            if (recordFormat) {
                this.mainDepthFormat = GL14.GL_DEPTH_COMPONENT24;
            }
            return true;
        }
        deleteTarget(target);
        if (tryCreateTarget(target, width, height, GL11.GL_DEPTH_COMPONENT)) {
            if (recordFormat) {
                this.mainDepthFormat = GL11.GL_DEPTH_COMPONENT;
            }
            return true;
        }
        int status = queryFboStatus();
        deleteTarget(target);
        this.logger.error(
                "[WaterShader] Failed to create complete FBO '{}' ({}x{}, status=0x{})",
                label, width, height, Integer.toHexString(status));
        return false;
    }

    private int createDepthTexture(int width, int height) {
        int tex = GL11.glGenTextures();
        if (tex == 0) {
            return 0;
        }
        int wrapMode = resolveClampWrapMode();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        configureDepthTextureParams(wrapMode);
        // Drain stale GL errors before probing GL_DEPTH_COMPONENT24 texture support
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                this.mainDepthFormat,
                width,
                height,
                0,
                GL11.GL_DEPTH_COMPONENT,
                GL11.GL_UNSIGNED_INT,
                (ByteBuffer) null);
        if (GL11.glGetError() != GL11.GL_NO_ERROR && this.mainDepthFormat != GL11.GL_DEPTH_COMPONENT) {
            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_DEPTH_COMPONENT,
                    width,
                    height,
                    0,
                    GL11.GL_DEPTH_COMPONENT,
                    GL11.GL_UNSIGNED_INT,
                    (ByteBuffer) null);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private void configureDepthTextureParams(int wrapMode) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapMode);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrapMode);
        if (this.capOpenGL14) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        } else if (this.capArbShadow) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, ARBShadow.GL_TEXTURE_COMPARE_MODE_ARB, GL11.GL_NONE);
        }
    }

    private boolean tryCreateTarget(FboTarget target, int width, int height, int depthFormat) {
        int fboId = glGenFbo();
        int texId = GL11.glGenTextures();
        int rboId = glGenRbo();
        if (fboId == 0 || texId == 0 || rboId == 0) {
            target.fboId = fboId;
            target.colorTexId = texId;
            target.depthRbId = rboId;
            return false;
        }

        target.fboId = fboId;
        target.colorTexId = texId;
        target.depthRbId = rboId;
        target.width = width;
        target.height = height;

        int wrapMode = resolveTextureWrapMode();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapMode);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrapMode);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                width,
                height,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        glBindRbo(rboId);
        glRboStorage(depthFormat, width, height);
        glBindRbo(0);

        glBindFbo(fboId);
        glAttachColorTex(texId);
        glAttachDepthRbo(rboId);

        int status = queryFboStatus();
        return status == completeStatusConstant();
    }

    private void ensureDummyBlackTexture() {
        if (this.dummyBlackTexId != 0) {
            return;
        }
        int tex = GL11.glGenTextures();
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 255).flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        this.dummyBlackTexId = tex;
    }

    private void ensureDummyTransparentTexture() {
        if (this.dummyTransparentTexId != 0) {
            return;
        }
        int tex = GL11.glGenTextures();
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0).flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        this.dummyTransparentTexId = tex;
    }

    private void ensureRainStreakTexture() {
        if (this.rainStreakTexId != 0) {
            return;
        }
        int w = ModernWeatherRenderer.STREAK_TEX_WIDTH;
        int h = ModernWeatherRenderer.STREAK_TEX_HEIGHT;
        this.rainStreakTexId = createProceduralWeatherTexture(
                w, h, GL11.GL_LINEAR, buf -> ModernWeatherRenderer.writeRainStreakPixels(buf, w, h));
    }

    private void ensureRainSplashTexture() {
        if (this.rainSplashTexId != 0) {
            return;
        }
        int w = ModernWeatherRenderer.SPLASH_ATLAS_WIDTH;
        int h = ModernWeatherRenderer.SPLASH_ATLAS_HEIGHT;
        this.rainSplashTexId = createProceduralWeatherTexture(
                w, h, GL11.GL_NEAREST, buf -> ModernWeatherRenderer.writeSplashAtlasPixels(buf, w, h));
    }

    private int createProceduralWeatherTexture(
            int width,
            int height,
            int filter,
            java.util.function.Consumer<ByteBuffer> writer) {
        int tex = GL11.glGenTextures();
        if (tex == 0) {
            return 0;
        }
        ByteBuffer buf = BufferUtils.createByteBuffer(width * height * 4);
        writer.accept(buf);
        buf.flip();
        int wrapMode = resolveClampWrapMode();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapMode);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrapMode);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private int createWeatherColorTexture(int width, int height) {
        int tex = GL11.glGenTextures();
        if (tex == 0) {
            return 0;
        }
        int wrapMode = resolveClampWrapMode();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapMode);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrapMode);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                width,
                height,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private void ensureDummyDepthTexture() {
        if (this.dummyDepthTexId != 0) {
            return;
        }
        int tex = GL11.glGenTextures();
        FloatBuffer pixel = BufferUtils.createFloatBuffer(1);
        pixel.put(1.0F).flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        configureDepthTextureParams(resolveClampWrapMode());
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, 1, 1, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, pixel);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        this.dummyDepthTexId = tex;
    }

    private int resolveClampWrapMode() {
        return this.capOpenGL12 ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_CLAMP;
    }

    private int resolveTextureWrapMode() {
        if (this.capOpenGL14) {
            return GL14.GL_MIRRORED_REPEAT;
        }
        if (this.capArbMirroredRepeat) {
            return ARBTextureMirroredRepeat.GL_MIRRORED_REPEAT_ARB;
        }
        if (this.capOpenGL12) {
            return GL12.GL_CLAMP_TO_EDGE;
        }
        return GL11.GL_CLAMP;
    }

    private void deleteFramebuffers() {
        if (this.weatherTexId != 0) {
            GL11.glDeleteTextures(this.weatherTexId);
            this.weatherTexId = 0;
        }
        if (this.waterDepthTexId != 0) {
            GL11.glDeleteTextures(this.waterDepthTexId);
            this.waterDepthTexId = 0;
        }
        if (this.handDepthTexId != 0) {
            GL11.glDeleteTextures(this.handDepthTexId);
            this.handDepthTexId = 0;
        }
        deleteTarget(this.mainTarget);
        deleteTarget(this.stencilTarget);
        deleteTarget(this.reflectionTarget);
    }

    private void deleteTarget(FboTarget target) {
        if (target.fboId != 0) {
            glDeleteFbo(target.fboId);
            target.fboId = 0;
        }
        if (target.depthRbId != 0) {
            glDeleteRbo(target.depthRbId);
            target.depthRbId = 0;
        }
        if (target.colorTexId != 0) {
            GL11.glDeleteTextures(target.colorTexId);
            target.colorTexId = 0;
        }
        target.width = 0;
        target.height = 0;
    }

    private void bindTarget(FboTarget target) {
        if (target.isAllocated()) {
            glBindFbo(target.fboId);
        }
    }

    private int glGenFbo() {
        return switch (this.backend) {
            case GL30_CORE -> GL30.glGenFramebuffers();
            case ARB -> ARBFramebufferObject.glGenFramebuffers();
            case EXT -> EXTFramebufferObject.glGenFramebuffersEXT();
            case UNSUPPORTED -> 0;
        };
    }

    private void glBindFbo(int fboId) {
        switch (this.backend) {
            case GL30_CORE -> GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
            case ARB -> ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, fboId);
            case EXT -> EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, fboId);
            case UNSUPPORTED -> {
            }
        }
    }

    private void glDeleteFbo(int fboId) {
        switch (this.backend) {
            case GL30_CORE -> GL30.glDeleteFramebuffers(fboId);
            case ARB -> ARBFramebufferObject.glDeleteFramebuffers(fboId);
            case EXT -> EXTFramebufferObject.glDeleteFramebuffersEXT(fboId);
            case UNSUPPORTED -> {
            }
        }
    }

    private int glGenRbo() {
        return switch (this.backend) {
            case GL30_CORE -> GL30.glGenRenderbuffers();
            case ARB -> ARBFramebufferObject.glGenRenderbuffers();
            case EXT -> EXTFramebufferObject.glGenRenderbuffersEXT();
            case UNSUPPORTED -> 0;
        };
    }

    private void glBindRbo(int rboId) {
        switch (this.backend) {
            case GL30_CORE -> GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, rboId);
            case ARB -> ARBFramebufferObject.glBindRenderbuffer(ARBFramebufferObject.GL_RENDERBUFFER, rboId);
            case EXT -> EXTFramebufferObject.glBindRenderbufferEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, rboId);
            case UNSUPPORTED -> {
            }
        }
    }

    private void glRboStorage(int format, int width, int height) {
        switch (this.backend) {
            case GL30_CORE -> GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, format, width, height);
            case ARB -> ARBFramebufferObject.glRenderbufferStorage(
                    ARBFramebufferObject.GL_RENDERBUFFER, format, width, height);
            case EXT -> EXTFramebufferObject.glRenderbufferStorageEXT(
                    EXTFramebufferObject.GL_RENDERBUFFER_EXT, format, width, height);
            case UNSUPPORTED -> {
            }
        }
    }

    private void glDeleteRbo(int rboId) {
        switch (this.backend) {
            case GL30_CORE -> GL30.glDeleteRenderbuffers(rboId);
            case ARB -> ARBFramebufferObject.glDeleteRenderbuffers(rboId);
            case EXT -> EXTFramebufferObject.glDeleteRenderbuffersEXT(rboId);
            case UNSUPPORTED -> {
            }
        }
    }

    private void glAttachColorTex(int texId) {
        switch (this.backend) {
            case GL30_CORE -> GL30.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texId, 0);
            case ARB -> ARBFramebufferObject.glFramebufferTexture2D(
                    ARBFramebufferObject.GL_FRAMEBUFFER,
                    ARBFramebufferObject.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    texId,
                    0);
            case EXT -> EXTFramebufferObject.glFramebufferTexture2DEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT,
                    GL11.GL_TEXTURE_2D,
                    texId,
                    0);
            case UNSUPPORTED -> {
            }
        }
    }

    private void glAttachDepthRbo(int rboId) {
        switch (this.backend) {
            case GL30_CORE -> GL30.glFramebufferRenderbuffer(
                    GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, rboId);
            case ARB -> ARBFramebufferObject.glFramebufferRenderbuffer(
                    ARBFramebufferObject.GL_FRAMEBUFFER,
                    ARBFramebufferObject.GL_DEPTH_ATTACHMENT,
                    ARBFramebufferObject.GL_RENDERBUFFER,
                    rboId);
            case EXT -> EXTFramebufferObject.glFramebufferRenderbufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT,
                    EXTFramebufferObject.GL_RENDERBUFFER_EXT,
                    rboId);
            case UNSUPPORTED -> {
            }
        }
    }

    private int queryFboStatus() {
        return switch (this.backend) {
            case GL30_CORE -> GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            case ARB -> ARBFramebufferObject.glCheckFramebufferStatus(ARBFramebufferObject.GL_FRAMEBUFFER);
            case EXT -> EXTFramebufferObject.glCheckFramebufferStatusEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT);
            case UNSUPPORTED -> 0;
        };
    }

    private int completeStatusConstant() {
        return switch (this.backend) {
            case GL30_CORE -> GL30.GL_FRAMEBUFFER_COMPLETE;
            case ARB -> ARBFramebufferObject.GL_FRAMEBUFFER_COMPLETE;
            case EXT -> EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT;
            case UNSUPPORTED -> -1;
        };
    }
}
