#!/bin/sh
# Build PRoot + loader for Android (aarch64, Bionic) with the NDK.
#
# Why the NDK and not musl/Alpine?
#   PRoot is a ptrace-based syscall translator. A musl-static PRoot can enter
#   an Alpine rootfs, but on Android it crashes (SIGSYS) the moment a real
#   dynamic process (Node.js) starts, because the seccomp filter it installs
#   interacts badly with Bionic's signal handling. Termux ships PRoot built
#   with the Android toolchain for exactly this reason, so we cross-compile the
#   Termux PRoot fork (which carries the Android patches) with the NDK.
#
# Outputs (all placed in $OUT, copied into jniLibs/arm64-v8a/):
#   libproot.so           - the PRoot binary (self-contained: Bionic system
#                           libs only; talloc and the ashmem shim are linked
#                           statically)
#   libproot-loader.so    - PRoot's loader stub
#
# The loader is distributed separately and passed via PROOT_LOADER
# (PROOT_UNBUNDLE_LOADER), because Android 10+ forbids exec() outside
# applicationInfo.nativeLibraryDir.
#
# Requires: ANDROID_NDK_HOME (or ANDROID_NDK_ROOT) pointing at an NDK r25+.
set -eu

PROOT_REPO="${PROOT_REPO:-https://github.com/termux/proot.git}"
PROOT_REF="${PROOT_REF:-v5.1.107.102}"
SHMEM_REF="${SHMEM_REF:-v0.7}"
TALLOC_VERSION="${TALLOC_VERSION:-2.4.2}"
OUT="${OUT:-$(cd "$(dirname "$0")" && pwd)/out}"
NPROC="${NPROC:-$(nproc 2>/dev/null || echo 2)}"

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [ -z "$NDK" ] || [ ! -d "$NDK" ]; then
    echo "error: set ANDROID_NDK_HOME to an Android NDK (r25+)" >&2
    exit 1
fi

HOST_TAG=""
for candidate in linux-x86_64 linux-aarch64; do
    if [ -d "$NDK/toolchains/llvm/prebuilt/$candidate" ]; then
        HOST_TAG="$candidate"
        break
    fi
done
[ -n "$HOST_TAG" ] || { echo "error: no NDK prebuilt toolchain found" >&2; exit 1; }

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG"
API="${ANDROID_API:-26}"
CC="$TOOLCHAIN/bin/aarch64-linux-android${API}-clang"
AR="$TOOLCHAIN/bin/llvm-ar"
STRIP="$TOOLCHAIN/bin/llvm-strip"
READELF="$TOOLCHAIN/bin/llvm-readelf"

[ -x "$CC" ] || { echo "error: compiler not found: $CC" >&2; exit 1; }

WORK="${WORK:-$(cd "$(dirname "$0")" && pwd)/proot-build}"
rm -rf "$WORK"
mkdir -p "$WORK" "$OUT" "$WORK/sysroot/lib" "$WORK/sysroot/include"
mkdir -p "$WORK/hostbin"
export PATH="$WORK/hostbin:$PATH"

# ---------------------------------------------------------------------------
# 1. libandroid-shmem (static) - Android has no SysV shared memory; Termux
#    PRoot links this shim that emulates it over ashmem.
# ---------------------------------------------------------------------------
echo "==> libandroid-shmem"
git clone --depth 1 --branch "$SHMEM_REF" https://github.com/termux/libandroid-shmem.git "$WORK/shmem"
(
    cd "$WORK/shmem"
    # Android's <paths.h> does not define _PATH_TMP; the shim needs it.
    # Built as a static archive so libproot.so is self-contained.
    "$CC" -fPIC -O2 -D_PATH_TMP='"/tmp/"' -c shmem.c -o shmem.o
    "$AR" rcs libandroid-shmem.a shmem.o
    mkdir -p "$WORK/sysroot/include/sys"
    cp shm.h "$WORK/sysroot/include/sys/shm.h"
    cp libandroid-shmem.a "$WORK/sysroot/lib/"
)

# ---------------------------------------------------------------------------
# 2. talloc (static) - PRoot's allocator. Built with Samba's waf in
#    cross-compile mode (aarch64 objects cannot run on the runner).
# ---------------------------------------------------------------------------
echo "==> talloc"
mkdir -p "$WORK/talloc"
TALLOC_TARBALL="$WORK/talloc.tar.gz"
# samba.org is intermittently unreachable from CI runners; try fast mirrors
# first (Debian ships the pristine upstream tarball) and fall back gracefully.
downloaded=0
for url in \
    "https://deb.debian.org/debian/pool/main/t/talloc/talloc_${TALLOC_VERSION}.orig.tar.gz" \
    "https://www.samba.org/ftp/talloc/talloc-${TALLOC_VERSION}.tar.gz" \
    "https://download.samba.org/pub/talloc/talloc-${TALLOC_VERSION}.tar.gz"; do
    echo "  trying $url"
    if curl -fsSL --retry 3 --retry-delay 3 --retry-all-errors --connect-timeout 15 --max-time 90 \
        "$url" -o "$TALLOC_TARBALL" && [ -s "$TALLOC_TARBALL" ] \
        && tar tzf "$TALLOC_TARBALL" >/dev/null 2>&1; then
        downloaded=1
        break
    fi
    rm -f "$TALLOC_TARBALL"
