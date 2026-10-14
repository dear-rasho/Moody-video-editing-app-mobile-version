cmake_minimum_required(VERSION 3.22.1)
project(moodywhisper C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# Stub version headers
file(WRITE ${CMAKE_CURRENT_BINARY_DIR}/ggml-version.h
        "#pragma once
#define GGML_VERSION \"v1.6.2\"
#define GGML_COMMIT \"unknown\"
")

file(WRITE ${CMAKE_CURRENT_BINARY_DIR}/whisper-version.h
        "#pragma once
#define WHISPER_VERSION \"v1.6.2\"
")

# ─── Whisper + GGML sources (explicit list, no glob) ───
set(WHISPER_SOURCES
        ${WHISPER_DIR}/whisper.cpp
        ${WHISPER_DIR}/ggml.c
        ${WHISPER_DIR}/ggml-alloc.c
        ${WHISPER_DIR}/ggml-backend.c
        ${WHISPER_DIR}/ggml-quants.c
)

add_library(whisper STATIC ${WHISPER_SOURCES})

target_include_directories(whisper PUBLIC
        ${WHISPER_DIR}
        ${CMAKE_CURRENT_BINARY_DIR}
)

target_compile_definitions(whisper PRIVATE
        GGML_USE_CPU
        WHISPER_VERSION="v1.6.2"
        GGML_VERSION="v1.6.2"
)

target_compile_options(whisper PRIVATE
        -O3
        -DNDEBUG
        -Wno-everything
)

# ─── JNI bridge ───
add_library(moodywhisper SHARED
        whisper_jni.cpp
)

target_link_libraries(moodywhisper
        whisper
        android
        log
)