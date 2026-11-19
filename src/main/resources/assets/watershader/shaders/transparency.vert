//for water transparency modification

varying vec4 texcoord;
varying vec4 vert_color;
varying float is_vertical;
varying float sky_exposure;
varying float fogFactor;

void main()
{
    texcoord = gl_MultiTexCoord0;
    vert_color = gl_Color;
    is_vertical = gl_Normal.y;
    sky_exposure = (gl_Normal.y > 0.5) ? 0.0 : clamp(gl_Normal.x, 0.0, 1.0);

    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;

    fogFactor = (gl_Fog.end - gl_Position.z) / max(0.001, gl_Fog.end - gl_Fog.start);
    fogFactor = clamp(fogFactor, 0.0, 1.0);
}
