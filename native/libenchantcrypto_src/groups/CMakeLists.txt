add_library(groups_objects OBJECT
    mls_crypto.cpp
    mls_tree_kem.cpp
    mls_state_machine.cpp
    storage_service.cpp
    groups_v2.cpp
    key_transparency.cpp
    sender_key.cpp
)

target_include_directories(groups_objects
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/..
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
    PUBLIC ${CMAKE_CURRENT_BINARY_DIR}/../proto/generated
    PUBLIC ${protobuf_INCLUDE_DIRS}
)

target_link_libraries(groups_objects
    PUBLIC primitives_objects secure_objects
    PUBLIC PkgConfig::protobuf
)

set_property(TARGET groups_objects PROPERTY POSITION_INDEPENDENT_CODE ON)