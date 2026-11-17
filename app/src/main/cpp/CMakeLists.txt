# file: app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.18.1)
project("nativebridge")

add_library(nativebridge SHARED native-lib.cpp)

# Подключаем готовые библиотеки из jniLibs
set(JNI_LIBS_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

add_library(byedpi SHARED IMPORTED)
set_target_properties(byedpi PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libbyedpi.so)

add_library(hev_socks5_tunnel SHARED IMPORTED)
set_target_properties(hev_socks5_tunnel PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libhev-socks5-tunnel.so)

# Линкуем
target_link_libraries(nativebridge byedpi hev_socks5_tunnel log)
