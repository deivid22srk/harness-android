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
#   libproot.so           - the PRoot binary
#   libproot-loader.so    - PRoot's loader stub
#   libandroid-shmem.so   - SysV shm↔ashmem shim (Android lacks SysV shm)
#
# talloc is linked *statically* into libproot.so, so no talloc library is
# needed at runtime. Bionic's libc/liblog/libandroid are provided by the
# platform. The app bundles the .so files above and sets LD_LIBRARY_PATH.
#
# Requires: ANDROID_NDK_HOME (or ANDROID_NDK_ROOT) pointing at an NDK r25+.
set -eu

PROOT_REPO="${PROOT_REPO:-https://github.com/termux/proot.git}"
PROOT_REF="${PROOT_REF:-v5.1.107.102}"
SHMEM_REF="${SHMEM_REF:-v0.7}"
TALLOC_VERSION="${TALLOC_VERSION:-2.5.0}"
OUT="${OUT:-$(pwd)/out}"
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

WORK="$(pwd)/proot-build"
rm -rf "$WORK"
mkdir -p "$WORK" "$OUT" "$WORK/sysroot/lib" "$WORK/sysroot/include"
mkdir -p "$WORK/hostbin"
export PATH="$WORK/hostbin:$PATH"

# ---------------------------------------------------------------------------
# 1. libandroid-shmem.so - endian/ABI-correct SysV shared memory over ashmem.
# ---------------------------------------------------------------------------
echo "==> libandroid-shmem"
git clone --depth 1 --branch "$SHMEM_REF" https://github.com/termux/libandroid-shmem.git "$WORK/shmem"
(
    cd "$WORK/shmem"
    # Android's <paths.h> does not define _PATH_TMP; the shim needs it.
    "$CC" -fPIC -O2 -D_PATH_TMP='"/tmp/"' -c shmem.c -o shmem.o
    "$CC" -shared -Wl,--version-script=exports.txt -Wl,-soname,libandroid-shmem.so \
        -o libandroid-shmem.so shmem.o -llog -landroid
    mkdir -p "$WORK/sysroot/include/sys"
    cp shm.h "$WORK/sysroot/include/sys/shm.h"
    cp libandroid-shmem.so "$WORK/sysroot/lib/"
    cp libandroid-shmem.so "$OUT/"
)

# ---------------------------------------------------------------------------
# 2. talloc (static) - PRoot's allocator. Built with Samba's waf in
#    cross-compile mode (aarch64 objects cannot run on the runner).
# ---------------------------------------------------------------------------
echo "==> talloc $TALLOC_VERSION"
mkdir -p "$WORK/talloc"
curl -sSL "https://www.samba.org/ftp/talloc/talloc-${TALLOC_VERSION}.tar.gz" \
    | tar xz -C "$WORK/talloc" --strip-components=1
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
printf '#!/bin/sh\nexec command -v "$1"\n' > "$WORK/hostbin/which"
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
cd "$WORK/proot/src"

CFLAGS="-O2 -fPIC -DARG_MAX=131072 -I$WORK/sysroot/include"
# -landroid-shmem resolves against the shared shim we bundle; talloc is static.
LDFLAGS="-Wl,-z,noexecstack -L$WORK/sysroot/lib -Wl,-Bstatic -ltalloc -Wl,-Bdynamic -landroid-shmem"

echo "==> loader"
make -j"$NPROC" loader/loader CC="$CC" LD="$CC" AR="$AR" STRIP="$STRIP" CFLAGS="$CFLAGS"

echo "==> proot"
make -j"$NPROC" proot CC="$CC" LD="$CC" AR="$AR" STRIP="$STRIP" \
    CFLAGS="$CFLAGS" LDFLAGS="$LDFLAGS" PROOT_WITH_LIBANDROID_SHMEM=true

"$STRIP" -s proot loader/loader
install -m 0755 proot "$OUT/libproot.so"
install -m 0755 loader/loader "$OUT/libproot-loader.so"

echo "==> Done"
"$READELF" -h "$OUT/libproot.so" 2>/dev/null | grep -E 'Class|Machine|Type' || true
"$READELF" -d "$OUT/libproot.so" 2>/dev/null | grep -iE 'needed' || true
ls -l "$OUT"
