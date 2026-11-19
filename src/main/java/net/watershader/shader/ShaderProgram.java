package net.watershader.shader;

import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBFragmentShader;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.ARBVertexShader;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Compiled and linked GLSL shader program with cached uniform lookups and
 * fallback to ARB shader objects on pre-OpenGL 2.0 contexts.
 */
public final class ShaderProgram implements AutoCloseable {
    private static boolean activeUseArb = false;

    private final String name;
    private final String vertexOrigin;
    private final String fragmentOrigin;
    private final boolean useArb;
    private final int programId;
    private final int vertexShaderId;
    private final int fragmentShaderId;
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final Map<String, Integer> uniformLocations = new HashMap<>();
    private final Set<String> warnedMissingUniforms = new HashSet<>();
    private boolean closed;

    private ShaderProgram(
        String name,
        String vertexOrigin,
        String fragmentOrigin,
        boolean useArb,
        int programId,
        int vertexShaderId,
        int fragmentShaderId
    ) {
        this.name = name;
        this.vertexOrigin = vertexOrigin;
        this.fragmentOrigin = fragmentOrigin;
        this.useArb = useArb;
        this.programId = programId;
        this.vertexShaderId = vertexShaderId;
        this.fragmentShaderId = fragmentShaderId;
    }

    /**
     * Compiles vertex and fragment shaders and links a GPU program.
     * Requires an active LWJGL OpenGL context on the calling thread.
     */
    public static ShaderProgram compile(ShaderSourceLoader.LoadedShaderSource source, Logger logger) {
        boolean useArb = detectUseArb();
        activeUseArb = useArb;
        int vertId = 0;
        int fragId = 0;
        int progId = 0;

        try {
            int vertType = useArb ? ARBVertexShader.GL_VERTEX_SHADER_ARB : GL20.GL_VERTEX_SHADER;
            int fragType = useArb ? ARBFragmentShader.GL_FRAGMENT_SHADER_ARB : GL20.GL_FRAGMENT_SHADER;

            vertId = compileStage(source.name(), "vertex", source.vertexOrigin(), vertType, source.vertexSource(), useArb, logger);
            fragId = compileStage(source.name(), "fragment", source.fragmentOrigin(), fragType, source.fragmentSource(), useArb, logger);

            progId = linkProgram(source.name(), source.vertexOrigin(), source.fragmentOrigin(), vertId, fragId, useArb, logger);
            return new ShaderProgram(
                source.name(),
                source.vertexOrigin(),
                source.fragmentOrigin(),
                useArb,
                progId,
                vertId,
                fragId
            );
        } catch (RuntimeException ex) {
            deleteHandles(useArb, progId, vertId, fragId);
            throw ex;
        }
    }

    private static boolean detectUseArb() {
        ContextCapabilities caps = GLContext.getCapabilities();
        if (caps != null && caps.OpenGL20) {
            return false;
        }
        if (caps != null && caps.GL_ARB_shader_objects && caps.GL_ARB_vertex_shader && caps.GL_ARB_fragment_shader) {
            return true;
        }
        String version = GL11.glGetString(GL11.GL_VERSION);
        throw new IllegalStateException("OpenGL < 2.0 without ARB shader extension support (GL_VERSION=" + version + ")");
    }

    /**
     * Unbinds any active GLSL program using the detected core GL20 or ARB shader path.
     */
    public static void unbindActive() {
        if (activeUseArb) {
            ARBShaderObjects.glUseProgramObjectARB(0);
        } else {
            GL20.glUseProgram(0);
        }
    }

