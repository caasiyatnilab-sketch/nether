cmake_minimum_required(VERSION 3.22.1)

project("local_ai_studio")

# ===== Real llama.cpp integration =====
# llama.cpp is vendored as a git submodule (pinned tag b4585).
# Run `git submodule update --init --recursive` before building.

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

if(NOT EXISTS ${LLAMA_DIR}/CMakeLists.txt)
    message(FATAL_ERROR
        "llama.cpp submodule is missing. Run: git submodule update --init --recursive")
endif()

# Build only the library; skip tools/tests/examples/server for a lean APK.
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)
# Android emulators are x86_64; keep CPU backend portable (no -march=native).
set(GGML_NATIVE          OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} ${CMAKE_BINARY_DIR}/llama.cpp EXCLUDE_FROM_ALL)

# Native JNI bridge
add_library(
    local_ai_studio
    SHARED
    native-lib.cpp
)

find_library(log-lib log)

target_link_libraries(
    local_ai_studio
    llama
    ${log-lib}
)

target_compile_options(local_ai_studio PRIVATE -std=c++17 -Wall -Wextra)
