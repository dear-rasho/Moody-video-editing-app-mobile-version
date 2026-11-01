#include <jni.h>
#include <android/log.h>

#include "core/SpectrumProcessor.h"
#include "graphics/GeometryGenerator.h"
#include "graphics/ShaderManager.h"

#include <algorithm>
#include <array>
#include <cstdint>
#include <limits>
#include <vector>

namespace {
constexpr char kTag[] = "MoodyVisualizerJNI";
#define LOG_ERROR(...) __android_log_print(ANDROID_LOG_ERROR, kTag, __VA_ARGS__)

using moody::visualizer::Layout;
using moody::visualizer::ShaderManager;
using moody::visualizer::SpectrumProcessor;
using moody::visualizer::Vertex;

struct NativeProcessor {
    explicit NativeProcessor(int bandCount) : processor(256, bandCount) {}
    SpectrumProcessor processor;
};

Layout layoutFromInt(jint layout) {
    if (layout < static_cast<jint>(Layout::Linear) ||
        layout > static_cast<jint>(Layout::Terrain)) {
        return Layout::Linear;
    }
    return static_cast<Layout>(layout);
}

jfloatArray toJavaFloatArray(JNIEnv* env, const std::vector<float>& values) {
    if (values.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;
    const auto result = env->NewFloatArray(static_cast<jsize>(values.size()));
    if (result != nullptr && !values.empty()) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    }
    return result;
}

}  // namespace

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_moody_moodyvideoeditor_utils_VisualizerNative_nativeAnalyzePcm(
        JNIEnv* env, jclass, jshortArray pcmArray, jint fftSize, jint hopSize,
        jint bandCount) {
    if (pcmArray == nullptr || fftSize < 256 || fftSize > 8192 || hopSize < 1 ||
        bandCount < 1 || bandCount > fftSize / 2) return nullptr;
    const jsize sampleCount = env->GetArrayLength(pcmArray);
    if (sampleCount <= 0) return env->NewFloatArray(0);

    jboolean copied = JNI_FALSE;
    const jshort* pcm = env->GetShortArrayElements(pcmArray, &copied);
    if (pcm == nullptr) return nullptr;

    jfloatArray result = nullptr;
    try {
        SpectrumProcessor processor(fftSize, bandCount);
        const auto magnitudes = processor.analyze(
                reinterpret_cast<const int16_t*>(pcm),
                static_cast<size_t>(sampleCount), hopSize);
        result = toJavaFloatArray(env, magnitudes);
    } catch (...) {
        LOG_ERROR("Spectrum analysis failed");
        result = nullptr;
    }
    env->ReleaseShortArrayElements(pcmArray, const_cast<jshort*>(pcm), JNI_ABORT);
    return result;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_moody_moodyvideoeditor_utils_VisualizerNative_nativeBassEnergy(
        JNIEnv* env, jclass, jfloatArray bandsArray) {
    if (bandsArray == nullptr) return 0.0f;
    const jsize count = env->GetArrayLength(bandsArray);
    if (count <= 0 || count > 512) return 0.0f;
    jboolean copied = JNI_FALSE;
    jfloat* bands = env->GetFloatArrayElements(bandsArray, &copied);
    if (bands == nullptr) return 0.0f;
    const float energy = SpectrumProcessor::bassEnergy(
            bands, static_cast<size_t>(count));
    env->ReleaseFloatArrayElements(bandsArray, bands, JNI_ABORT);
    return energy;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_moody_moodyvideoeditor_utils_VisualizerNative_nativeCreateProcessor(
        JNIEnv*, jclass, jint bandCount) {
    if (bandCount < 1 || bandCount > 512) return 0;
    try {
        return reinterpret_cast<jlong>(new NativeProcessor(bandCount));
    } catch (...) {
        LOG_ERROR("Could not allocate spectrum processor");
        return 0;
    }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_moody_moodyvideoeditor_utils_VisualizerNative_nativeSmoothBands(
        JNIEnv* env, jclass, jlong handle, jfloatArray bandsArray, jfloat smoothing) {
    auto* session = reinterpret_cast<NativeProcessor*>(handle);
    if (session == nullptr || bandsArray == nullptr) return nullptr;
    const jsize count = env->GetArrayLength(bandsArray);
    if (count != session->processor.bandCount()) return nullptr;
    jboolean copied = JNI_FALSE;
    jfloat* bands = env->GetFloatArrayElements(bandsArray, &copied);
    if (bands == nullptr) return nullptr;
    session->processor.smoothInPlace(
            bands, static_cast<size_t>(count), std::clamp(smoothing, 0.0f, 0.95f));
    jfloatArray result = env->NewFloatArray(count);
    if (result != nullptr) env->SetFloatArrayRegion(result, 0, count, bands);
    env->ReleaseFloatArrayElements(bandsArray, bands, JNI_ABORT);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_moody_moodyvideoeditor_utils_VisualizerNative_nativeResetProcessor(
        JNIEnv*, jclass, jlong handle) {
    auto* session = reinterpret_cast<NativeProcessor*>(handle);
    if (session != nullptr) session->processor.reset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_moody_moodyvideoeditor_utils_VisualizerNative_nativeReleaseProcessor(
        JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<NativeProcessor*>(handle);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_moody_moodyvideoeditor_utils_VisualizerNative_nativeGenerateGeometry(
        JNIEnv* env, jclass, jfloatArray bandsArray, jint layout, jfloat scale,
        jfloat phase) {
    if (bandsArray == nullptr) return nullptr;
    const jsize count = env->GetArrayLength(bandsArray);
    if (count < 1 || count > 512) return nullptr;
    jboolean copied = JNI_FALSE;
    jfloat* bands = env->GetFloatArrayElements(bandsArray, &copied);
    if (bands == nullptr) return nullptr;

    std::vector<float> packed;
    try {
        const auto vertices = moody::visualizer::generateGeometry(
                bands, count, layoutFromInt(layout), scale, phase);
        packed.reserve(vertices.size() * 5);
        for (const Vertex& vertex : vertices) {
            packed.push_back(vertex.x);
            packed.push_back(vertex.y);
            packed.push_back(vertex.intensity);
            packed.push_back(vertex.ringIndex);
            packed.push_back(vertex.glowLevel);
        }
    } catch (...) {
        LOG_ERROR("Geometry generation failed");
        env->ReleaseFloatArrayElements(bandsArray, bands, JNI_ABORT);
        return nullptr;
    }
    env->ReleaseFloatArrayElements(bandsArray, bands, JNI_ABORT);
    return toJavaFloatArray(env, packed);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_moody_moodyvideoeditor_utils_VisualizerNative_nativeCreateGlRenderer(
        JNIEnv*, jclass) {
    try {
        return reinterpret_cast<jlong>(new ShaderManager());
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_moody_moodyvideoeditor_utils_VisualizerNative_nativeInitializeGlRenderer(
        JNIEnv*, jclass, jlong handle) {
    auto* renderer = reinterpret_cast<ShaderManager*>(handle);
    return renderer != nullptr && renderer->initialize() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_moody_moodyvideoeditor_utils_VisualizerNative_nativeResizeGlRenderer(
        JNIEnv*, jclass, jlong handle, jint width, jint height) {
    auto* renderer = reinterpret_cast<ShaderManager*>(handle);
    if (renderer != nullptr) renderer->setViewport(width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_moody_moodyvideoeditor_utils_VisualizerNative_nativeDrawGlRenderer(
        JNIEnv* env, jclass, jlong handle, jfloatArray bandsArray, jint layout,
        jfloat positionX, jfloat positionY, jfloat scale, jlong primaryColor,
        jlong accentColor, jlongArray ringColorsArray, jfloat glow,
        jfloat opacity, jfloat phase) {
    auto* renderer = reinterpret_cast<ShaderManager*>(handle);
    if (renderer == nullptr || bandsArray == nullptr || ringColorsArray == nullptr) return;
    const jsize count = env->GetArrayLength(bandsArray);
    if (count < 1 || count > 512 || env->GetArrayLength(ringColorsArray) != 5) return;
    jfloat* bands = env->GetFloatArrayElements(bandsArray, nullptr);
    if (bands == nullptr) return;
    jlong* colors = env->GetLongArrayElements(ringColorsArray, nullptr);
    if (colors == nullptr) {
        env->ReleaseFloatArrayElements(bandsArray, bands, JNI_ABORT);
        return;
    }

    try {
        std::array<uint64_t, 5> nativeRingColors{};
        for (size_t i = 0; i < nativeRingColors.size(); ++i) {
            nativeRingColors[i] = static_cast<uint64_t>(colors[i]);
        }
        const auto selectedLayout = layoutFromInt(layout);
        const auto vertices = moody::visualizer::generateGeometry(
                bands, count, selectedLayout, 1.0f, phase);
        renderer->draw(vertices, selectedLayout,
                       positionX, positionY, scale,
                       static_cast<uint64_t>(primaryColor),
                       static_cast<uint64_t>(accentColor),
                       nativeRingColors,
                       glow, opacity);
    } catch (...) {
        LOG_ERROR("Native visualizer draw failed");
    }
    env->ReleaseLongArrayElements(ringColorsArray, colors, JNI_ABORT);
    env->ReleaseFloatArrayElements(bandsArray, bands, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_moody_moodyvideoeditor_utils_VisualizerNative_nativeDestroyGlRenderer(
        JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<ShaderManager*>(handle);
}
