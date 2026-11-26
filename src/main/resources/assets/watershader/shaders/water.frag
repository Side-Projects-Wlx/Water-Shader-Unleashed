//#version 120

uniform float timer;
uniform sampler2D colorMap;
uniform sampler2D reflectedColorMap;
uniform sampler2D stencilMap;
uniform sampler2D waterDepthMap;
uniform sampler2D handDepthMap;
uniform sampler2D weatherMap;
uniform sampler2D seabedDepthMap;
uniform vec3 water_color;
uniform vec2 texelSize;
uniform float ripple_strength;
uniform float fresnel_strength;
uniform float z_near;
uniform float z_far;
uniform float refl_brightness;
uniform float rain_strength;
uniform float rain_blur_strength;
uniform mat4 inv_view_proj;
uniform vec2 rain_cam_xz;
uniform float weather_active;
uniform float camera_in_water;
uniform float is_underwater;
uniform float uw_brightness;
uniform float env_sky_ambient;
uniform float water_depth_valid;
uniform float seabed_depth_valid;
uniform float depth_absorption_enabled;
uniform float depth_absorption_strength;
uniform float horizon_fog_enabled;
uniform vec3 fog_color;
uniform float fog_start;
uniform float fog_end;
uniform float modern_cave_water;
varying vec4 texcoord;

float sampleWaterStencilR(vec2 uv, float fallbackR)
{
    vec4 s = texture2D(stencilMap, clamp(uv, 0.001, 0.999));
    return (s.r > 0.05) ? s.r : fallbackR;
}

vec3 sampleWaterColor(vec2 uv, vec3 fallbackRgb)
{
    vec2 cuv = clamp(uv, 0.001, 0.999);
    vec4 s = texture2D(stencilMap, cuv);
    return (s.r > 0.05) ? texture2D(colorMap, cuv).rgb : fallbackRgb;
}

vec4 sampleReflectionColor(vec2 uv, vec2 fallbackUv)
{
    vec2 cuv = clamp(uv, 0.001, 0.999);
    if (texture2D(stencilMap, cuv).r > 0.05) {
        return texture2D(reflectedColorMap, cuv);
    }
    vec2 horizUv = vec2(cuv.x, fallbackUv.y);
    if (texture2D(stencilMap, horizUv).r > 0.05) {
        return texture2D(reflectedColorMap, horizUv);
    }
    return texture2D(reflectedColorMap, fallbackUv);
}

float sampleWaterSkyExposure(vec2 uv, float centerR, float centerA)
{
    if (centerR > 0.45) {
        return clamp(centerA, 0.0, 1.0);
    }
    vec2 stepUv = texelSize * 2.5;
    float sumA = 0.0;
    float weight = 0.0;
    vec4 s0 = texture2D(stencilMap, clamp(uv + vec2( stepUv.x, 0.0), 0.001, 0.999));
    if (s0.r > 0.35) { sumA += s0.a; weight += 1.0; }
    vec4 s1 = texture2D(stencilMap, clamp(uv + vec2(-stepUv.x, 0.0), 0.001, 0.999));
    if (s1.r > 0.35) { sumA += s1.a; weight += 1.0; }
    vec4 s2 = texture2D(stencilMap, clamp(uv + vec2(0.0,  stepUv.y), 0.001, 0.999));
    if (s2.r > 0.35) { sumA += s2.a; weight += 1.0; }
    vec4 s3 = texture2D(stencilMap, clamp(uv + vec2(0.0, -stepUv.y), 0.001, 0.999));
    if (s3.r > 0.35) { sumA += s3.a; weight += 1.0; }
    return (weight > 0.5) ? clamp(sumA / weight, 0.0, 1.0) : clamp(centerA, 0.0, 1.0);
}

float linearizeDepth(float d, float n, float f)
{
    float zNdc = clamp(d, 0.0, 1.0) * 2.0 - 1.0;
    return (2.0 * n * f) / max(0.0001, f + n - zNdc * (f - n));
}

vec2 rainHash2(vec2 p)
{
    vec2 q = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(q) * 43758.5453);
}

