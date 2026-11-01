#include "GeometryGenerator.h"

#include <algorithm>
#include <cmath>

namespace moody::visualizer {
namespace {
constexpr float kPi = 3.14159265358979323846f;

Vertex vertex(float x, float y, float magnitude, float ringIndex = -1.0f,
              float glowLevel = 1.0f) {
    return {x, y, std::clamp(magnitude, 0.0f, 1.0f), ringIndex, glowLevel};
}

float magnitudeAt(const float* magnitudes, int count, int index) {
    const int wrapped = ((index % count) + count) % count;
    return std::clamp(magnitudes[wrapped], 0.0f, 1.0f);
}

float interpolateCyclic(const float* magnitudes, int count, float sample) {
    sample = std::fmod(sample, static_cast<float>(count));
    if (sample < 0.0f) sample += count;
    const int index = static_cast<int>(sample);
    const float t = sample - index;
    const float p0 = magnitudeAt(magnitudes, count, (index - 1 + count) % count);
    const float p1 = magnitudeAt(magnitudes, count, index % count);
    const float p2 = magnitudeAt(magnitudes, count, (index + 1) % count);
    const float p3 = magnitudeAt(magnitudes, count, (index + 2) % count);
    return std::clamp(
            0.5f * ((2.0f * p1) + (-p0 + p2) * t +
                    (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * t * t +
                    (-p0 + 3.0f * p1 - 3.0f * p2 + p3) * t * t * t),
            0.0f, 1.0f);
}

void addQuad(std::vector<Vertex>& vertices, float x0, float y0, float x1, float y1,
             float x2, float y2, float x3, float y3, float intensity,
             float ringIndex = -1.0f, float innerGlow = 1.0f,
             float outerGlow = 1.0f) {
    const Vertex a = vertex(x0, y0, intensity, ringIndex, innerGlow);
    const Vertex b = vertex(x1, y1, intensity, ringIndex, outerGlow);
    const Vertex c = vertex(x2, y2, intensity, ringIndex, innerGlow);
    const Vertex d = vertex(x3, y3, intensity, ringIndex, outerGlow);
    vertices.insert(vertices.end(), {a, b, c, c, b, d});
}
}

std::vector<Vertex> generateLinearBars(const float* magnitudes, int count, float scale) {
    if (magnitudes == nullptr || count < 1) return {};
    std::vector<Vertex> vertices;
    vertices.reserve(static_cast<size_t>(count) * 6);
    const float slot = 1.84f / count;
    const float width = slot * 0.72f;
    const float bottom = -0.78f;
    const float maximumHeight = 1.48f * std::clamp(scale, 0.1f, 4.0f);
    for (int i = 0; i < count; ++i) {
        const float x0 = -0.92f + slot * i + (slot - width) * 0.5f;
        const float x1 = x0 + width;
        const float value = std::clamp(magnitudes[i], 0.0f, 1.0f);
        const float top = bottom + maximumHeight * value;
        const Vertex bl = vertex(x0, bottom, value);
        const Vertex br = vertex(x1, bottom, value);
        const Vertex tl = vertex(x0, top, value);
        const Vertex tr = vertex(x1, top, value);
        vertices.insert(vertices.end(), {bl, br, tl, tl, br, tr});
    }
    return vertices;
}

std::vector<Vertex> generateCircularWave(const float* magnitudes, int count, float scale) {
    if (magnitudes == nullptr || count < 3) return {};
    constexpr int kSubdivisions = 4;
    const int points = count * kSubdivisions;
    std::vector<Vertex> vertices;
    vertices.reserve(static_cast<size_t>(points + 1) * 2);
    const float factor = std::clamp(scale, 0.1f, 4.0f);
    for (int i = 0; i <= points; ++i) {
        const float sample = static_cast<float>(i % points) / kSubdivisions;
        const float value = interpolateCyclic(magnitudes, count, sample);
        const float angle = (2.0f * kPi * i / points) - kPi * 0.5f;
        const float liquidRadius = (0.34f + 0.19f * value) * factor;
        const float innerRadius = std::max(0.08f, liquidRadius - 0.035f * factor);
        vertices.push_back(vertex(innerRadius * std::cos(angle),
                                  innerRadius * std::sin(angle), value));
        vertices.push_back(vertex(liquidRadius * std::cos(angle),
                                  liquidRadius * std::sin(angle), value));
    }
    return vertices;
}

std::vector<Vertex> generateRadialSpikes(
        const float* magnitudes, int count, float scale) {
    if (magnitudes == nullptr || count < 3) return {};
    std::vector<Vertex> vertices;
    vertices.reserve(static_cast<size_t>(count) * 6);
    const float factor = std::clamp(scale, 0.1f, 4.0f);
    constexpr float kHalfWidth = 0.006f;
    for (int i = 0; i < count; ++i) {
        const float angle = 2.0f * kPi * i / count - kPi * 0.5f;
        const float value = std::clamp(magnitudes[i], 0.0f, 1.0f);
        const float inner = 0.31f * factor;
        const float outer = (0.34f + 0.34f * value) * factor;
        const float c = std::cos(angle);
        const float s = std::sin(angle);
        const float tx = -s * kHalfWidth;
        const float ty = c * kHalfWidth;
        addQuad(vertices,
                inner * c + tx, inner * s + ty,
                inner * c - tx, inner * s - ty,
                outer * c + tx, outer * s + ty,
                outer * c - tx, outer * s - ty, value);
    }
    return vertices;
}

std::vector<Vertex> generateRadialRings(
        const float* magnitudes, int count, float scale, float phase) {
    if (magnitudes == nullptr || count < 3) return {};
    constexpr int kRings = 5;
    constexpr int kSubdivisions = 4;
    const int points = count * kSubdivisions;
    std::vector<Vertex> vertices;
    constexpr int kGlowStrips = 7;
    vertices.reserve(static_cast<size_t>(kRings) * points * kGlowStrips * 6);
    const float factor = std::clamp(scale, 0.1f, 4.0f);
    for (int ring = 0; ring < kRings; ++ring) {
        const float base = 0.16f + ring * 0.09f;
        const float coreHalfWidth = base * 0.055f;
        const float haloWidth = base * 0.20f;
        const float direction = ring % 2 == 0 ? 1.0f : -1.0f;
        const float angularSpeed = direction * (0.12f + ring * 0.045f);
        constexpr float glowLevels[kGlowStrips + 1] = {
                0.0f, 0.025f, 0.16f, 1.0f, 1.0f, 0.16f, 0.025f, 0.0f};
        const float totalWidth = coreHalfWidth + haloWidth;
        const float offsets[kGlowStrips + 1] = {
                -totalWidth,
                -(coreHalfWidth + haloWidth * 0.65f),
                -(coreHalfWidth + haloWidth * 0.3f),
                -coreHalfWidth,
                coreHalfWidth,
                coreHalfWidth + haloWidth * 0.3f,
                coreHalfWidth + haloWidth * 0.65f,
                totalWidth
        };
        for (int i = 0; i < points; ++i) {
            const float angle = 2.0f * kPi * i / points + phase * angularSpeed;
            const float sample = static_cast<float>(i) / kSubdivisions;
            const float smooth = interpolateCyclic(magnitudes, count, sample);
            const float next = interpolateCyclic(
                    magnitudes, count, sample + 1.0f / kSubdivisions);
            const float nextAngle =
                    2.0f * kPi * (i + 1) / points + phase * angularSpeed;
            const float r0 = base + base * 0.38f * std::sqrt(smooth);
            const float r1 = base + base * 0.38f * std::sqrt(next);
            for (int strip = 0; strip < kGlowStrips; ++strip) {
                const float innerOffset = offsets[strip];
                const float outerOffset = offsets[strip + 1];
                const float inner0 = (r0 + innerOffset) * factor;
                const float outer0 = (r0 + outerOffset) * factor;
                const float inner1 = (r1 + innerOffset) * factor;
                const float outer1 = (r1 + outerOffset) * factor;
                addQuad(vertices,
                        inner0 * std::cos(angle), inner0 * std::sin(angle),
                        outer0 * std::cos(angle), outer0 * std::sin(angle),
                        inner1 * std::cos(nextAngle), inner1 * std::sin(nextAngle),
                        outer1 * std::cos(nextAngle), outer1 * std::sin(nextAngle),
                        smooth, static_cast<float>(ring),
                        glowLevels[strip], glowLevels[strip + 1]);
            }
        }
    }
    return vertices;
}

std::vector<Vertex> generateOscilloscope(
        const float* magnitudes, int count, float scale) {
    if (magnitudes == nullptr || count < 2) return {};
    constexpr int kSubdivisions = 3;
    const int points = (count - 1) * kSubdivisions + 1;
    std::vector<Vertex> vertices;
    vertices.reserve(static_cast<size_t>(points - 1) * 6);
    const float factor = std::clamp(scale, 0.1f, 4.0f);
    constexpr float kThickness = 0.012f;
    auto point = [&](int index, float* x, float* y, float* value) {
        const float sample = static_cast<float>(index) / kSubdivisions;
        const int base = std::min(static_cast<int>(sample), count - 2);
        const float t = sample - base;
        const float p0 = magnitudeAt(magnitudes, count, std::max(0, base - 1));
        const float p1 = magnitudeAt(magnitudes, count, base);
        const float p2 = magnitudeAt(magnitudes, count, base + 1);
        const float p3 = magnitudeAt(magnitudes, count, base + 2);
        *value = std::clamp(
                0.5f * ((2.0f * p1) + (-p0 + p2) * t +
                        (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * t * t +
                        (-p0 + 3.0f * p1 - 3.0f * p2 + p3) * t * t * t),
                0.0f, 1.0f);
        const float edgeFade = std::min(
                1.0f, std::min(sample, count - 1.0f - sample) / 5.0f);
        *x = -0.92f + 1.84f * sample / (count - 1);
        *y = (*value - 0.5f) * 0.78f * factor * edgeFade;
    };
    for (int i = 0; i < points - 1; ++i) {
        float x0, y0, value0, x1, y1, value1;
        point(i, &x0, &y0, &value0);
        point(i + 1, &x1, &y1, &value1);
        const float dx = x1 - x0;
        const float dy = y1 - y0;
        const float length = std::max(std::sqrt(dx * dx + dy * dy), 1.0e-5f);
        const float nx = -dy / length * kThickness;
        const float ny = dx / length * kThickness;
        addQuad(vertices, x0 + nx, y0 + ny, x0 - nx, y0 - ny,
                x1 + nx, y1 + ny, x1 - nx, y1 - ny, (value0 + value1) * 0.5f);
    }
    return vertices;
}

std::vector<Vertex> generateTerrain(
        const float* magnitudes, int count, float scale, float phase) {
    if (magnitudes == nullptr || count < 3) return {};
    constexpr int kRows = 8;
    const int columns = std::min(count, 40);
    std::vector<Vertex> vertices;
    vertices.reserve(static_cast<size_t>(kRows) * (columns - 1) * 6 +
                     static_cast<size_t>(columns) * (kRows - 1) * 6);
    const float factor = std::clamp(scale, 0.1f, 4.0f);
    auto point = [&](int row, int column, float* x, float* y, float* value) {
        const int band = column * (count - 1) / (columns - 1);
        const float audio = std::clamp(magnitudes[band], 0.0f, 1.0f);
        const float depth = static_cast<float>(row) / (kRows - 1);
        const float travel = std::fmod(depth + phase * 0.18f, 1.0f);
        const float perspective = 0.25f + travel * 0.75f;
        const float span = 0.15f + travel * 1.7f;
        const float elevation = audio * 0.28f * factor *
                (0.35f + 0.65f * std::sin(kPi * travel));
        *x = (static_cast<float>(column) / (columns - 1) * 2.0f - 1.0f) * span;
        *y = -0.76f + perspective * 0.86f + elevation;
        *value = audio * perspective;
    };
    constexpr float kLineWidth = 0.0035f;
    auto addLine = [&](float x0, float y0, float x1, float y1, float intensity) {
        const float dx = x1 - x0;
        const float dy = y1 - y0;
        const float length = std::max(std::sqrt(dx * dx + dy * dy), 1.0e-5f);
        const float nx = -dy / length * kLineWidth;
        const float ny = dx / length * kLineWidth;
        addQuad(vertices, x0 + nx, y0 + ny, x0 - nx, y0 - ny,
                x1 + nx, y1 + ny, x1 - nx, y1 - ny, intensity);
    };
    for (int row = 0; row < kRows; ++row) {
        for (int column = 0; column < columns; ++column) {
            float x0, y0, value0;
            point(row, column, &x0, &y0, &value0);
            if (column + 1 < columns) {
                float x1, y1, value1;
                point(row, column + 1, &x1, &y1, &value1);
                addLine(x0, y0, x1, y1, (value0 + value1) * 0.5f);
            }
            if (row + 1 < kRows) {
                float x1, y1, value1;
                point(row + 1, column, &x1, &y1, &value1);
                addLine(x0, y0, x1, y1, (value0 + value1) * 0.5f);
            }
        }
    }
    return vertices;
}

std::vector<Vertex> generateGeometry(
        const float* magnitudes, int count, Layout layout, float scale, float phase) {
    switch (layout) {
        case Layout::Circular:
            return generateCircularWave(magnitudes, count, scale);
        case Layout::RadialSpikes:
            return generateRadialSpikes(magnitudes, count, scale);
        case Layout::RadialRings:
            return generateRadialRings(magnitudes, count, scale, phase);
        case Layout::Oscilloscope:
            return generateOscilloscope(magnitudes, count, scale);
        case Layout::Terrain:
            return generateTerrain(magnitudes, count, scale, phase);
        case Layout::Linear:
        default:
            return generateLinearBars(magnitudes, count, scale);
    }
}

}  // namespace moody::visualizer
