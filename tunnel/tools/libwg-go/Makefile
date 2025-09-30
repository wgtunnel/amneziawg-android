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

GO_VERSION := 1.25.1
GO_DIR := $(BUILDDIR)/go-$(GO_VERSION)

export GOROOT := $(GO_DIR)
export PATH := $(GO_DIR)/bin:$(PATH)

GO_PLATFORM := $(shell uname -s | tr '[:upper:]' '[:lower:]')-$(NDK_GO_ARCH_MAP_$(shell uname -m))
GO_TARBALL := go$(GO_VERSION).$(GO_PLATFORM).tar.gz
GO_HASH_darwin-amd64 := 1d622468f767a1b9fe1e1e67bd6ce6744d04e0c68712adc689748bbeccb126bb
GO_HASH_darwin-arm64 := 68deebb214f39d542e518ebb0598a406ab1b5a22bba8ec9ade9f55fb4dd94a6c
GO_HASH_linux-amd64 := 7716a0d940a0f6ae8e1f3b3f4f36299dc53e31b16840dbd171254312c41ca12e

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
    patch -p1 -f -N -r- -d "$(dir $@)" < goruntime-boottime-over-monotonic.diff && \
    touch "$@"'

$(DESTDIR)/libam-go.so: export PATH := $(BUILDDIR)/go-$(GO_VERSION)/bin/:$(PATH)
$(DESTDIR)/libam-go.so: $(BUILDDIR)/go-$(GO_VERSION)/.prepared go.mod
	go build -tags linux,notest,cgo -ldflags="-X github.com/amnezia-vpn/amneziawg-go/ipc.socketDirectory=/data/data/$(ANDROID_PACKAGE_NAME)/cache/amneziawg -buildid=" -v -trimpath -buildvcs=false -o "$@" -buildmode c-shared github.com/amnezia-vpn/amneziawg-android

.DELETE_ON_ERROR: