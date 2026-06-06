add_library(agents_objects OBJECT
    agent_id.cpp
    agent_session.cpp
)

target_include_directories(agents_objects
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/..
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
    PUBLIC ${CMAKE_CURRENT_BINARY_DIR}/../proto/generated
)

target_link_libraries(agents_objects
    PUBLIC primitives_objects secure_objects protocol_objects
)

set_property(TARGET agents_objects PROPERTY POSITION_INDEPENDENT_CODE ON)