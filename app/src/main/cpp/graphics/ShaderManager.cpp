#include "ShaderManager.h"

#include "../shaders/VisualizerShaders.h"
#include <android/log.h>
#include <algorithm>
#include <cstddef>
#include <vector>

namespace moody::visualizer {
namespace {
constexpr char kTag[] = "MoodyVisualizerGL";
}

ShaderManager::~ShaderManager() {
    release();
}

GLuint ShaderManager::compile(GLenum type, const char* source) {
    const GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint compiled = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (compiled == GL_TRUE) return shader;
    GLint length = 0;
    glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &length);
    if (length > 1) {
        std::vector<char> message(static_cast<size_t>(length));
        glGetShaderInfoLog(shader, length, nullptr, message.data());
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Shader compile: %s",
                            message.data());
    }
    glDeleteShader(shader);
    return 0;
}

bool ShaderManager::initialize() {
    program_ = 0;
    vertexBuffer_ = 0;
    const GLuint vertex = compile(GL_VERTEX_SHADER, kVisualizerVertexShader);
    const GLuint fragment = compile(GL_FRAGMENT_SHADER, kVisualizerFragmentShader);
    if (vertex == 0 || fragment == 0) {
        if (vertex != 0) glDeleteShader(vertex);
        if (fragment != 0) glDeleteShader(fragment);
        return false;
    }

    program_ = glCreateProgram();
    glAttachShader(program_, vertex);
    glAttachShader(program_, fragment);
    glLinkProgram(program_);
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    GLint linked = GL_FALSE;
    glGetProgramiv(program_, GL_LINK_STATUS, &linked);
    if (linked != GL_TRUE) {
        GLint length = 0;
        glGetProgramiv(program_, GL_INFO_LOG_LENGTH, &length);
        if (length > 1) {
            std::vector<char> message(static_cast<size_t>(length));
            glGetProgramInfoLog(program_, length, nullptr, message.data());
            __android_log_print(ANDROID_LOG_ERROR, kTag, "Program link: %s",
                                message.data());
        }
        release();
        return false;
    }

    positionUniform_ = glGetUniformLocation(program_, "uPosition");
    scaleUniform_ = glGetUniformLocation(program_, "uScale");
    passScaleUniform_ = glGetUniformLocation(program_, "uPassScale");
    aspectUniform_ = glGetUniformLocation(program_, "uAspect");
    primaryUniform_ = glGetUniformLocation(program_, "uColorPrimary");
    accentUniform_ = glGetUniformLocation(program_, "uColorAccent");
    ringColorsUniform_ = glGetUniformLocation(program_, "uRingColors[0]");
    glowUniform_ = glGetUniformLocation(program_, "uGlow");
    opacityUniform_ = glGetUniformLocation(program_, "uOpacity");
    glGenBuffers(1, &vertexBuffer_);
    if (vertexBuffer_ == 0) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Could not allocate vertex buffer");
        release();
        return false;
    }
    return true;
}

void ShaderManager::setViewport(int width, int height) {
    viewportWidth_ = width > 0 ? width : 1;
    viewportHeight_ = height > 0 ? height : 1;
    glViewport(0, 0, viewportWidth_, viewportHeight_);
}

