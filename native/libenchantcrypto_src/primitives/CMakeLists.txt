add_library(primitives_objects OBJECT
    x25519.cpp
    ed25519.cpp
    xeddsa.cpp
    xchacha20.cpp
    hkdf.cpp
    hash.cpp
    hmac.cpp
    random.cpp
    base64.cpp
    constant_time.cpp
    aes.cpp
    aes_gcm_siv.cpp
    hpke.cpp
)

target_include_directories(primitives_objects
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../../src
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/../../include
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/boringssl/include
)

target_link_libraries(primitives_objects
    PUBLIC sodium
    PUBLIC crypto
    PRIVATE secure_objects
)

set_property(TARGET primitives_objects PROPERTY POSITION_INDEPENDENT_CODE ON)