varying vec4 position;
varying float sky_exposure;
varying float raw_still;
varying float dist_val;
varying float is_water_vert;
uniform mat4 inverse_view;

void main()
{
    if (gl_Normal.y > 0.5) {
        // Non-water Layer 1 geometry (ice, portals, excluded blocks)
        position = vec4(gl_Vertex.x, 0.0, gl_Vertex.z, 1.0);
        sky_exposure = 0.0;
        raw_still = 0.0;
        dist_val = 0.0;
        is_water_vert = 0.0;
    } else {
        vec4 worldRel = inverse_view * gl_ModelViewMatrix * gl_Vertex;
        float root = sqrt(worldRel.x * worldRel.x + worldRel.z * worldRel.z);
        dist_val = 0.1 + sqrt(sqrt(root)) / 2.5;

        float reflAllowed = 1.0 - clamp(gl_Normal.z, 0.0, 1.0);
        raw_still = clamp(-gl_Normal.y, 0.0, 1.0) * reflAllowed;
        is_water_vert = 1.0;

        position = vec4(gl_Vertex.x, 0.0, gl_Vertex.z, 1.0);
        sky_exposure = clamp(gl_Normal.x, 0.0, 1.0);
    }

    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
}
