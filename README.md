# 节点链 NodeTree

一个把「问题 → 答案 → 下一个问题」的分支链路交给用户自己搭建的测试 App。

搭好一条链，跑一遍，得到「通过 / 未通过 / 中性」的结论。典型场景是按年龄、性别等条件把人分流到不同的题目上，比如新生入学评估。

Kotlin + Jetpack Compose，数据以 JSON 文档形式存在应用私有目录，不依赖任何后端。

---

## 环境要求

| 项 | 版本 | 说明 |
|---|---|---|
| Android Studio | 2026.1.3 或更新 | 需要能识别 AGP 9 的版本，旧版本打不开这个工程 |
| JDK | 17+ | 用 Studio 自带的 JBR 即可，不必单独装 |
| Android SDK Platform | **android-37.0** | `compileSdk = 37`，缺这个平台无法编译 |
| Gradle | 9.5.0 | 由 wrapper 自动下载，不用手动装 |

运行设备需要 **Android 12（API 31）或更高**。

### 工具链版本

这套组合是配对验证过的，升级任何一项前建议先跑通 `./gradlew build`：

```
AGP            9.3.0
Kotlin         2.2.10
Gradle         9.5.0
Compose BOM    2026.02.01
```

---

## 首次构建

### 1. 配置 SDK 路径

仓库里**没有** `local.properties`（它含本机绝对路径，不应提交）。在项目根目录新建：

```properties
sdk.dir=C\:/Users/你的用户名/AppData/Local/Android/Sdk
```

macOS / Linux：

```properties
sdk.dir=/Users/你的用户名/Library/Android/sdk
```

> **Windows 用户注意**：盘符后面的冒号必须转义成 `\:`，路径分隔符建议直接用正斜杠。
>
> 写成 `C:\Users\...`（未转义的反斜杠）会让 Gradle 报一句毫无线索的
> `java.io.IOException: Invalid file path`；只写 `C:/Users/...`（冒号没转义）能编译过，
> 但 lint 会报 `PropertyEscape` 错误。两个坑都踩过，照上面的格式写最省事。

用 Android Studio 打开工程的话，这个文件会自动生成，可跳过这一步。

### 2. 命令行构建需要设 JAVA_HOME

Studio 内部构建用的是自带的 JBR，命令行不会自动继承。先指向它：

```powershell
# Windows PowerShell
$env:JAVA_HOME = "E:\AndroidStudio\jbr"     # 换成你的 Studio 安装路径
```

```bash
# macOS
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

### 3. 构建

```bash
./gradlew assembleDebug        # 打 debug 包
./gradlew installDebug         # 直接装到已连接的设备
./gradlew testDebugUnitTest    # 跑单元测试
./gradlew lintDebug            # 静态检查
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

首次构建需要联网拉依赖。`settings.gradle.kts` 里已经把**阿里云镜像**配在前面、官方源兜底，国内网络下不用额外配代理。

---

## 关于 release 构建

`./gradlew assembleRelease` 能成功，但产出的是 `app-release-unsigned.apk` —— **未签名，装不上**，会报 `INSTALL_PARSE_FAILED_NO_CERTIFICATES`。

debug 包由 AGP 用调试证书自动签名，所以日常开发不用管签名。要发布则需自行配置 release 签名：生成 keystore、把密码放进不提交的文件（`local.properties` 或 `keystore.properties`，两者都已在 `.gitignore` 里）、在 `app/build.gradle.kts` 中接上 `signingConfig`。

代码混淆当前是关闭的（`optimization { enable = false }`）。发布前建议打开，包体大约能再小三到四成，但要实测一遍。

---

## 模拟器

工程本身不限定设备，但要在低版本上验证兼容性，建议至少备一个 API 31 的镜像：

```bash
sdkmanager "system-images;android-31;google_apis;x86_64"
sdkmanager "system-images;android-36;google_apis;x86_64"
```

Windows 上模拟器依赖 WHPX 硬件加速，可以先自检：

```bash
emulator -accel-check
```

---

## 项目结构

```
app/src/main/java/com/example/nodechain/
├── MainActivity.kt          入口 Activity
├── NodeChainApp.kt          Application + 导航图
├── data/
│   ├── Models.kt            数据模型、链路校验与遍历（纯函数，单元测试主要覆盖这里）
│   └── ChainStore.kt        单例存储：StateFlow + JSON 落盘，编辑即保存
└── ui/
    ├── home/                首页：节点链列表
    ├── editor/              链编辑、节点编辑、链路预览树
    ├── run/                 测试执行与结果
    ├── history/             测试历史
    ├── transfer/            导入导出
    ├── about/               环境自检页
    └── common/              可复用组件
```

### 数据存储

没有用 Room。节点链本质是个带分支的文档而非关系表，用 JSON 更贴合，也省掉 KSP 那层构建复杂度。

两个文件，位于应用私有目录 `files/`：

- `chains.json` —— 所有节点链
- `history.json` —— 测试记录（含测试级批注）

编辑是即时生效的：内存中的 `StateFlow` 立刻更新，落盘做 300ms 防抖，所以界面上没有「保存」按钮。

---

## 改代码前值得知道的几件事

这几条是踩过坑之后定下的约定，照着走能少走弯路。

**输入框统一用 `ui/common/AutoSaveTextField`**，不要直接写 `OutlinedTextField(value = store里的值)`。StateFlow 的回传慢一帧，直接双向绑定在快速输入时会丢字符、跳光标。

**图标只用 `material-icons-core`**。Compose BOM 2026.02.01 起 material3 不再传递依赖图标库，所以要显式声明（版本由 BOM 管）。core 包不含 `ExpandMore`、`ContentCopy`、`History` 等，它们在 extended 里，而 extended 有好几 MB —— 不值得为一两个图标引入。core 里基本都有等价的：`ExpandMore` → `KeyboardArrowDown`，`History` → `DateRange`。

**自适应图标必须留在 `mipmap-anydpi-v26/`**。lint 的 `ObsoleteSdkInt` 会建议把 `-v26` 去掉（因为 minSdk 已是 31），但照做会让 aapt2 资源链接失败，清理重建也救不回来。这条检查已在 `build.gradle.kts` 里单独关闭。

**AGP 9 的 DSL 和 8.x 不一样**，按旧写法改会直接编译失败：

- `compileSdk` 是块语法：`compileSdk { version = release(37) }`
- **不需要**声明 `org.jetbrains.kotlin.android` 插件，Kotlin 支持已内建进 AGP
- 混淆开关是 `buildTypes { release { optimization { enable = false } } }`

---

## 测试

```bash
./gradlew testDebugUnitTest
```

单元测试集中在 `ChainLogicTest`，覆盖链路校验与遍历这些纯逻辑：可达性、断头路检测、成环不死循环、序列化往返、旧版本记录的向后兼容、导入导出的格式识别。

UI 层没有插桩测试。
