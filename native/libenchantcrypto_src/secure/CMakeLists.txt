add_library(secure_objects OBJECT
    buffer.cpp
)

target_include_directories(secure_objects
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../../src
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/../../include
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(secure_objects
    PUBLIC sodium
)

set_property(TARGET secure_objects PROPERTY POSITION_INDEPENDENT_CODE ON)