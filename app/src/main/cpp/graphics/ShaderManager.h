#pragma once

#include "GeometryGenerator.h"
#include <GLES3/gl3.h>
#include <array>
#include <cstdint>
#include <vector>

namespace moody::visualizer {

class ShaderManager {
public:
    ShaderManager() = default;
    ~ShaderManager();
    ShaderManager(const ShaderManager&) = delete;
    ShaderManager& operator=(const ShaderManager&) = delete;

    bool initialize();
    void setViewport(int width, int height);
    void draw(const std::vector<Vertex>& vertices,
              Layout layout,
              float positionX,
              float positionY,
              float scale,
              uint64_t primaryColor,
              uint64_t accentColor,
              const std::array<uint64_t, 5>& ringColors,
              float glow,
              float opacity);

private:
    static GLuint compile(GLenum type, const char* source);
    void release() noexcept;

    GLuint program_ = 0;
    GLuint vertexBuffer_ = 0;
    GLint positionUniform_ = -1;
    GLint scaleUniform_ = -1;
    GLint passScaleUniform_ = -1;
    GLint aspectUniform_ = -1;
    GLint primaryUniform_ = -1;
    GLint accentUniform_ = -1;
    GLint ringColorsUniform_ = -1;
    GLint glowLevelAttribute_ = 3;
    GLint glowUniform_ = -1;
    GLint opacityUniform_ = -1;
    int viewportWidth_ = 1;
    int viewportHeight_ = 1;
};

}  // namespace moody::visualizer
