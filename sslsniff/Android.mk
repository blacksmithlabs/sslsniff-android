LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

APP_STL := stlport_static
TARGET_PLATFORM := android-14
TARGET_ARCH_ABI := armeabi-v7a

LOCAL_MODULE := sslsniff
LOCAL_SRC_FILES += sslsniff.cpp \
	FirefoxAddonUpdater.cpp \
	FirefoxUpdater.cpp \
	UpdateManager.cpp \
	Logger.cpp \
	FingerprintManager.cpp \
	SSLBridge.cpp \
	certificate/CertificateManager.cpp \
	certificate/AuthorityCertificateManager.cpp \
	certificate/TargetedCertificateManager.cpp \
	SSLConnectionManager.cpp \
	SessionCache.cpp \
	util/Destination.cpp \
	HTTPSBridge.cpp \
	http/OCSPDenier.cpp \
	http/HttpHeaders.cpp \
	http/HttpConnectionManager.cpp \
	http/HttpBridge.cpp

LOCAL_C_INCLUDES += $(LOCAL_PATH)/../dependencies/include \
	$(NDK_ROOT)/sources/cxx-stl/gnu-libstdc++/4.7/libs/armeabi-v7a/include \
	$(NDK_ROOT)/sources/cxx-stl/gnu-libstdc++/4.7/include
LOCAL_LDLIBS += -ldl \
	-L$(LOCAL_PATH)/../dependencies/lib \
	-lssl -lcrypto -llog4cpp \
	-lboost_system -lboost_filesystem -lboost_thread \
	-L$(NDK_ROOT)/sources/cxx-stl/gnu-libstdc++/4.7/libs/armeabi-v7a \
	-lgnustl_static -lsupc++ -lstdc++

LOCAL_CPP_FEATURES += exceptions rtti

#LOCAL_ALLOW_UNDEFINED_SYMBOLS := true

include $(BUILD_EXECUTABLE)

