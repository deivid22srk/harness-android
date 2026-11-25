#!/bin/sh
# POSIX provisioning script executed inside the Alpine rootfs under PRoot.
#
# NOTE: The in-app provisioner (core/env/Provisioner.kt) performs the same steps
# in Kotlin, which is the path the app actually uses and which allows progress
# reporting and checksum handling in-process. This script documents the exact
# reference sequence and is useful for debugging a device with adb shell.
set -eu

echo "==> Configuring DNS"
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > /etc/resolv.conf
printf '127.0.0.1\tlocalhost\n::1\t\tlocalhost\n' > /etc/hosts

echo "==> Updating package index"
apk update

echo "==> Installing tools"
apk add --no-cache nodejs npm git ripgrep bash curl openssh-client ca-certificates

echo "==> Installing Pi coding agent"
npm install -g "@earendil-works/pi-coding-agent@${PI_VERSION:-1.6.0}"

echo "==> Versions"
node --version
pi --version