    private static int compileStage(
        String shaderName,
        String stageName,
        String origin,
        int shaderType,
        String source,
        boolean useArb,
        Logger logger
    ) {
        int shaderId = useArb
            ? ARBShaderObjects.glCreateShaderObjectARB(shaderType)
            : GL20.glCreateShader(shaderType);
        if (shaderId == 0) {
            throw new ShaderCompileException(shaderName, stageName, "glCreateShader returned 0 (origin=" + origin + ")");
        }

        if (useArb) {
            ARBShaderObjects.glShaderSourceARB(shaderId, source);
            ARBShaderObjects.glCompileShaderARB(shaderId);
        } else {
            GL20.glShaderSource(shaderId, source);
            GL20.glCompileShader(shaderId);
        }

        int status = useArb
            ? ARBShaderObjects.glGetObjectParameteriARB(shaderId, ARBShaderObjects.GL_OBJECT_COMPILE_STATUS_ARB)
            : GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS);
        int logLen = useArb
            ? ARBShaderObjects.glGetObjectParameteriARB(shaderId, ARBShaderObjects.GL_OBJECT_INFO_LOG_LENGTH_ARB)
            : GL20.glGetShaderi(shaderId, GL20.GL_INFO_LOG_LENGTH);
        String infoLog = readShaderLog(shaderId, logLen, useArb).trim();

        if (status != GL11.GL_TRUE) {
            String diagnostic = "Origin: " + origin + "\nCompiler log:\n" + infoLog
                + "\nSource:\n" + ShaderSourceLoader.formatNumberedSource(source);
            deleteHandles(useArb, 0, shaderId, 0);
            throw new ShaderCompileException(shaderName, stageName, diagnostic);
        }

