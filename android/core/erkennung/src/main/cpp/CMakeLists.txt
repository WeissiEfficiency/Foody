# JNI-Hülle der Fotoerkennung über llama.cpp (ADR 0017). Nur mit -Pfoody.nativ gebaut; das Ergebnis liegt in nativ/.
cmake_minimum_required(VERSION 3.22)
project(foody_erkennung LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp festgenagelt: b12024 ist die in Phase 0 auf dem 13R gemessene Version
include(FetchContent)
FetchContent_Declare(
    llama
    URL https://github.com/ggml-org/llama.cpp/archive/refs/tags/b12024.zip
    URL_HASH SHA256=ddfe74017392495c039716afaed7f0a00cf866f0c9e8f4ae92f9bea61aa5c8e2
)

# Nur Bibliotheken, statisch, nur CPU: keine Tools, kein OpenMP (eigener Thread-Pool von ggml), keine GPU/NPU
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)
set(MTMD_VIDEO OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
# Snapdragon 8 Gen 3 (OnePlus 13R): Int8-Matrix (i8mm), Skalarprodukt und fp16; das Tablet lädt die Bibliothek nie
set(GGML_CPU_ARM_ARCH "armv8.6-a+dotprod+fp16" CACHE STRING "" FORCE)
FetchContent_MakeAvailable(llama)

add_library(foody_erkennung SHARED foody_erkennung.cpp)
target_link_libraries(foody_erkennung PRIVATE llama mtmd log)
# Nur die JNI-Funktionen nach außen, ungenutzter Code fällt weg
target_link_options(foody_erkennung PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
