cmake_minimum_required(VERSION 3.22.1)
project(protosdk_native)

add_library(root_checker SHARED root_checker.cpp)
add_library(emulator_checker SHARED emulator_checker.cpp)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(root_checker ${log-lib})
target_link_libraries(emulator_checker ${log-lib} ${android-lib})