        if (!infoLog.isEmpty()) {
            logger.warn("Shader '{}' ({}, {}) compiler warnings:\n{}", shaderName, stageName, origin, infoLog);
        }
        return shaderId;
    }

    private static int linkProgram(
        String shaderName,
        String vertOrigin,
        String fragOrigin,
        int vertId,
        int fragId,
        boolean useArb,
        Logger logger
    ) {
        int progId = useArb ? ARBShaderObjects.glCreateProgramObjectARB() : GL20.glCreateProgram();
        if (progId == 0) {
            throw new ShaderCompileException(shaderName, "link", "glCreateProgram returned 0");
        }

        if (useArb) {
            ARBShaderObjects.glAttachObjectARB(progId, vertId);
            ARBShaderObjects.glAttachObjectARB(progId, fragId);
            ARBShaderObjects.glLinkProgramARB(progId);
        } else {
            GL20.glAttachShader(progId, vertId);
            GL20.glAttachShader(progId, fragId);
            GL20.glLinkProgram(progId);
        }

        int status = useArb
            ? ARBShaderObjects.glGetObjectParameteriARB(progId, ARBShaderObjects.GL_OBJECT_LINK_STATUS_ARB)
            : GL20.glGetProgrami(progId, GL20.GL_LINK_STATUS);
        int logLen = useArb
            ? ARBShaderObjects.glGetObjectParameteriARB(progId, ARBShaderObjects.GL_OBJECT_INFO_LOG_LENGTH_ARB)
            : GL20.glGetProgrami(progId, GL20.GL_INFO_LOG_LENGTH);
        String infoLog = readProgramLog(progId, logLen, useArb).trim();

        if (status != GL11.GL_TRUE) {
            String diagnostic = "Vert: " + vertOrigin + ", Frag: " + fragOrigin + "\nLinker log:\n" + infoLog;
            deleteHandles(useArb, progId, 0, 0);
            throw new ShaderCompileException(shaderName, "link", diagnostic);
        }

        if (!infoLog.isEmpty()) {
            logger.warn("Shader '{}' link warnings:\n{}", shaderName, infoLog);
        }
        return progId;
    }

    private static String readShaderLog(int shaderId, int length, boolean useArb) {
        int bufLen = Math.max(length, 4096);
        return useArb
            ? ARBShaderObjects.glGetInfoLogARB(shaderId, bufLen)
            : GL20.glGetShaderInfoLog(shaderId, bufLen);
    }

    private static String readProgramLog(int progId, int length, boolean useArb) {
        int bufLen = Math.max(length, 4096);
        return useArb
            ? ARBShaderObjects.glGetInfoLogARB(progId, bufLen)
            : GL20.glGetProgramInfoLog(progId, bufLen);
    }

    private static void deleteHandles(boolean useArb, int progId, int vertId, int fragId) {
        if (useArb) {
            if (progId != 0) ARBShaderObjects.glDeleteObjectARB(progId);
            if (vertId != 0) ARBShaderObjects.glDeleteObjectARB(vertId);
            if (fragId != 0) ARBShaderObjects.glDeleteObjectARB(fragId);
        } else {
            if (progId != 0) GL20.glDeleteProgram(progId);
            if (vertId != 0) GL20.glDeleteShader(vertId);
            if (fragId != 0) GL20.glDeleteShader(fragId);
        }
    }

    public void bind() {
        ensureOpen();
        if (useArb) {
            ARBShaderObjects.glUseProgramObjectARB(programId);
        } else {
            GL20.glUseProgram(programId);
        }
    }

    public void unbind() {
        if (useArb) {
            ARBShaderObjects.glUseProgramObjectARB(0);
        } else {
            GL20.glUseProgram(0);
        }
    }

    public int getUniformLocation(String uniformName, Logger logger) {
        ensureOpen();
        return uniformLocations.computeIfAbsent(uniformName, key -> {
            int loc = useArb
                ? ARBShaderObjects.glGetUniformLocationARB(programId, key)
                : GL20.glGetUniformLocation(programId, key);
            if (loc < 0 && warnedMissingUniforms.add(key)) {
                logger.debug("Shader '{}' has no active uniform '{}'", name, key);
            }
            return loc;
        });
    }

    public void setUniform1i(String uniformName, int value, Logger logger) {
        int loc = getUniformLocation(uniformName, logger);
        if (loc < 0) return;
        if (useArb) {
            ARBShaderObjects.glUniform1iARB(loc, value);
        } else {
            GL20.glUniform1i(loc, value);
        }
    }

    public void setUniform1f(String uniformName, float value, Logger logger) {
        int loc = getUniformLocation(uniformName, logger);
        if (loc < 0) return;
        if (useArb) {
            ARBShaderObjects.glUniform1fARB(loc, value);
        } else {
            GL20.glUniform1f(loc, value);
        }
    }

    public void setUniform2f(String uniformName, float x, float y, Logger logger) {
        int loc = getUniformLocation(uniformName, logger);
        if (loc < 0) return;
        if (useArb) {
            ARBShaderObjects.glUniform2fARB(loc, x, y);
        } else {
            GL20.glUniform2f(loc, x, y);
        }
    }

    public void setUniform3f(String uniformName, float x, float y, float z, Logger logger) {
        int loc = getUniformLocation(uniformName, logger);
        if (loc < 0) return;
        if (useArb) {
            ARBShaderObjects.glUniform3fARB(loc, x, y, z);
        } else {
            GL20.glUniform3f(loc, x, y, z);
        }
    }

    public void setUniformMat4(String uniformName, float[] matrix4x4, boolean transpose, Logger logger) {
        int loc = getUniformLocation(uniformName, logger);
        if (loc < 0) return;
        matrixBuffer.clear();
        matrixBuffer.put(matrix4x4, 0, 16);
        matrixBuffer.flip();
        if (useArb) {
            ARBShaderObjects.glUniformMatrix4ARB(loc, transpose, matrixBuffer);
        } else {
            GL20.glUniformMatrix4(loc, transpose, matrixBuffer);
        }
    }

    public String getName() {
        return name;
    }

    public String getVertexOrigin() {
        return vertexOrigin;
    }

    public String getFragmentOrigin() {
        return fragmentOrigin;
    }

    public int getProgramId() {
        return programId;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("ShaderProgram '" + name + "' has already been closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (useArb) {
            if (programId != 0) {
                if (vertexShaderId != 0) ARBShaderObjects.glDetachObjectARB(programId, vertexShaderId);
                if (fragmentShaderId != 0) ARBShaderObjects.glDetachObjectARB(programId, fragmentShaderId);
            }
        } else {
            if (programId != 0) {
                if (vertexShaderId != 0) GL20.glDetachShader(programId, vertexShaderId);
                if (fragmentShaderId != 0) GL20.glDetachShader(programId, fragmentShaderId);
            }
        }
        deleteHandles(useArb, programId, vertexShaderId, fragmentShaderId);
        uniformLocations.clear();
    }
}
