cmake_minimum_required(VERSION 3.22.1)
project("mymeeting")

add_library(nv21_converter SHARED nv21_converter.cpp)
find_library(log-lib log)
target_link_libraries(nv21_converter android log ${log-lib})