void ShaderManager::draw(const std::vector<Vertex>& vertices,
                         Layout layout,
                         float positionX,
                         float positionY,
                         float scale,
                         uint64_t primaryColor,
                         uint64_t accentColor,
                         const std::array<uint64_t, 5>& ringColors,
                         float glow,
                         float opacity) {
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    if (program_ == 0 || vertexBuffer_ == 0 || vertices.empty()) return;

    glUseProgram(program_);
    glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer_);
    glBufferData(GL_ARRAY_BUFFER,
                 static_cast<GLsizeiptr>(vertices.size() * sizeof(Vertex)),
                 vertices.data(), GL_STREAM_DRAW);
    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);
    glEnableVertexAttribArray(2);
    glEnableVertexAttribArray(3);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, sizeof(Vertex),
                          reinterpret_cast<const void*>(offsetof(Vertex, x)));
    glVertexAttribPointer(1, 1, GL_FLOAT, GL_FALSE, sizeof(Vertex),
                          reinterpret_cast<const void*>(offsetof(Vertex, intensity)));
    glVertexAttribPointer(2, 1, GL_FLOAT, GL_FALSE, sizeof(Vertex),
                          reinterpret_cast<const void*>(offsetof(Vertex, ringIndex)));
    glVertexAttribPointer(3, 1, GL_FLOAT, GL_FALSE, sizeof(Vertex),
                          reinterpret_cast<const void*>(offsetof(Vertex, glowLevel)));
    glUniform2f(positionUniform_, std::clamp(positionX, 0.0f, 1.0f),
                std::clamp(positionY, 0.0f, 1.0f));
    glUniform1f(scaleUniform_, std::clamp(scale, 0.1f, 4.0f));
    glUniform2f(aspectUniform_,
                static_cast<float>(std::min(viewportWidth_, viewportHeight_)) /
                        viewportWidth_,
                static_cast<float>(std::min(viewportWidth_, viewportHeight_)) /
                        viewportHeight_);
    glUniform3f(primaryUniform_,
                static_cast<float>((primaryColor >> 16) & 0xFF) / 255.0f,
                static_cast<float>((primaryColor >> 8) & 0xFF) / 255.0f,
                static_cast<float>(primaryColor & 0xFF) / 255.0f);
    glUniform3f(accentUniform_,
                static_cast<float>((accentColor >> 16) & 0xFF) / 255.0f,
                static_cast<float>((accentColor >> 8) & 0xFF) / 255.0f,
                static_cast<float>(accentColor & 0xFF) / 255.0f);
    std::array<GLfloat, 15> ringColorValues{};
    for (size_t i = 0; i < ringColors.size(); ++i) {
        ringColorValues[i * 3] =
                static_cast<float>((ringColors[i] >> 16) & 0xFF) / 255.0f;
        ringColorValues[i * 3 + 1] =
                static_cast<float>((ringColors[i] >> 8) & 0xFF) / 255.0f;
        ringColorValues[i * 3 + 2] =
                static_cast<float>(ringColors[i] & 0xFF) / 255.0f;
    }
    glUniform3fv(ringColorsUniform_, static_cast<GLsizei>(ringColors.size()),
                 ringColorValues.data());
    const float glowIntensity = std::clamp(glow, 0.0f, 5.0f);
    glUniform1f(glowUniform_, glowIntensity);
    glUniform1f(opacityUniform_, std::clamp(opacity, 0.0f, 1.0f));

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE);
    const GLenum primitive = layout == Layout::Circular
            ? GL_TRIANGLE_STRIP : GL_TRIANGLES;
    const GLsizei count = static_cast<GLsizei>(vertices.size());
    const float glowAlpha = std::min(glowIntensity * 0.12f, 0.6f);
    if (layout == Layout::RadialRings) {
        glUniform1f(opacityUniform_, std::clamp(opacity, 0.0f, 1.0f));
        glUniform1f(passScaleUniform_, 1.0f);
        glDrawArrays(primitive, 0, count);
    } else {
        glUniform1f(opacityUniform_, std::clamp(opacity, 0.0f, 1.0f) * glowAlpha);
        glUniform1f(passScaleUniform_, 1.32f);
        glDrawArrays(primitive, 0, count);
        glUniform1f(opacityUniform_, std::clamp(opacity, 0.0f, 1.0f) * glowAlpha);
        glUniform1f(passScaleUniform_, 1.14f);
        glDrawArrays(primitive, 0, count);
        glUniform1f(opacityUniform_, std::clamp(opacity, 0.0f, 1.0f));
        glUniform1f(passScaleUniform_, 1.0f);
        glDrawArrays(primitive, 0, count);
    }
    glDisable(GL_BLEND);
    glDisableVertexAttribArray(0);
    glDisableVertexAttribArray(1);
    glDisableVertexAttribArray(2);
    glDisableVertexAttribArray(3);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glUseProgram(0);
}

void ShaderManager::release() noexcept {
    if (vertexBuffer_ != 0) glDeleteBuffers(1, &vertexBuffer_);
    if (program_ != 0) glDeleteProgram(program_);
    vertexBuffer_ = 0;
    program_ = 0;
}

}  // namespace moody::visualizer
