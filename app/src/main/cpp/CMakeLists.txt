cmake_minimum_required(VERSION 3.22.1)

project("local_ai_studio")

# ===== SPRINT 1: Llama.cpp Integration =====

# Add native C++ sources
add_library(
    local_ai_studio
    SHARED
    native-lib.cpp
)

# Standard Android logging
find_library(log-lib log)
target_link_libraries(local_ai_studio ${log-lib})

# ===== Future: Link llama.cpp prebuilt =====
# add_library(llama STATIC IMPORTED)
# set_target_properties(llama PROPERTIES
#     IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/arm64-v8a/libllama.a
# )
# target_link_libraries(local_ai_studio llama)

# Set C++ standard
target_compile_options(local_ai_studio PRIVATE -std=c++17 -Wall -Wextra)
