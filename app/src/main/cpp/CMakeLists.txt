cmake_minimum_required(VERSION 3.22.1)
project("agentx_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization and binary stripping for release and production targets
add_compile_options(
    -O3
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
    -fno-rtti
)

add_link_options(
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
)

add_library(
    agentx_llama
    SHARED
    llama_bridge.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    agentx_llama
    ${log-lib}
)
