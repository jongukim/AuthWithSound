LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := fftw3-prebuilt
LOCAL_SRC_FILES := libfftw3.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := analyzer
LOCAL_SRC_FILES := fftw.c
LOCAL_STATIC_LIBRARIES += fftw3-prebuilt
LOCAL_CFLAGS += -O3
include $(BUILD_SHARED_LIBRARY)

