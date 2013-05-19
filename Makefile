
ifndef NDK_ROOT
  NDK_ROOT := ~/android/ndk-r8e-i686/
endif
NDK_EXEC := ndk-build
NDK_ARGS := -B
APP_BUILD_DIR := ./libs/armeabi-v7a/
APP_LIB_DIR := ./dependencies/lib/
APP_EXEC := sslsniff
APP_DBG_EXEC := gdbserver
INSTALL_DIR := /system/sslsniff/

all: build

build: jni
	$(NDK_ROOT)$(NDK_EXEC) $(NDK_ARGS)

debug: jni
	$(NDK_ROOT)$(NDK_EXEC) $(NDK_ARGS) NDK_DEBUG=1

install: libs/armeabi-v7a/sslsniff
	adb shell su -c 'mount -o rw,remount /system; mkdir  $(INSTALL_DIR); chmod 777 $(INSTALL_DIR)'
	adb push $(APP_BUILD_DIR)$(APP_EXEC) $(INSTALL_DIR)$(APP_EXEC)
	adb push $(APP_LIB_DIR)libcrypto.so $(INSTALL_DIR)libcrypto.so
	adb push $(APP_LIB_DIR)libssl.so $(INSTALL_DIR)libssl.so
	adb shell su -c ln -s $(INSTALL_DIR)$(APP_EXEC) /system/bin/$(APP_EXEC)
	if [ -a $(APP_BUILD_DIR)$(APP_DBG_EXEC) ]; then \
		adb push $(APP_BUILD_DIR)$(APP_DBG_EXEC) $(INSTALL_DIR)$(APP_DBG_EXEC) ; \
		adb shell su -c ln -s $(APP_BUILD_DIR)$(APP_DBG_EXEC) /system/bin/$(APP_DBG_EXEC) ; \
	fi
	adb shell su -c 'mount -o ro,remount /system'

clean:
	rm -rf libs obj

