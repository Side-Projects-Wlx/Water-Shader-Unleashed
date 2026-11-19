package net.watershader.math;

/**
 * Pure OpenGL column-major 4x4 matrix and oblique near-plane math helpers.
 *
 * <p>All matrices follow OpenGL column-major layout ({@code m[col * 4 + row]}). Keeping math
 * completely decoupled from LWJGL/OpenGL allows thorough unit verification without a GL context.
 */
public final class ReflectionMath {
    private static final float MIN_ORTHO_PERSPECTIVE_CHECK = 1e-4F;
    private static final float MIN_POSITIVE_DOT = 1e-4F;

    private ReflectionMath() {}

    /**
     * Computes the inverse of an orthogonal rigid/reflection 4x4 matrix (upper 3x3 columns
     * orthonormal, last row {@code [0, 0, 0, 1]}), writing the column-major result into {@code out}.
     *
     * <p>The result stays in OpenGL column-major order so GLSL {@code uniform mat4} uploads should
     * pass {@code transpose = false}.
     */
    public static void inverseOrth(float[] out, float[] m) {
        if (out == null || out.length < 16 || m == null || m.length < 16) {
            throw new IllegalArgumentException("Matrices must have at least 16 floats");
        }
        // Transpose upper 3x3 (valid when upper 3x3 columns are orthonormal, including diag(1, -1, 1))
        out[0] = m[0];
        out[1] = m[4];
        out[2] = m[8];
        out[3] = 0.0F;

        out[4] = m[1];
        out[5] = m[5];
        out[6] = m[9];
        out[7] = 0.0F;

        out[8] = m[2];
        out[9] = m[6];
        out[10] = m[10];
        out[11] = 0.0F;

        float tx = m[12];
        float ty = m[13];
        float tz = m[14];
        out[12] = -(m[0] * tx + m[1] * ty + m[2] * tz);
        out[13] = -(m[4] * tx + m[5] * ty + m[6] * tz);
        out[14] = -(m[8] * tx + m[9] * ty + m[10] * tz);
        out[15] = 1.0F;
    }

    /**
     * Transforms a plane equation {@code p = (px, py, pz, pw)} by {@code (M^-1)^T} given
     * {@code invM = M^-1} in column-major order, yielding the plane in eye space.
     *
     * <p>Matches OpenGL's clip-plane transformation convention so {@code out . (M * v) == p . v}.
     */
    public static void transformPlaneTransposeInverse(float[] outPlane, float[] invM, float[] plane) {
        if (outPlane == null || outPlane.length < 4 || invM == null || invM.length < 16
                || plane == null || plane.length < 4) {
            throw new IllegalArgumentException("Invalid array lengths for plane transform");
        }
        float px = plane[0];
        float py = plane[1];
        float pz = plane[2];
        float pw = plane[3];
        // Column j of invM is row j of invM^T
        outPlane[0] = invM[0] * px + invM[1] * py + invM[2] * pz + invM[3] * pw;
        outPlane[1] = invM[4] * px + invM[5] * py + invM[6] * pz + invM[7] * pw;
        outPlane[2] = invM[8] * px + invM[9] * py + invM[10] * pz + invM[11] * pw;
        outPlane[3] = invM[12] * px + invM[13] * py + invM[14] * pz + invM[15] * pw;
    }

    /**
     * Checks whether a column-major 4x4 projection matrix is perspective (rather than orthographic).
     * Orthographic GUI/photo-mode matrices have {@code proj[11] == 0} and {@code proj[15] == 1}.
     */
    public static boolean isPerspectiveProjection(float[] proj) {
        return proj != null && proj.length >= 16 && Math.abs(proj[11]) > MIN_ORTHO_PERSPECTIVE_CHECK;
    }

