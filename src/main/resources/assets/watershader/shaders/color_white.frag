varying vec4 position;
varying float sky_exposure;
varying float raw_still;
varying float dist_val;
varying float is_water_vert;

void main()
{
    float x = 1.0 - abs(mod(position.x, 16.0) * 0.125 - 1.0);
    float z = 1.0 - abs(mod(position.z, 16.0) * 0.125 - 1.0);
    float visible = 0.0;
    if (is_water_vert > 0.5) {
        float s = clamp(raw_still, 0.0, 1.0);
        float stillWeight = s * s * (3.0 - 2.0 * s);
        visible = mix(0.1, dist_val, stillWeight);
    }

    gl_FragColor = vec4(visible, x, z, clamp(sky_exposure, 0.0, 1.0));
}
