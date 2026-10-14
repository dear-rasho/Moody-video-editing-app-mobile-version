#include <jni.h>
#include <android/log.h>
#include <string>
#include <sstream>
#include <vector>
#include "whisper.h"
#include <unistd.h>
#include <cmath>

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct WhisperContext {
    struct whisper_context *ctx = nullptr;
    bool is_loaded = false;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_moody_moodyvideoeditor_utils_WhisperEngine_nativeInit(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath) {

    const char *path = env->GetStringUTFChars(modelPath, nullptr);

    auto *wctx = new WhisperContext();

    // ═══════════════════════════════════════════════════════════
    //  whisper.cpp v1.6.2 CONTEXT PARAMS
    //  (use_mmap, dtw_*, etc. later versions mein hain)
    // ═══════════════════════════════════════════════════════════
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    cparams.flash_attn = false;

    wctx->ctx = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (wctx->ctx == nullptr) {
        LOGE("❌ Failed to load whisper model");
        delete wctx;
        return 0;
    }

    wctx->is_loaded = true;
    LOGI("✅ Whisper model loaded successfully");
    return reinterpret_cast<jlong>(wctx);
}

JNIEXPORT void JNICALL
Java_com_moody_moodyvideoeditor_utils_WhisperEngine_nativeRelease(
        JNIEnv *env,
        jobject /* this */,
        jlong ptr) {

    auto *wctx = reinterpret_cast<WhisperContext *>(ptr);
    if (wctx != nullptr) {
        if (wctx->ctx != nullptr) {
            whisper_free(wctx->ctx);
        }
        delete wctx;
    }
}

JNIEXPORT jstring JNICALL
Java_com_moody_moodyvideoeditor_utils_WhisperEngine_nativeTranscribe(
        JNIEnv *env,
        jobject /* this */,
        jlong ptr,
        jfloatArray pcmArray,
        jstring language) {

    auto *wctx = reinterpret_cast<WhisperContext *>(ptr);
    if (wctx == nullptr || !wctx->is_loaded) {
        return env->NewStringUTF("{\"error\":\"not_loaded\"}");
    }

    jsize pcmSize = env->GetArrayLength(pcmArray);
    jfloat *pcmData = env->GetFloatArrayElements(pcmArray, nullptr);

    if (pcmData == nullptr || pcmSize <= 0) {
        env->ReleaseFloatArrayElements(pcmArray, pcmData, JNI_ABORT);
        return env->NewStringUTF("{\"error\":\"empty_pcm\"}");
    }

    // ─── Audio sanity check ───
    float peak = 0.0f;
    double sumSq = 0.0;
    for (jsize i = 0; i < pcmSize; i++) {
        float v = pcmData[i];
        float a = v < 0 ? -v : v;
        if (a > peak) peak = a;
        sumSq += (double)v * (double)v;
    }
    double rms = (pcmSize > 0) ? sqrt(sumSq / (double)pcmSize) : 0.0;
    LOGE("📊 PCM chunk: size=%d, peak=%.4f, rms=%.4f, dur=%.2fs",
         (int)pcmSize, peak, rms, (double)pcmSize / 16000.0);

    if (peak < 0.001f) {
        LOGE("🔇 Silent chunk — skipping inference");
        env->ReleaseFloatArrayElements(pcmArray, pcmData, JNI_ABORT);
        return env->NewStringUTF("{\"error\":\"silent\"}");
    }

    struct whisper_full_params params =
            whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

    std::string langStr;
    if (language != nullptr) {
        const char *langChars = env->GetStringUTFChars(language, nullptr);
        langStr = std::string(langChars);
        env->ReleaseStringUTFChars(language, langChars);
    }

    bool autoDetect = (langStr == "auto" || langStr.empty());
    if (autoDetect) {
        params.language = "auto";
        params.detect_language = true;
    } else {
        params.language = langStr.c_str();
        params.detect_language = false;
    }

    // ═══════════════════════════════════════════════════════════
    //  CRASH-SAFE THREADING
    //    Mobile: 2 threads = stable. 8 threads = crash.
    // ═══════════════════════════════════════════════════════════
    int cores = (int)sysconf(_SC_NPROCESSORS_ONLN);
    int useThreads = (cores >= 4) ? 2 : 1;   // ← 2 max for stability
    params.n_threads = useThreads;

    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;

    // ─── Safe speedups ───
    params.token_timestamps = true;
    params.suppress_blank = true;

    // ─── Greedy (fastest) ───
    params.greedy.best_of = 1;

    LOGE("▶️ whisper_full: threads=%d, samples=%d, lang=%s",
         useThreads, (int)pcmSize,
         autoDetect ? "auto" : langStr.c_str());

    int result = whisper_full(wctx->ctx, params, pcmData, pcmSize);

    env->ReleaseFloatArrayElements(pcmArray, pcmData, JNI_ABORT);

    if (result != 0) {
        LOGE("❌ whisper_full error code: %d", result);
        return env->NewStringUTF("{\"error\":\"inference_failed\"}");
    }

    std::string detectedLang = "unknown";
    if (autoDetect) {
        int langId = whisper_full_lang_id(wctx->ctx);
        if (langId >= 0) {
            const char *s = whisper_lang_str(langId);
            if (s != nullptr) detectedLang = s;
        }
    } else {
        detectedLang = langStr;
    }

    std::ostringstream json;
    json << "{\"lang\":\"" << detectedLang << "\",\"segments\":[";

    int nSegments = whisper_full_n_segments(wctx->ctx);
    bool first = true;

    LOGE("✅ whisper_full produced %d segments", nSegments);

    for (int i = 0; i < nSegments; i++) {
        const char *text = whisper_full_get_segment_text(wctx->ctx, i);
        int64_t t0 = whisper_full_get_segment_t0(wctx->ctx, i);
        int64_t t1 = whisper_full_get_segment_t1(wctx->ctx, i);

        std::string safeText = (text != nullptr) ? std::string(text) : std::string("");
        std::string escaped;
        escaped.reserve(safeText.size());
        for (char c : safeText) {
            switch (c) {
                case '"':  escaped += "\\\""; break;
                case '\\': escaped += "\\\\"; break;
                case '\n': escaped += "\\n";  break;
                case '\r': escaped += "\\r";  break;
                case '\t': escaped += "\\t";  break;
                default:
                    if (static_cast<unsigned char>(c) >= 0x20) {
                        escaped += c;
                    }
            }
        }

        if (!first) json << ",";
        first = false;

        json << "{\"start\":" << (t0 * 10)
             << ",\"end\":"   << (t1 * 10)
             << ",\"text\":\"" << escaped << "\"}";
    }

    json << "]}";

    return env->NewStringUTF(json.str().c_str());
}

} // extern "C"