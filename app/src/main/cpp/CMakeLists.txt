cmake_minimum_required(VERSION 3.22.1)
project(input_mapper_uinput LANGUAGES CXX)

add_library(uinput_jni SHARED uinput_jni.cpp)

target_compile_features(uinput_jni PRIVATE cxx_std_20)
target_compile_options(uinput_jni PRIVATE -Wall -Wextra -Werror -fno-exceptions -fno-rtti)

# The JNI implementation deliberately uses no C++ standard-library facilities. This keeps the
# raw uinput boundary small and, on an on-device Termux build, avoids embedding a dependency on
# Termux's private libc++_shared.so. Normal Android Studio/NDK builds may still use the regular
# toolchain; the Termux path explicitly tells clang++ not to add a C++ standard library at link.
option(INPUTMAPPER_TERMUX "Build JNI library inside Termux for APK packaging" OFF)
if(INPUTMAPPER_TERMUX)
    target_link_options(uinput_jni PRIVATE -nostdlib++)
endif()
