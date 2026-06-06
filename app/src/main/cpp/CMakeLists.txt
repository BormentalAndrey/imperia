cmake_minimum_required(VERSION 3.22.1)
project("retroemulator")

# Установка C++ стандарта
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Пути к библиотекам
set(WINLATOR_LIBS ${CMAKE_SOURCE_DIR}/libs)

# Добавление предкомпилированных библиотек
add_library(box86 SHARED IMPORTED)
set_target_properties(box86 PROPERTIES
    IMPORTED_LOCATION ${WINLATOR_LIBS}/${ANDROID_ABI}/libbox86.so
)

add_library(box64 SHARED IMPORTED)
set_target_properties(box64 PROPERTIES
    IMPORTED_LOCATION ${WINLATOR_LIBS}/${ANDROID_ABI}/libbox64.so
)

add_library(wine SHARED IMPORTED)
set_target_properties(wine PROPERTIES
    IMPORTED_LOCATION ${WINLATOR_LIBS}/${ANDROID_ABI}/libwine.so
)

# Исходные файлы
add_library(native-lib SHARED
    native-lib.cpp
    wine-bridge.cpp
    game-launcher.cpp
    config-manager.cpp
)

# Подключение заголовочных файлов
target_include_directories(native-lib PRIVATE
    ${CMAKE_SOURCE_DIR}/include
    ${WINLATOR_LIBS}/include
)

# Линковка библиотек
target_link_libraries(native-lib
    box86
    box64
    wine
    android
    log
)
