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

set(SRC ${CMAKE_CURRENT_SOURCE_DIR}/libenchantcrypto_src)
set(INC ${CMAKE_CURRENT_SOURCE_DIR}/libenchantcrypto_include)
set(JNI ${CMAKE_CURRENT_SOURCE_DIR}/bindings/jni)

# Build all C++ sources into the JNI .so directly
add_library(enchantcrypto_jni SHARED
    ${JNI}/enchant_base_jni.cpp
    ${SRC}/secure/buffer.cpp
    ${SRC}/primitives/x25519.cpp
    ${SRC}/primitives/ed25519.cpp
    ${SRC}/primitives/xeddsa.cpp
    ${SRC}/primitives/xchacha20.cpp
    ${SRC}/primitives/hkdf.cpp
    ${SRC}/primitives/hash.cpp
    ${SRC}/primitives/hmac.cpp
    ${SRC}/primitives/random.cpp
    ${SRC}/primitives/base64.cpp
    ${SRC}/primitives/constant_time.cpp
    ${SRC}/primitives/aes_stub.cpp
    ${SRC}/api.cpp
)

target_include_directories(enchantcrypto_jni
    PRIVATE
        ${INC}
        ${SRC}
        ${SRC}/primitives
        ${sodium_host_INCLUDE_DIRS}
)

if(ANDROID)
    target_include_directories(enchantcrypto_jni PRIVATE ${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/include)
endif()

if(ANDROID AND EXISTS "${SODIUM_SO}")
    message(STATUS "Linking against: ${SODIUM_SO}")
    target_link_libraries(enchantcrypto_jni PRIVATE "${SODIUM_SO}")
else()
    target_link_libraries(enchantcrypto_jni PRIVATE PkgConfig::sodium_host)
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
