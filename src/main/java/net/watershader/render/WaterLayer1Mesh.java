package net.watershader.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;

/**
 * Self-contained triangle mesh for one chunk's Layer 1 translucent geometry using the exact
 * 32-byte interleaved vertex layout expected by fixed-function shaders ({@code xyz + uv + rgba + normal}).
 *
 * <p>Uploads lazily to an OpenGL static VBO when available and frees GPU/CPU resources on close.
 */
public final class WaterLayer1Mesh {
    public static final int INTS_PER_VERTEX = 8;
    public static final int STRIDE_BYTES = 32;
    private static int vboSupportState = 0; // 0 = unknown, 1 = supported, -1 = unsupported

    private final int originX;
    private final int originY;
    private final int originZ;
    private final int vertexCount;

    private ByteBuffer directBytes;
    private int vboId = 0;
    private boolean vboAttempted = false;
    private boolean closed = false;

    public WaterLayer1Mesh(int originX, int originY, int originZ, int[] words, int wordCount) {
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.vertexCount = wordCount / INTS_PER_VERTEX;
        this.directBytes = BufferUtils.createByteBuffer(this.vertexCount * STRIDE_BYTES);
        this.directBytes.asIntBuffer().put(words, 0, this.vertexCount * INTS_PER_VERTEX);
        this.directBytes.position(0).limit(this.vertexCount * STRIDE_BYTES);
    }

    public int getOriginX() {
        return this.originX;
    }

    public int getOriginY() {
        return this.originY;
    }

    public int getOriginZ() {
        return this.originZ;
    }

    public int getVertexCount() {
        return this.vertexCount;
    }

    /**
     * Draws this chunk's Layer 1 triangles relative to the interpolated camera position.
     * Caller should save client array states / matrix state or call {@link #beginDrawBatch()} /
     * {@link #endDrawBatch()} around a batch of chunk meshes.
     */
    public void draw(double camX, double camY, double camZ) {
        if (this.closed || this.vertexCount <= 0) {
            return;
        }
        ensureVboUploaded();

        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(
                    (float) (this.originX - camX),
                    (float) (this.originY - camY),
                    (float) (this.originZ - camZ));

            if (this.vboId != 0) {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);
                GL11.glVertexPointer(3, GL11.GL_FLOAT, STRIDE_BYTES, 0L);
                GL11.glTexCoordPointer(2, GL11.GL_FLOAT, STRIDE_BYTES, 12L);
                GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, STRIDE_BYTES, 20L);
                GL11.glNormalPointer(GL11.GL_BYTE, STRIDE_BYTES, 24L);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, this.vertexCount);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            } else if (this.directBytes != null) {
                ByteBuffer bytes = this.directBytes;
                if (supportsVbo()) {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                }
                bytes.position(0);
                GL11.glVertexPointer(3, STRIDE_BYTES, bytes.asFloatBuffer());
                bytes.position(12);
                GL11.glTexCoordPointer(2, STRIDE_BYTES, bytes.asFloatBuffer());
                bytes.position(20);
                GL11.glColorPointer(4, true, STRIDE_BYTES, bytes);
                bytes.position(24);
                GL11.glNormalPointer(STRIDE_BYTES, bytes);
                bytes.position(0);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, this.vertexCount);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    public static void beginDrawBatch() {
        if (supportsVbo()) {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
    }

    public static void endDrawBatch() {
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        if (supportsVbo()) {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    public void close() {
        this.closed = true;
        if (this.vboId != 0) {
            GL15.glDeleteBuffers(this.vboId);
            this.vboId = 0;
        }
        this.directBytes = null;
    }

    private void ensureVboUploaded() {
        if (this.vboAttempted || this.directBytes == null) {
            return;
        }
        this.vboAttempted = true;
        if (!supportsVbo()) {
            return;
        }
        int id = GL15.glGenBuffers();
        if (id == 0) {
            return;
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
        this.directBytes.position(0);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, this.directBytes, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        this.vboId = id;
        // Release staging off-heap bytes once uploaded to GPU
        this.directBytes = null;
    }

    public static void resetCapabilityCache() {
        vboSupportState = 0;
    }

    private static boolean supportsVbo() {
        if (vboSupportState == 0) {
            ContextCapabilities caps = GLContext.getCapabilities();
            vboSupportState = (caps != null && caps.OpenGL15) ? 1 : -1;
        }
        return vboSupportState > 0;
    }
}
