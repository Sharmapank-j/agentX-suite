cmake_minimum_required(VERSION 3.22.1)
project("agentx_llama" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Build the upstream llama.cpp runtime as part of the Android native build.
# Pin an immutable upstream commit so model execution is reproducible.
include(FetchContent)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG d38bea34227bbf9069d62fdc239c98ab6e05435a
    GIT_SHALLOW FALSE
)
FetchContent_MakeAvailable(llama_cpp)

find_library(log-lib log)
add_library(agentx_llama SHARED llama_jni.cpp)
target_include_directories(agentx_llama PRIVATE ${llama_cpp_SOURCE_DIR}/include)
target_link_libraries(agentx_llama PRIVATE llama ${log-lib})
