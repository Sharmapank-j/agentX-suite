cmake_minimum_required(VERSION 3.22.1)

project("agentx_llama" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android log library
find_library(log-lib log)

# AgentX native JNI bridge
add_library(agentx_llama SHARED
    llama_jni.cpp
)

target_include_directories(agentx_llama PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(agentx_llama PRIVATE
    ${log-lib}
)
