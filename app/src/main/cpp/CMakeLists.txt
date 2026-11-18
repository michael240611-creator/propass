# file: app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.18.1)
project("nativebridge")

# Создаем нашу библиотеку-мост
add_library(nativebridge SHARED native-lib.cpp)

# Линкуем только с системным логгером (нам больше не нужно линковать .so напрямую)
target_link_libraries(nativebridge log)