done
[ "$downloaded" = 1 ] || { echo "error: could not download a valid talloc tarball" >&2; exit 1; }
tar xzf "$TALLOC_TARBALL" -C "$WORK/talloc" --strip-components=1
test -f "$WORK/talloc/configure" || { echo "error: talloc tarball missing configure" >&2; exit 1; }
cat > "$WORK/talloc/cross-answers.txt" <<'EOF'
Checking uname sysname type: "Linux"
Checking uname machine type: "dontcare"
Checking uname release type: "dontcare"
Checking uname version type: "dontcare"
Checking simple C program: OK
building library support: OK
Checking for large file support: OK
Checking for -D_FILE_OFFSET_BITS=64: OK
Checking for WORDS_BIGENDIAN: OK
Checking for C99 vsnprintf: OK
Checking for HAVE_SECURE_MKSTEMP: OK
rpath library support: OK
-Wl,--version-script support: FAIL
Checking correct behavior of strtoll: OK
Checking correct behavior of strptime: OK
Checking for HAVE_IFACE_GETIFADDRS: OK
Checking for HAVE_IFACE_IFCONF: OK
Checking for HAVE_IFACE_IFREQ: OK
Checking getconf LFS_CFLAGS: OK
Checking for large file support without additional flags: OK
Checking for working strptime: OK
Checking for HAVE_SHARED_MMAP: OK
Checking for HAVE_MREMAP: OK
Checking for HAVE_INCOHERENT_MMAP: OK
Checking getconf large file support flags work: OK
EOF
# The bundled waf bootstrap shells out to `which`.
cat > "$WORK/hostbin/which" <<'SHIM'
#!/bin/sh
# Minimal `which` for the waf bootstrap.
status=1
for arg in "$@"; do
    case "$arg" in
        -a) continue ;;
    esac
    found=$(command -v "$arg" 2>/dev/null)
    if [ -n "$found" ]; then
        echo "$found"
        status=0
    fi
done
exit $status
SHIM
chmod +x "$WORK/hostbin/which"
(
    cd "$WORK/talloc"
    CFLAGS="-O2 -fPIC" CC="$CC" AR="$AR" PYTHON=python3 ./configure \
        --prefix="$WORK/sysroot" --disable-rpath --disable-python \
        --cross-compile --cross-answers=cross-answers.txt > configure.log 2>&1 \
        || { tail -40 configure.log; exit 1; }
    make -j"$NPROC" > make.log 2>&1 || { tail -40 make.log; exit 1; }
    ( cd bin/default && "$AR" rcu libtalloc.a talloc*.o )
    test -s bin/default/libtalloc.a || { echo "error: libtalloc.a not produced" >&2; exit 1; }
    cp bin/default/libtalloc.a "$WORK/sysroot/lib/"
    cp talloc.h "$WORK/sysroot/include/"
)

# ---------------------------------------------------------------------------
# 3. PRoot + loader (dynamic against Bionic, talloc linked statically).
# ---------------------------------------------------------------------------
echo "==> PRoot $PROOT_REF"
git clone --depth 1 --branch "$PROOT_REF" "$PROOT_REPO" "$WORK/proot"

# ashmem_memfd.c uses strcmp/memset without including <string.h>. Under the
# NDK's stricter C mode this is an error, and the loader is freestanding so a
# global -include would break it. Patch the single file instead.
ASHMEM="$WORK/proot/src/extension/ashmem_memfd/ashmem_memfd.c"
if [ -f "$ASHMEM" ] && ! grep -q "include <string.h>" "$ASHMEM"; then
    sed -i 's|#include <stdlib.h>|#include <stdlib.h>\n#include <string.h>|' "$ASHMEM"
fi

# We ship the loader as a separate file and pass it via PROOT_LOADER, exactly
# like Termux (PROOT_UNBUNDLE_LOADER). That skips the objcopy-based loader
# wrapping (which needs to run the target's objcopy) and the 32-bit loader
# (unused: the guest is always aarch64). Removing HAS_LOADER_32BIT from the
# ARM64 branch keeps the build to a single static loader.
ARCH="$WORK/proot/src/arch.h"
sed -i '/#define HAS_LOADER_32BIT true/d' "$ARCH"

cd "$WORK/proot/src"

# Pass flags through the environment, not as command-line overrides: the
# GNUmakefile appends (-DPROOT_UNBUNDLE_LOADER, -DWITH_LIBANDROID_SHMEM, …) to
# CFLAGS, and command-line variables would clobber those.
export CFLAGS="-O2 -fPIC -DARG_MAX=131072 -I$WORK/sysroot/include"
# Both talloc and the ashmem shim are linked statically so the resulting
# libproot.so only needs Bionic system libraries (libc, libdl, liblog,
# libandroid). This matters because Android does not reliably honor
# LD_LIBRARY_PATH for app processes.
export LDFLAGS="-Wl,-z,noexecstack -Wl,-z,origin -Wl,-rpath,\$ORIGIN \
    -L$WORK/sysroot/lib -Wl,-Bstatic -ltalloc -landroid-shmem -Wl,-Bdynamic -llog -landroid"

MAKE_ARGS="CC=$CC LD=$CC AR=$AR STRIP=$STRIP \
    OBJCOPY=$TOOLCHAIN/bin/llvm-objcopy OBJDUMP=$TOOLCHAIN/bin/llvm-objdump \
    PROOT_WITH_LIBANDROID_SHMEM=true \
    PROOT_UNBUNDLE_LOADER=/data/local/tmp/libproot-loader.so"

echo "==> loader"
# shellcheck disable=SC2086
make -j"$NPROC" loader/loader $MAKE_ARGS

echo "==> proot"
# shellcheck disable=SC2086
make -j"$NPROC" proot $MAKE_ARGS

"$STRIP" -s proot loader/loader
install -m 0755 proot "$OUT/libproot.so"
install -m 0755 loader/loader "$OUT/libproot-loader.so"

echo "==> Done"
"$READELF" -h "$OUT/libproot.so" 2>/dev/null | grep -E 'Class|Machine|Type' || true
"$READELF" -d "$OUT/libproot.so" 2>/dev/null | grep -iE 'needed' || true
ls -l "$OUT"
