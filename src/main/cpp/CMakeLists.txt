cmake_minimum_required(VERSION 3.22.1)

project(byedpi_native)

if (NOT "${CMAKE_BUILD_TYPE}" STREQUAL "Debug")
    set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
    add_compile_options(-O3 -flto -fno-exceptions -fno-rtti)
    add_link_options(-flto -Wl,--gc-sections,--strip-all,--build-id=sha1)
endif ()

file(GLOB BYE_DPI_SRC byedpi/*.c)
list(REMOVE_ITEM BYE_DPI_SRC ${CMAKE_CURRENT_SOURCE_DIR}/byedpi/win_service.c)

add_library(byedpi SHARED ${BYE_DPI_SRC} native-lib.c)

target_include_directories(byedpi PRIVATE byedpi)

target_compile_definitions(byedpi PRIVATE ANDROID_APP)

target_compile_options(byedpi PRIVATE -std=c99 -Wall -Wno-unused -Wno-unused-parameter)

target_link_libraries(byedpi PRIVATE android log)
