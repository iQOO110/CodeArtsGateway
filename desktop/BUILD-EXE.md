# 生成 Windows exe（无需 Android SDK）

本目录（`desktop/`）是 Windows 桌面版，复用 `app/src/main/java/com/codearts/gateway/core` 的网关核心，
配 `desktop/src/main/java/android/**` 里的最小 Android 桩，可在 Windows 上直接生成 GUI 版 exe。

## 前置
- Windows 10/11 x64
- JDK 17（**必须带 `jpackage`**，Oracle JDK / Temurin JDK 均可；只装 JRE 不行）
  下载：https://adoptium.net/temurin/releases/?version=17 （选 JDK，不是 JRE）

## 方式一：一条命令出 exe（推荐）
1. 把 `CodeArtsGateway` 解压到任意目录（例如 `D:\CodeArtsGateway`）。
2. 双击运行 `make-exe.bat`（会自动先构建 jar，再调用 jpackage 生成 exe）。
3. 产物在：
   `desktop\dist\CodeArtsGateway\CodeArtsGateway.exe`
   该目录已内置运行时 JRE，拷走整个 `CodeArtsGateway` 文件夹即可在任意 Windows 机器运行，无需装 Java。

## 方式二：手动三步
1. 构建可运行 jar（二选一）：
   - 有网络：在项目根目录运行 `gradlew.bat :desktop:fatJar`，得到
     `desktop\build\libs\codearts-gateway-desktop.jar`
   - 无网络/无 Gradle：双击 `build-desktop.bat`（用 javac + PowerShell 合并依赖）
2. 生成 exe（JDK 17 的 `jpackage` 在 PATH 或 `%JAVA_HOME%\bin`）：
   ```bat
   jpackage --type app-image --name CodeArtsGateway ^
     --input desktop\build\exe-input ^
     --main-jar codearts-gateway-desktop.jar ^
     --main-class com.codearts.gateway.desktop.Main ^
     --dest desktop\dist
   ```
   其中 `desktop\build\exe-input` 里只放那个 jar。
3. 运行 `desktop\dist\CodeArtsGateway\CodeArtsGateway.exe`。

## 使用
- 打开窗口后填 **码道 Cookie**（含 `cftk`），按需改端口/密钥/开关，点“保存并启动”。
- 监听地址默认 `http://127.0.0.1:8080`（端口可在窗口改），OpenAI 兼容接口：
  `POST /v1/chat/completions`、`GET /v1/models`、`GET /health`。
- 产物默认落在 `%USERPROFILE%\CodeArtsGateway\Download\...`，窗口有“打开产物目录”。
  想让 agent 直接读到产物，把这个目录加进 agent 的工作区即可。
- 配置存于 `%USERPROFILE%\CodeArtsGateway\config\codearts_gateway.properties`。
- 无界面模式：`CodeArtsGateway.exe --headless`，或 `--base=D:\data` 指定数据目录。

## 文件清单
- `desktop/src/main/java/com/codearts/gateway/desktop/`：`Main`（入口）、`Gui`（Swing 窗口）
- `desktop/src/main/java/android/**`：桌面 Android 桩
- `desktop/libs/androidasync-3.1.0.jar`、`json-20240303.jar`：依赖
- `desktop/build/libs/codearts-gateway-desktop.jar`：已预编译的可运行 jar（含 core）
- `make-exe.bat` / `build-desktop.bat`：构建脚本
- `../app/src/main/java/com/codearts/gateway/core/`：网关核心源码（desktop 直接编译它）