    /**
     * Applies Eric Lengyel's oblique near-plane clipping modification in-place to an OpenGL
     * column-major perspective projection matrix.
     *
     * <p>Points satisfying {@code clipPlaneEye . v_eye > 0} remain inside {@code z_ndc > -1}, while
     * points behind the plane ({@code < 0}) are clipped by the near plane. Keeps the scale factor
     * positive even when the frustum looks completely behind the clip plane so the sign never flips.
     *
     * @return {@code true} if the projection matrix was modified; {@code false} if orthographic or
     * degenerate.
     */
    public static boolean modifyProjectionMatrix(float[] proj, float[] clipPlaneEye) {
        if (!isPerspectiveProjection(proj) || clipPlaneEye == null || clipPlaneEye.length < 4) {
            return false;
        }
        float p00 = proj[0];
        float p11 = proj[5];
        if (Math.abs(p00) < 1e-6F || Math.abs(p11) < 1e-6F || Math.abs(proj[14]) < 1e-6F) {
            return false;
        }

        // Far-plane frustum corner in clip space opposite to the plane normal
        float qxClip = signumNoZero(clipPlaneEye[0]);
        float qyClip = signumNoZero(clipPlaneEye[1]);

        // Solve P * q_eye = (qxClip, qyClip, 1, 1) for standard symmetric/asymmetric GL perspective
        // Note: column 2 is (proj[8], proj[9], proj[10], proj[11]), where standard proj[11] == -1
        float qx = (qxClip + proj[8] / proj[11]) / p00;
        float qy = (qyClip + proj[9] / proj[11]) / p11;
        float qz = 1.0F / proj[11];
        float qw = (1.0F - proj[10] / proj[11]) / proj[14];

        float dot = clipPlaneEye[0] * qx + clipPlaneEye[1] * qy
                + clipPlaneEye[2] * qz + clipPlaneEye[3] * qw;
        // Keep positive scale k > 0 so z_clip + w_clip = k * (N . v) never inverts clip side
        float scale = (dot > MIN_POSITIVE_DOT) ? (2.0F / dot) : 1.0F;

        // Replace row 2 of projection matrix: C' = scale * clipPlaneEye - row3(P)
        proj[2] = clipPlaneEye[0] * scale - proj[3];
        proj[6] = clipPlaneEye[1] * scale - proj[7];
        proj[10] = clipPlaneEye[2] * scale - proj[11];
        proj[14] = clipPlaneEye[3] * scale - proj[15];
        return true;
    }

