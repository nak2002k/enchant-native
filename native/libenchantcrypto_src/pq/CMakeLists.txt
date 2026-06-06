add_library(pq_objects OBJECT
    ml_kem.cpp
)

target_include_directories(pq_objects
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../../src
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/../../include
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(pq_objects
    PUBLIC sodium
)

if(ENABLE_POST_QUANTUM)
    target_include_directories(pq_objects
        PRIVATE ${kyber_ref_SOURCE_DIR}/ref
    )
    target_sources(pq_objects PRIVATE
        ${kyber_ref_SOURCE_DIR}/ref/cbd.c
        ${kyber_ref_SOURCE_DIR}/ref/fips202.c
        ${kyber_ref_SOURCE_DIR}/ref/indcpa.c
        ${kyber_ref_SOURCE_DIR}/ref/kem.c
        ${kyber_ref_SOURCE_DIR}/ref/ntt.c
        ${kyber_ref_SOURCE_DIR}/ref/poly.c
        ${kyber_ref_SOURCE_DIR}/ref/polyvec.c
        ${kyber_ref_SOURCE_DIR}/ref/reduce.c
        ${kyber_ref_SOURCE_DIR}/ref/symmetric-shake.c
        ${kyber_ref_SOURCE_DIR}/ref/verify.c
        ${CMAKE_CURRENT_SOURCE_DIR}/randombytes.c
    )
    target_compile_definitions(pq_objects PRIVATE KYBER_K=3)
    target_compile_definitions(pq_objects PRIVATE ENCHANT_ENABLE_PQ)
endif()

set_property(TARGET pq_objects PROPERTY POSITION_INDEPENDENT_CODE ON)
