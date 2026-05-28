# ============================================================================
# CMake 最低版本要求
# ============================================================================
cmake_minimum_required(VERSION 3.20)

# ============================================================================
# 全局属性设置：启用 IDE 文件夹分类（如 VS 的文件夹视图）
# ============================================================================
set_property(GLOBAL PROPERTY USE_FOLDERS ON)

# ============================================================================
# 项目名称定义
# ============================================================================
project(cpptrader)

# ============================================================================
# C++ 标准设置
# ============================================================================
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ============================================================================
# Doxygen 文档生成工具配置
# 如果系统中安装了 Doxygen，则添加一个自定义目标用于生成文档
# ============================================================================
find_package(Doxygen)
if(DOXYGEN_FOUND)
  set(DOXYGEN "doxygen")
  if(NOT TARGET ${DOXYGEN})
    add_custom_command(OUTPUT "Doxyfile" COMMAND ${DOXYGEN_EXECUTABLE} "Doxyfile" WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/documents")
    add_custom_target(${DOXYGEN} DEPENDS "Doxyfile")
    set_target_properties(${DOXYGEN} PROPERTIES FOLDER "doxygen")
  endif()
endif()

# ============================================================================
# 自定义 CMake 模块搜索路径
# ============================================================================
set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")

# ============================================================================
# C++ 标准设置（必须在包含 SetCompilerFeatures 之前设置）
# ============================================================================
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ============================================================================
# 引入编译器特性、警告选项、平台特性及系统信息检测模块
# ============================================================================
include(SetCompilerFeatures)
include(SetCompilerWarnings)
include(SetPlatformFeatures)
include(SystemInformation)

# ============================================================================
# 添加子模块目录
# ============================================================================
add_subdirectory("modules")

# ============================================================================
# asio 依赖（header-only，优先使用本地目录，否则通过 FetchContent 获取）
# ============================================================================
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/asio/asio/include/asio.hpp")
  set(asio_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/asio")
  message(STATUS "Using local asio from: ${asio_SOURCE_DIR}")
else()
  include(FetchContent)
  FetchContent_Declare(
    asio
    GIT_REPOSITORY https://github.com/chriskohlhoff/asio.git
    GIT_TAG asio-1-30-2
    GIT_SHALLOW TRUE
  )
  FetchContent_MakeAvailable(asio)
endif()

# asio 需要 Threads
find_package(Threads REQUIRED)

# OpenSSL for HMAC signature verification
find_package(OpenSSL REQUIRED)

# ============================================================================
# 链接库列表：添加公共基础库
# ============================================================================
list(APPEND LINKLIBS cppcommon)

# ============================================================================
# Windows 平台补充系统库链接
# ============================================================================
if(WIN32)
  list(APPEND LINKLIBS rpcrt4 userenv ws2_32 mswsock)
endif()

# ============================================================================
# 系统头文件搜索路径
# ============================================================================
include_directories(SYSTEM "${CMAKE_CURRENT_SOURCE_DIR}/modules")

# ============================================================================
# 构建核心库 cpptrader
# 收集所有头文件、内联文件和源文件，编译为静态/动态库
# ============================================================================
file(GLOB_RECURSE LIB_HEADER_FILES "include/*.h" "source/*.h")
file(GLOB_RECURSE LIB_INLINE_FILES "include/*.inl" "source/*.inl")
file(GLOB_RECURSE LIB_SOURCE_FILES "include/*.cpp" "source/*.cpp")

# 排除 protocol 可执行文件源文件
list(FILTER LIB_HEADER_FILES EXCLUDE REGEX ".*/source/protocol/server_main\\.cpp$")
list(FILTER LIB_INLINE_FILES EXCLUDE REGEX ".*/source/protocol/server_main\\.cpp$")
list(FILTER LIB_SOURCE_FILES EXCLUDE REGEX ".*/source/protocol/server_main\\.cpp$")

add_library(cpptrader ${LIB_HEADER_FILES} ${LIB_INLINE_FILES} ${LIB_SOURCE_FILES})
set_target_properties(cpptrader PROPERTIES COMPILE_FLAGS "${PEDANTIC_COMPILE_FLAGS}" FOLDER "libraries")
target_include_directories(cpptrader PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_include_directories(cpptrader PRIVATE "${asio_SOURCE_DIR}/asio/include")
target_compile_definitions(cpptrader PRIVATE ASIO_STANDALONE)
target_link_libraries(cpptrader ${LINKLIBS} Threads::Threads OpenSSL::SSL OpenSSL::Crypto)
list(APPEND INSTALL_TARGETS cpptrader)
list(APPEND LINKLIBS cpptrader)

# ============================================================================
# 非模块模式下的附加组件：示例、基准测试、测试、安装
# 当 CPPTRADER_MODULE 未定义时，构建这些附加目标
# ============================================================================
if(NOT CPPTRADER_MODULE)

  # ============================================================================
  # 示例程序（Examples）
  # 遍历 examples/ 目录下的所有 .cpp 文件，每个文件编译为一个独立的可执行文件
  # ============================================================================
  file(GLOB EXAMPLE_HEADER_FILES "examples/*.h")
  file(GLOB EXAMPLE_INLINE_FILES "examples/*.inl")
  file(GLOB EXAMPLE_SOURCE_FILES RELATIVE "${CMAKE_CURRENT_SOURCE_DIR}/examples" "examples/*.cpp")
  foreach(EXAMPLE_SOURCE_FILE ${EXAMPLE_SOURCE_FILES})
    string(REGEX REPLACE "(.*)\\.cpp" "\\1" EXAMPLE_NAME ${EXAMPLE_SOURCE_FILE})
    set(EXAMPLE_TARGET "cpptrader-example-${EXAMPLE_NAME}")
    add_executable(${EXAMPLE_TARGET} ${EXAMPLE_HEADER_FILES} ${EXAMPLE_INLINE_FILES} "examples/${EXAMPLE_SOURCE_FILE}")
    set_target_properties(${EXAMPLE_TARGET} PROPERTIES COMPILE_FLAGS "${PEDANTIC_COMPILE_FLAGS}" FOLDER "examples")
    target_link_libraries(${EXAMPLE_TARGET} ${LINKLIBS})
    list(APPEND INSTALL_TARGETS ${EXAMPLE_TARGET})
    list(APPEND INSTALL_TARGETS_PDB ${EXAMPLE_TARGET})
  endforeach()

  # ============================================================================
  # 基准测试程序（Benchmarks / Performance）
  # 遍历 performance/ 目录下的所有 .cpp 文件，每个文件编译为一个独立的可执行文件
  # 额外链接 cppbenchmark 库
  # ============================================================================
  file(GLOB BENCHMARK_HEADER_FILES "performance/*.h")
  file(GLOB BENCHMARK_INLINE_FILES "performance/*.inl")
  file(GLOB BENCHMARK_SOURCE_FILES RELATIVE "${CMAKE_CURRENT_SOURCE_DIR}/performance" "performance/*.cpp")
  foreach(BENCHMARK_SOURCE_FILE ${BENCHMARK_SOURCE_FILES})
    string(REGEX REPLACE "(.*)\\.cpp" "\\1" BENCHMARK_NAME ${BENCHMARK_SOURCE_FILE})
    set(BENCHMARK_TARGET "cpptrader-performance-${BENCHMARK_NAME}")
    add_executable(${BENCHMARK_TARGET} ${BENCHMARK_HEADER_FILES} ${BENCHMARK_INLINE_FILES} "performance/${BENCHMARK_SOURCE_FILE}")
    set_target_properties(${BENCHMARK_TARGET} PROPERTIES COMPILE_FLAGS "${PEDANTIC_COMPILE_FLAGS}" FOLDER "performance")
    target_link_libraries(${BENCHMARK_TARGET} ${LINKLIBS} cppbenchmark)
    list(APPEND INSTALL_TARGETS ${BENCHMARK_TARGET})
    list(APPEND INSTALL_TARGETS_PDB ${BENCHMARK_TARGET})
  endforeach()

  # ============================================================================
  # 单元测试程序（Tests）
  # 编译 tests/ 目录下的所有源文件为一个可执行文件，使用 Catch2 测试框架
  # ============================================================================
  file(GLOB TESTS_HEADER_FILES "tests/*.h")
  file(GLOB TESTS_INLINE_FILES "tests/*.inl")
  file(GLOB TESTS_SOURCE_FILES "tests/*.cpp")
  add_executable(cpptrader-tests ${TESTS_HEADER_FILES} ${TESTS_INLINE_FILES} ${TESTS_SOURCE_FILES})
  set_target_properties(cpptrader-tests PROPERTIES COMPILE_FLAGS "${PEDANTIC_COMPILE_FLAGS}" FOLDER "tests")
  target_include_directories(cpptrader-tests PRIVATE Catch2)
  target_link_libraries(cpptrader-tests ${LINKLIBS} Catch2)
  list(APPEND INSTALL_TARGETS cpptrader-tests)
  list(APPEND INSTALL_TARGETS_PDB cpptrader-tests)

  # ============================================================================
  # CTest 配置：注册测试用例，按字典序执行并显示每个用例的执行时间
  # ============================================================================
  enable_testing()
  add_test(cpptrader-tests cpptrader-tests --durations yes --order lex)

  # ============================================================================
  # Protocol 服务端可执行文件
  # ============================================================================
  add_executable(cpptrader-protocol-server
    source/protocol/server_main.cpp
  )
  target_include_directories(cpptrader-protocol-server PRIVATE "${asio_SOURCE_DIR}/asio/include")
  target_compile_definitions(cpptrader-protocol-server PRIVATE ASIO_STANDALONE)
  set_target_properties(cpptrader-protocol-server PROPERTIES COMPILE_FLAGS "${PEDANTIC_COMPILE_FLAGS}" FOLDER "protocol")
  target_link_libraries(cpptrader-protocol-server ${LINKLIBS} Threads::Threads)
  list(APPEND INSTALL_TARGETS cpptrader-protocol-server)
  list(APPEND INSTALL_TARGETS_PDB cpptrader-protocol-server)

  # ============================================================================
  # 安装规则：将所有目标安装到项目源码目录下的 bin/ 文件夹
  # RUNTIME：可执行文件（.exe/.dll）
  # LIBRARY：动态库（.so/.dylib）
  # ARCHIVE：静态库（.lib/.a）
  # ============================================================================
  install(TARGETS ${INSTALL_TARGETS}
    RUNTIME DESTINATION "${PROJECT_SOURCE_DIR}/bin"
    LIBRARY DESTINATION "${PROJECT_SOURCE_DIR}/bin"
    ARCHIVE DESTINATION "${PROJECT_SOURCE_DIR}/bin")

  # ============================================================================
  # 安装 PDB 调试符号文件（仅 MSVC 编译器）
  # 使用生成器表达式 $<TARGET_PDB_FILE> 获取每个目标的 PDB 文件路径
  # ============================================================================
  if(MSVC)
    foreach(INSTALL_TARGET_PDB ${INSTALL_TARGETS_PDB})
      install(FILES $<TARGET_PDB_FILE:${INSTALL_TARGET_PDB}> DESTINATION "${PROJECT_SOURCE_DIR}/bin")
    endforeach()
  endif()

endif()
