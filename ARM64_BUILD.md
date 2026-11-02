# 在 ARM64 Ubuntu（如 DSH 手机容器）上编译

Google 官方 build-tools 里的 `aapt2` 原生二进制只有 x86_64 版本，
在 aarch64 Linux 上直接执行会报 `cannot execute: required file not found`。
本仓库提供了 `build-arm64-ubuntu.sh`，通过 QEMU 用户态模拟运行 aapt2，完成完整构建。

## 一次性环境准备

```bash
# 1. JDK 17
apt-get update && apt-get install -y openjdk-17-jdk unzip

# 2. Android SDK 命令行工具（153MB）
python3 - <<'EOF'
import urllib.request, zipfile, os
url = 'https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip'
out = '/tmp/cmdline.zip'
with urllib.request.urlopen(url, timeout=600) as r:
    data = r.read()
with open(out, 'wb') as f:
    f.write(data)
os.makedirs('/root/android-sdk', exist_ok=True)
zipfile.ZipFile(out).extractall('/root/android-sdk')
EOF

# 3. 安装 SDK 组件（SDK 根 = /root/android-sdk）
/root/android-sdk/cmdline-tools/bin/sdkmanager --no_https \
  "build-tools;35.0.0" "platform-tools" "platforms;android-34"

# 4. QEMU 用户态模拟 + x86_64 系统库（aapt2 依赖）
apt-get install -y qemu-user

# 下载并解压 x86_64 的 libc6 / libc-bin / libgcc-s1
python3 - <<'EOF'
import urllib.request, subprocess, os
base = 'http://archive.ubuntu.com/ubuntu/pool/main/g/'
def fetch(rel, dest):
    with urllib.request.urlopen(base + rel, timeout=120) as r:
        open(dest, 'wb').write(r.read())
    print('fetched', rel)
os.makedirs('/tmp/x86_64-libs', exist_ok=True)
fetch('glibc/libc6_2.39-0ubuntu8_amd64.deb',     '/tmp/libc6.deb')
fetch('glibc/libc-bin_2.39-0ubuntu8_amd64.deb',   '/tmp/libcbin.deb')
fetch('gcc-14/libgcc-s1_14-20240412-0ubuntu1_amd64.deb', '/tmp/libgcc.deb')
for d in ('/tmp/libc6.deb', '/tmp/libcbin.deb', '/tmp/libgcc.deb'):
    subprocess.run(['dpkg', '-x', d, '/tmp/x86_64-libs'], check=True)
print('libs extracted to /tmp/x86_64-libs')
EOF

# 5. 验证 QEMU 能跑 x86_64 aapt2
qemu-x86_64 -L /tmp/x86_64-libs/usr /root/android-sdk/build-tools/35.0.0/aapt2 version
# 应输出: Android Asset Packaging Tool (aapt) 2.19-xxxxx
```

> 注：SDK 组件安装步骤在部分环境里需要接受 license（交互式 `y`），
> 或先执行 `sdkmanager --licenses` 一次性接受。

## 补丁：让 Gradle 走 QEMU 版 aapt2

默认情况下 Gradle 会从 build-tools 目录取 aapt2，无法在 ARM64 执行。
`build-arm64-ubuntu.sh` 在构建前自动把 build-tools 的 aapt2 替换为
调用 `qemu-x86_64 -L /tmp/x86_64-libs/usr <原始 aapt2>` 的 shell wrapper。
原始二进制备份在同目录的 `aapt2.orig`。

## 构建

```bash
./build-arm64-ubuntu.sh
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

脚本内部等价于：

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export ANDROID_HOME=/root/android-sdk
echo "sdk.dir=/root/android-sdk" > local.properties   # 路径不同请改这里
./gradlew --no-daemon assembleDebug
```

## 已知问题

- `aapt2FromMavenOverride=true` 不可用：Maven 的 aapt2 JAR 里内嵌的同样是
  x86_64 原生二进制，不能解决架构问题。
- 本仓库的 `gradle.properties` 保持与官方发布一致（未启用 Maven 覆盖）。
