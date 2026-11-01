#pragma once

namespace moody::visualizer {

inline constexpr char kVisualizerVertexShader[] = R"(
#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in float aIntensity;
layout(location = 2) in float aRingIndex;
layout(location = 3) in float aGlowLevel;
uniform vec2 uPosition;
uniform float uScale;
uniform float uPassScale;
uniform vec2 uAspect;
out float vIntensity;
out float vRingIndex;
out float vGlowLevel;
void main() {
    vec2 p = aPosition * uScale * uPassScale;
    p *= uAspect;
    p += vec2(uPosition.x * 2.0 - 1.0, 1.0 - uPosition.y * 2.0);
    gl_Position = vec4(p, 0.0, 1.0);
    vIntensity = aIntensity;
    vRingIndex = aRingIndex;
    vGlowLevel = aGlowLevel;
}
)";

inline constexpr char kVisualizerFragmentShader[] = R"(
#version 300 es
precision highp float;
in float vIntensity;
in float vRingIndex;
in float vGlowLevel;
uniform vec3 uColorPrimary;
uniform vec3 uColorAccent;
uniform vec3 uRingColors[5];
uniform float uGlow;
uniform float uOpacity;
out vec4 fragColor;
void main() {
    float intensity = clamp(vIntensity, 0.0, 1.0);
    float gradient = smoothstep(0.05, 0.95, intensity);
    vec3 neon = vRingIndex >= 0.0
        ? uRingColors[int(vRingIndex + 0.5)]
        : mix(uColorPrimary, uColorAccent, gradient);
    float decay = exp(-4.0 * (1.0 - intensity));
    if (vRingIndex >= 0.0) {
        float halo = vGlowLevel < 0.99
            ? vGlowLevel * uGlow * 1.8
            : 1.0;
        float emissive = vGlowLevel < 0.99
            ? 0.8 + uGlow * 1.2
            : 1.0 + uGlow * 1.6;
        fragColor = vec4(neon * emissive,
                         uOpacity * halo * (0.55 + 0.45 * intensity));
    } else {
        float emissive = 0.65 + uGlow * (0.55 + 1.4 * decay);
        fragColor = vec4(neon * emissive,
                         uOpacity * (0.35 + 0.65 * intensity));
    }
}
)";

}  // namespace moody::visualizer
