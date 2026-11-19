uniform sampler2D colorMap;
varying vec4 texcoord;

void main()
{
    vec4 color = texture2D(colorMap, texcoord.rg);

    if (color.a < 0.6) discard;

    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
}
