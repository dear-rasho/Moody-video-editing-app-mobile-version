#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <exception>
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
    return std::clamp(value / 10.0f, -5.0f, 5.0f);
}

void rgbToHsv(float red, float green, float blue, float* hue, float* saturation) {
    const float maximum = std::max(red, std::max(green, blue));
    const float minimum = std::min(red, std::min(green, blue));
    const float delta = maximum - minimum;
    *saturation = maximum <= 0.0f ? 0.0f : delta / maximum;
    if (delta <= 0.0f) {
        *hue = 0.0f;
    } else if (maximum == red) {
        *hue = 60.0f * std::fmod((green - blue) / delta + (green < blue ? 6.0f : 0.0f), 6.0f);
    } else if (maximum == green) {
        *hue = 60.0f * ((blue - red) / delta + 2.0f);
    } else {
        *hue = 60.0f * ((red - green) / delta + 4.0f);
    }
}

void applyMatrix(uint32_t* pixel, const float* matrix) {
    const float alpha = static_cast<float>((*pixel >> 24) & 0xFF);
    const float red = static_cast<float>((*pixel >> 16) & 0xFF);
    const float green = static_cast<float>((*pixel >> 8) & 0xFF);
    const float blue = static_cast<float>(*pixel & 0xFF);
    const auto channel = [](float value) {
        return static_cast<uint32_t>(std::clamp(std::lround(value), 0L, 255L));
    };
    const uint32_t outRed = channel(matrix[0] * red + matrix[1] * green +
                                    matrix[2] * blue + matrix[3] * alpha + matrix[4]);
    const uint32_t outGreen = channel(matrix[5] * red + matrix[6] * green +
                                      matrix[7] * blue + matrix[8] * alpha + matrix[9]);
    const uint32_t outBlue = channel(matrix[10] * red + matrix[11] * green +
                                     matrix[12] * blue + matrix[13] * alpha + matrix[14]);
    *pixel = (static_cast<uint32_t>(alpha) << 24) | (outRed << 16) |
             (outGreen << 8) | outBlue;
}

void applyColorChannels(uint32_t* pixel, const float* adjustments) {
    float red = static_cast<float>((*pixel >> 16) & 0xFF);
    float green = static_cast<float>((*pixel >> 8) & 0xFF);
    float blue = static_cast<float>(*pixel & 0xFF);
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

    const auto channel = [](float value) {
        return static_cast<uint32_t>(std::clamp(std::lround(value), 0L, 255L));
    };
    *pixel = (*pixel & 0xFF000000U) | (channel(red) << 16) |
             (channel(green) << 8) | channel(blue);
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

    void* rawPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &rawPixels) != ANDROID_BITMAP_RESULT_SUCCESS ||
        rawPixels == nullptr) {
        return JNI_FALSE;
    }

    try {
        const uint32_t width = info.width;
        const uint32_t height = info.height;
        const uint32_t stride = info.stride / sizeof(uint32_t);
        auto* pixels = static_cast<uint32_t*>(rawPixels);
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
                uint32_t& pixel = row[x];
                if ((pixel >> 24) == 0) continue;
                if (applyColorMatrix) applyMatrix(&pixel, matrix);
                applyColorChannels(&pixel, adjustments);
            }
        }

        const float sharpen = std::clamp(adjustments[9] / 25.0f, 0.0f, 2.0f);
        if (sharpen > 0.001f && width >= 3 && height >= 3) {
            std::vector<uint32_t> source(static_cast<size_t>(width) * height);
            for (uint32_t y = 0; y < height; ++y) {
                std::copy_n(pixels + static_cast<size_t>(y) * stride, width,
                            source.data() + static_cast<size_t>(y) * width);
            }
            constexpr int kernel[3] = {1, 2, 1};
            for (uint32_t y = 1; y + 1 < height; ++y) {
                auto* row = pixels + static_cast<size_t>(y) * stride;
                for (uint32_t x = 1; x + 1 < width; ++x) {
                    const uint32_t center = source[static_cast<size_t>(y) * width + x];
                    float sums[3] = {0.0f, 0.0f, 0.0f};
                    float weightSum = 0.0f;
                    for (int dy = -1; dy <= 1; ++dy) {
                        for (int dx = -1; dx <= 1; ++dx) {
                            const uint32_t neighbor = source[
                                static_cast<size_t>(static_cast<int>(y) + dy) * width +
                                static_cast<uint32_t>(static_cast<int>(x) + dx)];
                            const float alpha = static_cast<float>((neighbor >> 24) & 0xFF) / 255.0f;
                            const float weight = static_cast<float>(
                                kernel[dy + 1] * kernel[dx + 1]) * alpha;
                            weightSum += weight;
                            sums[0] += static_cast<float>((neighbor >> 16) & 0xFF) * weight;
                            sums[1] += static_cast<float>((neighbor >> 8) & 0xFF) * weight;
                            sums[2] += static_cast<float>(neighbor & 0xFF) * weight;
                        }
                    }
                    if (weightSum <= 0.0f) continue;
                    const float values[3] = {
                        static_cast<float>((center >> 16) & 0xFF),
                        static_cast<float>((center >> 8) & 0xFF),
                        static_cast<float>(center & 0xFF)
                    };
                    const auto channel = [&](int index) {
                        const float blurred = sums[index] / weightSum;
                        return static_cast<uint32_t>(std::clamp(
                            std::lround(values[index] +
                                        (values[index] - blurred) * sharpen),
                            0L, 255L));
                    };
                    row[x] = (center & 0xFF000000U) | (channel(0) << 16) |
                             (channel(1) << 8) | channel(2);
                }
            }
        }

        const float noise = std::clamp(adjustments[10] / 100.0f, 0.0f, 1.0f);
        const float vignette = std::clamp(adjustments[11] / 100.0f, -1.0f, 1.0f);
        if (noise > 0.0f || vignette != 0.0f) {
            const float centerX = static_cast<float>(width) / 2.0f;
            const float centerY = static_cast<float>(height) / 2.0f;
            const float maxRadius = std::max(
                std::sqrt(centerX * centerX + centerY * centerY), 1.0f);
            for (uint32_t y = 0; y < height; ++y) {
                auto* row = pixels + static_cast<size_t>(y) * stride;
                const float normalizedY = (static_cast<float>(y) - centerY) / maxRadius;
                for (uint32_t x = 0; x < width; ++x) {
                    const uint32_t pixel = row[x];
                    const int alpha = static_cast<int>((pixel >> 24) & 0xFF);
                    if (alpha == 0) continue;
                    int channels[3] = {
                        static_cast<int>((pixel >> 16) & 0xFF),
                        static_cast<int>((pixel >> 8) & 0xFF),
                        static_cast<int>(pixel & 0xFF)
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
                    row[x] = (static_cast<uint32_t>(alpha) << 24) |
                             (static_cast<uint32_t>(channels[0]) << 16) |
                             (static_cast<uint32_t>(channels[1]) << 8) |
                             static_cast<uint32_t>(channels[2]);
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
