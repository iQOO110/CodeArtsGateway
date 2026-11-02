#!/bin/bash
# build-arm64-ubuntu.sh — 在 aarch64 Ubuntu（如 DSH 手机容器）上编译 CodeArts 网关
# 详见 ARM64_BUILD.md。用法: ./build-arm64-ubuntu.sh [android-sdk 路径] [jvm 路径]

set -e

SDK="${1:-/root/android-sdk}"
JVM="${2:-/usr/lib/jvm/java-17-openjdk-arm64}"
AAPT2_LIBS="/tmp/x86_64-libs"

# 定位 build-tools（优先 35.0.0）
BT_DIR=""
for v in 35.0.0 34.0.0 33.0.1; do
    if [ -d "$SDK/build-tools/$v" ]; then BT_DIR="$SDK/build-tools/$v"; break; fi
done
if [ -z "$BT_DIR" ]; then
    BT_DIR="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
    [ -n "$BT_DIR" ] || { echo "错误: 在 $SDK 下找不到 build-tools"; exit 1; }
fi
AAPT2="$BT_DIR/aapt2"

if [ "$(uname -m)" = "aarch64" ]; then
    command -v qemu-x86_64 >/dev/null || { echo "错误: 缺少 qemu-x86_64 (apt-get install -y qemu-user)"; exit 1; }
    [ -d "$AAPT2_LIBS/usr" ] || { echo "错误: 缺少 $AAPT2_LIBS (x86_64 系统库，见 ARM64_BUILD.md)"; exit 1; }

    # 给 aapt2 打 QEMU wrapper（原始二进制备份为 aapt2.orig）
    if [ ! -f "$AAPT2.orig" ]; then
        cp "$AAPT2" "$AAPT2.orig"
    fi
    cat > "$AAPT2" <<EOF
#!/bin/bash
exec qemu-x86_64 -L $AAPT2_LIBS/usr $AAPT2.orig "\$@"
EOF
    chmod +x "$AAPT2"
    echo "已用 QEMU wrapper 替换 $AAPT2"
fi

export JAVA_HOME="$JVM"
export ANDROID_HOME="$SDK"
echo "sdk.dir=$SDK" > local.properties

./gradlew --no-daemon assembleDebug
echo
echo "BUILD OK → app/build/outputs/apk/debug/app-debug.apk"
