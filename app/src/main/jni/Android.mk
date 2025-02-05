LOCAL_PATH := $(call my-dir)

# Prebuilt libyuv
include $(CLEAR_VARS)
LOCAL_MODULE := libyuv_static
LOCAL_SRC_FILES := third_party/libyuv/android/obj/local/armeabi-v7a/libyuv_static.a
include $(PREBUILT_STATIC_LIBRARY)

# Your library
include $(CLEAR_VARS)
LOCAL_MODULE := nv21_converter
LOCAL_SRC_FILES := nv21_converter.cpp
LOCAL_STATIC_LIBRARIES := libyuv_static
LOCAL_CFLAGS += -I$(LOCAL_PATH)/third_party/libyuv/include
include $(BUILD_SHARED_LIBRARY)