vec3 evalRainRippleLayer(vec2 scaledXZ, float timerVal, float intFreq, float seedOffset, float spawnProb)
{
    vec2 cell = mod(floor(scaledXZ), 64.0);
    vec2 local = fract(scaledXZ);
    vec2 hA = rainHash2(cell + seedOffset);
    vec2 hB = rainHash2(cell + seedOffset + vec2(19.7, 43.1));

    if (hA.x > spawnProb) {
        return vec3(0.0, 0.0, 0.0);
    }

    float duty = fract(timerVal * intFreq + hA.y);
    float activeWindow = 0.48;
    if (duty >= activeWindow) {
        return vec3(0.0, 0.0, 0.0);
    }
    float age = duty / activeWindow;

    vec2 center = vec2(0.24 + 0.52 * hB.x, 0.24 + 0.52 * hB.y);
    float maxRad = 0.09 + 0.13 * fract(hB.x * 7.13 + hB.y * 11.7);
    float radius = 0.01 + maxRad * age;

    vec2 delta = local - center;
    float dist = length(delta);
    float rd = dist - radius;
    float strength = 0.65 + 0.35 * fract(hA.y * 13.3 + hB.x * 5.7);
    float ringBand = exp(-(rd * rd) * 520.0) * (1.0 - age) * smoothstep(0.0, 0.08, age);
    float impactPulse = exp(-(dist * dist) * 1600.0) * (1.0 - smoothstep(0.0, 0.24, age));
    float rippleIntensity = clamp((ringBand + 0.70 * impactPulse) * strength, 0.0, 1.0);
    vec2 dir = (dist > 0.0005) ? (delta / dist) : vec2(0.0, 0.0);
    return vec3(dir * ringBand * strength, rippleIntensity);
}

