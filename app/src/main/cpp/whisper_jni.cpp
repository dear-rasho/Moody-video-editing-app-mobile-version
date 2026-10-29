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
    LOGI("✅ Whisper model loaded");
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

    // ─── Sanity check ───
    float peak = 0.0f;
    for (jsize i = 0; i < pcmSize; i++) {
        float a = pcmData[i] < 0 ? -pcmData[i] : pcmData[i];
        if (a > peak) peak = a;
    }
    LOGE("📊 PCM: size=%d, peak=%.4f, dur=%.2fs",
         (int)pcmSize, peak, (double)pcmSize / 16000.0);

    if (peak < 0.001f) {
        LOGE("🔇 Silent PCM — skipping");
        env->ReleaseFloatArrayElements(pcmArray, pcmData, JNI_ABORT);
        return env->NewStringUTF("{\"error\":\"silent\"}");
    }

    struct whisper_full_params params =
            whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

    // ─── Language setup ───
    std::string langStr;
    if (language != nullptr) {
        const char *lc = env->GetStringUTFChars(language, nullptr);
        langStr = std::string(lc);
        env->ReleaseStringUTFChars(language, lc);
    }

    bool autoDetect = (langStr == "auto" || langStr.empty());

    // ═══════════════════════════════════════════════════════════
    //  AUTO-DETECT LANGUAGE (v1.6.2 correct way)
    //  - Set language = "auto" (string sentinel)
    //  - Set detect_language = true
    //  Whisper will detect from first ~30s and use that language
    // ═══════════════════════════════════════════════════════════
    if (autoDetect) {
        params.language = "auto";
        params.detect_language = true;
        LOGE("🌐 Auto-detect mode");
    } else {
        // Local string storage so pointer remains valid during whisper_full
        static thread_local std::string langStorage;
        langStorage = langStr;
        params.language = langStorage.c_str();
        params.detect_language = false;
        LOGE("🎯 Forced language: %s", langStorage.c_str());
    }
    // ═══════════════════════════════════════════════════════════
    //  FORCE NATIVE SCRIPT (not English translation)
    //  initial_prompt biases Whisper to output in the requested
    //  language's NATIVE SCRIPT.
    //  Example: "hi" → Devanagari, "ur" → Urdu script
    // ═══════════════════════════════════════════════════════════
    static thread_local std::string promptStorage;

    if (!autoDetect) {
        if (langStr == "hi") {
            // Hindi Devanagari script prompt
            promptStorage = "यह हिन्दी में लिखा गया है। कृपया हिन्दी में लिखें।";
            params.initial_prompt = promptStorage.c_str();
            LOGE("📝 Hindi prompt set");
        } else if (langStr == "ur") {
            // Urdu script prompt
            promptStorage = "یہ اردو میں لکھا گیا ہے۔ اردو میں لکھیں۔";
            params.initial_prompt = promptStorage.c_str();
            LOGE("📝 Urdu prompt set");
        } else if (langStr == "ar") {
            // Arabic script prompt
            promptStorage = "هذا نص عربي. الرجاء الكتابة بالعربية.";
            params.initial_prompt = promptStorage.c_str();
            LOGE("📝 Arabic prompt set");
        } else if (langStr == "fa") {
            promptStorage = "این متن به زبان فارسی است.";
            params.initial_prompt = promptStorage.c_str();
        }
        // Other languages — no prompt
    }
    // ═══════════════════════════════════════════════════════════
    //  LANGUAGE-SPECIFIC INITIAL PROMPT
    //  Biases Whisper to output in the NATIVE SCRIPT of that
    //  language instead of translating to English.
    // ═══════════════════════════════════════════════════════════
    if (!autoDetect) {
        if (langStr == "ur") {
            // Urdu script bias
            params.initial_prompt =
                    "یہ ایک اردو میں لکھا گیا متن ہے۔ براہ کرم اردو میں لکھیں۔";
        } else if (langStr == "hi") {
            // Hindi Devanagari bias
            params.initial_prompt =
                    "यह हिन्दी में लिखा गया पाठ है। कृपया हिन्दी में लिखें।";
        } else if (langStr == "ar") {
            // Arabic script bias
            params.initial_prompt =
                    "هذا نص مكتوب باللغة العربية. الرجاء الكتابة بالعربية.";
        } else if (langStr == "fa") {
            // Persian bias
            params.initial_prompt =
                    "این متن به زبان فارسی نوشته شده است.";
        }
        // Other languages don't need bias
    }
    // ─── Cap CPU parallelism to avoid oversubscribing mobile devices ───
    int cores = (int)sysconf(_SC_NPROCESSORS_ONLN);
    int useThreads = (cores >= 4) ? 4 : (cores >= 2 ? 2 : 1);
    params.n_threads = useThreads;

    // ─── Standard settings ───
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_context = false;
    params.single_segment = false;
    params.token_timestamps = false;
    params.suppress_blank = true;
    params.greedy.best_of = 1;

    LOGE("▶️ whisper_full: threads=%d, samples=%d, auto=%d",
         useThreads, (int)pcmSize, autoDetect ? 1 : 0);

    int result = whisper_full(wctx->ctx, params, pcmData, pcmSize);

    env->ReleaseFloatArrayElements(pcmArray, pcmData, JNI_ABORT);

    if (result != 0) {
        LOGE("❌ whisper_full failed: %d", result);
        return env->NewStringUTF("{\"error\":\"inference_failed\"}");
    }

    // ─── Get detected language ───
    std::string detectedLang = "unknown";
    int langId = whisper_full_lang_id(wctx->ctx);
    if (langId >= 0) {
        const char *s = whisper_lang_str(langId);
        if (s != nullptr) detectedLang = s;
    }

    int nSegments = whisper_full_n_segments(wctx->ctx);
    LOGE("✅ Result: lang=%s, segments=%d",
         detectedLang.c_str(), nSegments);

    // ─── Fallback if auto-detect produced nothing ───
    if (nSegments == 0 && autoDetect) {
        LOGE("⚠️ Auto-detect produced 0 segments — retrying with English");

        // Second pass with English
        params.language = "en";
        params.detect_language = false;

        jfloat *pcmData2 = env->GetFloatArrayElements(pcmArray, nullptr);
        result = whisper_full(wctx->ctx, params, pcmData2, pcmSize);
        env->ReleaseFloatArrayElements(pcmArray, pcmData2, JNI_ABORT);

        if (result == 0) {
            nSegments = whisper_full_n_segments(wctx->ctx);
            detectedLang = "en";
            LOGE("🇬🇧 English retry: %d segments", nSegments);
        }
    }

    // ─── Build JSON ───
    std::ostringstream json;
    json << "{\"lang\":\"" << detectedLang << "\",\"segments\":[";

    bool first = true;
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