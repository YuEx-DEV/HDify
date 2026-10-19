cmake_minimum_required(VERSION 3.22.1)
project("hdify_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ncnn_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ncnn/${ANDROID_ABI}/lib/cmake/ncnn")
find_package(ncnn REQUIRED)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(android-lib android)

add_library(hdify_native SHARED
    gpu/gpu_info.cpp
    gpu/gpu_selftest.cpp
    gpu/gpu_benchmark.cpp
    log/native_log.cpp
    log/breadcrumbs.cpp
    log/crash_handler.cpp
    engines/realcugan/realcugan.cpp
    engines/realesrgan/realesrgan.cpp
    engines/rife/warp.cpp
    engines/rife/rife.cpp
    jni/jni_bridge.cpp
)

target_include_directories(hdify_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/gpu
    ${CMAKE_CURRENT_SOURCE_DIR}/log
    ${CMAKE_CURRENT_SOURCE_DIR}/engines/realcugan
    ${CMAKE_CURRENT_SOURCE_DIR}/engines/realesrgan
    ${CMAKE_CURRENT_SOURCE_DIR}/engines/rife
    ${CMAKE_CURRENT_SOURCE_DIR}/ncnn/${ANDROID_ABI}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/ncnn/${ANDROID_ABI}/include/ncnn
)

target_link_libraries(hdify_native PRIVATE
    ncnn
    ${log-lib}
    ${jnigraphics-lib}
    ${android-lib}
)
