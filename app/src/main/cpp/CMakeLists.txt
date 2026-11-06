cmake_minimum_required(VERSION 3.22.1)
project("orsted_sf11")

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

file(GLOB STOCKFISH_CORE "${CMAKE_SOURCE_DIR}/stockfish/*.cpp")
list(FILTER STOCKFISH_CORE EXCLUDE REGEX ".*/main\\.cpp$")
file(GLOB STOCKFISH_SYZYGY "${CMAKE_SOURCE_DIR}/stockfish/syzygy/*.cpp")

set(STOCKFISH_SRC ${STOCKFISH_CORE} ${STOCKFISH_SYZYGY})

add_library(orsted_engine SHARED native-lib.cpp ${STOCKFISH_SRC})

target_include_directories(orsted_engine PRIVATE
    ${CMAKE_SOURCE_DIR}/stockfish
    ${CMAKE_SOURCE_DIR}/stockfish/syzygy
)

target_compile_definitions(orsted_engine PRIVATE
    USE_PTHREADS
)

if(CMAKE_SIZEOF_VOID_P EQUAL 8)
    target_compile_definitions(orsted_engine PRIVATE IS_64BIT)
endif()

target_compile_options(orsted_engine PRIVATE
    -O2
    -Wall
)

find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(orsted_engine ${log-lib} ${android-lib})
