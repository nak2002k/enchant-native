cmake_minimum_required(VERSION 3.20)

find_package(PkgConfig REQUIRED)
pkg_check_modules(protobuf REQUIRED IMPORTED_TARGET protobuf)

set(VEIL_PROTO_FILES
    ${CMAKE_CURRENT_SOURCE_DIR}/sealed_sender.proto
)

set(VEIL_PROTO_OUT_DIR ${CMAKE_CURRENT_BINARY_DIR}/generated)
file(MAKE_DIRECTORY ${VEIL_PROTO_OUT_DIR})

set(VEIL_PROTO_CC_FILES)

find_program(PROTOC_EXECUTABLE protoc REQUIRED)

set(PROTOC_FLAGS --cpp_out=${VEIL_PROTO_OUT_DIR} --experimental_allow_proto3_optional)
foreach(_inc ${protobuf_INCLUDE_DIRS})
    list(APPEND PROTOC_FLAGS -I${_inc})
endforeach()

foreach(PROTO_FILE ${VEIL_PROTO_FILES})
    get_filename_component(PROTO_NAME ${PROTO_FILE} NAME_WE)
    set(CC_FILE ${VEIL_PROTO_OUT_DIR}/${PROTO_NAME}.pb.cc)
    set(H_FILE ${VEIL_PROTO_OUT_DIR}/${PROTO_NAME}.pb.h)
    list(APPEND VEIL_PROTO_CC_FILES ${CC_FILE})

    add_custom_command(
        OUTPUT ${CC_FILE} ${H_FILE}
        COMMAND ${PROTOC_EXECUTABLE} ${PROTOC_FLAGS} -I${CMAKE_CURRENT_SOURCE_DIR} ${PROTO_FILE}
        DEPENDS ${PROTO_FILE}
        COMMENT "Generating C++ from veil proto ${PROTO_NAME}"
        VERBATIM
    )
endforeach()

add_library(veil_objects OBJECT
    chain.cpp
    envelope_state.cpp
    veil_session.cpp
    veil_types.cpp
    veil_v1.cpp
    veil_v2.cpp
    veil_async.cpp
    ${VEIL_PROTO_CC_FILES}
)

target_include_directories(veil_objects
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../../src
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../../src/veil
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/../../include
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
    PUBLIC ${VEIL_PROTO_OUT_DIR}
)

target_link_libraries(veil_objects
    PUBLIC sodium
    PUBLIC PkgConfig::protobuf
)

set_source_files_properties(${VEIL_PROTO_CC_FILES}
    PROPERTIES COMPILE_FLAGS "-Wno-nullability-extension"
)

set_property(TARGET veil_objects PROPERTY POSITION_INDEPENDENT_CODE ON)
