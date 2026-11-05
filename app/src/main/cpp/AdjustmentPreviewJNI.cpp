#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <exception>
#include <array>
#include <limits>
#include <vector>

namespace {

constexpr char kTag[] = "AdjustmentPreviewJNI";

float clamp01(float value) {
    return std::clamp(value, 0.0f, 1.0f);
}

float smoothstep(float edge0, float edge1, float value) {
    const float t = clamp01((value - edge0) / (edge1 - edge0));
    return t * t * (3.0f - 2.0f * t);
}

float hueWeight(float hue, float target, float range, float saturation) {
    float difference = std::fabs(hue - target);
    difference = std::min(difference, 360.0f - difference);
    return (1.0f - smoothstep(range * 0.25f, range, difference)) *
           smoothstep(0.02f, 0.15f, saturation);
}

float channelStrength(float value) {
    return std::clamp(value * 0.015f, -1.5f, 1.5f);
}

void rgbToHsv(float red, float green, float blue, float* hue, float* saturation) {
    const float maximum = std::max(red, std::max(green, blue));
    const float minimum = std::min(red, std::min(green, blue));
    const float delta = maximum - minimum;
    *saturation = maximum <= 0.0f ? 0.0f : delta / maximum;
    if (delta <= 0.0f) {
        *hue = 0.0f;
    } else if (maximum == red) {
        *hue = 60.0f * ((green - blue) / delta + (green < blue ? 6.0f : 0.0f));
    } else if (maximum == green) {
        *hue = 60.0f * ((blue - red) / delta + 2.0f);
    } else {
        *hue = 60.0f * ((red - green) / delta + 4.0f);
    }
}

void applyMatrix(float* channels, float alpha, const float* matrix) {
    const float red = channels[0];
    const float green = channels[1];
    const float blue = channels[2];
    channels[0] = std::clamp(matrix[0] * red + matrix[1] * green +
                              matrix[2] * blue + matrix[3] * alpha + matrix[4],
                              0.0f, 255.0f);
    channels[1] = std::clamp(matrix[5] * red + matrix[6] * green +
                              matrix[7] * blue + matrix[8] * alpha + matrix[9],
                              0.0f, 255.0f);
    channels[2] = std::clamp(matrix[10] * red + matrix[11] * green +
                              matrix[12] * blue + matrix[13] * alpha + matrix[14],
                              0.0f, 255.0f);
}

void applyColorChannels(float* channels, const float* adjustments,
                        bool hasChannelAdjustment) {
    if (!hasChannelAdjustment) return;

    float& red = channels[0];
    float& green = channels[1];
    float& blue = channels[2];
    float hue = 0.0f;
    float saturation = 0.0f;
    rgbToHsv(red, green, blue, &hue, &saturation);

    const auto apply = [&](int index, float target, float range, int mode) {
        const float strength = channelStrength(adjustments[index]);
        if (strength == 0.0f) return;
        const float amount = strength * hueWeight(hue, target, range, saturation);
        if (std::fabs(amount) <= 0.001f) return;
        if (mode == 0) {
            if (amount > 0.0f) {
                red += (255.0f - red) * amount * 0.9f;
                green *= 1.0f - amount * 0.45f;
                blue *= 1.0f - amount * 0.45f;
            } else {
                const float average = (red + green + blue) / 3.0f;
                red += (average - red) * -amount * 0.9f;
                green += (average - green) * -amount * 0.5f;
                blue += (average - blue) * -amount * 0.5f;
            }
        } else if (mode == 1) {
            if (amount > 0.0f) {
                red += (255.0f - red) * amount * 0.7f;
                green += (180.0f - green) * amount * 0.5f;
                blue *= 1.0f - amount * 0.5f;
            } else {
                const float average = (red + green + blue) / 3.0f;
                red += (average - red) * -amount * 0.7f;
                green += (average - green) * -amount * 0.7f;
            }
        } else if (mode == 2) {
            if (amount > 0.0f) {
                red += (255.0f - red) * amount * 0.75f;
                green += (255.0f - green) * amount * 0.75f;
                blue *= 1.0f - amount * 0.6f;
            } else {
                const float average = (red + green + blue) / 3.0f;
                red += (average - red) * -amount * 0.7f;
                green += (average - green) * -amount * 0.7f;
            }
        } else if (mode == 3) {
            if (amount > 0.0f) {
                green += (255.0f - green) * amount * 0.9f;
                red *= 1.0f - amount * 0.45f;
                blue *= 1.0f - amount * 0.45f;
            } else {
                const float average = (red + green + blue) / 3.0f;
                green += (average - green) * -amount * 0.9f;
                red += (average - red) * -amount * 0.5f;
                blue += (average - blue) * -amount * 0.5f;
            }
        } else if (mode == 4) {
            if (amount > 0.0f) {
                green += (255.0f - green) * amount * 0.7f;
                blue += (255.0f - blue) * amount * 0.7f;
                red *= 1.0f - amount * 0.6f;
            } else {
                const float average = (red + green + blue) / 3.0f;
                green += (average - green) * -amount * 0.7f;
                blue += (average - blue) * -amount * 0.7f;
            }
        } else if (mode == 5) {
            if (amount > 0.0f) {
                blue += (255.0f - blue) * amount * 0.9f;
                red *= 1.0f - amount * 0.45f;
                green *= 1.0f - amount * 0.45f;
            } else {
                const float average = (red + green + blue) / 3.0f;
                blue += (average - blue) * -amount * 0.9f;
                red += (average - red) * -amount * 0.5f;
                green += (average - green) * -amount * 0.5f;
            }
        } else if (mode == 6) {
            if (amount > 0.0f) {
                red += (220.0f - red) * amount * 0.7f;
                blue += (255.0f - blue) * amount * 0.7f;
                green *= 1.0f - amount * 0.6f;
            } else {
                const float average = (red + green + blue) / 3.0f;
                red += (average - red) * -amount * 0.7f;
                blue += (average - blue) * -amount * 0.7f;
            }
        } else if (mode == 7) {
            if (amount > 0.0f) {
                red += (255.0f - red) * amount * 0.75f;
                blue += (255.0f - blue) * amount * 0.75f;
                green *= 1.0f - amount * 0.6f;
            } else {
                const float average = (red + green + blue) / 3.0f;
                red += (average - red) * -amount * 0.7f;
                blue += (average - blue) * -amount * 0.7f;
            }
        }
    };

    apply(0, 0.0f, 30.0f, 0);
    apply(1, 30.0f, 25.0f, 1);
    apply(2, 60.0f, 30.0f, 2);
    apply(3, 120.0f, 45.0f, 3);
    apply(4, 180.0f, 30.0f, 4);
    apply(5, 240.0f, 30.0f, 5);
    apply(6, 280.0f, 25.0f, 6);
    apply(7, 300.0f, 25.0f, 7);

    const float skin = channelStrength(adjustments[8]) *
                       hueWeight(hue, 25.0f, 20.0f, saturation);
    red = std::clamp(red + skin * 30.0f, 0.0f, 255.0f);
    green = std::clamp(green + skin * 15.0f, 0.0f, 255.0f);

}

using ColorMatrix = std::array<float, 20>;

ColorMatrix identityMatrix() {
    return {1.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f};
}

void postConcat(ColorMatrix& matrix, const ColorMatrix& next) {
    ColorMatrix result{};
    for (int row = 0; row < 4; ++row) {
        const int offset = row * 5;
        for (int column = 0; column < 4; ++column) {
            result[offset + column] =
                next[offset] * matrix[column] +
                next[offset + 1] * matrix[5 + column] +
                next[offset + 2] * matrix[10 + column] +
                next[offset + 3] * matrix[15 + column];
        }
        result[offset + 4] =
            next[offset] * matrix[4] +
            next[offset + 1] * matrix[9] +
            next[offset + 2] * matrix[14] +
            next[offset + 3] * matrix[19] +
            next[offset + 4];
    }
    matrix = result;
}

ColorMatrix channelMatrix(float redScale, float greenScale, float blueScale,
                         float redOffset = 0.0f, float greenOffset = 0.0f,
                         float blueOffset = 0.0f) {
    return {redScale, 0.0f, 0.0f, 0.0f, redOffset,
            0.0f, greenScale, 0.0f, 0.0f, greenOffset,
            0.0f, 0.0f, blueScale, 0.0f, blueOffset,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f};
}

ColorMatrix buildAdjustmentMatrix(const float* values) {
    ColorMatrix matrix = identityMatrix();
    if (values[0] != 0.0f) {
        const float offset = values[0] / 100.0f * 48.0f;
        postConcat(matrix, channelMatrix(1.0f, 1.0f, 1.0f, offset, offset, offset));
    }
    if (values[1] != 0.0f) {
        const float scale = 1.0f + values[1] / 100.0f * 0.5f;
        const float offset = (1.0f - scale) * 128.0f;
        postConcat(matrix, channelMatrix(scale, scale, scale, offset, offset, offset));
    }
    if (values[2] != 0.0f) {
        const float scale = std::pow(2.0f, values[2] / 100.0f);
        postConcat(matrix, channelMatrix(scale, scale, scale));
    }
    const float saturation = std::clamp(
        1.0f + values[7] / 100.0f + values[8] / 200.0f, 0.0f, 3.0f);
    if (saturation != 1.0f) {
        constexpr float redLuma = 0.213f;
        constexpr float greenLuma = 0.715f;
        constexpr float blueLuma = 0.072f;
        const float inverse = 1.0f - saturation;
        postConcat(matrix, {
            inverse * redLuma + saturation, inverse * greenLuma, inverse * blueLuma, 0.0f, 0.0f,
            inverse * redLuma, inverse * greenLuma + saturation, inverse * blueLuma, 0.0f, 0.0f,
            inverse * redLuma, inverse * greenLuma, inverse * blueLuma + saturation, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f});
    }
    if (values[10] != 0.0f) {
        const float red = 1.0f + values[10] / 100.0f * 0.3f;
        const float blue = 1.0f - values[10] / 100.0f * 0.3f;
        postConcat(matrix, channelMatrix(red, 1.0f, blue));
    }
    if (values[11] != 0.0f) {
        const float green = 1.0f - values[11] / 100.0f * 0.3f;
        const float redBlue = 1.0f + values[11] / 100.0f * 0.15f;
        postConcat(matrix, channelMatrix(redBlue, green, redBlue));
    }
    const float offsets[] = {
        values[3] / 100.0f * 80.0f,
        values[4] / 100.0f * 60.0f,
        values[5] / 100.0f * 70.0f,
        values[6] / 100.0f * 50.0f
    };
    for (float offset : offsets) {
        if (offset != 0.0f) {
            postConcat(matrix, channelMatrix(1.0f, 1.0f, 1.0f, offset, offset, offset));
        }
    }
    if (values[9] != 0.0f) {
        const float scale = 1.0f + values[9] / 100.0f * 0.25f;
        const float offset = (1.0f - scale) * 128.0f;
        postConcat(matrix, channelMatrix(scale, scale, scale, offset, offset, offset));
    }
    return matrix;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_moody_moodyvideoeditor_utils_AdjustmentNative_nativeProcessBitmap(
        JNIEnv* env, jobject, jobject bitmap, jfloatArray adjustmentArray,
        jfloatArray matrixArray) {
    if (bitmap == nullptr || adjustmentArray == nullptr || matrixArray == nullptr ||
        env->GetArrayLength(adjustmentArray) < 12 ||
        env->GetArrayLength(matrixArray) < 20) {
        return JNI_FALSE;
    }

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        info.width == 0 || info.height == 0) {
        return JNI_FALSE;
    }

    jfloat adjustments[12];
    jfloat matrix[20];
    env->GetFloatArrayRegion(adjustmentArray, 0, 12, adjustments);
    env->GetFloatArrayRegion(matrixArray, 0, 20, matrix);
    if (env->ExceptionCheck()) return JNI_FALSE;
    for (float value : adjustments) {
        if (!std::isfinite(value)) return JNI_FALSE;
    }
    const bool hasColorChannels = std::any_of(
        adjustments, adjustments + 9, [](float value) { return value != 0.0f; });
    const float sharpen = std::clamp(adjustments[9] / 25.0f, 0.0f, 2.0f);
    const float noise = std::clamp(adjustments[10] / 100.0f, 0.0f, 1.0f);
    const float vignette = std::clamp(adjustments[11] / 100.0f, -1.0f, 1.0f);

    void* rawPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &rawPixels) != ANDROID_BITMAP_RESULT_SUCCESS ||
        rawPixels == nullptr) {
        return JNI_FALSE;
    }

    try {
        const uint32_t width = info.width;
        const uint32_t height = info.height;
        const uint32_t stride = info.stride;
        auto* pixels = static_cast<uint8_t*>(rawPixels);
        constexpr float identity[20] = {
            1.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        };
        bool applyColorMatrix = false;
        for (size_t index = 0; index < 20; ++index) {
            if (!std::isfinite(matrix[index])) {
                AndroidBitmap_unlockPixels(env, bitmap);
                return JNI_FALSE;
            }
            if (matrix[index] != identity[index]) applyColorMatrix = true;
        }

        for (uint32_t y = 0; y < height; ++y) {
            auto* row = pixels + static_cast<size_t>(y) * stride;
            for (uint32_t x = 0; x < width; ++x) {
                auto* pixel = row + static_cast<size_t>(x) * 4;
                const float alpha = pixel[3];
                if (alpha == 0.0f) continue;
                float channels[3] = {
                    static_cast<float>(pixel[0]),
                    static_cast<float>(pixel[1]),
                    static_cast<float>(pixel[2])
                };
                if (applyColorMatrix) applyMatrix(channels, alpha, matrix);
                applyColorChannels(channels, adjustments, hasColorChannels);
                for (int channel = 0; channel < 3; ++channel) {
                    pixel[channel] = static_cast<uint8_t>(
                        std::clamp(std::lround(channels[channel]), 0L, 255L));
                }
            }
        }

        if (sharpen > 0.001f && width >= 3 && height >= 3) {
            std::vector<uint8_t> source(static_cast<size_t>(width) * height * 4);
            for (uint32_t y = 0; y < height; ++y) {
                std::copy_n(pixels + static_cast<size_t>(y) * stride,
                            static_cast<size_t>(width) * 4,
                            source.data() + static_cast<size_t>(y) * width * 4);
            }
            constexpr int kernel[3] = {1, 2, 1};
            for (uint32_t y = 1; y + 1 < height; ++y) {
                auto* row = pixels + static_cast<size_t>(y) * stride;
                for (uint32_t x = 1; x + 1 < width; ++x) {
                    const size_t centerIndex =
                        (static_cast<size_t>(y) * width + x) * 4;
                    const auto* center = source.data() + centerIndex;
                    float sums[3] = {0.0f, 0.0f, 0.0f};
                    float weightSum = 0.0f;
                    for (int dy = -1; dy <= 1; ++dy) {
                        for (int dx = -1; dx <= 1; ++dx) {
                            const size_t neighborIndex =
                                (static_cast<size_t>(static_cast<int>(y) + dy) * width +
                                 static_cast<uint32_t>(static_cast<int>(x) + dx)) * 4;
                            const auto* neighbor = source.data() + neighborIndex;
                            const float alpha = static_cast<float>(neighbor[3]) / 255.0f;
                            const float weight = static_cast<float>(
                                kernel[dy + 1] * kernel[dx + 1]) * alpha;
                            weightSum += weight;
                            sums[0] += static_cast<float>(neighbor[0]) * weight;
                            sums[1] += static_cast<float>(neighbor[1]) * weight;
                            sums[2] += static_cast<float>(neighbor[2]) * weight;
                        }
                    }
                    if (weightSum <= 0.0f) continue;
                    const float values[3] = {
                        static_cast<float>(center[0]),
                        static_cast<float>(center[1]),
                        static_cast<float>(center[2])
                    };
                    auto* output = row + static_cast<size_t>(x) * 4;
                    for (int index = 0; index < 3; ++index) {
                        const float blurred = sums[index] / weightSum;
                        output[index] = static_cast<uint8_t>(std::clamp(
                            std::lround(values[index] +
                                        (values[index] - blurred) * sharpen),
                            0L, 255L));
                    }
                }
            }
        }

        if (noise > 0.0f || vignette != 0.0f) {
            const float centerX = static_cast<float>(width) / 2.0f;
            const float centerY = static_cast<float>(height) / 2.0f;
            const float maxRadius = std::max(
                std::sqrt(centerX * centerX + centerY * centerY), 1.0f);
            for (uint32_t y = 0; y < height; ++y) {
                auto* row = pixels + static_cast<size_t>(y) * stride;
                const float normalizedY = (static_cast<float>(y) - centerY) / maxRadius;
                for (uint32_t x = 0; x < width; ++x) {
                    auto* pixel = row + static_cast<size_t>(x) * 4;
                    if (pixel[3] == 0) continue;
                    int channels[3] = {
                        static_cast<int>(pixel[0]),
                        static_cast<int>(pixel[1]),
                        static_cast<int>(pixel[2])
                    };
                    if (vignette != 0.0f) {
                        const float normalizedX = (static_cast<float>(x) - centerX) / maxRadius;
                        const float radius = std::sqrt(normalizedX * normalizedX +
                                                       normalizedY * normalizedY);
                        const float edge = smoothstep(0.35f, 0.98f, radius);
                        const float blend = vignette < 0.0f
                            ? edge * std::fabs(vignette) * 0.95f
                            : edge * vignette * 0.5f;
                        const float target = vignette < 0.0f ? 0.0f : 255.0f;
                        for (int& component : channels) {
                            component = static_cast<int>(std::clamp(
                                std::lround(component + (target - component) * blend),
                                0L, 255L));
                        }
                    }
                    if (noise > 0.0f) {
                        uint32_t hash = (y * width + x) ^ 0x9E3779B9U;
                        hash = (hash ^ (hash >> 13U)) * 0x5BD1E995U;
                        hash ^= hash >> 15U;
                        const float random = static_cast<float>(hash & 0xFFFFU) / 65535.0f;
                        if (random > 0.45f - noise * 0.30f) {
                            const int gray = static_cast<int>((hash >> 16U) & 0xFFU);
                            const float blend = std::clamp(
                                noise * 0.7f * (0.4f + random * 0.6f), 0.0f, 0.85f);
                            for (int& component : channels) {
                                component = static_cast<int>(std::clamp(
                                    std::lround(component + (gray - component) * blend),
                                    0L, 255L));
                            }
                        }
                    }
                    for (int channel = 0; channel < 3; ++channel) {
                        pixel[channel] = static_cast<uint8_t>(channels[channel]);
                    }
                }
            }
        }
    } catch (const std::exception& error) {
        AndroidBitmap_unlockPixels(env, bitmap);
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "Native frame processing failed: %s", error.what());
        return JNI_FALSE;
    } catch (...) {
        AndroidBitmap_unlockPixels(env, bitmap);
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Native frame processing failed");
        return JNI_FALSE;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_moody_moodyvideoeditor_utils_AdjustmentNative_nativeBuildColorMatrix(
        JNIEnv* env, jobject, jfloatArray adjustmentArray) {
    if (adjustmentArray == nullptr || env->GetArrayLength(adjustmentArray) < 24) {
        return nullptr;
    }
    float values[24];
    env->GetFloatArrayRegion(adjustmentArray, 0, 24, values);
    if (env->ExceptionCheck()) return nullptr;
    for (float value : values) {
        if (!std::isfinite(value)) return nullptr;
    }
    const ColorMatrix matrix = buildAdjustmentMatrix(values);
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(matrix.size()));
    if (result == nullptr) return nullptr;
    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(matrix.size()), matrix.data());
    return env->ExceptionCheck() ? nullptr : result;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_moody_moodyvideoeditor_utils_AdjustmentNative_nativeBuildCubeLut(
        JNIEnv* env, jobject, jfloatArray adjustmentArray, jint lutSize) {
    if (adjustmentArray == nullptr || env->GetArrayLength(adjustmentArray) < 24 ||
        lutSize < 2 || lutSize > 128) {
        return nullptr;
    }
    float values[24];
    env->GetFloatArrayRegion(adjustmentArray, 0, 24, values);
    if (env->ExceptionCheck()) return nullptr;
    for (float value : values) {
        if (!std::isfinite(value)) return nullptr;
    }

    const size_t entryCount = static_cast<size_t>(lutSize) * lutSize * lutSize;
    if (entryCount > static_cast<size_t>(std::numeric_limits<jsize>::max()) / 3) {
        return nullptr;
    }
    try {
        std::vector<jfloat> lut(entryCount * 3);
        const ColorMatrix matrix = buildAdjustmentMatrix(values);
        const float pixelAdjustments[12] = {
            values[15], values[16], values[17], values[18], values[19], values[20],
            values[21], values[22], values[23], values[13], values[12], values[14]
        };
        const bool hasColorChannels = std::any_of(
            pixelAdjustments, pixelAdjustments + 9,
            [](float value) { return value != 0.0f; });
        const float maxIndex = static_cast<float>(lutSize - 1);
        size_t index = 0;
        for (int blue = 0; blue < lutSize; ++blue) {
            for (int green = 0; green < lutSize; ++green) {
                for (int red = 0; red < lutSize; ++red) {
                    float channels[3] = {
                        static_cast<float>(red) * 255.0f / maxIndex,
                        static_cast<float>(green) * 255.0f / maxIndex,
                        static_cast<float>(blue) * 255.0f / maxIndex
                    };
                    applyMatrix(channels, 255.0f, matrix.data());
                    applyColorChannels(channels, pixelAdjustments, hasColorChannels);
                    lut[index++] = std::clamp(channels[0], 0.0f, 255.0f);
                    lut[index++] = std::clamp(channels[1], 0.0f, 255.0f);
                    lut[index++] = std::clamp(channels[2], 0.0f, 255.0f);
                }
            }
        }

        jfloatArray result = env->NewFloatArray(static_cast<jsize>(lut.size()));
        if (result == nullptr) return nullptr;
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(lut.size()), lut.data());
        return env->ExceptionCheck() ? nullptr : result;
    } catch (const std::exception& error) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Native LUT generation failed: %s",
                            error.what());
        return nullptr;
    } catch (...) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Native LUT generation failed");
        return nullptr;
    }
}