    /**
     * Multiplies two column-major 4x4 matrices: {@code out = a * b}.
     */
    public static void multiply(float[] out, float[] a, float[] b) {
        for (int col = 0; col < 4; col++) {
            int c4 = col * 4;
            float b0 = b[c4];
            float b1 = b[c4 + 1];
            float b2 = b[c4 + 2];
            float b3 = b[c4 + 3];
            out[c4] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3;
            out[c4 + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3;
            out[c4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3;
            out[c4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3;
        }
    }

    /**
     * Multiplies a column-major 4x4 matrix by a 4-component vector: {@code out = m * v}.
     */
    public static void transformVec4(float[] out, float[] m, float[] v) {
        float vx = v[0];
        float vy = v[1];
        float vz = v[2];
        float vw = v[3];
        out[0] = m[0] * vx + m[4] * vy + m[8] * vz + m[12] * vw;
        out[1] = m[1] * vx + m[5] * vy + m[9] * vz + m[13] * vw;
        out[2] = m[2] * vx + m[6] * vy + m[10] * vz + m[14] * vw;
        out[3] = m[3] * vx + m[7] * vy + m[11] * vz + m[15] * vw;
    }

    /**
     * Inverts a general column-major 4x4 matrix {@code m} into {@code out}.
     *
     * @return {@code true} if invertible ({@code |det| > 1e-12}), {@code false} if singular.
     */
    public static boolean invert4x4(float[] out, float[] m) {
        if (out == null || out.length < 16 || m == null || m.length < 16) {
            throw new IllegalArgumentException("Matrices must have at least 16 floats");
        }
        double m00 = m[0], m01 = m[1], m02 = m[2], m03 = m[3];
        double m10 = m[4], m11 = m[5], m12 = m[6], m13 = m[7];
        double m20 = m[8], m21 = m[9], m22 = m[10], m23 = m[11];
        double m30 = m[12], m31 = m[13], m32 = m[14], m33 = m[15];

        double b00 = m00 * m11 - m01 * m10;
        double b01 = m00 * m12 - m02 * m10;
        double b02 = m00 * m13 - m03 * m10;
        double b03 = m01 * m12 - m02 * m11;
        double b04 = m01 * m13 - m03 * m11;
        double b05 = m02 * m13 - m03 * m12;
        double b06 = m20 * m31 - m21 * m30;
        double b07 = m20 * m32 - m22 * m30;
        double b08 = m20 * m33 - m23 * m30;
        double b09 = m21 * m32 - m22 * m31;
        double b10 = m21 * m33 - m23 * m31;
        double b11 = m22 * m33 - m23 * m32;

        double det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
        if (!Double.isFinite(det) || Math.abs(det) < 1e-12) {
            return false;
        }
        double invDet = 1.0 / det;
        out[0] = (float) ((m11 * b11 - m12 * b10 + m13 * b09) * invDet);
        out[1] = (float) ((m02 * b10 - m01 * b11 - m03 * b09) * invDet);
        out[2] = (float) ((m31 * b05 - m32 * b04 + m33 * b03) * invDet);
        out[3] = (float) ((m22 * b04 - m21 * b05 - m23 * b03) * invDet);
        out[4] = (float) ((m12 * b08 - m10 * b11 - m13 * b07) * invDet);
        out[5] = (float) ((m00 * b11 - m02 * b08 + m03 * b07) * invDet);
        out[6] = (float) ((m32 * b02 - m30 * b05 - m33 * b01) * invDet);
        out[7] = (float) ((m20 * b05 - m22 * b02 + m23 * b01) * invDet);
        out[8] = (float) ((m10 * b10 - m11 * b08 + m13 * b06) * invDet);
        out[9] = (float) ((m01 * b08 - m00 * b10 - m03 * b06) * invDet);
        out[10] = (float) ((m30 * b04 - m31 * b02 + m33 * b00) * invDet);
        out[11] = (float) ((m21 * b02 - m20 * b04 - m23 * b00) * invDet);
        out[12] = (float) ((m11 * b07 - m10 * b09 - m12 * b06) * invDet);
        out[13] = (float) ((m00 * b09 - m01 * b07 + m02 * b06) * invDet);
        out[14] = (float) ((m31 * b01 - m30 * b03 - m32 * b00) * invDet);
        out[15] = (float) ((m20 * b03 - m21 * b01 + m22 * b00) * invDet);
        return true;
    }

    /**
     * Computes {@code (proj * modelView)^-1} into {@code outInvVp} using caller-provided scratch
     * {@code tmpVp} (zero per-frame allocations).
     */
    public static boolean computeInvViewProj(float[] outInvVp, float[] proj, float[] modelView, float[] tmpVp) {
        multiply(tmpVp, proj, modelView);
        return invert4x4(outInvVp, tmpVp);
    }

    /**
     * Extracts {@code (zNear, zFar)} from an OpenGL column-major perspective projection matrix.
     *
     * @return {@code true} if valid positive finite near/far distances were extracted;
     * {@code false} otherwise (writing fallback {@code (0.05F, 512.0F)}).
     */
    public static boolean extractNearFar(float[] proj, float[] outNearFar) {
        if (outNearFar == null || outNearFar.length < 2) {
            throw new IllegalArgumentException("outNearFar must have at least 2 floats");
        }
        if (!isPerspectiveProjection(proj)) {
            outNearFar[0] = 0.05F;
            outNearFar[1] = 512.0F;
            return false;
        }
        float p10 = proj[10];
        float p14 = proj[14];
        float denomNear = p10 - 1.0F;
        float denomFar = p10 + 1.0F;
        if (Math.abs(denomNear) < 1e-6F || Math.abs(denomFar) < 1e-6F) {
            outNearFar[0] = 0.05F;
            outNearFar[1] = 512.0F;
            return false;
        }
        float zNear = p14 / denomNear;
        float zFar = p14 / denomFar;
        if (!Float.isFinite(zNear) || !Float.isFinite(zFar) || zNear <= 1e-4F || zFar <= zNear) {
            outNearFar[0] = 0.05F;
            outNearFar[1] = 512.0F;
            return false;
        }
        outNearFar[0] = zNear;
        outNearFar[1] = zFar;
        return true;
    }

    private static float signumNoZero(float value) {
        if (value > 0.0F) {
            return 1.0F;
        }
        if (value < 0.0F) {
            return -1.0F;
        }
        return 0.0F;
    }
}
