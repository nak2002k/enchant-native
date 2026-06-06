add_library(protocol_objects OBJECT
    handshake.cpp
    x3dh.cpp
    prekey.cpp
    identity_store.cpp
    identity_trust_store.cpp
    session_archive.cpp
    triple_ratchet.cpp
    triple_ratchet_outgoing.cpp
    safety_number.cpp
    fingerprint.cpp
    session_state.cpp
    session_record.cpp
    session_builder.cpp
    session.cpp
    forward_secrecy.cpp
    error_aggregator.cpp
    exceptions.cpp
    mismatched_devices.cpp
    stale_devices.cpp
    recipient_errors.cpp
    multi_device.cpp
    multi_recipient.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../../src/i_identity_store.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../../src/i_session_store.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../../src/device_id.cpp
)

target_include_directories(protocol_objects
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../../src
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/../../include
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
    PUBLIC ${CMAKE_CURRENT_BINARY_DIR}/../proto/generated
    PUBLIC ${protobuf_INCLUDE_DIRS}
)

target_link_libraries(protocol_objects
    PUBLIC primitives_objects
    PUBLIC secure_objects
    PUBLIC PkgConfig::protobuf
)

set_property(TARGET protocol_objects PROPERTY POSITION_INDEPENDENT_CODE ON)