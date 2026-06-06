add_library(zk_objects OBJECT
    zkcredential/presentation.cpp
    zkgroup/client_zk_profile.cpp
)

target_include_directories(zk_objects
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/../../include
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/..
    PRIVATE ${sodium_INCLUDE_DIRS}
)
