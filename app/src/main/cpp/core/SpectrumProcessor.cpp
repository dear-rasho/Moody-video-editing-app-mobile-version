#include "SpectrumProcessor.h"

#include <algorithm>
#include <cmath>
#include <stdexcept>

namespace moody::visualizer {
namespace {
constexpr float kPi = 3.14159265358979323846f;
constexpr float kFloorDb = -72.0f;
}

SpectrumProcessor::SpectrumProcessor(int fftSize, int bandCount)
        : fftSize_(fftSize), bandCount_(bandCount) {
    if (fftSize < 256 || (fftSize & (fftSize - 1)) != 0 ||
        bandCount < 1 || bandCount > fftSize / 2) {
        throw std::invalid_argument("Invalid FFT or band size");
    }

    window_.resize(static_cast<size_t>(fftSize_));
    fftWork_.resize(static_cast<size_t>(fftSize_));
    smoothed_.assign(static_cast<size_t>(bandCount_), 0.0f);
    bandStart_.resize(static_cast<size_t>(bandCount_));
    bandEnd_.resize(static_cast<size_t>(bandCount_));

    for (int i = 0; i < fftSize_; ++i) {
        window_[static_cast<size_t>(i)] =
                0.5f - 0.5f * std::cos(2.0f * kPi * i / (fftSize_ - 1));
    }
    for (int band = 0; band < bandCount_; ++band) {
        const float low = std::pow(fftSize_ * 0.5f + 1.0f,
                                   static_cast<float>(band) / bandCount_);
        const float high = std::pow(fftSize_ * 0.5f + 1.0f,
                                    static_cast<float>(band + 1) / bandCount_);
        bandStart_[static_cast<size_t>(band)] =
                std::max(1, static_cast<int>(std::floor(low)));
        bandEnd_[static_cast<size_t>(band)] =
                std::max(bandStart_[static_cast<size_t>(band)] + 1,
                         static_cast<int>(std::ceil(high)));
    }
}

std::vector<float> SpectrumProcessor::analyze(
        const int16_t* pcm, size_t sampleCount, int hopSize) {
    if (pcm == nullptr || sampleCount == 0 || hopSize < 1) return {};
    const size_t frameCount = sampleCount <= static_cast<size_t>(fftSize_)
            ? 1
            : 1 + (sampleCount - static_cast<size_t>(fftSize_) +
                   static_cast<size_t>(hopSize) - 1) / static_cast<size_t>(hopSize);
    std::vector<float> result(frameCount * static_cast<size_t>(bandCount_));
    std::vector<float> frame(static_cast<size_t>(bandCount_));
    for (size_t frameIndex = 0; frameIndex < frameCount; ++frameIndex) {
        const size_t offset = frameIndex * static_cast<size_t>(hopSize);
        const size_t available = offset < sampleCount
                ? std::min(static_cast<size_t>(fftSize_), sampleCount - offset)
                : 0;
        analyzeFrame(pcm + std::min(offset, sampleCount), available, frame.data());
        std::copy(frame.begin(), frame.end(),
                  result.begin() + frameIndex * static_cast<size_t>(bandCount_));
        if (available == 0) break;
    }
    return result;
}

float SpectrumProcessor::bassEnergy(const float* bands, size_t count) noexcept {
    if (bands == nullptr || count == 0) return 0.0f;
    const float logMaxBin = std::log(513.0f);
    const size_t first = std::min(
            count - 1,
            static_cast<size_t>(count * std::log(2.0f) / logMaxBin));
    const size_t last = std::clamp(
            static_cast<size_t>(count * std::log(14.0f) / logMaxBin),
            first + 1, count);
    float energy = 0.0f;
    for (size_t index = first; index < last; ++index) {
        const float value = std::isfinite(bands[index])
                ? std::clamp(bands[index], 0.0f, 1.0f) : 0.0f;
        energy += value;
    }
    return energy / static_cast<float>(last - first);
}

void SpectrumProcessor::analyzeFrame(
        const int16_t* pcm, size_t sampleCount, float* output) {
    for (int i = 0; i < fftSize_; ++i) {
        const float sample = pcm == nullptr || static_cast<size_t>(i) >= sampleCount
                ? 0.0f
                : static_cast<float>(pcm[i]) * (1.0f / 32768.0f);
        fftWork_[static_cast<size_t>(i)] = {sample * window_[static_cast<size_t>(i)], 0.0f};
    }

    for (int i = 1, j = 0; i < fftSize_; ++i) {
        int bit = fftSize_ >> 1;
        for (; (j & bit) != 0; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) std::swap(fftWork_[static_cast<size_t>(i)],
                             fftWork_[static_cast<size_t>(j)]);
    }
    for (int length = 2; length <= fftSize_; length <<= 1) {
        const float angle = -2.0f * kPi / length;
        const std::complex<float> step(std::cos(angle), std::sin(angle));
        for (int base = 0; base < fftSize_; base += length) {
            std::complex<float> twiddle(1.0f, 0.0f);
            const int half = length >> 1;
            for (int i = 0; i < half; ++i) {
                auto& even = fftWork_[static_cast<size_t>(base + i)];
                auto& odd = fftWork_[static_cast<size_t>(base + i + half)];
                const std::complex<float> value = odd * twiddle;
                const std::complex<float> original = even;
                even = original + value;
                odd = original - value;
                twiddle *= step;
            }
        }
    }

    const int maxBin = fftSize_ / 2;
    for (int band = 0; band < bandCount_; ++band) {
        const int start = std::min(bandStart_[static_cast<size_t>(band)], maxBin);
        const int end = std::min(bandEnd_[static_cast<size_t>(band)], maxBin + 1);
        float magnitude = 0.0f;
        int count = 0;
        for (int bin = start; bin < end; ++bin) {
            magnitude += std::abs(fftWork_[static_cast<size_t>(bin)]) * (2.0f / fftSize_);
            ++count;
        }
        magnitude = count > 0 ? magnitude / count : 0.0f;
        const float db = 20.0f * std::log10(std::max(magnitude, 1.0e-7f));
        output[band] = std::clamp((db - kFloorDb) / -kFloorDb, 0.0f, 1.0f);
    }
}

void SpectrumProcessor::smoothInPlace(float* bands, size_t count, float smoothing) {
    if (bands == nullptr || count != smoothed_.size()) return;
    const float factor = std::clamp(smoothing, 0.0f, 0.95f);
    for (size_t i = 0; i < count; ++i) {
        const float input = std::isfinite(bands[i])
                ? std::clamp(bands[i], 0.0f, 1.0f) : 0.0f;
        smoothed_[i] = smoothed_[i] * factor + input * (1.0f - factor);
        bands[i] = smoothed_[i];
    }
}

void SpectrumProcessor::reset() noexcept {
    std::fill(smoothed_.begin(), smoothed_.end(), 0.0f);
}

}  // namespace moody::visualizer
