find_package(PkgConfig REQUIRED)
pkg_check_modules(protobuf REQUIRED IMPORTED_TARGET protobuf)

set(PROTO_FILES
    ${CMAKE_CURRENT_SOURCE_DIR}/protobuf/storage.proto
    ${CMAKE_CURRENT_SOURCE_DIR}/protobuf/wire.proto
)

set(PROTO_OUT_DIR ${CMAKE_CURRENT_BINARY_DIR}/generated)
file(MAKE_DIRECTORY ${PROTO_OUT_DIR})

set(PROTO_CC_FILES)

find_program(PROTOC_EXECUTABLE protoc REQUIRED)

set(PROTOC_FLAGS --cpp_out=${PROTO_OUT_DIR} --experimental_allow_proto3_optional)
foreach(_inc ${protobuf_INCLUDE_DIRS})
    list(APPEND PROTOC_FLAGS -I${_inc})
endforeach()

foreach(PROTO_FILE ${PROTO_FILES})
    get_filename_component(PROTO_NAME ${PROTO_FILE} NAME_WE)
    set(CC_FILE ${PROTO_OUT_DIR}/${PROTO_NAME}.pb.cc)
    list(APPEND PROTO_CC_FILES ${CC_FILE})

    add_custom_command(
        OUTPUT ${CC_FILE}
        COMMAND ${PROTOC_EXECUTABLE} ${PROTOC_FLAGS} -I${CMAKE_CURRENT_SOURCE_DIR}/protobuf ${PROTO_FILE}
        DEPENDS ${PROTO_FILE}
        COMMENT "Generating C++ from proto ${PROTO_NAME}"
        VERBATIM
    )
endforeach()

add_library(proto_objects OBJECT
    varint.cpp
    buffer.cpp
    protobuf_serializer.cpp
    ${PROTO_CC_FILES}
)

target_include_directories(proto_objects
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../../src
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/../../include
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
    PUBLIC ${PROTO_OUT_DIR}
)

target_link_libraries(proto_objects
    PUBLIC sodium
    PUBLIC PkgConfig::protobuf
)

set_source_files_properties(${PROTO_CC_FILES}
    PROPERTIES COMPILE_FLAGS "-Wno-nullability-extension"
)

set_property(TARGET proto_objects PROPERTY POSITION_INDEPENDENT_CODE ON)