void main ()
{
    vec2 baseUv = texcoord.rg;
    float handDepth = texture2D(handDepthMap, texcoord.rg).r;
    bool isHandPixel = handDepth < 0.9999;
    if (is_underwater > 0.005 && !isHandPixel) {
        vec2 uwOffset = vec2(
            sin(texcoord.y * 20.0 + timer * 6.2831853 * 2.0) * 0.0045 + sin(texcoord.y * 41.0 - timer * 6.2831853 * 3.0) * 0.0018,
            cos(texcoord.x * 16.0 + timer * 6.2831853 * 2.0) * 0.0035 + cos(texcoord.x * 33.0 + timer * 6.2831853 * 3.0) * 0.0015
        ) * is_underwater;
        vec2 candidateUv = clamp(texcoord.rg + uwOffset, 0.002, 0.998);
        if (texture2D(handDepthMap, candidateUv).r >= 0.9999) {
            baseUv = candidateUv;
        }
    }

    float in_water;
    vec4 color;
    vec4 stencil = texture2D(stencilMap, baseUv);

    if (stencil.r > 0.05) in_water = 1.0;
    else in_water = 0.0;

    // Water-masked screen-space smoothing of stencil.r to round off block/triangle transitions
    // between flowing water (0.1) and still water (> 0.5) without bleeding across dry land edges.
    float eff_r = stencil.r;
    if (in_water > 0.5) {
        vec2 step1 = texelSize * 3.0;
        vec2 step2 = texelSize * 7.0;
        vec2 step3 = texelSize * 13.0;
        float sumR = stencil.r * 2.0;
        sumR += sampleWaterStencilR(baseUv + vec2( step1.x, 0.0), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2(-step1.x, 0.0), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2(0.0,  step1.y), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2(0.0, -step1.y), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2( step2.x,  step2.y), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2(-step2.x,  step2.y), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2( step2.x, -step2.y), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2(-step2.x, -step2.y), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2( step2.x, 0.0), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2(-step2.x, 0.0), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2(0.0,  step2.y), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2(0.0, -step2.y), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2( step3.x, 0.0), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2(-step3.x, 0.0), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2(0.0,  step3.y), stencil.r);
        sumR += sampleWaterStencilR(baseUv + vec2(0.0, -step3.y), stencil.r);
        eff_r = sumR / 18.0;
    }

    float skyExposure = (in_water > 0.5)
        ? sampleWaterSkyExposure(baseUv, stencil.r, stencil.a)
        : 0.0;

    // Unified surface depth & camera-relative world position reconstruction (shared by rain ripples,
    // depth absorption, and horizon fog fade).
    float surfaceDepth = 1.0;
    float surfaceDist = z_far;
    bool hasSurfaceWorldPos = false;
    vec3 surfaceWorldRel = vec3(0.0, 0.0, 0.0);
    float surfaceDistXZ = 0.0;
    if (in_water > 0.5 && water_depth_valid > 0.5) {
        surfaceDepth = texture2D(waterDepthMap, baseUv).r;
        if (surfaceDepth < 0.9999) {
            surfaceDist = linearizeDepth(surfaceDepth, z_near, z_far);
            vec4 ndcPos = vec4(baseUv * 2.0 - 1.0, surfaceDepth * 2.0 - 1.0, 1.0);
            vec4 wRel = inv_view_proj * ndcPos;
            if (abs(wRel.w) > 0.0001) {
                surfaceWorldRel = wRel.xyz / wRel.w;
                surfaceDistXZ = length(surfaceWorldRel.xz);
                hasSurfaceWorldPos = true;
            }
        }
    }

    // Distance horizon fog factor and un-distorted shoreline contact feather
    float horizonFogT = 0.0;
    if (in_water > 0.5 && horizon_fog_enabled > 0.5 && camera_in_water < 0.5 && hasSurfaceWorldPos) {
        horizonFogT = clamp((surfaceDist - fog_start) / max(0.001, fog_end - fog_start), 0.0, 1.0);
    }

    float shoreFade = 1.0;
    if (in_water > 0.5 && depth_absorption_enabled > 0.5 && seabed_depth_valid > 0.5
        && camera_in_water < 0.5 && hasSurfaceWorldPos) {
        float baseSeabedRaw = texture2D(seabedDepthMap, baseUv).r;
        float baseSeabedDist = (baseSeabedRaw < 0.9999)
            ? linearizeDepth(baseSeabedRaw, z_near, z_far)
            : (surfaceDist + 12.0);
        float shoreThickness = max(0.0, baseSeabedDist - surfaceDist);
        shoreFade = smoothstep(0.0, 0.65, shoreThickness);
    }

    // Damp ripple distortion softly at shallow shorelines and into distant horizon mist
    float effRippleStrength = ripple_strength * mix(0.45, 1.0, shoreFade) * (1.0 - 0.65 * horizonFogT);

    // Distortion begin (Checkpoint B 2-layer sine waves, smooth crossfade into flowing waves)
    float used_timer = timer;
    float time_scale = 2.0;
    float stillSize1 = 1.6 * 6.3 * max(eff_r, 0.15);
    float flowWaveBlend = 1.0 - smoothstep(0.11, 0.28, eff_r);

    float x_wave_w = smoothstep(0.25, 0.55, eff_r) * effRippleStrength;
    float z_wave_w = effRippleStrength;

    float wave1X = sin(2.0 * time_scale * 3.14159 * used_timer - sin(0.5 * stillSize1 * 3.14159 * stencil.g) + (stillSize1 * 3.14159 * stencil.g));
    float wave1Z = sin(sin(time_scale * 3.14159 * used_timer) + 1.5 * sin(0.8 * stillSize1 * 3.14159 * stencil.b));
    if (flowWaveBlend > 0.001) {
        float flowSize1 = 1.6 * 6.3 * 6.0;
        float flowTime1 = 2.0 * 1.5;
        float wave1X_flow = sin(2.0 * flowTime1 * 3.14159 * used_timer - sin(0.5 * flowSize1 * 3.14159 * stencil.g) + (flowSize1 * 3.14159 * stencil.g));
        float wave1Z_flow = sin(sin(flowTime1 * 3.14159 * used_timer) + 1.5 * sin(0.8 * flowSize1 * 3.14159 * stencil.b));
        wave1X = mix(wave1X, wave1X_flow, flowWaveBlend);
        wave1Z = mix(wave1Z, wave1Z_flow, flowWaveBlend);
    }
    float x_scale = 1.0 + x_wave_w * (wave1X / 100.0);
    float z_scale = 1.0 + z_wave_w * (wave1Z / 150.0);
    vec2 disturbed = clamp(vec2(x_scale * baseUv.x, z_scale * baseUv.y), 0.001, 0.999);

    vec4 reflection = (in_water > 0.5) ? sampleReflectionColor(disturbed.rg, baseUv) : vec4(0.0);

    time_scale = 3.0;
    float size_scale = 2.4 * 6.3 * eff_r;

    float wave2X = sin(2.0 * time_scale * 3.14159 * used_timer - sin(0.25 * size_scale * 3.14159 * stencil.g) + size_scale * 3.14159 * stencil.g);
    float wave2Z = sin(sin(time_scale * 3.14159 * used_timer) + 1.5 * sin(size_scale * 3.14159 * stencil.b));
    x_scale = 1.0 + x_wave_w * (wave2X / 100.0);
    z_scale = 1.0 + z_wave_w * (wave2Z / 100.0);
    vec2 disturbed_2 = clamp(vec2(x_scale * baseUv.x, z_scale * baseUv.y), 0.001, 0.999);
    // Distortion end

    if (in_water > 0.5) {
        vec4 reflection_2 = sampleReflectionColor(disturbed_2.rg, baseUv);
        reflection = (reflection + reflection_2) / 2.0;
    }

    // 6.2 Rain-aware blurry reflection & organic non-repeating surface ripples (open-sky still water only)
    float stillRainWeight = in_water * (1.0 - camera_in_water) * smoothstep(0.18, 0.58, eff_r) * skyExposure;
    float rainBlurMask = stillRainWeight * rain_blur_strength;
    vec2 rippleShift = vec2(0.0, 0.0);
    float rippleHighlight = 0.0;
    if (rainBlurMask > 0.005) {
        if (rain_strength > 0.005 && hasSurfaceWorldPos) {
            float distFade = smoothstep(30.0, 6.0, surfaceDistXZ);
            if (distFade > 0.01 && surfaceDistXZ > 0.05) {
                vec2 wXZ = rain_cam_xz + surfaceWorldRel.xz;
                vec2 rotA = vec2(0.8 * wXZ.x - 0.6 * wXZ.y, 0.6 * wXZ.x + 0.8 * wXZ.y);
                vec2 rotB = vec2(0.8 * wXZ.x + 0.6 * wXZ.y, -0.6 * wXZ.x + 0.8 * wXZ.y);
                vec3 r1 = evalRainRippleLayer(wXZ * 0.80, used_timer, 9.0, 11.3, 0.34);
                vec3 r2 = evalRainRippleLayer(rotA * 0.50, used_timer, 6.0, 37.9, 0.36);
                vec3 r3 = evalRainRippleLayer(rotB * 1.00, used_timer, 12.0, 73.1, 0.30);
                vec2 worldDir = r1.xy
                    + vec2(0.8 * r2.x + 0.6 * r2.y, -0.6 * r2.x + 0.8 * r2.y) * 0.90
                    + vec2(0.8 * r3.x - 0.6 * r3.y, 0.6 * r3.x + 0.8 * r3.y) * 0.75;
                vec2 fwd = surfaceWorldRel.xz / surfaceDistXZ;
                vec2 right = vec2(-fwd.y, fwd.x);
                vec2 screenDir = vec2(dot(worldDir, right) * 0.70, -dot(worldDir, fwd));
                float activeMask = stillRainWeight * rain_strength * distFade;
                rippleShift = screenDir * (0.011 * activeMask * effRippleStrength);
                rippleHighlight = clamp(r1.z + 0.85 * r2.z + 0.70 * r3.z, 0.0, 1.0) * activeMask;
            }
        }

        float pG = 6.2831853 * stencil.g;
        float pB = 6.2831853 * stencil.b;
        float tR = 6.2831853 * used_timer;
        vec2 rainJitter = vec2(
            cos(pG * 3.0) * sin(tR * 10.0) + 0.55 * cos(pG * 5.0) * cos(tR * 14.0),
            cos(pB * 3.0) * cos(tR * 11.0) + 0.55 * cos(pB * 5.0) * sin(tR * 15.0)
        ) * (0.0018 * rainBlurMask * effRippleStrength);

        vec2 blurBase = clamp(0.5 * (disturbed.rg + disturbed_2.rg) + rainJitter + rippleShift, 0.002, 0.998);
        vec2 stepA = texelSize * vec2(8.0, 11.0) * rainBlurMask;
        vec2 stepB = texelSize * vec2(17.0, 23.0) * rainBlurMask;
        vec2 stepC = texelSize * vec2(20.0, 26.0) * rainBlurMask;

        vec4 reflAccum = sampleReflectionColor(blurBase, baseUv) * 2.0;
        reflAccum += sampleReflectionColor(blurBase + vec2( stepA.x,  stepA.y), baseUv) * 1.4;
        reflAccum += sampleReflectionColor(blurBase + vec2(-stepA.x,  stepA.y), baseUv) * 1.4;
        reflAccum += sampleReflectionColor(blurBase + vec2( stepA.x, -stepA.y), baseUv) * 1.4;
        reflAccum += sampleReflectionColor(blurBase + vec2(-stepA.x, -stepA.y), baseUv) * 1.4;

        reflAccum += sampleReflectionColor(blurBase + vec2( stepB.x, 0.0), baseUv) * 1.0;
        reflAccum += sampleReflectionColor(blurBase + vec2(-stepB.x, 0.0), baseUv) * 1.0;
        reflAccum += sampleReflectionColor(blurBase + vec2(0.0,  stepB.y), baseUv) * 1.0;
        reflAccum += sampleReflectionColor(blurBase + vec2(0.0, -stepB.y), baseUv) * 1.0;

        reflAccum += sampleReflectionColor(blurBase + vec2( stepC.x,  stepC.y), baseUv) * 0.65;
        reflAccum += sampleReflectionColor(blurBase + vec2(-stepC.x,  stepC.y), baseUv) * 0.65;
        reflAccum += sampleReflectionColor(blurBase + vec2( stepC.x, -stepC.y), baseUv) * 0.65;
        reflAccum += sampleReflectionColor(blurBase + vec2(-stepC.x, -stepC.y), baseUv) * 0.65;

        reflection = mix(reflection, reflAccum / 14.2, 0.92 * rainBlurMask);
    }

    // 'refraction' (for all under-water, guarded against sampling foreground dry pillars/entities)
    vec2 sampleUv = baseUv;
    if (in_water > 0.05) {
        float look_up_range = 0.008;
        vec2 refrUv = clamp(disturbed.rg + rippleShift * 0.55, 0.002, 0.998);
        bool depthGuard = (seabed_depth_valid > 0.5 && camera_in_water < 0.5 && hasSurfaceWorldPos);
        if (texture2D(stencilMap, vec2(refrUv.x + look_up_range, refrUv.y + look_up_range)).r > 0.001 &&
            texture2D(stencilMap, vec2(refrUv.x - look_up_range, refrUv.y - look_up_range)).r > 0.001 &&
            texture2D(stencilMap, refrUv).r > 0.001 &&
            (!depthGuard || texture2D(seabedDepthMap, refrUv).r >= surfaceDepth - 0.0005)) {
            sampleUv = refrUv;
        } else if (texture2D(stencilMap, vec2(disturbed.r + look_up_range, disturbed.g + look_up_range)).r > 0.001 &&
            texture2D(stencilMap, vec2(disturbed.r - look_up_range, disturbed.g - look_up_range)).r > 0.001 &&
            texture2D(stencilMap, vec2(disturbed.r, disturbed.g)).r > 0.001 &&
            (!depthGuard || texture2D(seabedDepthMap, disturbed.rg).r >= surfaceDepth - 0.0005)) {
            sampleUv = disturbed.rg;
        }
        color = texture2D(colorMap, sampleUv);

        // Feather Pass 0 colorMap across the flowing-to-still splash transition zone
        float transW = 1.0 - smoothstep(0.12, 0.56, eff_r);
        if (transW > 0.01) {
            vec2 cStep1 = texelSize * 3.5;
            vec2 cStep2 = texelSize * 7.0;
            vec3 sumC = color.rgb * 2.0;
            sumC += sampleWaterColor(sampleUv + vec2( cStep1.x, 0.0), color.rgb);
            sumC += sampleWaterColor(sampleUv + vec2(-cStep1.x, 0.0), color.rgb);
            sumC += sampleWaterColor(sampleUv + vec2(0.0,  cStep1.y), color.rgb);
            sumC += sampleWaterColor(sampleUv + vec2(0.0, -cStep1.y), color.rgb);
            sumC += sampleWaterColor(sampleUv + vec2( cStep2.x,  cStep2.y), color.rgb);
            sumC += sampleWaterColor(sampleUv + vec2(-cStep2.x,  cStep2.y), color.rgb);
            sumC += sampleWaterColor(sampleUv + vec2( cStep2.x, -cStep2.y), color.rgb);
            sumC += sampleWaterColor(sampleUv + vec2(-cStep2.x, -cStep2.y), color.rgb);
            vec3 blurredColor = sumC / 10.0;
            color.rgb = mix(color.rgb, blurredColor, transW * 0.75);
        }
    } else {
        color = texture2D(colorMap, baseUv);
    }

    float flow_factor = in_water * (1.0 - smoothstep(0.10, 0.50, eff_r));
    float still_factor = in_water * smoothstep(0.10, 0.50, eff_r);

    // True depth-based Beer-Lambert optical absorption (applied to the refracted seabed before
    // surface reflection so Fresnel reflections sit cleanly on top; strictly lit, zero night/cave glow).
    if (in_water > 0.5 && camera_in_water < 0.5 && seabed_depth_valid > 0.5
        && depth_absorption_enabled > 0.5 && depth_absorption_strength > 0.005 && hasSurfaceWorldPos) {
        float refrSeabedRaw = texture2D(seabedDepthMap, sampleUv).r;
        float refrSeabedDist = (refrSeabedRaw < 0.9999)
            ? linearizeDepth(refrSeabedRaw, z_near, z_far)
            : (surfaceDist + 12.0);
        float waterThickness = clamp(refrSeabedDist - surfaceDist, 0.0, 24.0);
        float transmittance = exp(-waterThickness * 0.24 * clamp(depth_absorption_strength, 0.0, 3.0));
        float depthFactor = 1.0 - transmittance;
        float seabedLum = (color.r + color.g + color.b) / 3.0;
        float deepLit = mix(max(seabedLum * 0.85, 0.015), max(env_sky_ambient * 0.55, seabedLum * 0.92), skyExposure);
        vec3 deepWaterTarget = (vec3(0.04, 0.17, 0.23) + 0.65 * water_color) * deepLit;
        float absMix = clamp(depthFactor * 0.72 * clamp(depth_absorption_strength, 0.0, 1.5) * still_factor, 0.0, 0.78);
        color.rgb = mix(color.rgb, deepWaterTarget, absMix);
    }

    // Combine reflection and scene at water surfaces (Checkpoint B formula + rain/cave/shore scaling)
    float disable_refl = max(0.0, eff_r - 0.1);
    float envReflScale = skyExposure * refl_brightness * (1.0 - 0.85 * camera_in_water)
                       * mix(0.45, 1.0, shoreFade);
    float reflection_strength = 0.5 * disable_refl * fresnel_strength * envReflScale;

    vec3 reflection_color = reflection_strength * disable_refl * reflection.rgb;

    float difference = (reflection_color.r + reflection_color.g + reflection_color.b) / 3.0 - (color.r + color.g + color.b) / 5.5;
    float warm_sun = clamp((reflection.r - reflection.b) * 1.6, 0.0, 1.0);
    float diffScale = (depth_absorption_enabled > 0.5 && seabed_depth_valid > 0.5) ? 0.38 : 1.0;
    difference = clamp(difference, 0.0, 0.22) * (1.0 - 0.75 * warm_sun) * skyExposure * diffScale;
    vec3 regular_color = color.rgb * (1.0 - in_water * reflection_strength) + (in_water * (difference * water_color));

    // Grounded color-palette harmonization for flowing water and non-reflective cave/indoor still water
    float effectiveReflSky = (refl_brightness > 0.01) ? skyExposure : 0.0;
    float caveMask = 1.0 - smoothstep(0.05, 0.75, effectiveReflSky);
    float caveStillWeight = still_factor * caveMask;
    float tint_factor = flow_factor + caveStillWeight;
    float scene_lum = (color.r + color.g + color.b) / 3.0;
    float flowGlowGate = clamp(skyExposure + 0.35 * smoothstep(0.08, 0.45, scene_lum), 0.0, 1.0);
    regular_color = regular_color * (1.0 - 0.16 * tint_factor) + (0.20 * tint_factor * scene_lum * flowGlowGate) * water_color;

    // Smooth modern glassy glaze & C1-continuous multi-octave ripples on cave/indoor water
    float caveWaterWeight = in_water * caveMask * clamp(modern_cave_water, 0.0, 1.0);
    if (caveWaterWeight > 0.005) {
        float fresnelAngle = clamp(disable_refl * 1.15, 0.0, 1.0);
        float fresnelCurve = fresnelAngle * fresnelAngle * (3.0 - 2.0 * fresnelAngle);
        float fresnelW = clamp(fresnel_strength, 0.0, 1.5);

        // Smooth glassy Fresnel absorption/glaze anchored strictly to scene_lum (zero cave glow in darkness);
        // base 0.14 glaze matches cave flowing water cleanly into cave static pools.
        vec3 caveGlazeColor = scene_lum * (vec3(0.36, 0.62, 0.68) + 0.55 * water_color);
        float glazeStrength = (0.14 * caveWaterWeight + 0.24 * fresnelCurve * caveStillWeight * clamp(modern_cave_water, 0.0, 1.0)) * fresnelW;
        regular_color = mix(regular_color, caveGlazeColor, clamp(glazeStrength, 0.0, 0.55));

        if (caveStillWeight > 0.005) {
            float gx = stencil.g * 3.14159265;
            float bz = stencil.b * 3.14159265;
            float wt = used_timer * 6.2831853;
            float sw1 = cos(gx * 4.0) * cos(bz * 3.0) * cos(wt * 2.0)
                      + cos(gx * 3.0) * cos(bz * 4.0) * sin(wt * 2.0);
            float sw2 = cos(gx * 8.0) * cos(bz * 7.0) * cos(wt * 3.0 + 1.1)
                      + cos(gx * 7.0) * cos(bz * 8.0) * sin(wt * 3.0 + 0.7);
            float sw3 = cos(gx * 14.0) * cos(bz * 12.0) * cos(wt * 4.0 + 2.3)
                      + cos(gx * 12.0) * cos(bz * 14.0) * sin(wt * 4.0 + 1.9);
            float waveHeight = clamp(0.50 * sw1 + 0.33 * sw2 + 0.17 * sw3, -1.0, 1.0);

            float waveNorm = 0.5 * waveHeight + 0.5;
            float crest = smoothstep(0.18, 0.88, waveNorm);
            float trough = 1.0 - smoothstep(0.10, 0.58, waveNorm);
            float specRaw = smoothstep(0.45, 0.98, waveNorm);
            float specular = specRaw * specRaw;

            float caveLightGate = smoothstep(0.05, 0.35, scene_lum);
            float rippleW = clamp(ripple_strength, 0.0, 2.0);

            // Gentle trough depth and silky animated liquid sheen across wave crests
            regular_color *= (1.0 - 0.06 * trough * caveStillWeight * rippleW);
            vec3 caveSheenTint = mix(vec3(0.55, 0.82, 0.86), vec3(0.32, 0.44, 0.46) + water_color, 0.45);
            float sheenAmount = (0.06 * crest + 0.15 * specular) * scene_lum * caveLightGate
                              * (0.45 + 0.55 * fresnelCurve) * caveStillWeight * fresnelW * rippleW;
            regular_color += caveSheenTint * sheenAmount;
        }
    }

    vec4 out_color = vec4(regular_color, 1.0) + vec4(reflection_color, 1.0);
    if (in_water > 0.5 && rippleHighlight > 0.002) {
        vec3 rippleTint = mix(vec3(0.55, 0.72, 0.80), reflection.rgb, 0.48);
        out_color.rgb += rippleTint * (rippleHighlight * 0.068 * skyExposure);
    }

    // Seamless horizon fog fade on distant still water & reflections
    if (in_water > 0.5 && horizonFogT > 0.002 && !isHandPixel) {
        out_color.rgb = mix(out_color.rgb, fog_color, horizonFogT * still_factor);
    }

    // 6.2/6.8 Isolated weather composite (un-refracted over water, occluded behind held hand)
    if (weather_active > 0.5 && !isHandPixel) {
        vec4 wCol = texture2D(weatherMap, baseUv);
        out_color.rgb = out_color.rgb * (1.0 - wCol.a) + wCol.rgb;
    }

    // 6.4 Underwater distance-aware water_color tint
    if (is_underwater > 0.005) {
        float uwDist = 0.3;
        if (!isHandPixel) {
            float uwRaw = texture2D(waterDepthMap, baseUv).r;
            uwDist = clamp(linearizeDepth(uwRaw, z_near, z_far), 0.0, 48.0);
        }
        float uwFog = 1.0 - exp(-uwDist * 0.065);
        vec3 uwTint = (vec3(0.03, 0.14, 0.22) + 0.55 * water_color) * uw_brightness;
        vec3 uwBase = out_color.rgb * mix(vec3(1.0), vec3(0.68, 0.90, 0.96), is_underwater);
        out_color.rgb = mix(uwBase, uwTint, uwFog * 0.58 * is_underwater);
    }

    gl_FragColor = out_color;
}
