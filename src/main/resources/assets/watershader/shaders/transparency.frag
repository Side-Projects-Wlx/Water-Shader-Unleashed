//for water transparency modification

uniform sampler2D colorMap;
uniform float water_transparency;
uniform float waterfall_transparency;
uniform vec3 waterfall_color;
uniform vec3 water_color;
uniform float waterfall_fog;
uniform float setting_v3;
uniform float env_sky_light;
uniform float still_refl_enabled;
uniform float modern_cave_water;
varying vec4 texcoord;
varying vec4 vert_color;
varying float is_vertical;
varying float sky_exposure;
varying float fogFactor;

void main()
{
    vec4 color = texture2D(colorMap, texcoord.rg);
    vec4 out_color = vec4(1.0);
    float alpha = color.a;

    if (is_vertical <= 0.1) { // required to render ice properly
        float stillW = clamp(-is_vertical, 0.0, 1.0);
        stillW = stillW * stillW * (3.0 - 2.0 * stillW);

        // Preserve baseline clear calm water outdoors (default water_transparency 0.1 -> 0.0 effective alpha
        // without the hardware GL_ALPHA_TEST > 0.1 cliff), while smoothly ramping to a calm translucent surface
        // in caves/enclosed/non-reflective spaces so static water remains clearly visible and matches flowing water.
        float openStillAlpha = max(0.0, water_transparency - 0.1);
        float effectiveReflSky = (still_refl_enabled > 0.5) ? clamp(sky_exposure, 0.0, 1.0) : 0.0;
        float caveMask = 1.0 - smoothstep(0.05, 0.75, effectiveReflSky);
        float caveStillAlpha = max(openStillAlpha, clamp(waterfall_transparency * 0.95, 0.30, 0.48));
        float stillAlpha = mix(openStillAlpha, caveStillAlpha, caveMask);
        alpha = mix(waterfall_transparency, stillAlpha, stillW);

        if (setting_v3 >= 1.0) {
            float v3Weight = clamp(sky_exposure * env_sky_light, 0.0, 1.0);
            vec3 lifted = (2.0 * color.r + 2.0 * color.g + 2.0 * color.b) / 4.5 + color.rgb * 0.8;
            color.rgb = mix(color.rgb, lifted, v3Weight);
        }

        vec3 baseRgb = vert_color.rgb * color.rgb;
        // Shift outdoor flowing water away from cobalt-purple toward the still water's teal-aqua palette
        float lum = dot(baseRgb, vec3(0.299, 0.587, 0.114));
        vec3 tealHarmonized = mix(baseRgb, lum * (vec3(0.42, 0.62, 0.58) + 0.45 * water_color), 0.65);

        float modernCaveMask = caveMask * clamp(modern_cave_water, 0.0, 1.0);
        // Replace the blocky 16x16 vanilla water texture on cave/enclosed static water with a smooth,
        // pixel-free vertex-lit aqua surface when modern cave water is enabled, matching cave flowing water.
        vec3 smoothCaveRgb = vert_color.rgb * (vec3(0.11, 0.17, 0.25) + 0.34 * water_color);
        float texLum = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        float flowDetail = clamp(mix(1.0, texLum / 0.33, 0.55), 0.72, 1.30);
        vec3 caveFlowRgb = smoothCaveRgb * flowDetail;
        vec3 tintedFlow = mix(tealHarmonized, caveFlowRgb, modernCaveMask) * waterfall_color;
        vec3 stillRgb = mix(tealHarmonized, smoothCaveRgb, modernCaveMask);

        vec3 waterRgb = mix(tintedFlow, stillRgb, stillW);
        float surfaceFogWeight = max(1.0 - stillW, caveMask) * clamp(waterfall_fog, 0.0, 1.0);
        waterRgb = mix(waterRgb, mix(gl_Fog.color.rgb, waterRgb, fogFactor), surfaceFogWeight);
        out_color = vec4(waterRgb, alpha);
    } else {
        if (alpha <= 0.1) {
            discard;
        }
        out_color = vec4(vert_color.r * color.r, vert_color.g * color.g, vert_color.b * color.b, alpha);
    }

    gl_FragColor = out_color;
}
