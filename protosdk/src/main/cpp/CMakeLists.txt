cmake_minimum_required(VERSION 3.22.1)
project(protosdk_native)

add_library(root_checker SHARED root_checker.cpp)
add_library(emulator_checker SHARED emulator_checker.cpp)
add_library(gpu_checker SHARED gpu_checker.cpp)
add_library(cpu_checker SHARED cpu_checker.cpp)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles-lib GLESv3)
find_library(dl-lib dl)

target_link_libraries(root_checker ${log-lib})
target_link_libraries(emulator_checker ${log-lib} ${android-lib})
target_link_libraries(gpu_checker ${log-lib} ${android-lib} ${egl-lib} ${gles-lib} ${dl-lib})
target_link_libraries(cpu_checker ${log-lib})
