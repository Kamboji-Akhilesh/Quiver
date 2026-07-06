cmake_minimum_required(VERSION 3.22.1)
project(quiver-llama CXX)

set(CMAKE_CXX_STANDARD 17)

# Slim the llama.cpp build for on-device use.
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)

# llama.cpp submodule lives beside this file.
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp ${CMAKE_BINARY_DIR}/llama.cpp)

add_library(llama-android SHARED llama-android.cpp)

find_library(log-lib log)
target_link_libraries(llama-android llama ggml android ${log-lib})
