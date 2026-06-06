# For more information about configuring CMake, read the documentation:
# https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.22.1)

project("local_ai_studio")

# Specifies the library name and kind (SHARED)
add_library(
    local_ai_studio
    SHARED
    native-lib.cpp
)

# Searches for a specified prebuilt library and stores the path as a variable.
find_library(
    log-lib
    log
)

# Specifies libraries CMake should link to your target library.
target_link_libraries(
    local_ai_studio
    ${log-lib}
)
