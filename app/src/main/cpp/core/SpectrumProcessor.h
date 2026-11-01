#pragma once

#include <cstddef>
#include <cstdint>
#include <complex>
#include <vector>

namespace moody::visualizer {

class SpectrumProcessor {
public:
    SpectrumProcessor(int fftSize, int bandCount);

    int fftSize() const noexcept { return fftSize_; }
    int bandCount() const noexcept { return bandCount_; }

    std::vector<float> analyze(const int16_t* pcm, size_t sampleCount, int hopSize);
    static float bassEnergy(const float* bands, size_t count) noexcept;
    void smoothInPlace(float* bands, size_t count, float smoothing);
    void reset() noexcept;

private:
    void analyzeFrame(const int16_t* pcm, size_t sampleCount, float* output);

    int fftSize_;
    int bandCount_;
    std::vector<float> window_;
    std::vector<std::complex<float>> fftWork_;
    std::vector<float> smoothed_;
    std::vector<int> bandStart_;
    std::vector<int> bandEnd_;
};

}  // namespace moody::visualizer
