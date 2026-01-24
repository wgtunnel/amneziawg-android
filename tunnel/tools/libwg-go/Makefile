# SPDX-License-Identifier: Apache-2.0
#
# Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.

BUILDDIR ?= $(CURDIR)/build
DESTDIR ?= $(CURDIR)/out

NDK_GO_ARCH_MAP_x86 := 386
NDK_GO_ARCH_MAP_x86_64 := amd64
NDK_GO_ARCH_MAP_arm := arm
NDK_GO_ARCH_MAP_arm64 := arm64
NDK_GO_ARCH_MAP_mips := mipsx
NDK_GO_ARCH_MAP_mips64 := mips64x

comma := ,
CLANG_FLAGS := --target=$(TARGET) --sysroot=$(SYSROOT)
export CGO_CFLAGS := $(CLANG_FLAGS) $(subst -mthumb,-marm,$(CFLAGS))
export CGO_LDFLAGS := $(CLANG_FLAGS) $(patsubst -Wl$(comma)--build-id=%,-Wl$(comma)--build-id=none,$(LDFLAGS)) -Wl,-soname=libam-go.so
export GOARCH := $(NDK_GO_ARCH_MAP_$(ANDROID_ARCH_NAME))
export GOOS := android
export CGO_ENABLED := 1

GO_VERSION := 1.25.5
GO_DIR := $(BUILDDIR)/go-$(GO_VERSION)

export GOROOT := $(GO_DIR)
export PATH := $(GO_DIR)/bin:$(PATH)

GO_PLATFORM := $(shell uname -s | tr '[:upper:]' '[:lower:]')-$(NDK_GO_ARCH_MAP_$(shell uname -m))
GO_TARBALL := go$(GO_VERSION).$(GO_PLATFORM).tar.gz
GO_HASH_darwin-amd64 := b69d51bce599e5381a94ce15263ae644ec84667a5ce23d58dc2e63e2c12a9f56
GO_HASH_darwin-arm64 := bed8ebe824e3d3b27e8471d1307f803fc6ab8e1d0eb7a4ae196979bd9b801dd3
GO_HASH_linux-amd64 := 9e9b755d63b36acf30c12a9a3fc379243714c1c6d3dd72861da637f336ebb35b

default: $(DESTDIR)/libam-go.so

$(GRADLE_USER_HOME)/caches/golang/$(GO_TARBALL):
	mkdir -p "$(dir $@)"
	flock "$@.lock" -c ' \
	[ -f "$@" ] && exit 0; \
	curl -o "$@.tmp" "https://dl.google.com/go/$(GO_TARBALL)" && \
	echo "$(GO_HASH_$(GO_PLATFORM))  $@.tmp" | sha256sum -c && \
	mv "$@.tmp" "$@"'

$(BUILDDIR)/go-$(GO_VERSION)/.prepared: $(GRADLE_USER_HOME)/caches/golang/$(GO_TARBALL)
	mkdir -p "$(dir $@)"
	flock "$@.lock" -c ' \
	[ -f "$@" ] && exit 0; \
	tar -C "$(dir $@)" --strip-components=1 -xzf "$^" && \
	cd "$(dir $@)/src/runtime" && sed -i '"'"'s/CLOCK_MONOTONIC/BOOTTIME/g'"'"' sys_linux_*.s && \
	cd "$(dir $@)/src/runtime" && sed -i '"'"'s/ $1/ $7/g'"'"' sys_linux_*.s && \
	cd "$(dir $@)/src/runtime" && sed -i '"'"'/libc_mach_absolute_time/a \//go:cgo_import_dynamic libc_mach_continuous_time mach_continuous_time "/usr/lib/libSystem.B.dylib"'"'"' sys_darwin.go && \
	cd "$(dir $@)/src/runtime" && sed -i '"'"'s/mach_absolute_time/mach_continuous_time/g'"'"' sys_darwin_amd64.s sys_darwin_arm64.s && \
	touch "$@"'

$(DESTDIR)/libam-go.so: export PATH := $(BUILDDIR)/go-$(GO_VERSION)/bin/:$(PATH)
$(DESTDIR)/libam-go.so: $(BUILDDIR)/go-$(GO_VERSION)/.prepared go.mod
	go build -tags linux,notest,cgo -ldflags="-X github.com/amnezia-vpn/amneziawg-go/ipc.socketDirectory=/data/data/$(ANDROID_PACKAGE_NAME)/cache/amneziawg -buildid=" -v -trimpath -buildvcs=false -o "$@" -buildmode c-shared github.com/amnezia-vpn/amneziawg-android

.DELETE_ON_ERROR: