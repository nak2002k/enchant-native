add_library(enchant_zkp_objects OBJECT
    ${CMAKE_CURRENT_SOURCE_DIR}/../primitives/hmac.cpp
)

target_include_directories(enchant_zkp_objects PUBLIC
    ${CMAKE_SOURCE_DIR}/src
    ${SODIUM_INCLUDE_DIRS}
)

target_link_libraries(enchant_zkp_objects PUBLIC sodium)
