#pragma once

#include <vector>

namespace moody::visualizer {

struct Vertex {
    float x;
    float y;
    float intensity;
    float ringIndex = -1.0f;
    float glowLevel = 1.0f;
};

enum class Layout : int {
    Linear = 0,
    Circular = 1,
    RadialSpikes = 2,
    RadialRings = 3,
    Oscilloscope = 4,
    Terrain = 5
};

std::vector<Vertex> generateLinearBars(const float* magnitudes, int count, float scale);
std::vector<Vertex> generateCircularWave(const float* magnitudes, int count, float scale);
std::vector<Vertex> generateRadialSpikes(const float* magnitudes, int count, float scale);
std::vector<Vertex> generateRadialRings(
        const float* magnitudes, int count, float scale, float phase);
std::vector<Vertex> generateOscilloscope(const float* magnitudes, int count, float scale);
std::vector<Vertex> generateTerrain(const float* magnitudes, int count, float scale, float phase);
std::vector<Vertex> generateGeometry(
        const float* magnitudes, int count, Layout layout, float scale, float phase = 0.0f);

}  // namespace moody::visualizer
