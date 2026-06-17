cmake_minimum_required(VERSION 3.20)
project(enchantcrypto_jni_build LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(PkgConfig REQUIRED)
pkg_check_modules(sodium_host REQUIRED IMPORTED_TARGET libsodium)

if(ANDROID)
    set(SODIUM_PREBUILT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/${ANDROID_ABI}")
    set(JNILIBS_SODIUM "${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/jniLibs/${ANDROID_ABI}/libsodium.so")
    if(EXISTS "${JNILIBS_SODIUM}")
        set(SODIUM_SO "${JNILIBS_SODIUM}")
    elseif(EXISTS "${SODIUM_PREBUILT_DIR}/libsodium.so")
        set(SODIUM_SO "${SODIUM_PREBUILT_DIR}/libsodium.so")
    endif()
endif()

set(JNI ${CMAKE_CURRENT_SOURCE_DIR}/bindings/jni)

# Build JNI bridge .so — links against prebuilt libenchantcrypto.so
add_library(enchantcrypto_jni SHARED
    ${JNI}/enchant_generated_jni.cpp
)

# Find libenchantcrypto.so (prebuilt or from lib/ directory)
set(ENCHANT_CRYPTO_SEARCH_DIRS
    "${CMAKE_CURRENT_SOURCE_DIR}/../../lib/libenchantcrypto/build_jni"
    "${CMAKE_CURRENT_SOURCE_DIR}/../../lib/libenchantcrypto/build_shared"
    "${CMAKE_CURRENT_SOURCE_DIR}/../../libraries/libenchantcrypto/build_shared"
)

if(ANDROID AND DEFINED ANDROID_ABI)
    list(APPEND ENCHANT_CRYPTO_SEARCH_DIRS
        "${CMAKE_CURRENT_SOURCE_DIR}/../../lib/libenchantcrypto/prebuilt/${ANDROID_ABI}"
        "${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/jniLibs/${ANDROID_ABI}"
    )
endif()

find_library(ENCHANT_CRYPTO_LIB enchantcrypto PATHS ${ENCHANT_CRYPTO_SEARCH_DIRS} NO_DEFAULT_PATH)

if(ENCHANT_CRYPTO_LIB)
    message(STATUS "Linking JNI against: ${ENCHANT_CRYPTO_LIB}")
    add_library(enchantcrypto_prebuilt SHARED IMPORTED)
    set_target_properties(enchantcrypto_prebuilt PROPERTIES
        IMPORTED_LOCATION "${ENCHANT_CRYPTO_LIB}"
    )
    target_link_libraries(enchantcrypto_jni PRIVATE enchantcrypto_prebuilt)
else()
    message(FATAL_ERROR "libenchantcrypto.so not found! Build it first with -DENCHANT_BUILD_SHARED=ON")
endif()

# Include paths for api.h
target_include_directories(enchantcrypto_jni
    PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/../../lib/libenchantcrypto/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/../../lib/libenchantcrypto/src"
        "${CMAKE_CURRENT_SOURCE_DIR}/../../lib/libenchantcrypto/third_party/boringssl/include"
        ${LIBSODIUM_INCLUDE_DIRS}
)

if(ANDROID)
    target_include_directories(enchantcrypto_jni PRIVATE ${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include)
endif()

if(ANDROID AND EXISTS "${SODIUM_SO}")
    message(STATUS "Linking against sodium: ${SODIUM_SO}")
    target_link_libraries(enchantcrypto_jni PRIVATE "${SODIUM_SO}")
else()
    target_link_libraries(enchantcrypto_jni PRIVATE PkgConfig::sodium_host)
endif()

if(NOT ANDROID AND EXISTS "/opt/homebrew/opt/openjdk@17/include/jni.h")
    target_include_directories(enchantcrypto_jni PRIVATE
        /opt/homebrew/opt/openjdk@17/include
        /opt/homebrew/opt/openjdk@17/include/darwin
    )
endif()

set_target_properties(enchantcrypto_jni PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

if(ANDROID)
    set(JNILIBS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/jniLibs/${ANDROID_ABI}")
    add_custom_command(TARGET enchantcrypto_jni POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E make_directory ${JNILIBS_DIR}
        COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:enchantcrypto_jni> ${JNILIBS_DIR}/
        COMMENT "Copying enchantcrypto_jni.so to jniLibs/${ANDROID_ABI}"
    )
endif()
