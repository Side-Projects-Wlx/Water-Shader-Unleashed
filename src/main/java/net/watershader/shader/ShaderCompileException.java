package net.watershader.shader;

/**
 * Thrown when loading, compiling, or linking a GLSL shader program fails,
 * carrying formatted stage/source diagnostics for logs.
 */
public final class ShaderCompileException extends RuntimeException {
    private final String shaderName;
    private final String stage;

    public ShaderCompileException(String shaderName, String stage, String message) {
        super("[" + shaderName + " / " + stage + "] " + message);
        this.shaderName = shaderName;
        this.stage = stage;
    }

    public ShaderCompileException(String shaderName, String stage, String message, Throwable cause) {
        super("[" + shaderName + " / " + stage + "] " + message, cause);
        this.shaderName = shaderName;
        this.stage = stage;
    }

    public String getShaderName() {
        return shaderName;
    }

    public String getStage() {
        return stage;
    }
}
